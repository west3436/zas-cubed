package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.persist.ChunkRecordCodec;
import io.github.west3436.zas.engine.zone.Zone;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The engine half of the integrity fuzz (NFR-010): one seed's run of random block edits, gas
 * operations, chunk unloads and reloads and save/load cycles, on the whole pipeline with a {@link
 * TestWorld} for the world, checked as it goes.
 *
 * <ul>
 *   <li><b>CORE-073.</b> Every atmos tick has the checker on, and any unexplained change fails.
 *   <li><b>GEO-032.</b> Every {@link #CHECKPOINT} operations and at the end, every chunk loads, the
 *       world settles, and the audit must find nothing in the whole region.
 *   <li><b>CORE-063.</b> Every tick, every zone and saved share must hold finite amounts, none
 *       negative.
 *   <li><b>An independent total.</b> The checker explains gas moved between memory and chunks by the
 *       category {@link LedgerCategory#STORAGE}, whatever the amount, so it can't see gas that a reload
 *       makes or loses. Here the gas of the zones and of the shares no zone holds yet must change only
 *       by what the other categories record. A share with no cells left, whose gas is dropped by design,
 *       is taken off. So is the difference the limit on DATA-008 makes, for the zones it happens to and
 *       only when it happens: {@link #noteDivisions}.
 * </ul>
 *
 * <p>Save/load cycles are clean stops: what a crash restores (#304) isn't tested. Gas added at a paused
 * zone is voided (GEO-036) and never enters a zone, so it needs no allowance; {@link Stats} totals it.
 * Resets leave out zones with another zone beside them: {@link #reset}.
 *
 * <p>Everything comes from the seed and runs inline, so a seed replays exactly (CORE-044, CORE-071). A
 * failure names the seed, the operation and the command that replays it.
 */
final class IntegrityFuzz {
    /** The operations of a run the requirement asks for. */
    static final int DEFAULT_OPS = 10_000;

    /** How many operations apart the world settles and is audited. */
    static final int CHECKPOINT = 1_000;

    private static final int GRID = 3;
    private static final int SIZE = GRID * 16;
    private static final int MIN_Y = 72;
    private static final int MAX_Y = 88; // across a section border, at y = 80
    private static final int MAX_SETTLE_TICKS = 64;

    /** How many of the last operations a failure lists. */
    private static final int TRACE = 30;

    static final GasRegistry REGISTRY = GasRegistry.of(List.of(
            Sim.gas("zas:nitrogen", 0.028, 20.8), Sim.gas("zas:oxygen", 0.032, 21.0), Sim.gas("zas:huge", 0.044, 37)));
    private static final int GASES = REGISTRY.size();
    private static final int N2 = REGISTRY.indexOf("zas:nitrogen");
    private static final int O2 = REGISTRY.indexOf("zas:oxygen");

    /** The gas that takes the huge additions: its allowance is relative, so the others' stays tight. */
    private static final int HUGE = REGISTRY.indexOf("zas:huge");

    private static final GeometryPalette PALETTE;
    /** A thin wall down the middle of x: two cells at one position. */
    private static final int DIVIDER;
    /** An open door that is a zone boundary on every face (GEO-007). */
    private static final int DOOR;
    /** An open door whose east face is a zone boundary (GEO-007). */
    private static final int DOOR_EAST;

    static {
        GeometryPalette.Builder b = GeometryPalette.builder();
        BitSet wall = new BitSet(GeometryDeriver.VOXELS);
        GeometryDeriver.fill(wall, 7, 0, 0, 9, 16, 16);
        DIVIDER = b.intern(GeometryDeriver.derive(wall));
        DOOR = b.intern(Geometry.declared(Set.of(), Geometry.FULL_VOLUME, Geometry.FLAG_ZONE_BOUNDARY));
        DOOR_EAST = b.intern(Geometry.AIR.withFlags(Geometry.boundaryFlag(Face.EAST)));
        PALETTE = b.build();
    }

    private static final Pattern DROPPED =
            Pattern.compile("The saved share of zone (\\d+) in chunk (-?\\d+), (-?\\d+) has no cells left.*");

    /** What a run did, to show it exercised what it claims to. */
    static final class Stats {
        int blockEdits;
        int gasOps;
        int unloads;
        int reloads;
        int saveLoads;
        int ticks;
        int checkpoints;
        int rejectedComponents;
        int mostZones;
        int mostPaused;
        int rejectedMixtures;
        int droppedShares;
        int resets;
        int resetsLeftOut;
        int dividedZones;
        int excusedDifferences;
        final double[] voided = new double[GASES];
        final double[] supplied = new double[GASES];

        @Override
        public String toString() {
            return blockEdits + " block edits, " + gasOps + " gas operations, " + unloads + " unloads, " + reloads
                    + " reloads, " + saveLoads + " save/load cycles, " + ticks + " atmos ticks, " + checkpoints
                    + " audits, " + rejectedComponents + " rejected components, at most " + mostZones + " zones and "
                    + mostPaused + " paused, " + rejectedMixtures + " rejected mixture changes, " + droppedShares
                    + " dropped shares, " + resets + " resets, " + resetsLeftOut + " left out, " + dividedZones
                    + " zones divided in a tick their chunk saved during, "
                    + excusedDifferences + " differences excused for them, " + voided[N2] + " mol of nitrogen voided, "
                    + supplied[N2] + " supplied";
        }
    }

    private record Room(int x0, int y0, int z0, int x1, int y1, int z1) {}

    /** A chunk that saved since the last tick, and the table it saved from. */
    private record SavedBehind(long chunk, ChunkRecord record, AtmosSnapshot before) {}

    /** A zone that divided in the tick one of its chunks saved during: see {@link #noteDivisions}. */
    private static final class Division {
        final long chunk;
        final ChunkRecord record;
        final long zone;
        final double[] gone;
        /** The zone and those it divided into, while they're in memory. */
        final Set<Long> zones = new HashSet<>();
        /** How many more ticks it may still show in, whatever leaves memory. */
        int fresh = 2;

        Division(long chunk, ChunkRecord record, long zone, double[] gone) {
            this.chunk = chunk;
            this.record = record;
            this.zone = zone;
            this.gone = gone;
        }
    }

    private final long seed;
    private final int ops;
    private final Random random;
    private final TestWorld world = new TestWorld(Ambient.STANDARD_AIR);
    private final GasConfig config = GasConfig.DEFAULT.withLog(this::warned);
    private final List<Room> rooms = new ArrayList<>();
    private final ArrayDeque<String> trace = new ArrayDeque<>();
    final Stats stats = new Stats();

    private Sim sim;

    /** What unloaded chunks saved, by chunk, read back from their bytes. */
    final TreeMap<Long, ChunkRecord> saved = new TreeMap<>();
    /** The records handed to the pipeline since the last tick, to look a dropped share up in. */
    private final TreeMap<Long, ChunkRecord> delivered = new TreeMap<>();

    private final List<SavedBehind> savedBehind = new ArrayList<>();
    private final List<Division> divisions = new ArrayList<>();

    private double[] expected = new double[GASES];
    private double[] moved = new double[GASES];
    private int reanchor;
    private long digest = 17;
    private int op;
    private Consumer<IntegrityFuzz> sabotage = f -> {};
    private int sabotageAt = -1;
    private boolean doubleShares;

    IntegrityFuzz(long seed, int ops) {
        this.seed = seed;
        this.ops = ops;
        this.random = new Random(seed);
    }

    /** Runs a change no operation makes, just before an operation: for tests that the checks catch it. */
    IntegrityFuzz sabotage(int at, Consumer<IntegrityFuzz> change) {
        sabotageAt = at;
        sabotage = change;
        return this;
    }

    /** The command that replays a seed. */
    static String replay(long seed) {
        return "./gradlew :engine:test --tests '*IntegrityFuzzTest*' -Pfuzz.seed=" + seed;
    }

    /**
     * Runs every operation, then settles and audits.
     *
     * @return a digest of the engine's states, the same for the same seed
     * @throws AssertionError naming the seed and the operation, if a check fails
     */
    long run() {
        try {
            newEngine();
            int count = 6 + random.nextInt(5);
            for (int i = 0; i < count; i++) {
                buildRoom();
            }
            for (op = 0; op < ops; op++) {
                if (op == sabotageAt) {
                    sabotage.accept(this);
                }
                operate();
                if (random.nextInt(4) == 0) {
                    step();
                }
                if ((op + 1) % CHECKPOINT == 0) {
                    checkpoint();
                }
            }
            op = ops - 1;
            checkpoint();
            return digest;
        } catch (Throwable t) {
            throw new AssertionError(
                    "NFR-010: seed " + seed + " failed at operation " + op + " of " + ops + ": " + t + "\nReplay: "
                            + replay(seed) + "\nThe last operations:\n" + String.join("\n", trace),
                    t);
        }
    }

    // ---- Operations -----------------------------------------------------------------------

    private void operate() {
        int r = random.nextInt(1000);
        if (r < 300) {
            add();
        } else if (r < 400) {
            remove();
        } else if (r < 430) {
            heat();
        } else if (r < 650) {
            editShell();
        } else if (r < 700) {
            editAnywhere();
        } else if (r < 725) {
            reseal();
        } else if (r < 740) {
            buildRoom();
        } else if (r < 750) {
            override();
        } else if (r < 770) {
            pin();
        } else if (r < 780) {
            log("unpin");
            sim.pipeline.unpin(owner());
            stats.gasOps++;
        } else if (r < 790) {
            reset();
        } else if (r < 860) {
            unload(false);
        } else if (r < 980) {
            reload();
        } else if (r < 995) {
            unload(true);
        } else {
            saveAndLoad();
        }
    }

    private void log(String what) {
        if (trace.size() == TRACE) {
            trace.removeFirst();
        }
        trace.addLast(op + ": " + what);
    }

    private String owner() {
        return "owner" + random.nextInt(3);
    }

    private Room room() {
        return rooms.get(random.nextInt(rooms.size()));
    }

    /** A position in a room, mostly, as most operations go where the gas is. */
    private int[] target() {
        if (random.nextInt(8) == 0) {
            return new int[] {random.nextInt(SIZE), MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1), random.nextInt(SIZE)};
        }
        Room room = room();
        return new int[] {
            room.x0 + random.nextInt(room.x1 - room.x0 + 1),
            room.y0 + random.nextInt(room.y1 - room.y0 + 1),
            room.z0 + random.nextInt(room.z1 - room.z0 + 1)
        };
    }

    private double amount() {
        return switch (random.nextInt(20)) {
            case 0 -> random.nextDouble() * 1e-9; // below the pruning epsilon
            case 1 -> random.nextDouble() * 1e5;
            default -> random.nextDouble() * 100;
        };
    }

    private double[] amounts() {
        double[] moles = new double[GASES];
        moles[N2] = amount();
        moles[O2] = random.nextBoolean() ? amount() : 0;
        if (random.nextInt(100) == 0) {
            moles[HUGE] = 1e250 * random.nextDouble();
        }
        return moles;
    }

    private int sub() {
        return random.nextInt(4) == 0 ? random.nextInt(2) : -1;
    }

    private void add() {
        int[] p = target();
        double[] moles = amounts();
        double temperature = random.nextInt(10) == 0 ? random.nextDouble() * 10_000 : 150 + random.nextDouble() * 300;
        int sub = sub();
        log("add " + moles[N2] + " N2, " + moles[O2] + " O2, " + moles[HUGE] + " huge at " + str(p) + " cell " + sub
                + ", " + temperature + " K");
        sim.pipeline.add(block(p), sub, moles, temperature, LedgerCategory.API, random.nextInt(8) != 0, "fuzz");
        stats.gasOps++;
    }

    private void remove() {
        int[] p = target();
        double[] moles = amounts();
        int sub = sub();
        log("remove " + moles[N2] + " N2, " + moles[O2] + " O2 at " + str(p) + " cell " + sub);
        sim.pipeline.remove(block(p), sub, moles, LedgerCategory.API, random.nextInt(8) != 0, "fuzz");
        stats.gasOps++;
    }

    private void heat() {
        int[] p = target();
        boolean add = random.nextBoolean();
        double joules = random.nextInt(50) == 0 ? 1e300 : random.nextDouble() * 1e7;
        log((add ? "add " : "remove ") + joules + " J at " + str(p));
        sim.pipeline.heat(block(p), sub(), add, joules, random.nextBoolean(), "fuzz");
        stats.gasOps++;
    }

    private void pin() {
        int[] p = target();
        log("pin at " + str(p));
        sim.pipeline.pin(block(p), sub(), owner());
        stats.gasOps++;
    }

    /**
     * A reset (OPS-013), except of a zone with another zone beside it. That zone gains an ambient
     * connection even if the room left behind is sealed, as beside a dissolved zone (CORE-076), which
     * {@code ops_013_a_neighbour_across_an_edge_now_opens_onto_the_ambient} pins and the audit reports
     * (GEO-032), so the two rules can't both hold there. A reset applies after the geometry of its tick, so
     * this one has a tick to itself, or a change or a chunk's record in that tick could give the zone a
     * neighbour after the check.
     */
    private void reset() {
        int[] p = target();
        int sub = sub();
        step();
        if (besideAnotherZone(p, sub)) {
            log("no reset at " + str(p) + ": there's a zone beside it");
            stats.resetsLeftOut++;
            return;
        }
        log("reset at " + str(p));
        sim.pipeline.reset(block(p), sub, "fuzz");
        stats.gasOps++;
        stats.resets++;
        step();
    }

    /** Whether a reset at the position would take a zone with an edge to another. */
    private boolean besideAnotherZone(int[] p, int sub) {
        long cell = sim.engine.classifier().space().cellAt(block(p), sub);
        Zone zone = cell == CellSpace.NO_CELL ? null : sim.engine.graph().zoneOf(cell);
        return zone != null && zone.edges().stream().anyMatch(e -> !e.isAmbient());
    }

    private int block() {
        return switch (random.nextInt(10)) {
            case 0, 1, 2, 3 -> TestWorld.AIR;
            case 4, 5, 6 -> TestWorld.SOLID;
            case 7 -> DIVIDER;
            case 8 -> DOOR;
            default -> DOOR_EAST;
        };
    }

    private void set(int x, int y, int z, int id) {
        log("set " + x + ", " + y + ", " + z + " to " + id);
        world.set(x, y, z, id);
        stats.blockEdits++;
    }

    private void editShell() {
        Room room = room();
        int x = room.x0 - 1 + random.nextInt(room.x1 - room.x0 + 3);
        int y = room.y0 - 1 + random.nextInt(room.y1 - room.y0 + 3);
        int z = room.z0 - 1 + random.nextInt(room.z1 - room.z0 + 3);
        switch (random.nextInt(3)) {
            case 0 -> x = random.nextBoolean() ? room.x0 - 1 : room.x1 + 1;
            case 1 -> y = random.nextBoolean() ? room.y0 - 1 : room.y1 + 1;
            default -> z = random.nextBoolean() ? room.z0 - 1 : room.z1 + 1;
        }
        set(x, y, z, block());
    }

    private void editAnywhere() {
        int[] p = target();
        set(p[0], p[1], p[2], block());
    }

    /** Makes a room whole again, as breached rooms would otherwise pile up. */
    private void reseal() {
        Room r = room();
        log("reseal " + r);
        for (int x = r.x0 - 1; x <= r.x1 + 1; x++) {
            for (int y = r.y0 - 1; y <= r.y1 + 1; y++) {
                for (int z = r.z0 - 1; z <= r.z1 + 1; z++) {
                    boolean shell = x < r.x0 || x > r.x1 || y < r.y0 || y > r.y1 || z < r.z0 || z > r.z1;
                    world.set(x, y, z, shell ? TestWorld.SOLID : TestWorld.AIR);
                }
            }
        }
        stats.blockEdits++;
    }

    private void buildRoom() {
        int width = 2 + random.nextInt(11);
        int height = 2 + random.nextInt(5);
        int depth = 2 + random.nextInt(11);
        int x = 1 + random.nextInt(SIZE - width - 2);
        int y = MIN_Y + 1 + random.nextInt(MAX_Y - MIN_Y - height - 1);
        int z = 1 + random.nextInt(SIZE - depth - 2);
        Room room = new Room(x, y, z, x + width - 1, y + height - 1, z + depth - 1);
        log("build " + room);
        world.room(room.x0, room.y0, room.z0, room.x1, room.y1, room.z1);
        rooms.add(room);
        if (rooms.size() > 24) {
            rooms.remove(random.nextInt(rooms.size()));
        }
        stats.blockEdits++;
    }

    /** Gives a position a geometry of its own, or takes it away (API-023). */
    private void override() {
        int[] p = target();
        Geometry geometry = switch (random.nextInt(3)) {
            case 0 -> Geometry.declared(EnumSet.noneOf(Face.class), Geometry.FULL_VOLUME, Geometry.FLAG_POSITIONAL);
            case 1 -> Geometry.SOLID.withFlags(Geometry.FLAG_POSITIONAL);
            default -> null;
        };
        log("override " + str(p) + " with " + geometry);
        world.override(p[0], p[1], p[2], geometry);
        stats.blockEdits++;
    }

    private long chunk() {
        return SectionKeys.chunk(random.nextInt(GRID), random.nextInt(GRID));
    }

    /**
     * Unloads a chunk as the server does: it saves, through its bytes, then unloads. With {@code late},
     * it does so between the next batch's close and apply, so the tick's result may be stale (CORE-070).
     */
    private void unload(boolean late) {
        long chunk = chunk();
        if (!world.isLoaded(chunk)) {
            reload();
            return;
        }
        log((late ? "unload during the next tick " : "unload ") + chunkStr(chunk));
        stats.unloads++;
        if (!late) {
            unloadNoting(sim, chunk);
            return;
        }
        sim.betweenCloseAndApply = s -> {
            s.betweenCloseAndApply = x -> {};
            unloadNoting(s, chunk);
        };
        step();
    }

    /** Whenever it unloads, a chunk saves from a table that's behind the block changes of the next tick. */
    private void unloadNoting(Sim s, long chunk) {
        AtmosSnapshot before = s.snapshot();
        unloadNow(s, chunk);
        if (saved.containsKey(chunk)) {
            savedBehind.add(new SavedBehind(chunk, saved.get(chunk), before));
        }
    }

    private void unloadNow(Sim s, long chunk) {
        ChunkRecord r = s.pipeline.recordFor(chunk, false);
        world.unload(chunk);
        s.pipeline.chunkUnloaded(chunk);
        keep(chunk, r);
    }

    private void keep(long chunk, ChunkRecord r) {
        ChunkRecord read = throughBytes(chunk, r);
        if (read != null) {
            saved.put(chunk, doubleShares ? doubled(chunk, read) : read);
        } else {
            saved.remove(chunk);
        }
    }

    private ChunkRecord throughBytes(long chunk, ChunkRecord r) {
        if (r == null) {
            return null;
        }
        checkFinite(r);
        byte[] bytes = ChunkRecordCodec.encode(r, REGISTRY);
        try {
            ChunkRecord read = ChunkRecordCodec.decode(bytes, chunk, REGISTRY, this::warned);
            // A queued addition's sub-cell isn't saved: it acts on the position's largest cell (GasOp).
            if (!r.withoutQueued().equals(read.withoutQueued())
                    || r.queuedAdditions().size() != read.queuedAdditions().size()) {
                throw new AssertionError("the record changed in its bytes: " + r + " became " + read);
            }
            return read;
        } catch (ChunkRecordCodec.CorruptRecordException e) {
            throw new AssertionError("a record didn't read back", e);
        }
    }

    private void reload() {
        List<Long> away = away();
        if (away.isEmpty()) {
            add();
            return;
        }
        reload(away.get(random.nextInt(away.size())));
    }

    private List<Long> away() {
        List<Long> away = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                long chunk = SectionKeys.chunk(x, z);
                if (!world.isLoaded(chunk)) {
                    away.add(chunk);
                }
            }
        }
        return away;
    }

    private void reload(long chunk) {
        log("reload " + chunkStr(chunk));
        ChunkRecord r = saved.remove(chunk);
        world.load(chunk);
        sim.pipeline.chunkLoaded(chunk, r);
        if (r != null) {
            delivered.put(chunk, r);
        }
        stats.reloads++;
    }

    /**
     * A clean stop and start: every loaded chunk writes its final save, a new engine takes over, and
     * the chunks come back as a server loads them, some of them later.
     */
    private void saveAndLoad() {
        log("save and load");
        stats.saveLoads++;
        Sim old = sim;
        TreeMap<Long, ChunkRecord> written = new TreeMap<>();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                long chunk = SectionKeys.chunk(x, z);
                if (world.isLoaded(chunk)) {
                    ChunkRecord r = throughBytes(chunk, old.pipeline.recordFor(chunk, true));
                    if (r != null) {
                        written.put(chunk, r);
                    }
                }
            }
        }
        long nextZoneId = old.pipeline.nextZoneId();
        newEngine();
        sim.engine.reserveZoneIds(nextZoneId);
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                long chunk = SectionKeys.chunk(x, z);
                if (!world.isLoaded(chunk)) {
                    continue;
                }
                ChunkRecord r = written.get(chunk);
                if (random.nextInt(4) == 0) {
                    // Not back yet. Its queued additions stay on the record until it is.
                    world.unload(chunk);
                    if (r != null) {
                        saved.put(chunk, r);
                    }
                } else {
                    sim.pipeline.chunkLoaded(chunk, r);
                    if (r != null) {
                        delivered.put(chunk, r);
                    }
                }
            }
        }
        divisions.forEach(d -> d.fresh = 1); // the new engine holds what the records say
        step();
    }

    private void newEngine() {
        sim = new Sim(world, null, EnclosureClassifier.FACTORY, PALETTE, REGISTRY, config);
        sim.engine.setSeed(seed);
    }

    // ---- Checks --------------------------------------------------------------------------

    private void step() {
        DimensionPipeline.Applied a = sim.step();
        stats.ticks++;
        delivered.clear();
        book(a);
        digest = digest * 31 + sim.engine.stateHash();
    }

    private void book(DimensionPipeline.Applied a) {
        AtmosSnapshot table = sim.snapshot();
        int paused = 0;
        for (ZoneView z : table.zones()) {
            checkFinite(z);
            paused += z.paused() ? 1 : 0;
        }
        stats.mostZones = Math.max(stats.mostZones, table.zoneCount());
        stats.mostPaused = Math.max(stats.mostPaused, paused);
        stats.rejectedComponents += a.rejected();
        ConservationReport report = a.conservation();
        if (report == null) {
            throw new AssertionError("the checker didn't run");
        }
        for (int i = 0; i < GASES; i++) {
            stats.voided[i] += report.voided(i);
            stats.supplied[i] += report.supplied(i);
        }
        noteDivisions(table);
        double[] total = total(table);
        if (a.rejected() > 0) {
            // The rejected tick's records are undone in the next, outside any period, and its table leaves
            // out a zone it has only just taken in, which is in no saved share either. A save and load
            // doesn't change that: the total is out until a tick after it.
            reanchor = 2;
        }
        if (reanchor > 0) {
            reanchor--;
            expected = total;
            settleDivisions(table);
            return;
        }
        for (int i = 0; i < GASES; i++) {
            for (LedgerCategory c : LedgerCategory.values()) {
                if (c != LedgerCategory.STORAGE) {
                    double change = report.recorded(c, i);
                    expected[i] += change;
                    moved[i] += Math.abs(change);
                }
            }
            double allowance = 1e-6 + 1e-9 * (Math.max(total[i], expected[i]) + moved[i]);
            double difference = Math.abs(total[i] - expected[i]);
            if (!(difference <= allowance)) {
                if (!(difference <= allowance + gasOfDivisionsShowing(i, table))) {
                    throw new AssertionError("gas " + i + ": the zones and the saved shares hold " + total[i]
                            + " mol, and the ledger's categories other than storage say " + expected[i]);
                }
                stats.excusedDifferences++;
                expected[i] = total[i];
            }
        }
        settleDivisions(table);
    }

    /**
     * Finds the limit on DATA-008. A chunk that saves writes the table from before the tick in flight or the
     * next one. If a block change in that tick divides the zone, the share written holds gas that's
     * in the other parts too, and the total goes out when those zones leave memory or the chunk loads. That
     * is all that's excused: a zone that once the table has caught up has gone, or has fewer cells in the
     * chunk than the share and less gas than it had, for the gas it no longer holds, in the ticks that shows
     * in, by no more than that or the share written, whichever is more.
     */
    private void noteDivisions(AtmosSnapshot table) {
        if (table.hasRejections()) {
            return; // a rejected zone is answered from the last table, so it hasn't caught up yet
        }
        for (SavedBehind s : savedBehind) {
            if (saved.get(s.chunk) != s.record) {
                continue; // it has loaded again, and a share whose zone is in memory isn't taken
            }
            for (ZoneShare share : s.record.zones()) {
                long id = share.zoneId();
                ZoneView before = s.before.zone(id);
                ZoneView now = table.zone(id);
                ZoneShare part = shareOf(table, s.chunk, id);
                // A share the table didn't have is one the zone hadn't taken in, so its gas was never in the zone.
                boolean wasInZone = before != null && shareOf(s.before, s.chunk, id) != null;
                boolean tookOver = table.shares(s.chunk).stream().anyMatch(h -> overlap(share.cells(), h.cells()));
                if (wasInZone && (now == null ? tookOver : (part == null || part.cellCount() < share.cellCount()))) {
                    double[] gone = new double[GASES];
                    boolean fell = false;
                    for (int i = 0; i < GASES; i++) {
                        gone[i] = Math.max(0, before.moles(i) - (now == null ? 0 : now.moles(i)));
                        fell |= gone[i] > 1e-9 * before.moles(i);
                        // The share itself can come back on top of what its other parts hold.
                        gone[i] = Math.max(gone[i], share.moles(i));
                    }
                    if (fell) {
                        Division division = new Division(s.chunk, s.record, id, gone);
                        if (now != null) {
                            division.zones.add(id);
                        }
                        for (long chunk : s.before.zoneChunks(id)) {
                            ZoneShare old = shareOf(s.before, chunk, id); // a chunk it's waiting on has none
                            for (ZoneShare other : old == null ? List.<ZoneShare>of() : table.shares(chunk)) {
                                if (overlap(old.cells(), other.cells())) {
                                    division.zones.add(other.zoneId());
                                }
                            }
                        }
                        divisions.add(division);
                        stats.dividedZones++;
                    }
                }
            }
        }
        savedBehind.clear();
    }

    /** What the divisions showing in this tick no longer hold of a gas: the most they can put the total out. */
    private double gasOfDivisionsShowing(int gas, AtmosSnapshot table) {
        double gone = 0;
        for (Division d : divisions) {
            boolean showing = d.fresh > 0 || saved.get(d.chunk) != d.record;
            for (long id : d.zones) {
                showing |= table.zone(id) == null;
            }
            if (showing) {
                gone += d.gone[gas];
            }
        }
        return gone;
    }

    private void settleDivisions(AtmosSnapshot table) {
        divisions.removeIf(d -> {
            d.fresh--;
            d.zones.removeIf(id -> table.zone(id) == null);
            return d.zones.isEmpty() || saved.get(d.chunk) != d.record;
        });
    }

    private static ZoneShare shareOf(AtmosSnapshot table, long chunk, long zone) {
        for (ZoneShare share : table.shares(chunk)) {
            if (share.zoneId() == zone) {
                return share;
            }
        }
        return null;
    }

    private static boolean overlap(long[] cells, long[] ascending) {
        for (long cell : cells) {
            if (Arrays.binarySearch(ascending, cell) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** What the published zones hold, and the saved shares no zone has cells for yet. */
    private double[] total(AtmosSnapshot table) {
        double[] total = new double[GASES];
        for (ZoneView z : table.zones()) {
            for (int i = 0; i < GASES; i++) {
                total[i] += z.moles(i);
            }
        }
        for (Map.Entry<Long, ChunkRecord> e : saved.entrySet()) {
            List<ZoneShare> held = table.shares(e.getKey());
            for (ZoneShare share : e.getValue().zones()) {
                boolean inMemory = held.stream().anyMatch(h -> h.zoneId() == share.zoneId());
                if (!inMemory && !dividedIntoZonesHoldingIt(e.getValue(), share, held)) {
                    for (int i = 0; i < GASES; i++) {
                        total[i] += share.moles(i);
                    }
                }
            }
        }
        return total;
    }

    /**
     * Whether a saved share's cells have gone to other zones by the division that {@link #noteDivisions}
     * found, so its gas is in them. Any other zone having those cells says nothing: the share goes back to
     * its own zone when the chunk loads.
     */
    private boolean dividedIntoZonesHoldingIt(ChunkRecord record, ZoneShare share, List<ZoneShare> held) {
        return divisions.stream().anyMatch(d -> d.record == record && d.zone == share.zoneId())
                && held.stream().anyMatch(h -> overlap(share.cells(), h.cells()));
    }

    private static void checkFinite(ZoneView z) {
        boolean ok = Double.isFinite(z.volume()) && z.volume() > 0 && Double.isFinite(z.temperature());
        ok &= z.temperature() >= 0 && Double.isFinite(z.pressure());
        for (int i = 0; i < z.gasCount(); i++) {
            ok &= Double.isFinite(z.moles(i)) && z.moles(i) >= 0;
        }
        if (!ok) {
            throw new AssertionError("a non-finite or negative value in " + z);
        }
    }

    private static void checkFinite(ChunkRecord r) {
        // ZoneShare and GasOp refuse these when built; here so that stays true of what is saved.
        for (ZoneShare share : r.zones()) {
            boolean ok = Double.isFinite(share.temperature());
            for (double n : share.moles()) {
                ok &= Double.isFinite(n) && n >= 0;
            }
            if (!ok) {
                throw new AssertionError("a non-finite or negative value in " + share);
            }
        }
    }

    /** Every chunk loads, the world settles, and the audit must find nothing. */
    private void checkpoint() {
        log("checkpoint");
        for (long chunk : away()) {
            reload(chunk);
        }
        settle();
        // Cells a block change put in doubt are checked at most this often, and the audit doesn't wait.
        for (int i = 0; i < EnclosureClassifier.DOUBT_CHECK_TICKS; i++) {
            step();
        }
        settle();
        stats.checkpoints++;
        GeometryAudit.Region region = new GeometryAudit.Region(-1, MIN_Y - 2, -1, SIZE, MAX_Y + 2, SIZE);
        List<SectionSnapshot> fresh = new ArrayList<>();
        for (long key : region.sections()) {
            SectionSource s = world.capture(key);
            if (s != null) {
                fresh.add(s.decode());
            }
        }
        GeometryAudit.Report report = sim.scheduler
                .inspect(engine -> GeometryAudit.run(engine, region, fresh, 40))
                .join();
        if (!report.clean()) {
            StringBuilder found = new StringBuilder();
            for (GeometryAudit.Finding f : report.findings()) {
                found.append("\n  ")
                        .append(f.kind())
                        .append(" at ")
                        .append(SectionKeys.blockX(f.block()))
                        .append(", ")
                        .append(SectionKeys.blockY(f.block()))
                        .append(", ")
                        .append(SectionKeys.blockZ(f.block()))
                        .append(", zone ")
                        .append(f.zoneId());
            }
            throw new AssertionError("the audit found " + report.total() + " differences:" + found);
        }
    }

    /** Ticks until no operation or classification waits and no zone is paused. */
    private void settle() {
        for (int i = 0; i < MAX_SETTLE_TICKS; i++) {
            step();
            if (settled()) {
                return;
            }
        }
        throw new AssertionError("the world didn't settle in " + MAX_SETTLE_TICKS + " atmos ticks: "
                + sim.pipeline.outstanding().size() + " operations outstanding, pending classification "
                + sim.engine.classifier().hasPending() + ", zones "
                + sim.snapshot().zones());
    }

    private boolean settled() {
        if (!sim.pipeline.outstanding().isEmpty() || sim.engine.classifier().hasPending()) {
            return false;
        }
        for (ZoneView z : sim.snapshot().zones()) {
            if (z.paused()) {
                return false;
            }
        }
        return sim.pipeline.heldBlocks().length == 0;
    }

    /** Engine warnings: rejections and dropped shares are by design, and any other is a failure. */
    private void warned(String message) {
        if (message.startsWith("Rejected GasMixture.")) {
            stats.rejectedMixtures++;
            return;
        }
        Matcher dropped = DROPPED.matcher(message);
        if (!dropped.matches()) {
            throw new AssertionError("engine warning: " + message);
        }
        stats.droppedShares++;
        long chunk = SectionKeys.chunk(Integer.parseInt(dropped.group(2)), Integer.parseInt(dropped.group(3)));
        ZoneShare share = Optional.ofNullable(delivered.get(chunk))
                .map(r -> r.share(Long.parseLong(dropped.group(1))))
                .orElseThrow(() -> new AssertionError("a dropped share nobody delivered: " + message));
        for (int i = 0; i < GASES; i++) {
            expected[i] -= share.moles(i);
        }
    }

    // ---- For tests of the checks ----------------------------------------------------------

    /** Adds gas to the first zone behind the ledger's back, in the first tick that has a zone. */
    void conjure(double moles) {
        boolean[] done = {false};
        sim.engine.setPhaseListener(phase -> {
            if (phase == Phase.EXCHANGE && !done[0]) {
                for (var zone : sim.engine.graph().zones()) {
                    zone.mixture().setMoles(N2, zone.mixture().moles(N2) + moles);
                    done[0] = true;
                    return;
                }
            }
        });
    }

    /** Doubles every share a chunk saves from now on. */
    void doubleSavedShares() {
        doubleShares = true;
    }

    private static ChunkRecord doubled(long chunk, ChunkRecord r) {
        List<ZoneShare> doubled = new ArrayList<>();
        for (ZoneShare share : r.zones()) {
            double[] moles = share.moles();
            for (int i = 0; i < moles.length; i++) {
                moles[i] *= 2;
            }
            doubled.add(new ZoneShare(
                    share.zoneId(), share.cells(), moles, share.temperature(), share.zoneChunks(), share.pins()));
        }
        return new ChunkRecord(chunk, doubled, r.heldBlocks(), r.queuedAdditions());
    }

    /** Fills the first zone's first cell without the engine hearing of it. */
    void loseBlockChange() {
        for (var zone : sim.engine.graph().zones()) {
            long cell = zone.cells()[0];
            int x = io.github.west3436.zas.engine.classify.CellIds.x(cell);
            int y = io.github.west3436.zas.engine.classify.CellIds.y(cell);
            int z = io.github.west3436.zas.engine.classify.CellIds.z(cell);
            world.set(x, y, z, TestWorld.SOLID);
            world.drainDirty(k -> true);
            return;
        }
    }

    // ---- Text -----------------------------------------------------------------------------

    private static long block(int[] p) {
        return SectionKeys.block(p[0], p[1], p[2]);
    }

    private static String str(int[] p) {
        return p[0] + ", " + p[1] + ", " + p[2];
    }

    private static String chunkStr(long chunk) {
        return "chunk " + SectionKeys.chunkX(chunk) + ", " + SectionKeys.chunkZ(chunk);
    }
}
