package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.service;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.EdgeSummary;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.AtmosWorkers;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The R0 reference scenarios (Phase D exit, issue #33): S-01 by commands, S-02 without hazards and
 * S-09 without pipes, plus the requirements they cover (CORE-036, CORE-075, GEO-020, GEO-022).
 *
 * <p><b>The vacuum dimension.</b> The GameTest server creates only the vanilla dimensions and
 * ignores datapack ones, so these tests borrow the End and give it the vacuum ambient of the test
 * datapack's {@code zas_gametest:vacuum} for the length of each test ({@link
 * Ambients#overrideForTest}). The End has no other tests in this batch, the override is cleared
 * when each test ends, and the tests run in a batch of their own, so the End's normal ambient (and
 * {@code env_007}, which excludes the End mid-batch) never meet them.
 *
 * <p><b>R0 adaptations</b> (docs/requirements/00-overview.md, "Scenario verification"): R0 has no
 * devices, pipes, canisters, alarms, firelocks, breathing, hazards or airflow forces. S-01 fills
 * the room with the {@code /zas gas} commands instead of canisters and a vent, and skips the
 * helmet check; S-02 checks the breach, the volume-aware venting (D-21) and zone splits and merges,
 * but no item or player movement, damage or firelock; S-09 saves and reloads zones and a paused
 * zone, with no pipe or canister contents to keep.
 *
 * <p>Like the other world tests, each test builds in its own area at a fixed place (see {@link
 * Rooms}), forces no chunk, runs within one game
 * tick by driving atmos ticks itself, and runs with the invariant checker on (CORE-073), checking
 * every atmos tick is conserved (CORE-036).
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScenarioTests {
    /** The batch of these tests, apart from the other tests that use the End. */
    private static final String BATCH = "r0_scenarios";

    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();

    /** Moles of standard air in one 1,000 L cell at 101.325 kPa and 293.15 K. */
    private static final double AIR_PER_CELL =
            StandardAir.PRESSURE * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);

    private ScenarioTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** Collects what commands send to their source. */
    private static final class Capture implements CommandSource {
        final List<Component> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String text() {
            StringBuilder b = new StringBuilder();
            for (Component m : messages) {
                b.append(m.getString()).append('\n');
            }
            return b.toString();
        }

        void clear() {
            messages.clear();
        }
    }

    /** A test's rooms in the vacuum End, with an operator's command source there. */
    private static final class Station {
        final Rooms r;
        final Capture capture = new Capture();
        final CommandSourceStack op;

        Station(GameTestHelper h, ServerLevel end, int index) {
            r = new Rooms(h, end, index);
            op = new CommandSourceStack(
                    capture,
                    Vec3.atCenterOf(r.origin),
                    Vec2.ZERO,
                    end,
                    2,
                    "zas_scenario",
                    Component.literal("zas_scenario"),
                    end.getServer(),
                    null);
        }

        /** Runs a command through the server's dispatcher. */
        void run(String command) {
            try {
                op.getServer().getCommands().getDispatcher().execute(command, op);
            } catch (CommandSyntaxException e) {
                throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
            }
        }

        /** Runs a gas command, then atmos ticks until it reports {@code needle}. */
        void gas(String command, String needle) {
            capture.clear();
            run(command);
            for (int i = 0; i < 8 && !capture.text().contains(needle); i++) {
                tick();
            }
            r.h.assertTrue(
                    capture.text().contains(needle),
                    "/" + command + ": no \"" + needle + "\" within 8 atmos ticks in:\n" + capture.text());
        }

        String at(int x, int y, int z) {
            BlockPos p = r.abs(x, y, z);
            return p.getX() + " " + p.getY() + " " + p.getZ();
        }

        /** One atmos tick, which the invariant checker must find conserved (CORE-036). */
        void tick() {
            r.tick();
            ConservationReport report = r.dimension().lastConservation();
            r.h.assertTrue(report != null, "the invariant checker didn't run");
            r.h.assertTrue(report.conserved(), "unexplained change: " + report.violations());
        }

        void ticks(int n) {
            for (int i = 0; i < n; i++) {
                tick();
            }
        }

        /** Runs atmos ticks until the future completes; a classification may take a few. */
        <T> T settle(CompletableFuture<T> future, int maxTicks) {
            for (int i = 0; i < maxTicks && !future.isDone(); i++) {
                tick();
            }
            r.h.assertTrue(future.isDone(), "the mutation didn't complete within " + maxTicks + " atmos ticks");
            return future.join();
        }

        /**
         * Sets every position from (x0, y0, z0) to (x1, y1, z1) to a hollow box: stone on its faces
         * and air inside. Positions that already hold the block are left alone.
         */
        void hollow(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (BlockPos p : BlockPos.betweenClosed(r.abs(x0, y0, z0), r.abs(x1, y1, z1))) {
                int x = p.getX() - r.origin.getX();
                int y = p.getY() - r.origin.getY();
                int z = p.getZ() - r.origin.getZ();
                boolean wall = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                place(p, wall ? STONE : AIR);
            }
        }

        /** Sets every position from (x0, y0, z0) to (x1, y1, z1) to a block. */
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
            for (BlockPos p : BlockPos.betweenClosed(r.abs(x0, y0, z0), r.abs(x1, y1, z1))) {
                place(p, state);
            }
        }

        private void place(BlockPos p, BlockState state) {
            if (r.level.getBlockState(p) != state) {
                r.level.setBlockAndUpdate(p, state);
            }
        }

        /**
         * Pressurises the sealed space at a point to standard air, 101.325 kPa at 21 % O₂ and
         * 293.15 K, with {@code /zas gas add}: the nitrogen at 293.15 K first, which forms the zone
         * in the vacuum, then the oxygen at the zone's temperature.
         */
        void pressurise(int x, int y, int z, int cells) {
            double n = cells * AIR_PER_CELL;
            gas(
                    "zas gas add " + at(x, y, z) + " zas:nitrogen " + arg(n * StandardAir.NITROGEN_FRACTION) + " "
                            + arg(StandardAir.TEMPERATURE),
                    "into a zone");
            gas("zas gas add " + at(x, y, z) + " zas:oxygen " + arg(n * StandardAir.OXYGEN_FRACTION), "into a zone");
        }
    }

    /** A number as a command argument: plain, and parsed back to the same double. */
    private static String arg(double v) {
        return BigDecimal.valueOf(v).toPlainString();
    }

    /**
     * Runs a scenario in the End with the test datapack's vacuum ambient and the invariant checker
     * on, then gives both back.
     */
    private static void inVacuum(GameTestHelper h, Consumer<ServerLevel> scenario) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum: " + vacuum);
        h.assertFalse(Ambients.isExcluded(Level.END), "the End is excluded");
        boolean checker = ZasServerConfig.CONSERVATION_CHECKER.get();
        Ambients.overrideForTest(Level.END, vacuum);
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            scenario.accept(end);
        } finally {
            Ambients.overrideForTest(Level.END, null);
            ZasServerConfig.CONSERVATION_CHECKER.set(checker);
        }
        h.succeed();
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    // ---- S-01 -----------------------------------------------------------------------------

    /**
     * S-01 by commands: in a vacuum dimension, a sealed room is filled with {@code /zas gas} to
     * 101.325 kPa of breathable air at 21 % O₂, forms one zone and holds the pressure. The
     * scenario's air alarm, canisters, vent and helmet check need R1 content and LIFE.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void s_01_pressurise_sealed_room_by_commands(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 40);
            Rooms r = s.r;
            s.fill(-1, -1, -1, 6, 6, 6, AIR);
            s.hollow(0, 0, 0, 4, 4, 4); // a sealed 3 × 3 × 3 room
            s.ticks(2);
            AtmosphereSample before = Atmospheres.at(end, r.abs(2, 2, 2));
            h.assertValueEqual(before.state(), AtmosphereState.AMBIENT, "the sealed room before filling");
            h.assertValueEqual(before.pressure(), 0.0, "the vacuum's pressure");

            // The nitrogen at 293.15 K forms the zone; the oxygen is filled into every cell.
            double n = 27 * AIR_PER_CELL;
            s.gas(
                    "zas gas add " + s.at(2, 2, 2) + " zas:nitrogen " + arg(n * StandardAir.NITROGEN_FRACTION) + " "
                            + arg(StandardAir.TEMPERATURE),
                    "into a zone");
            ZoneInfo formed = r.zone(2, 2, 2);
            h.assertValueEqual(formed.cellCount(), 27, "the zone's cells");
            close(h, formed.mixture().temperature(), StandardAir.TEMPERATURE, 1e-9, "temperature");
            s.capture.clear();
            s.run("zas gas fill " + s.at(0, 0, 0) + " " + s.at(4, 4, 4) + " zas:oxygen "
                    + arg(AIR_PER_CELL * StandardAir.OXYGEN_FRACTION));
            h.assertTrue(s.capture.text().contains("Queued additions at 27 positions"), s.capture.text());
            for (int i = 0; i < 8 && !s.capture.text().contains("Filled"); i++) {
                s.tick();
            }
            h.assertTrue(s.capture.text().contains("Filled"), "the fill didn't report: " + s.capture.text());

            ZoneInfo full = r.zone(2, 2, 2);
            h.assertValueEqual(full.id(), formed.id(), "one zone throughout");
            h.assertValueEqual(full.cellCount(), 27, "cells");
            h.assertTrue(full.enclosed(), "enclosed");
            h.assertValueEqual(full.edges(), EdgeSummary.NONE, "edges");
            close(h, full.mixture().pressure(), StandardAir.PRESSURE, 1, "pressure within 1 kPa");
            close(h, full.mixture().pressure(), StandardAir.PRESSURE, 1e-9 * StandardAir.PRESSURE, "pressure");
            close(h, full.mixture().composition().get(O2), StandardAir.OXYGEN_FRACTION, 1e-9, "O2 fraction");
            close(h, full.mixture().composition().get(N2), StandardAir.NITROGEN_FRACTION, 1e-9, "N2 fraction");
            close(h, full.mixture().temperature(), StandardAir.TEMPERATURE, 1e-9, "temperature");

            // The commands see it too.
            s.capture.clear();
            s.run("zas zone at " + s.at(2, 2, 2));
            h.assertTrue(
                    s.capture.text().contains("Zone " + full.id().value() + ": 27 cells, 27000 L, enclosed, active"),
                    s.capture.text());
            h.assertTrue(s.capture.text().contains("101.325 kPa, 293.15 K"), s.capture.text());

            // It holds: a minute of atmos ticks changes nothing.
            s.ticks(60);
            ZoneInfo held = r.zone(2, 2, 2);
            h.assertValueEqual(held.id(), full.id(), "the zone");
            h.assertValueEqual(held.mixture().moles(), full.mixture().moles(), "moles after 60 s");
            close(h, held.mixture().pressure(), full.mixture().pressure(), 1e-12, "pressure after 60 s");
        });
    }

    // ---- S-02 -----------------------------------------------------------------------------

    /**
     * S-02 without hazards: breaking a window of a pressurised room in vacuum gives its zone an
     * ambient edge and it vents, a 27-cell room faster than a 1,000-cell one (D-21), until it is
     * near vacuum and dissolves (CORE-076). Items, players, damage and the firelock are R1.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void s_02_breach_vents_small_room_faster_than_large(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 41);
            Rooms r = s.r;
            s.fill(-1, -1, -1, 23, 13, 13, AIR);
            s.hollow(0, 0, 0, 4, 4, 4); // small: 3 × 3 × 3, 27 cells
            s.hollow(10, 0, 0, 21, 11, 11); // large: 10 × 10 × 10, 1,000 cells
            BlockPos smallWindow = r.abs(2, 4, 2);
            BlockPos largeWindow = r.abs(15, 11, 5);
            end.setBlockAndUpdate(smallWindow, GLASS);
            end.setBlockAndUpdate(largeWindow, GLASS);
            s.pressurise(2, 2, 2, 27);
            s.pressurise(15, 5, 5, 1000);
            ZoneInfo small = r.zone(2, 2, 2);
            ZoneInfo large = r.zone(15, 5, 5);
            h.assertValueEqual(small.cellCount(), 27, "small room");
            h.assertValueEqual(large.cellCount(), 1000, "large room");
            close(h, small.mixture().pressure(), StandardAir.PRESSURE, 1e-6, "small room pressure");
            close(h, large.mixture().pressure(), StandardAir.PRESSURE, 1e-6, "large room pressure");

            end.destroyBlock(smallWindow, false);
            end.destroyBlock(largeWindow, false);
            s.tick();
            ZoneInfo smallOpen = r.zone(2, 2, 2);
            ZoneInfo largeOpen = r.zone(15, 5, 5);
            for (ZoneInfo z : List.of(smallOpen, largeOpen)) {
                h.assertTrue(z.breached(), "zone " + z.id() + " isn't breached");
                h.assertValueEqual(z.edges().ambientEdges(), 1, "zone " + z.id() + " ambient edges");
            }
            double smallLeft = smallOpen.mixture().pressure() / small.mixture().pressure();
            double largeLeft = largeOpen.mixture().pressure() / large.mixture().pressure();
            h.assertTrue(smallLeft < 1 && largeLeft < 1, "a room didn't vent: " + smallLeft + ", " + largeLeft);
            h.assertTrue(
                    smallLeft < largeLeft,
                    "the small room kept " + smallLeft + " of its pressure, the large one " + largeLeft);

            // Each further tick, the small room keeps a smaller fraction than the large one, until it
            // is near vacuum and dissolves into the ambient.
            int ticks = 1;
            while (r.zoned(2, 2, 2) && ticks < 1000) {
                double smallBefore = r.zone(2, 2, 2).mixture().pressure();
                double largeBefore = r.zone(15, 5, 5).mixture().pressure();
                s.tick();
                ticks++;
                double largeNow = r.zone(15, 5, 5).mixture().pressure();
                h.assertTrue(largeNow < largeBefore, "the large room stopped venting at tick " + ticks);
                if (r.zoned(2, 2, 2)) {
                    double smallNow = r.zone(2, 2, 2).mixture().pressure();
                    h.assertTrue(smallNow < smallBefore, "the small room stopped venting at tick " + ticks);
                    h.assertTrue(
                            smallNow / smallBefore < largeNow / largeBefore,
                            "the small room vented slower than the large one at tick " + ticks);
                }
            }
            h.assertFalse(r.zoned(2, 2, 2), "the small room didn't vent to vacuum within 1,000 s");
            AtmosphereSample vented = Atmospheres.at(end, r.abs(2, 2, 2));
            h.assertValueEqual(vented.state(), AtmosphereState.AMBIENT, "the vented room");
            h.assertValueEqual(vented.pressure(), 0.0, "the vented room's pressure");
            ZoneInfo stillVenting = r.zone(15, 5, 5);
            h.assertTrue(
                    stillVenting.mixture().pressure() > 0.5 * StandardAir.PRESSURE,
                    "the large room drained as fast: " + stillVenting.mixture().pressure() + " kPa after " + ticks
                            + " s");

            // Reseal the large room, so nothing vents after the test.
            end.setBlockAndUpdate(largeWindow, GLASS);
            s.tick();
            h.assertTrue(r.zone(15, 5, 5).enclosed(), "the resealed room is breached");
        });
    }

    /**
     * S-02 on a two-room station: closing the door between the rooms splits the zone; a breach in
     * one room vents only that room; resealing it and topping it up by command lets it be
     * pressurised again; opening the door merges the rooms back into one zone (CORE-023, CORE-024,
     * CORE-064). In R0 the "door" is a block of the shared wall: doors as zone boundaries are R1
     * (GEO-007).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void s_02_station_breach_split_and_merge(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 42);
            Rooms r = s.r;
            s.fill(-1, -1, -1, 10, 6, 6, AIR);
            s.hollow(0, 0, 0, 4, 4, 4); // A: 3 × 3 × 3
            s.hollow(4, 0, 0, 8, 4, 4); // B: 3 × 3 × 3, sharing the wall at x = 4
            BlockPos door = r.abs(4, 2, 2);
            BlockPos window = r.abs(8, 2, 2);
            end.setBlockAndUpdate(door, AIR);
            end.setBlockAndUpdate(window, GLASS);

            // Pressurised with the door open: one zone of 27 + 1 + 27 cells.
            s.pressurise(2, 2, 2, 55);
            ZoneInfo station = r.zone(2, 2, 2);
            h.assertValueEqual(r.zone(6, 2, 2).id(), station.id(), "one zone through the open door");
            h.assertValueEqual(station.cellCount(), 55, "cells");
            close(h, station.mixture().pressure(), StandardAir.PRESSURE, 1e-6, "pressure");

            // Closing the door splits it; the door's gas stays, shared by volume (CORE-022, CORE-064).
            end.setBlockAndUpdate(door, STONE);
            s.tick();
            ZoneInfo a = r.zone(2, 2, 2);
            ZoneInfo b = r.zone(6, 2, 2);
            h.assertFalse(a.id().equals(b.id()), "the closed door didn't split the zone");
            h.assertValueEqual(a.cellCount(), 27, "A's cells");
            h.assertValueEqual(b.cellCount(), 27, "B's cells");
            double total = station.mixture().totalMoles();
            close(h, a.mixture().totalMoles() + b.mixture().totalMoles(), total, 1e-9 * total, "moles conserved");
            close(h, a.mixture().pressure(), b.mixture().pressure(), 1e-9, "shared by volume");
            close(h, a.mixture().pressure(), StandardAir.PRESSURE * 55 / 54, 1e-6, "the door's gas kept");

            // A breach in B vents B only.
            end.destroyBlock(window, false);
            s.tick();
            h.assertTrue(r.zone(6, 2, 2).breached(), "B isn't breached");
            h.assertTrue(r.zone(2, 2, 2).enclosed(), "A is breached");
            s.ticks(20);
            ZoneInfo vented = r.zone(6, 2, 2);
            h.assertTrue(vented.mixture().pressure() < 0.9 * b.mixture().pressure(), "B didn't vent");
            h.assertValueEqual(r.zone(2, 2, 2).mixture().moles(), a.mixture().moles(), "A changed");

            // Resealed, B holds what it has left.
            end.setBlockAndUpdate(window, GLASS);
            s.tick();
            ZoneInfo resealed = r.zone(6, 2, 2);
            h.assertTrue(resealed.enclosed(), "the resealed room is breached");
            h.assertValueEqual(resealed.cellCount(), 27, "B's cells after resealing");
            s.ticks(5);
            h.assertValueEqual(
                    r.zone(6, 2, 2).mixture().moles(), resealed.mixture().moles(), "B leaks");

            // Topped up by command to A's amounts, B is pressurised again.
            ZoneInfo aNow = r.zone(2, 2, 2);
            s.gas(
                    "zas gas set " + s.at(6, 2, 2) + " zas:nitrogen "
                            + arg(aNow.mixture().moles(N2)),
                    "Set Nitrogen");
            s.gas(
                    "zas gas set " + s.at(6, 2, 2) + " zas:oxygen "
                            + arg(aNow.mixture().moles(O2)),
                    "Set Oxygen");
            ZoneInfo topped = r.zone(6, 2, 2);
            close(h, topped.mixture().pressure(), aNow.mixture().pressure(), 1e-6, "B's pressure after topping up");

            // Opening the door merges the rooms, which are equivalent now (CORE-023).
            double before = aNow.mixture().totalMoles() + topped.mixture().totalMoles();
            end.setBlockAndUpdate(door, AIR);
            for (int i = 0;
                    i < 40 && !r.zone(2, 2, 2).id().equals(r.zone(6, 2, 2).id());
                    i++) {
                s.tick();
            }
            ZoneInfo merged = r.zone(2, 2, 2);
            h.assertValueEqual(r.zone(6, 2, 2).id(), merged.id(), "the open door didn't merge the rooms");
            h.assertValueEqual(merged.cellCount(), 55, "cells");
            h.assertTrue(merged.enclosed(), "the station is breached");
            close(h, merged.mixture().totalMoles(), before, 1e-9 * before, "moles conserved by the merge");
        });
    }

    // ---- S-09 -----------------------------------------------------------------------------

    /**
     * S-09 without pipes: a restart keeps every zone's composition and temperature to 1e-9, and a
     * zone paused by an unloaded chunk at the restart stays paused until that chunk loads, then
     * resumes unchanged. The restart is {@code AtmosService#stopForTest} and {@code startForTest}
     * (see {@code PersistenceTests}); the unloaded chunk is {@link DimensionAtmos#simulateUnload}.
     * Pipe and canister contents arrive with CONT (R1).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void s_09_restart_keeps_zones_and_paused_zone_resumes(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 43);
            Rooms r = s.r;
            r.clear(-1, 23);
            r.shell(0, 4); // A: air, 293.15 K
            r.shell(4, 8); // B: nitrogen and CO2 at 350 K
            r.shell(10, 21); // P: oxygen at 280 K, across the chunks at x = 0 and x = 16
            s.pressurise(2, 2, 2, 27);
            s.gas("zas gas add " + s.at(6, 2, 2) + " zas:nitrogen 500 350", "into a zone");
            s.gas("zas gas add " + s.at(6, 2, 2) + " zas:carbon_dioxide 50", "into a zone");
            s.gas("zas gas add " + s.at(12, 2, 2) + " zas:oxygen 300 280", "into a zone");
            ZoneInfo a = r.zone(2, 2, 2);
            ZoneInfo b = r.zone(6, 2, 2);
            ZoneInfo p = r.zone(12, 2, 2);
            h.assertValueEqual(r.zone(19, 2, 2).id(), p.id(), "P spans both chunks");
            close(h, b.mixture().temperature(), 350, 1e-9, "B's temperature");
            close(h, p.mixture().temperature(), 280, 1e-9, "P's temperature");
            h.assertTrue(b.mixture().moles(CO2) > 0, "B has no CO2");

            // P's east chunk unloads: P pauses and stays as it was.
            byte[] east = r.dimension().simulateUnload(r.chunk(16));
            h.assertTrue(east != null, "the east chunk saved nothing");
            s.ticks(2);
            h.assertTrue(r.zone(12, 2, 2).paused(), "P didn't pause");
            r.same(p, r.zone(12, 2, 2), "P while paused");

            // The server stops and starts again, with the east chunk still unloaded.
            Map<Long, byte[]> saved = service().stopForTest(end, r.chunkKey(0));
            h.assertTrue(saved.containsKey(r.chunkKey(0)), "the station's chunk saved nothing");
            DimensionAtmos restarted = service().startForTest(end, saved);
            restarted.simulateUnload(r.chunk(16));
            s.ticks(2);
            r.same(a, r.zone(2, 2, 2), "A after the restart");
            r.same(b, r.zone(6, 2, 2), "B after the restart");
            h.assertValueEqual(
                    r.zone(2, 2, 2).mixture().composition(), a.mixture().composition(), "A's air");
            h.assertValueEqual(
                    r.zone(6, 2, 2).mixture().composition(), b.mixture().composition(), "B's mixture");
            ZoneInfo frozen = Atmospheres.zone(end, p.id())
                    .orElseThrow(() -> new GameTestAssertException("P didn't survive the restart"));
            h.assertTrue(frozen.paused(), "P resumed with its east chunk unloaded");
            h.assertValueEqual(Atmospheres.at(end, r.abs(12, 2, 2)).state(), AtmosphereState.PAUSED, "a point in P");

            // The east chunk loads: P resumes, unchanged.
            restarted.simulateLoad(r.chunk(16), east);
            s.ticks(2);
            ZoneInfo resumed = r.zone(12, 2, 2);
            h.assertFalse(resumed.paused(), "P didn't resume");
            r.same(p, resumed, "P after resuming");
            h.assertValueEqual(r.zone(19, 2, 2).id(), p.id(), "P's east part");
        });
    }

    // ---- CORE-075 -------------------------------------------------------------------------

    /**
     * Filling a one-cell zone between two zones shares its gas between them by volume; filling a
     * one-cell zone with no neighbours releases its gas to the ambient (CORE-075).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_075_one_cell_zone_filled_between_two_zones(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 44);
            Rooms r = s.r;
            r.clear(-1, 20);
            r.shell(0, 4); // A: x 1 to 3, 27 cells
            r.shell(4, 6); // M: x 5, cut down to one cell below
            r.shell(6, 9); // B: x 7 to 8, 18 cells
            s.fill(5, 1, 1, 5, 3, 3, STONE);
            r.set(5, 2, 2, AIR);
            // Zone ids rise in creation order: A, then B, then M.
            s.settle(GasMutations.add(end, r.point(2, 2, 2), oxygen(50), source("core_075")), 8);
            s.settle(GasMutations.add(end, r.point(7, 2, 2), oxygen(5), source("core_075")), 8);
            s.settle(GasMutations.add(end, r.point(5, 2, 2), oxygen(400), source("core_075")), 8);
            h.assertValueEqual(r.zone(5, 2, 2).cellCount(), 1, "M's cells");

            // Open M's walls: each opening joins the lower-id zone beside it (GEO-023), so M stays
            // one cell, between A (now 28 cells) and B (now 19).
            r.set(4, 2, 2, AIR);
            r.set(6, 2, 2, AIR);
            s.tick();
            ZoneInfo a = r.zone(2, 2, 2);
            ZoneInfo m = r.zone(5, 2, 2);
            ZoneInfo b = r.zone(7, 2, 2);
            h.assertValueEqual(m.cellCount(), 1, "M's cells after the openings");
            h.assertValueEqual(a.cellCount(), 28, "A's cells");
            h.assertValueEqual(b.cellCount(), 19, "B's cells");
            h.assertValueEqual(m.edges().zoneEdges(), 2, "M's edges");

            // Fill M: its gas goes to A and B in proportion to their volumes.
            r.set(5, 2, 2, STONE);
            s.tick();
            h.assertFalse(r.zoned(5, 2, 2), "M is still zoned");
            h.assertTrue(Atmospheres.zone(end, m.id()).isEmpty(), "M survived");
            double displaced = m.mixture().moles(O2);
            double share = a.volume() / (a.volume() + b.volume());
            r.close(r.zone(2, 2, 2).mixture().moles(O2), a.mixture().moles(O2) + share * displaced, "A's O2");
            r.close(r.zone(7, 2, 2).mixture().moles(O2), b.mixture().moles(O2) + (1 - share) * displaced, "B's O2");

            // A one-cell zone with no neighbours: its gas goes to the ambient.
            r.shell(12, 14);
            s.fill(13, 1, 1, 13, 3, 3, STONE);
            r.set(13, 2, 2, AIR);
            s.settle(GasMutations.add(end, r.point(13, 2, 2), oxygen(10), source("core_075")), 8);
            ZoneInfo lone = r.zone(13, 2, 2);
            h.assertValueEqual(lone.cellCount(), 1, "the lone zone's cells");
            r.set(13, 2, 2, STONE);
            s.tick();
            h.assertTrue(Atmospheres.zone(end, lone.id()).isEmpty(), "the lone zone survived");
            ConservationReport report = r.dimension().lastConservation();
            int o2 = r.dimension().registry().indexOf(O2.toString());
            r.close(report.recorded(LedgerCategory.AMBIENT, o2), -lone.mixture().moles(O2), "O2 to the ambient");
        });
    }

    // ---- GEO-020, GEO-022 ---------------------------------------------------------------------

    /**
     * A sealed hall of more cells than the enclosure bound (65,536, D-23) classifies as open: gas
     * added in it goes to the ambient and no zone forms. Walled in two, each half is under the
     * bound and zones (GEO-020).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void geo_020_hall_too_big_to_enclose_reports_open(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 45);
            Rooms r = s.r;
            // Interior 41 × 40 × 41 = 67,240 cells.
            s.hollow(0, 0, 0, 42, 41, 42);
            s.ticks(2);
            MutationResult open =
                    s.settle(GasMutations.add(end, r.point(21, 20, 21), oxygen(10), source("geo_020")), 30);
            h.assertValueEqual(open.outcome(), MutationOutcome.AMBIENT, "the hall's outcome");
            h.assertFalse(r.zoned(21, 20, 21), "the hall zoned");
            h.assertValueEqual(
                    Atmospheres.at(end, r.abs(21, 20, 21)).state(), AtmosphereState.AMBIENT, "a point in the hall");

            // A wall at x = 21 leaves two halves of 20 × 40 × 41 = 32,800 cells: sealed, and zoned.
            s.fill(21, 1, 1, 21, 40, 41, STONE);
            MutationResult half =
                    s.settle(GasMutations.add(end, r.point(10, 20, 21), oxygen(10), source("geo_020")), 30);
            h.assertValueEqual(half.outcome(), MutationOutcome.ZONE, "a half's outcome");
            h.assertValueEqual(r.zone(10, 20, 21).cellCount(), 32_800, "the half's cells");
        });
    }

    /**
     * Enclosure classification runs on the atmos worker, after the batch closes, and its results
     * come back with the apply: a sealed room zones, a room with a hole goes to the ambient, and
     * the server thread never answers before the worker (GEO-022). A region reaching an unloaded
     * chunk stays unresolved: {@code geo_024_room_reaching_unloaded_chunk_is_unresolved}.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void geo_022_classification_results_from_worker(GameTestHelper h) {
        inVacuum(h, end -> {
            Station s = new Station(h, end, 46);
            Rooms r = s.r;
            r.clear(-1, 12);
            r.shell(0, 4); // sealed
            r.shell(6, 10); // with a hole in its roof
            r.set(8, 4, 2, AIR);
            s.ticks(2);
            CompletableFuture<MutationResult> sealed =
                    GasMutations.add(end, r.point(2, 2, 2), oxygen(10), source("geo_022"));
            CompletableFuture<MutationResult> holed =
                    GasMutations.add(end, r.point(8, 2, 2), oxygen(10), source("geo_022"));
            DimensionAtmos d = r.dimension();
            for (int i = 0; i < 8 && !(sealed.isDone() && holed.isDone()); i++) {
                boolean sealedDone = sealed.isDone();
                boolean holedDone = holed.isDone();
                service().closeNow(end);
                h.assertTrue(d.isInFlight(), "the batch isn't with the worker");
                h.assertTrue(
                        sealed.isDone() == sealedDone && holed.isDone() == holedDone,
                        "classified on the server thread at close");
                service().applyNow(end);
                h.assertTrue(
                        d.lastWorkerThread().startsWith(AtmosWorkers.THREAD_PREFIX),
                        "the tick ran on " + d.lastWorkerThread());
            }
            h.assertTrue(sealed.isDone() && holed.isDone(), "not classified within 8 atmos ticks");
            h.assertValueEqual(sealed.join().outcome(), MutationOutcome.ZONE, "the sealed room");
            h.assertValueEqual(r.zone(2, 2, 2).cellCount(), 27, "the sealed room's zone");
            h.assertTrue(r.zone(2, 2, 2).enclosed(), "the sealed room's zone is breached");
            h.assertValueEqual(holed.join().outcome(), MutationOutcome.AMBIENT, "the room with a hole");
            h.assertFalse(r.zoned(8, 2, 2), "the room with a hole zoned");
            h.assertValueEqual(holed.join().moved(), oxygen(10), "the gas left for the ambient");
        });
    }
}
