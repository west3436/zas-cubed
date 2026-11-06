package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.EnclosureClassifier;
import io.github.west3436.zas.engine.scheduler.GeometryAudit;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GameTest half of the integrity fuzz (NFR-010): one seed's run of random block edits, gas
 * operations, chunk unloads and reloads and save/load cycles in a real level, with pipes, vents and
 * canisters, which the engine half can't have. CORE-073 compares only zones, so this one totals the gas
 * in them as well.
 *
 * <ul>
 *   <li><b>The world.</b> Four rooms across four chunks, joined by corridors and two rows of pipes,
 *       inside stone the run never edits. Nothing here is open to the air, so the fuzz's gas can leave
 *       only in ways it can see. The ambient doesn't hold it.
 *   <li><b>CORE-073.</b> Every atmos tick has the checker on, and any unexplained change fails.
 *   <li><b>An independent total.</b> Every {@value #CHECKPOINT} operations every chunk loads and the
 *       world settles. Then {@link GasCensus} across zones, networks and devices must equal what the run
 *       put in and took out itself, and what the ledger's categories record other than API and devices,
 *       which is gas devices move and stays in the total, and storage. Gas voided or supplied at paused
 *       zones (GEO-036, GEO-038) is by design and counts as recorded, as does gas sent to the open air
 *       at a position with no cell, and only there.
 *   <li><b>What's written.</b> Now and then a real save runs, and what the chunks wrote of the gas must
 *       equal what the pipes and devices hold.
 *   <li><b>GEO-032 and CORE-063.</b> At the same points the audit must find nothing, and everything must
 *       hold finite amounts, none negative.
 * </ul>
 *
 * <p>A chunk is "unloaded" with {@link DimensionAtmos#simulateUnload}, which leaves its block entities
 * where they are, since a GameTest can't unload a chunk on demand. Really unloading pipes is {@link
 * PipeSaveTests}' job. Save/load cycles are clean stops: what a crash restores (#304) isn't tested.
 *
 * <p>Nothing is excused here. The run stays away from what the engine half has to allow for: a chunk unloads
 * with no block change queued and none held, so the limit on DATA-008 can't come up, and the atmos ticks
 * straight after a reload, so no share loses all its cells and has its gas dropped.
 *
 * <p>Everything runs within one game tick from the seed, on atmos ticks and device steps it drives itself.
 * A failure names the seed, the operation and the command that replays it. It has a batch to itself, as
 * the ledger covers the whole dimension.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntegrityFuzzTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrityFuzzTests.class);
    private static final String BATCH = "zas_atmos_integrity_fuzz";

    /** The seed every PR runs. */
    private static final long PR_SEED = 3;

    /** The operations of a run the requirement asks for. */
    private static final int DEFAULT_OPS = 10_000;

    /** How many operations apart the world settles and is checked. */
    private static final int CHECKPOINT = 250;

    /** How many checkpoints apart a real save runs. */
    private static final int REAL_SAVE = 8;

    /** How many of the last operations a failure lists. */
    private static final int TRACE = 30;

    private static final int MAX_SETTLE_TICKS = 64;
    private static final int FIRST_AREA = 2100;
    private static final int LAST = 31; // the stone's far side on x and z
    private static final int ROOF = 6;
    private static final ResourceLocation GAS = PipeSaveTests.GAS;

    /** The rooms' insides: x0, z0, x1, z1, all from y = 1 to 5. */
    private static final int[][] ROOMS = {{2, 2, 13, 13}, {18, 2, 29, 13}, {2, 18, 13, 29}, {18, 18, 29, 29}};

    /** What devices sent to or took from the open air since the last atmos tick was booked. */
    private static final List<OpenAirGasEvent> OPEN_AIR = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((OpenAirGasEvent e) -> {
            if (!e.source().equals(Area.SOURCE)) {
                OPEN_AIR.add(e);
            }
        });
    }

    private IntegrityFuzzTests() {}

    private static long property(String name, long otherwise) {
        return Long.getLong(name, otherwise);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void nfr_010_integrity_under_fuzzing_with_pipes(GameTestHelper h) {
        long first = property("zas.fuzz.seed", PR_SEED);
        long seeds = property("zas.fuzz.seeds", 1);
        int ops = (int) property("zas.fuzz.ops", DEFAULT_OPS);
        List<Long> failed = new ArrayList<>();
        var sequence = h.startSequence();
        for (long i = 0; i < seeds; i++) {
            long seed = first + i;
            // Every seed has its run: one failing doesn't stop the sequence, so each logs its own failure.
            sequence = sequence.thenExecute(() -> {
                // The same area each time, so a seed does the same on its own as among others.
                Run run = new Run(h, FIRST_AREA, seed, ops);
                long start = System.nanoTime();
                try {
                    run.run();
                    if (ops >= DEFAULT_OPS) {
                        run.assertExercised();
                    }
                    LOGGER.info(
                            "NFR-010: seed {} with pipes, {} operations, {} ms: {}",
                            seed,
                            ops,
                            (System.nanoTime() - start) / 1_000_000,
                            run);
                } catch (GameTestAssertException e) {
                    LOGGER.error(e.getMessage());
                    failed.add(seed);
                }
            });
        }
        sequence.thenExecute(() -> h.assertTrue(failed.isEmpty(), "NFR-010: seeds with pipes failed: " + failed))
                .thenSucceed();
    }

    /** Gas made in a pipe is no change to any zone, so CORE-073 says nothing: only the total can catch it. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void nfr_010_gas_made_in_a_pipe_fails_the_total(GameTestHelper h) {
        h.startSequence()
                .thenExecute(() -> {
                    Run run = new Run(h, FIRST_AREA - 1, 5, 300);
                    run.makeGasAt = 100;
                    String message = null;
                    try {
                        run.run();
                    } catch (GameTestAssertException e) {
                        message = e.getMessage();
                    }
                    h.assertTrue(message != null, "gas made in a pipe wasn't noticed");
                    h.assertTrue(message.contains("seed 5 failed at operation 249"), message);
                    h.assertTrue(message.contains("-Pfuzz.seed=5"), message);
                    h.assertTrue(message.contains("the pipes and zones hold"), message);
                })
                .thenSucceed();
    }

    /** One seed's run. */
    private static final class Run {
        final GameTestHelper h;
        final Area a;
        final long seed;
        final int ops;
        final Random random;
        final ChunkPos[] chunks;
        final long[] chunkKeys;
        /** The chunks away, and the bytes each saved, which may be null. */
        final Map<ChunkPos, byte[]> away = new LinkedHashMap<>();

        final Set<Long> kept = new HashSet<>();
        final List<int[]> row = new ArrayList<>();
        final ArrayDeque<String> trace = new ArrayDeque<>();
        int gas;
        int op;
        int makeGasAt = -1;
        double expected;
        double moved;
        ConservationReport lastReport;

        int blockEdits;
        int apiOps;
        int pipeFills;
        int ventCommands;
        int unloads;
        int reloads;
        int saveLoads;
        int realSaves;
        int ticks;
        int checkpoints;
        double voided;
        double supplied;
        double openAir;

        Run(GameTestHelper h, int area, long seed, int ops) {
            this.h = h;
            this.a = new Area(h, area);
            this.seed = seed;
            this.ops = ops;
            this.random = new Random(seed);
            this.chunks = new ChunkPos[] {
                new ChunkPos(a.abs(0, 0, 0)),
                new ChunkPos(a.abs(16, 0, 0)),
                new ChunkPos(a.abs(0, 0, 16)),
                new ChunkPos(a.abs(16, 0, 16))
            };
            this.chunkKeys = new long[chunks.length];
            for (int i = 0; i < chunks.length; i++) {
                chunkKeys[i] = chunks[i].toLong();
            }
        }

        void run() {
            boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
            ZasServerConfig.CONSERVATION_CHECKER.set(true);
            // A burst chooses its segment with the level's random (CONT-108), and a seed must replay.
            a.level.getRandom().setSeed(seed);
            for (ChunkPos chunk : chunks) {
                PipeSaveTests.hold(a, chunk, true);
            }
            try {
                OPEN_AIR.clear();
                restart(Map.of());
                gas = atmos().registry().indexOf(GAS.toString());
                build();
                for (op = 0; op < ops; op++) {
                    if (op == makeGasAt) {
                        makeGas();
                    }
                    operate();
                    if (op % CHECKPOINT == CHECKPOINT - 1 || op == ops - 1) {
                        checkpoint();
                    }
                }
            } catch (Throwable t) {
                throw new GameTestAssertException("NFR-010: seed " + seed + " failed at operation " + op + " of " + ops
                        + ": " + t + "\nReplay: ./gradlew :zas-atmos:runGameTestServer -Pfuzz.seed=" + seed
                        + "\nThe last operations:\n" + String.join("\n", trace));
            } finally {
                cleanUp();
                ZasServerConfig.CONSERVATION_CHECKER.set(was);
            }
        }

        void assertExercised() {
            h.assertTrue(blockEdits > 500, "block edits: " + this);
            h.assertTrue(apiOps > 200, "gas operations: " + this);
            h.assertTrue(pipeFills > 100, "gas put in pipes: " + this);
            h.assertTrue(ventCommands > 10, "vent commands: " + this);
            h.assertTrue(unloads > 100 && reloads > 100, "unloads and reloads: " + this);
            h.assertTrue(saveLoads > 10, "save/load cycles: " + this);
            h.assertTrue(realSaves > 2, "real saves: " + this);
            h.assertTrue(voided > 0, "gas voided at paused zones: " + this);
            h.assertTrue(checkpoints >= 40, "checkpoints: " + this);
        }

        @Override
        public String toString() {
            return blockEdits + " block edits, " + apiOps + " API operations, " + pipeFills + " fills of pipes, "
                    + ventCommands + " vent commands, " + unloads + " unloads, " + reloads + " reloads, " + saveLoads
                    + " save/load cycles, " + realSaves + " real saves, " + ticks + " atmos ticks, " + checkpoints
                    + " checkpoints, " + voided + " mol voided, " + supplied + " mol supplied, " + openAir
                    + " mol to or from the open air";
        }

        DimensionAtmos atmos() {
            return a.service().dimensionFor(a.level);
        }

        void log(String what) {
            if (trace.size() == TRACE) {
                trace.removeFirst();
            }
            trace.addLast(op + ": " + what);
        }

        // ---- The world ------------------------------------------------------------------------

        void build() {
            for (BlockPos p : BlockPos.betweenClosed(a.abs(0, 0, 0), a.abs(LAST, ROOF, LAST))) {
                a.level.setBlockAndUpdate(p, Area.STONE);
            }
            for (int[] room : ROOMS) {
                for (int x = room[0]; x <= room[2]; x++) {
                    for (int y = 1; y < ROOF; y++) {
                        for (int z = room[1]; z <= room[3]; z++) {
                            a.set(x, y, z, Area.AIR);
                        }
                    }
                }
            }
            for (int i = 14; i <= 17; i++) {
                a.set(i, 3, 7, Area.AIR);
                a.set(i, 3, 24, Area.AIR);
                a.set(7, 3, i, Area.AIR);
                a.set(24, 3, i, Area.AIR);
            }
            // A row west to east across x = 16, and one north to south across z = 16, each with a canister.
            vent(9, 3, 4, Direction.WEST);
            for (int x = 10; x <= 21; x++) {
                pipe(x, 3, 4);
            }
            vent(22, 3, 4, Direction.EAST);
            vent(25, 3, 9, Direction.NORTH);
            for (int z = 10; z <= 21; z++) {
                pipe(25, 3, z);
            }
            vent(25, 3, 22, Direction.SOUTH);
            canister(12, 4);
            canister(25, 12);
        }

        void pipe(int x, int y, int z) {
            a.place(x, y, z, GasDeviceTests.pipe());
            row.add(new int[] {x, y, z, -1});
        }

        void vent(int x, int y, int z, Direction facing) {
            placeVent(x, y, z, facing);
            row.add(new int[] {x, y, z, facing.ordinal()});
        }

        void placeVent(int x, int y, int z, Direction facing) {
            a.place(x, y, z, GasDeviceTests.vent(facing));
            VentBlockEntity vent = a.entity(x, y, z);
            vent.power().setTestPowered(true);
            vent.command(VentSettings.blow(200));
        }

        /** A port on a row and a canister on the port, which are left alone. */
        void canister(int x, int z) {
            a.place(x, 4, z, GasDeviceTests.port());
            a.place(x, 5, z, GasDeviceTests.canister());
            kept.add(a.abs(x, 4, z).asLong());
            kept.add(a.abs(x, 5, z).asLong());
        }

        /** Removes what's left without its gas: the next run starts with no zones to record against it. */
        void cleanUp() {
            for (BlockPos p : BlockPos.betweenClosed(a.abs(0, 0, 0), a.abs(LAST, ROOF, LAST))) {
                a.level.setBlockAndUpdate(p, Area.AIR);
            }
            away.keySet().forEach(chunk -> atmos().simulateLoad(chunk, null));
            away.clear();
            a.tick();
            restart(Map.of());
            for (ChunkPos chunk : chunks) {
                PipeSaveTests.hold(a, chunk, false);
            }
        }

        void restart(Map<Long, byte[]> saved) {
            a.service().stopForTest(a.level);
            a.service().startForTest(a.level, saved);
        }

        boolean isAway(int x, int y, int z) {
            return away.containsKey(new ChunkPos(a.abs(x, y, z)));
        }

        // ---- Operations -----------------------------------------------------------------------

        void operate() {
            int r = random.nextInt(100);
            if (r < 20) {
                add();
            } else if (r < 28) {
                remove();
            } else if (r < 48) {
                edit();
            } else if (r < 58) {
                editRow();
            } else if (r < 66) {
                fillPipe();
            } else if (r < 72) {
                commandVent();
            } else if (r < 82) {
                unload();
            } else if (r < 92) {
                reload();
            } else if (r < 93) {
                saveAndLoad();
            }
            if (r >= 93 || random.nextInt(3) == 0) {
                step();
            }
        }

        /** A position inside a room, mostly, as most operations go where the gas is. */
        int[] target() {
            if (random.nextInt(8) == 0) {
                return new int[] {
                    1 + random.nextInt(LAST - 1), 1 + random.nextInt(ROOF - 1), 1 + random.nextInt(LAST - 1)
                };
            }
            int[] room = ROOMS[random.nextInt(ROOMS.length)];
            return new int[] {
                room[0] + random.nextInt(room[2] - room[0] + 1),
                1 + random.nextInt(ROOF - 1),
                room[1] + random.nextInt(room[3] - room[1] + 1)
            };
        }

        double amount() {
            return switch (random.nextInt(20)) {
                case 0 -> random.nextDouble() * 1e-9;
                case 1 -> random.nextDouble() * 1e5;
                default -> random.nextDouble() * 100;
            };
        }

        void add() {
            int[] p = target();
            if (!a.get(p[0], p[1], p[2]).isAir()) {
                return;
            }
            double moles = amount();
            log("add " + moles + " at " + str(p));
            MutationResult result = settle(GasMutations.add(
                    a.level, a.point(p[0], p[1], p[2]), new GasParcel(Map.of(GAS, moles), T), Area.SOURCE));
            account(result, result.moved().moles(GAS));
            apiOps++;
        }

        void remove() {
            int[] p = target();
            if (!a.get(p[0], p[1], p[2]).isAir()) {
                return;
            }
            double moles = amount();
            log("remove " + moles + " at " + str(p));
            MutationResult result =
                    settle(GasMutations.remove(a.level, a.point(p[0], p[1], p[2]), Map.of(GAS, moles), Area.SOURCE));
            account(result, -result.moved().moles(GAS));
            apiOps++;
        }

        /**
         * What the run itself put in or took out. The API's category can't say, as devices use it for
         * what they move. At a paused zone this is undone by what the ledger says is voided or supplied;
         * the open air has no entry, and none is needed, as the gas never counted.
         */
        void account(MutationResult result, double change) {
            if (result.outcome() != MutationOutcome.AMBIENT) {
                expected += change;
                moved += Math.abs(change);
            }
        }

        /** Turns stone to air or air to stone, and takes away a pipe or vent. Mostly in a corridor. */
        void edit() {
            int[] p = random.nextBoolean() ? corridor() : target();
            int x = p[0];
            int y = p[1];
            int z = p[2];
            if (isAway(x, y, z) || kept.contains(a.abs(x, y, z).asLong())) {
                return;
            }
            BlockState now = a.get(x, y, z);
            BlockState next = now.is(Area.STONE.getBlock()) || !now.isAir() ? Area.AIR : Area.STONE;
            log("set " + str(p) + " to " + next.getBlock());
            a.set(x, y, z, next);
            blockEdits++;
        }

        int[] corridor() {
            int along = 14 + random.nextInt(4);
            return switch (random.nextInt(4)) {
                case 0 -> new int[] {along, 3, 7};
                case 1 -> new int[] {along, 3, 24};
                case 2 -> new int[] {7, 3, along};
                default -> new int[] {24, 3, along};
            };
        }

        /** Takes a part of a row away, or puts it back. */
        void editRow() {
            int[] part = row.get(random.nextInt(row.size()));
            int x = part[0];
            int y = part[1];
            int z = part[2];
            if (isAway(x, y, z)) {
                return;
            }
            if (a.level.getBlockEntity(a.abs(x, y, z)) != null) {
                log("take away the row at " + str(part));
                a.set(x, y, z, Area.AIR);
            } else if (part[3] < 0) {
                log("put a pipe at " + str(part));
                a.place(x, y, z, GasDeviceTests.pipe());
            } else {
                log("put a vent at " + str(part));
                placeVent(x, y, z, Direction.values()[part[3]]);
            }
            blockEdits++;
        }

        /** Puts gas straight into what a row's part belongs to, as a device or a player's tank would. */
        void fillPipe() {
            int[] part = row.get(random.nextInt(row.size()));
            fill(part[0], part[1], part[2], amount());
        }

        void fill(int x, int y, int z, double moles) {
            if (a.level.getBlockEntity(a.abs(x, y, z)) instanceof PipeNodeEntity node) {
                log("fill the pipe at " + x + ", " + y + ", " + z + " with " + moles);
                node.network().gas().add(new GasParcel(Map.of(GAS, moles), T));
                expected += moles;
                moved += moles;
                pipeFills++;
            }
        }

        /** Gas nothing accounts for, in the first part of a row still there, for the test of this test. */
        void makeGas() {
            for (int[] part : row) {
                if (a.level.getBlockEntity(a.abs(part[0], part[1], part[2])) instanceof PipeNodeEntity node) {
                    node.network().gas().add(new GasParcel(Map.of(GAS, 50.0), T));
                    return;
                }
            }
            throw new IllegalStateException("no part of a row is left to make gas in");
        }

        void commandVent() {
            int[] part = row.get(random.nextInt(row.size()));
            if (part[3] >= 0
                    && a.level.getBlockEntity(a.abs(part[0], part[1], part[2])) instanceof VentBlockEntity vent) {
                double target = 50 + random.nextDouble() * 500;
                VentSettings settings = switch (random.nextInt(3)) {
                    case 0 -> VentSettings.OFF;
                    case 1 -> VentSettings.blow(target);
                    default -> VentSettings.siphon(target);
                };
                log("command the vent at " + str(part) + ": " + settings);
                vent.power().setTestPowered(true);
                vent.command(settings);
                ventCommands++;
            }
        }

        void unload() {
            ChunkPos chunk = chunks[random.nextInt(chunks.length)];
            if (away.containsKey(chunk)) {
                reload();
                return;
            }
            // Nothing queued: a block change could divide a zone just after its share is written, which is
            // a limit on DATA-008 that the engine half excuses, and this half has no need to.
            tick();
            if (holdsChange(chunk)) {
                // It could come back with all of a share's cells gone, whose gas is dropped by design.
                reload();
                return;
            }
            if (random.nextInt(4) == 0) {
                // Gas on its way as its chunk unloads is voided, and the ledger says so (GEO-036).
                DeviceTicker.stepNow(a.level);
            }
            log("unload " + chunk);
            away.put(chunk, atmos().simulateUnload(chunk));
            unloads++;
        }

        boolean holdsChange(ChunkPos chunk) {
            return LongStream.of(atmos().pipeline().heldBlocks())
                    .anyMatch(held ->
                            SectionKeys.blockX(held) >> 4 == chunk.x && SectionKeys.blockZ(held) >> 4 == chunk.z);
        }

        void reload() {
            if (away.isEmpty()) {
                return;
            }
            List<ChunkPos> keys = new ArrayList<>(away.keySet());
            reload(keys.get(random.nextInt(keys.size())));
        }

        void reload(ChunkPos chunk) {
            log("reload " + chunk);
            atmos().simulateLoad(chunk, away.remove(chunk));
            reloads++;
            // Before anything else changes: a share whose cells have all gone is dropped by design, which
            // the engine half allows for.
            tick();
        }

        void saveAndLoad() {
            log("save and load");
            settleWorld();
            restart(a.service().stopForTest(a.level, chunkKeys));
            saveLoads++;
            tick();
        }

        // ---- Ticks and checks -----------------------------------------------------------------

        void step() {
            DeviceTicker.stepNow(a.level);
            tick();
        }

        void tick() {
            a.tick();
            ticks++;
            bookOpenAir();
            ConservationReport report = atmos().lastConservation();
            if (report == null) {
                throw new AssertionError("the checker didn't run");
            }
            if (report == lastReport) {
                return;
            }
            lastReport = report;
            if (!report.conserved()) {
                throw new AssertionError(
                        "CORE-073 found changes that no source or sink explains: " + report.violations());
            }
            for (LedgerCategory c : LedgerCategory.values()) {
                if (c != LedgerCategory.API && c != LedgerCategory.DEVICE && c != LedgerCategory.STORAGE) {
                    double change = report.recorded(c, gas);
                    expected += change;
                    moved += Math.abs(change);
                }
            }
            double out = report.voided(gas);
            double in = report.supplied(gas);
            expected += in - out;
            moved += in + out;
            voided += out;
            supplied += in;
        }

        /**
         * Gas devices sent to or took from the open air. Nothing here is open to it, so that can only be at
         * a position with no cell (CORE-057): a block filled in since the device looked, or one whose
         * change is held for a paused zone (GEO-037), so that it has no cell yet. Anywhere else, this is
         * a sealed room classified as open.
         */
        void bookOpenAir() {
            for (OpenAirGasEvent e : OPEN_AIR) {
                if (!isOurs(e.pos())) {
                    continue;
                }
                if (a.level.getBlockState(e.pos()).isAir() && !isHeld(e.pos())) {
                    throw new AssertionError("gas went to the open air at " + e.pos() + ", in a sealed room");
                }
                double change = e.gas().moles(GAS);
                expected += e.added() ? -change : change;
                moved += change;
                openAir += change;
            }
            OPEN_AIR.clear();
        }

        boolean isHeld(BlockPos pos) {
            long key = SectionKeys.block(pos.getX(), pos.getY(), pos.getZ());
            return LongStream.of(atmos().pipeline().heldBlocks()).anyMatch(held -> held == key);
        }

        <T> T settle(CompletableFuture<T> future) {
            for (int i = 0; i < MAX_SETTLE_TICKS && !future.isDone(); i++) {
                tick();
            }
            h.assertTrue(future.isDone(), "an operation didn't complete in " + MAX_SETTLE_TICKS + " atmos ticks");
            return future.join();
        }

        /** Every chunk comes back, and ticks until nothing is on its way, with no device stepping. */
        void settleWorld() {
            new ArrayList<>(away.keySet()).forEach(this::reload);
            // Cells a block change put in doubt are checked at most this often, and the audit doesn't wait.
            for (int i = 0; i <= EnclosureClassifier.DOUBT_CHECK_TICKS; i++) {
                tick();
            }
            for (int i = 0; i < MAX_SETTLE_TICKS; i++) {
                if (settled()) {
                    return;
                }
                tick();
            }
            throw new AssertionError("the world didn't settle in " + MAX_SETTLE_TICKS + " atmos ticks");
        }

        boolean settled() {
            DimensionPipeline pipeline = atmos().pipeline();
            if (!pipeline.outstanding().isEmpty() || pipeline.heldBlocks().length > 0) {
                return false;
            }
            for (ZoneView zone : atmos().published().zones()) {
                if (zone.paused()) {
                    return false;
                }
            }
            for (AtmosDevice device : DeviceTicker.loaded(a.level)) {
                if (isOurs(device.getBlockPos()) && !device.readyToStep()) {
                    return false;
                }
            }
            return true;
        }

        boolean isOurs(BlockPos pos) {
            ChunkPos chunk = new ChunkPos(pos);
            for (ChunkPos c : chunks) {
                if (c.equals(chunk)) {
                    return true;
                }
            }
            return false;
        }

        void checkpoint() {
            log("checkpoint");
            settleWorld();
            checkpoints++;
            double total = GasCensus.total(a, GAS, chunks);
            finite("the total", total);
            if (!close(total, expected)) {
                throw new AssertionError("the pipes and zones hold " + total + " mol, and what went in and the ledger's"
                        + " categories other than devices and storage say " + expected);
            }
            checkTable();
            audit();
            if (checkpoints % REAL_SAVE == 0) {
                realSave();
            }
        }

        boolean close(double x, double y) {
            return Math.abs(x - y) <= 1e-6 + 1e-9 * (Math.max(x, y) + moved);
        }

        void checkTable() {
            AtmosSnapshot table = atmos().published();
            for (ZoneView zone : table.zones()) {
                for (int i = 0; i < table.registry().size(); i++) {
                    finite("zone " + zone.id(), zone.moles(i));
                }
            }
        }

        void finite(String what, double moles) {
            if (!Double.isFinite(moles) || moles < 0) {
                throw new AssertionError(what + " holds " + moles + " mol");
            }
        }

        void audit() {
            BlockPos low = a.abs(-1, -1, -1);
            BlockPos high = a.abs(LAST + 1, ROOF + 1, LAST + 1);
            DimensionAtmos.AuditResult result = settle(atmos().audit(
                            GeometryAudit.Region.spanning(
                                    low.getX(), low.getY(), low.getZ(), high.getX(), high.getY(), high.getZ()),
                            40));
            if (!result.report().clean() || result.pendingSections() > 0) {
                throw new AssertionError("the audit found " + result.report() + ", with " + result.pendingSections()
                        + " sections waiting");
            }
        }

        /** What the chunks write of the gas in pipes and devices is what they hold now. */
        void realSave() {
            log("real save");
            a.level.getChunkSource().save(true);
            double written = 0;
            for (ChunkPos chunk : chunks) {
                written += PipeSaveTests.written(a, chunk);
            }
            double held = GasCensus.inDevices(a, GAS, chunks);
            if (!close(written, held)) {
                throw new AssertionError(
                        "the chunks wrote " + written + " mol, and the pipes and devices hold " + held);
            }
            realSaves++;
        }

        private static String str(int[] p) {
            return p[0] + ", " + p[1] + ", " + p[2];
        }
    }
}
