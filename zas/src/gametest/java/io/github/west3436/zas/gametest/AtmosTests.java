package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.EdgeSummary;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.scheduler.AtmosWorkers;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The atmos scheduler and zones on demand in a real level (CORE, GEO, API). Each test builds its
 * rooms from stone in its own area of the Overworld near the origin (see the area helper), then drives atmos ticks itself with {@link
 * AtmosService#tickNow}, so it doesn't depend on when the regular ticks fall. Other tests' ticks
 * may run in between; every check is about the test's own rooms.
 *
 * <p>The Overworld's ambient is standard air (ENV-002), so a zone formed by adding gas holds the
 * room's air plus what was added.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosTests {
    private static final double REL = 1e-9;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Every open-air event, for the CORE-058 tests to pick theirs from by source. */
    private static final List<OpenAirGasEvent> EVENTS = new CopyOnWriteArrayList<>();

    private static final List<Boolean> EVENT_THREADS = new CopyOnWriteArrayList<>();

    /** Each test's building area. */
    private static final Map<GameTestHelper, BlockPos> AREAS = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        NeoForge.EVENT_BUS.addListener((OpenAirGasEvent e) -> {
            EVENTS.add(e);
            EVENT_THREADS.add(e.level().getServer().isSameThread());
        });
    }

    private AtmosTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    private static SourceId source(String test) {
        return SourceId.of(ZasMod.MOD_ID, test);
    }

    private static AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    private static void tick(GameTestHelper h) {
        service().tickNow(h.getLevel());
    }

    private static void ticks(GameTestHelper h, int n) {
        for (int i = 0; i < n; i++) {
            tick(h);
        }
    }

    /** Runs atmos ticks until the future completes; a classification may take a few. */
    private static <T> T settle(GameTestHelper h, CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick(h);
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /** Clears the structure to air, then builds a hollow stone box with corners min and max. */
    private static void shell(GameTestHelper h, BlockPos min, BlockPos max) {
        requireArea(h);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            boolean wall = p.getX() == min.getX()
                    || p.getX() == max.getX()
                    || p.getY() == min.getY()
                    || p.getY() == max.getY()
                    || p.getZ() == min.getZ()
                    || p.getZ() == max.getZ();
            set(h, p, wall ? STONE : AIR);
        }
    }

    private static void clear(GameTestHelper h) {
        requireArea(h);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(7, 6, 6))) {
            set(h, p, AIR);
        }
    }

    /**
     * Gives a test its own building area in the Overworld, at a fixed place near the origin.
     *
     * <p>GameTest structures land at random coordinates up to about 15 million blocks out. Cell
     * ids name every position out to the world border, so a structure's own place would do too
     * (the {@code PersistenceTests} border tests build there), but a fixed area keeps the tests
     * independent of where the structures land and of the terrain around them.
     *
     * <p>No chunk is forced: building loads the area's chunks, and each test runs within one game
     * tick (it drives atmos ticks itself), so nothing unloads under it. Forced chunks and their
     * neighbours, generated asynchronously, once left the server's shutdown waiting on chunk work
     * on a slow CI runner.
     */
    private static void area(GameTestHelper h, int index) {
        AREAS.put(h, new BlockPos(4096 + 256 * index, 100, 4096));
    }

    private static void requireArea(GameTestHelper h) {
        if (!AREAS.containsKey(h)) {
            throw new GameTestAssertException("the test has no building area");
        }
    }

    /** A position of the test's building area. */
    private static BlockPos abs(GameTestHelper h, BlockPos rel) {
        BlockPos origin = AREAS.get(h);
        return origin == null ? h.absolutePos(rel) : origin.offset(rel);
    }

    private static void set(GameTestHelper h, BlockPos rel, BlockState state) {
        h.getLevel().setBlockAndUpdate(abs(h, rel), state);
    }

    private static void set(GameTestHelper h, BlockPos rel, net.minecraft.world.level.block.Block block) {
        set(h, rel, block.defaultBlockState());
    }

    private static Vec3 point(GameTestHelper h, int x, int y, int z) {
        return Vec3.atCenterOf(abs(h, new BlockPos(x, y, z)));
    }

    private static ZoneInfo zone(GameTestHelper h, int x, int y, int z) {
        return Atmospheres.zoneAt(h.getLevel(), abs(h, new BlockPos(x, y, z)))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    private static boolean zoned(GameTestHelper h, int x, int y, int z) {
        return Atmospheres.zoneAt(h.getLevel(), abs(h, new BlockPos(x, y, z))).isPresent();
    }

    /** Overworld ambient moles of a gas in a volume. */
    private static double ambient(ResourceLocation gas, double litres) {
        return Ambients.ambient(Level.OVERWORLD).moles(gas.toString(), litres);
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    private static GasParcel oxygen(double moles) {
        return new GasParcel(Map.of(O2, moles), StandardAir.TEMPERATURE);
    }

    private static DimensionAtmos dimension(GameTestHelper h) {
        return service().dimensionFor(h.getLevel());
    }

    // ---- Zones on demand ---------------------------------------------------------------

    /**
     * A sealed room stays unzoned, like a natural cave, until an API call adds gas to it; then it
     * forms a zone holding the room's air plus all of the added gas (CORE-051, CORE-055, GEO-028),
     * and the zone and point queries report it (API-010, API-011).
     */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_051_sealed_room_zones_only_on_demand(GameTestHelper h) {
        area(h, 1);
        ServerLevel level = h.getLevel();
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        ticks(h, 2);
        BlockPos inside = abs(h, new BlockPos(2, 2, 2));
        h.assertValueEqual(Atmospheres.at(level, inside).state(), AtmosphereState.AMBIENT, "before the add");
        h.assertFalse(zoned(h, 2, 2, 2), "a sealed room formed a zone without a trigger");

        CompletableFuture<MutationResult> add =
                GasMutations.add(level, point(h, 2, 2, 2), oxygen(10), source("core_051"));
        h.assertFalse(add.isDone(), "completed before an atmos tick applied it");
        MutationResult r = settle(h, add);
        h.assertValueEqual(r.outcome(), MutationOutcome.ZONE, "outcome");
        h.assertValueEqual(r.moved(), oxygen(10), "moved");

        ZoneInfo z = zone(h, 2, 2, 2);
        h.assertValueEqual(z.cellCount(), 27, "cells");
        close(h, z.volume(), 27_000, "volume");
        h.assertTrue(z.enclosed(), "a sealed room is enclosed");
        h.assertFalse(z.paused(), "paused");
        h.assertValueEqual(z.edges(), EdgeSummary.NONE, "edges of a sealed room");
        close(h, z.mixture().moles(O2), ambient(O2, 27_000) + 10, "O2: the room's air plus the add");
        close(h, z.mixture().moles(N2), ambient(N2, 27_000), "N2");

        AtmosphereSample s = Atmospheres.at(level, inside);
        h.assertValueEqual(s.state(), AtmosphereState.ZONED, "state");
        h.assertValueEqual(s.zone(), java.util.Optional.of(z.id()), "zone id");
        close(h, s.pressure(), z.mixture().pressure(), "pressure");
        h.assertTrue(s.pressure() > StandardAir.PRESSURE, "the zone's pressure didn't rise");
        h.assertValueEqual(Atmospheres.zone(level, z.id()), java.util.Optional.of(z), "zone by id");
        h.assertFalse(zoned(h, 6, 2, 2), "open air outside the room is zoned");
        h.succeed();
    }

    /** A removal by API call in a sealed room triggers a zone too (CORE-055). */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_055_api_remove_triggers_zone(GameTestHelper h) {
        area(h, 2);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        MutationResult r =
                settle(h, GasMutations.remove(h.getLevel(), point(h, 2, 2, 2), Map.of(O2, 5.0), source("core_055")));
        h.assertValueEqual(r.outcome(), MutationOutcome.ZONE, "outcome");
        close(h, r.moved().moles(O2), 5, "O2 delivered");
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), ambient(O2, 27_000) - 5, "O2 left in the zone");
        h.succeed();
    }

    /** A lit furnace is not a Normal trigger, so a sealed room with one stays unzoned (CORE-055). */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_055_lit_furnace_forms_no_zone(GameTestHelper h) {
        area(h, 3);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        set(h, new BlockPos(2, 1, 2), Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true));
        ticks(h, 3);
        h.assertFalse(zoned(h, 2, 2, 2), "a lit furnace formed a zone under Normal");
        h.succeed();
    }

    // ---- Breaches ------------------------------------------------------------------------

    /**
     * Breaking the wall between a zone and a sealed unzoned room joins the room to the zone,
     * bringing the room's ambient air (GEO-023, CORE-055's breach trigger).
     */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void geo_023_breach_into_sealed_room_joins_zone(GameTestHelper h) {
        area(h, 4);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(3, 4, 4)); // A: 2 × 3 × 3
        shell(h, new BlockPos(3, 0, 0), new BlockPos(7, 4, 4)); // B: 3 × 3 × 3, sharing x = 3
        settle(h, GasMutations.add(h.getLevel(), point(h, 1, 2, 2), oxygen(20), source("geo_023")));
        ZoneInfo a = zone(h, 1, 2, 2);
        h.assertValueEqual(a.cellCount(), 18, "room A");
        h.assertFalse(zoned(h, 5, 2, 2), "room B zoned before the breach");
        double o2 = a.mixture().moles(O2);

        set(h, new BlockPos(3, 2, 2), AIR);
        ticks(h, 2);
        ZoneInfo joined = zone(h, 5, 2, 2);
        h.assertValueEqual(joined.id(), zone(h, 1, 2, 2).id(), "one zone");
        h.assertValueEqual(joined.cellCount(), 18 + 1 + 27, "cells");
        close(h, joined.mixture().moles(O2), o2 + ambient(O2, 28_000), "the zone's O2 plus the room's ambient share");
        h.assertTrue(joined.enclosed(), "still enclosed");
        h.succeed();
    }

    /** Breaking a window to the open air gives the zone an ambient edge and it vents (GEO-025, S-02). */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void geo_025_broken_window_vents(GameTestHelper h) {
        area(h, 5);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        set(h, new BlockPos(4, 2, 2), Blocks.GLASS);
        settle(h, GasMutations.add(h.getLevel(), point(h, 2, 2, 2), oxygen(500), source("geo_025")));
        double sealed = zone(h, 2, 2, 2).mixture().pressure();
        ticks(h, 2);
        close(h, zone(h, 2, 2, 2).mixture().pressure(), sealed, "a sealed zone kept its pressure");

        set(h, new BlockPos(4, 2, 2), AIR);
        tick(h);
        ZoneInfo open = zone(h, 2, 2, 2);
        h.assertTrue(open.breached(), "the zone isn't breached");
        h.assertValueEqual(open.edges().ambientEdges(), 1, "ambient edges");
        h.assertTrue(open.mixture().pressure() < sealed, "the zone didn't vent");
        double first = open.mixture().pressure();
        tick(h);
        if (zoned(h, 2, 2, 2)) {
            h.assertTrue(zone(h, 2, 2, 2).mixture().pressure() < first, "venting stopped");
        }
        h.succeed();
    }

    /** A line of panes keeps two zones at different pressures apart (GEO-004). */
    @GameTest(template = "geo_004_panes", timeoutTicks = 200)
    public static void geo_004_panes_keep_zones_apart(GameTestHelper h) {
        area(h, 6);
        BlockState pane = Blocks.GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.SOUTH, true);
        shell(h, new BlockPos(1, 1, 1), new BlockPos(7, 5, 5));
        for (int y = 2; y <= 4; y++) {
            for (int z = 2; z <= 4; z++) {
                set(h, new BlockPos(4, y, z), pane);
            }
        }
        settle(h, GasMutations.add(h.getLevel(), point(h, 2, 3, 3), oxygen(200), source("geo_004")));
        h.assertFalse(zoned(h, 6, 3, 3), "the zone crossed the panes");
        settle(h, GasMutations.add(h.getLevel(), point(h, 6, 3, 3), oxygen(20), source("geo_004")));
        ticks(h, 3);
        ZoneInfo west = zone(h, 2, 3, 3);
        ZoneInfo east = zone(h, 6, 3, 3);
        h.assertFalse(west.id().equals(east.id()), "the panes didn't keep two zones");
        h.assertValueEqual(west.edges().zoneEdges(), 0, "an edge across the panes");
        h.assertTrue(
                west.mixture().pressure() - east.mixture().pressure() > 1, "the pressures equalized across the panes");
        h.succeed();
    }

    // ---- Zone geometry -------------------------------------------------------------------

    /** Placing a block in a sealed zone keeps its gas, so the pressure rises by V/(V − v) (CORE-022). */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_022_displaced_gas_raises_pressure(GameTestHelper h) {
        area(h, 7);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        settle(h, GasMutations.add(h.getLevel(), point(h, 2, 2, 2), oxygen(10), source("core_022")));
        ZoneInfo before = zone(h, 2, 2, 2);
        set(h, new BlockPos(1, 1, 1), STONE);
        tick(h);
        ZoneInfo after = zone(h, 2, 2, 2);
        h.assertValueEqual(after.cellCount(), 26, "cells");
        close(h, after.mixture().totalMoles(), before.mixture().totalMoles(), "moles");
        close(h, after.mixture().pressure(), before.mixture().pressure() * 27 / 26, "pressure");
        h.succeed();
    }

    /** Walling a zone in two leaves two zones, with the gas shared by volume (CORE-024, CORE-064). */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_024_wall_splits_zone(GameTestHelper h) {
        area(h, 8);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(6, 4, 4)); // 5 × 3 × 3
        settle(h, GasMutations.add(h.getLevel(), point(h, 1, 2, 2), oxygen(30), source("core_024")));
        double o2 = zone(h, 1, 2, 2).mixture().moles(O2);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                set(h, new BlockPos(3, y, z), STONE);
            }
        }
        tick(h);
        ZoneInfo west = zone(h, 1, 2, 2);
        ZoneInfo east = zone(h, 5, 2, 2);
        h.assertFalse(west.id().equals(east.id()), "still one zone");
        h.assertValueEqual(west.cellCount(), 18, "west cells");
        h.assertValueEqual(east.cellCount(), 18, "east cells");
        close(h, west.mixture().moles(O2) + east.mixture().moles(O2), o2, "O2 conserved");
        close(h, west.mixture().moles(O2), east.mixture().moles(O2), "shared by volume");
        h.succeed();
    }

    /**
     * Rooms at different pressures joined by one opening exchange until equivalent, then merge
     * into one zone with their moles conserved (CORE-023).
     */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_023_rooms_merge_when_equivalent(GameTestHelper h) {
        area(h, 9);
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(3, 4, 4)); // 18 cells
        shell(h, new BlockPos(3, 0, 0), new BlockPos(6, 4, 4)); // 18 cells
        settle(h, GasMutations.add(h.getLevel(), point(h, 1, 2, 2), oxygen(700), source("core_023")));
        settle(h, GasMutations.add(h.getLevel(), point(h, 5, 2, 2), oxygen(100), source("core_023")));
        double total = zone(h, 1, 2, 2).mixture().totalMoles()
                + zone(h, 5, 2, 2).mixture().totalMoles();
        h.assertFalse(zone(h, 1, 2, 2).id().equals(zone(h, 5, 2, 2).id()), "one zone before the opening");

        set(h, new BlockPos(3, 2, 2), AIR);
        tick(h);
        h.assertFalse(zone(h, 1, 2, 2).id().equals(zone(h, 5, 2, 2).id()), "merged before equalizing");
        // About 55 s through the one opening with volume-aware sharing (CORE-035).
        for (int i = 0;
                i < 120 && !zone(h, 1, 2, 2).id().equals(zone(h, 5, 2, 2).id());
                i++) {
            tick(h);
        }
        ZoneInfo merged = zone(h, 1, 2, 2);
        h.assertValueEqual(merged.id(), zone(h, 5, 2, 2).id(), "the rooms never merged");
        h.assertValueEqual(merged.cellCount(), 37, "cells");
        close(h, merged.mixture().totalMoles(), total + ambient(N2, 1000) + ambient(O2, 1000), "moles conserved");
        h.succeed();
    }

    // ---- Open air --------------------------------------------------------------------------

    /**
     * Adding gas in the open air releases it to the ambient: no zone forms, the result reports the
     * ambient (CORE-053), and an event carries the amount and position (CORE-058, API-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void core_053_open_air_add_goes_to_ambient(GameTestHelper h) {
        SourceId src = source("core_053");
        BlockPos pos = h.absolutePos(new BlockPos(0, 3, 0));
        GasParcel gas = new GasParcel(Map.of(O2, 3.0, N2, 1.0), 300);
        MutationResult r = settle(h, GasMutations.add(h.getLevel(), Vec3.atCenterOf(pos), gas, src));
        h.assertValueEqual(r.outcome(), MutationOutcome.AMBIENT, "outcome");
        h.assertValueEqual(r.moved(), gas, "moved");
        h.assertTrue(Atmospheres.zoneAt(h.getLevel(), pos).isEmpty(), "a zone formed in the open air");
        OpenAirGasEvent e = event(h, src);
        h.assertTrue(e.added(), "an addition");
        h.assertValueEqual(e.pos(), pos, "position");
        h.assertValueEqual(e.gas(), gas, "amount");
        h.succeed();
    }

    /**
     * Removing gas from the open air yields the ambient's gases at its temperature, and none it
     * lacks (CORE-057), with an event (CORE-058).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void core_057_open_air_remove_yields_ambient_gas(GameTestHelper h) {
        SourceId src = source("core_057");
        BlockPos pos = h.absolutePos(new BlockPos(0, 3, 0));
        MutationResult r = settle(
                h, GasMutations.remove(h.getLevel(), Vec3.atCenterOf(pos), Map.of(N2, 4.0, O2, 1.0, CO2, 2.0), src));
        h.assertValueEqual(r.outcome(), MutationOutcome.AMBIENT, "outcome");
        h.assertValueEqual(r.moved().moles(), Map.of(N2, 4.0, O2, 1.0), "the ambient's gases only");
        close(h, r.moved().temperature(), Ambients.ambient(Level.OVERWORLD).temperature(), "temperature");
        OpenAirGasEvent e = event(h, src);
        h.assertFalse(e.added(), "a removal");
        h.assertValueEqual(e.pos(), pos, "position");
        h.assertValueEqual(e.gas(), r.moved(), "amount");
        h.succeed();
    }

    private static OpenAirGasEvent event(GameTestHelper h, SourceId src) {
        OpenAirGasEvent found = null;
        for (int i = 0; i < EVENTS.size(); i++) {
            if (EVENTS.get(i).source().equals(src)) {
                found = EVENTS.get(i);
                h.assertTrue(EVENT_THREADS.get(i), "the event fired off the server thread (API-032)");
            }
        }
        if (found == null) {
            throw new GameTestAssertException("no open-air event from " + src);
        }
        return found;
    }

    // ---- The scheduler -----------------------------------------------------------------------

    /** Atmos ticks run on a zas-atmos worker, and results are applied on the server thread (CORE-045). */
    @GameTest(template = "empty")
    public static void core_045_simulation_runs_on_worker_thread(GameTestHelper h) {
        h.assertFalse(ZasServerConfig.singleThreaded(), "the test server runs single-threaded");
        tick(h);
        String worker = dimension(h).lastWorkerThread();
        h.assertTrue(worker.startsWith(AtmosWorkers.THREAD_PREFIX), "the tick ran on " + worker);
        h.succeed();
    }

    /** Results are applied on the server thread (CORE-069). */
    @GameTest(template = "empty")
    public static void core_069_results_applied_on_server_thread(GameTestHelper h) {
        tick(h);
        h.assertTrue(dimension(h).lastApplyOnServerThread(), "a result was applied off the server thread");
        h.succeed();
    }

    /**
     * Each dimension has its own zones and scheduler: a Nether zone and an Overworld zone are
     * independent, and while the Nether's batch is held in flight (its simulation paused) the
     * Overworld keeps ticking and the Nether's table doesn't change (CORE-042).
     */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_042_dimensions_are_independent(GameTestHelper h) {
        area(h, 12);
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "no Nether");
        BlockPos base = new BlockPos(1024, 100, 1024);
        for (BlockPos p : BlockPos.betweenClosed(base, base.offset(4, 4, 4))) {
            boolean wall = p.getX() == base.getX()
                    || p.getX() == base.getX() + 4
                    || p.getY() == base.getY()
                    || p.getY() == base.getY() + 4
                    || p.getZ() == base.getZ()
                    || p.getZ() == base.getZ() + 4;
            nether.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
        BlockPos netherInside = base.offset(2, 2, 2);
        CompletableFuture<MutationResult> inNether =
                GasMutations.add(nether, Vec3.atCenterOf(netherInside), oxygen(40), source("core_042"));
        for (int i = 0; i < 8 && !inNether.isDone(); i++) {
            service().tickNow(nether);
        }
        h.assertValueEqual(inNether.join().outcome(), MutationOutcome.ZONE, "Nether outcome");
        ZoneInfo netherZone = Atmospheres.zoneAt(nether, netherInside).orElseThrow();

        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        settle(h, GasMutations.add(h.getLevel(), point(h, 2, 2, 2), oxygen(10), source("core_042")));
        // Hold the Nether's batch in flight while the Overworld changes and ticks.
        service().closeNow(nether);
        ZoneInfo before = zone(h, 2, 2, 2);
        settle(h, GasMutations.add(h.getLevel(), point(h, 2, 2, 2), oxygen(10), source("core_042")));
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), before.mixture().moles(O2) + 10, "Overworld zone");
        h.assertValueEqual(
                Atmospheres.zoneAt(nether, netherInside).orElseThrow(), netherZone, "the Nether's table changed");
        service().applyNow(nether);
        h.assertValueEqual(
                Atmospheres.zoneAt(nether, netherInside).orElseThrow().mixture(),
                netherZone.mixture(),
                "the Overworld changed the Nether's zone");
        h.succeed();
    }

    /**
     * A chunk under a zone reloads between batch close and apply: the result for that zone is
     * discarded (CORE-070), and the queued gas is applied in the next atmos tick, once (CORE-074).
     */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_070_stale_result_discarded_and_gas_kept(GameTestHelper h) {
        area(h, 10);
        ServerLevel level = h.getLevel();
        clear(h);
        shell(h, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        settle(h, GasMutations.add(level, point(h, 2, 2, 2), oxygen(10), source("core_070")));
        ZoneInfo before = zone(h, 2, 2, 2);
        long rejectedBefore = dimension(h).rejectedComponents();

        CompletableFuture<MutationResult> add =
                GasMutations.add(level, point(h, 2, 2, 2), oxygen(5), source("core_070"));
        service().applyNow(level);
        service().closeNow(level);
        ChangeTracker.markReloaded(level, new ChunkPos(abs(h, new BlockPos(2, 2, 2))));
        service().applyNow(level);
        h.assertTrue(dimension(h).rejectedComponents() > rejectedBefore, "nothing was rejected");
        h.assertFalse(add.isDone(), "the discarded tick completed the mutation");
        h.assertValueEqual(zone(h, 2, 2, 2), before, "the discarded result was published");

        MutationResult r = settle(h, add);
        h.assertValueEqual(r.outcome(), MutationOutcome.ZONE, "outcome");
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), before.mixture().moles(O2) + 5, "the gas was lost or doubled");
        h.succeed();
    }

    /** A scenario run with the invariant checker on reports nothing unexplained (CORE-073). */
    @GameTest(template = "geo_003_room", timeoutTicks = 200)
    public static void core_073_checker_reports_nothing_unexplained(GameTestHelper h) {
        area(h, 11);
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            clear(h);
            shell(h, new BlockPos(0, 0, 0), new BlockPos(6, 4, 4));
            settle(h, GasMutations.add(h.getLevel(), point(h, 2, 2, 2), oxygen(100), source("core_073")));
            settle(h, GasMutations.remove(h.getLevel(), point(h, 2, 2, 2), Map.of(N2, 10.0), source("core_073")));
            set(h, new BlockPos(3, 2, 2), STONE); // displace a cell
            tick(h);
            set(h, new BlockPos(6, 2, 2), AIR); // vent
            for (int i = 0; i < 4; i++) {
                tick(h);
                ConservationReport report = dimension(h).lastConservation();
                h.assertTrue(report != null, "the checker didn't run");
                h.assertTrue(report.conserved(), "unexplained: " + report.violations());
            }
        } finally {
            ZasServerConfig.CONSERVATION_CHECKER.set(was);
        }
        h.succeed();
    }
}
