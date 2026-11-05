package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterRelease;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.portable.PortableScrubberBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableScrubberMath;
import io.github.west3436.zas.atmos.portable.PortableStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.scrubber.ScrubberStatus;
import io.github.west3436.zas.atmos.vent.PassiveVentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentFlow;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.atmos.vent.VentStatus;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Devices and pipes at a paused zone (GEO-035, D-43): each goes by the zone's last-known state, what
 * it sends is voided and what it draws is supplied (GEO-036, GEO-038), so its network keeps running.
 * At an unresolved cell it holds instead.
 *
 * <p>Each test builds its own {@link Area} and runs within one game tick, in a batch of its own. A
 * room runs from x 10 to 21, across the chunk border at x = 16; its devices are in the west chunk,
 * which stays. The east chunk is unloaded only as far as the atmosphere can tell ({@link
 * DimensionAtmos#simulateUnload}), since a GameTest can't unload one on demand (GEO-034).
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PausedZoneDeviceTests {
    private static final String BATCH = "zas_atmos_paused_zones";
    private static final ResourceLocation CO2 = ControllerPreset.CO2;
    private static final double OPEN_END = 1 - Math.pow(1 - PipeBlockEntity.OPEN_END_RATIO, AtmosTicks.SECONDS);

    private PausedZoneDeviceTests() {}

    /** A sealed, zoned room across two chunks, which a test pauses; it loads again when the test ends. */
    private static final class Room implements AutoCloseable {
        final GameTestHelper h;
        final Area a;
        final DimensionAtmos atmos;
        final ChunkPos east;
        private boolean paused;
        private byte[] saved;
        private ZoneInfo known;

        Room(GameTestHelper h, int index, Map<ResourceLocation, Double> moles) {
            this.h = h;
            this.a = new Area(h, index);
            this.atmos = a.service().dimensionFor(a.level);
            this.east = new ChunkPos(a.abs(16, 0, 0));
            a.box(10, 0, 21, 4);
            if (!moles.isEmpty()) {
                a.fill(12, 2, 2, moles);
            }
        }

        /** Past a new device's wait for its cell to settle (CellWatch). */
        void settle() {
            for (int i = 0; i < 5; i++) {
                a.step();
            }
        }

        /**
         * Unloads the east chunk. The tick publishes the paused zone, and a step has the room's
         * devices read it, so from the next step they act on it.
         */
        void pause() {
            saved = atmos.simulateUnload(east);
            paused = true;
            a.tick();
            ZoneInfo zone = a.zone(12, 2, 2);
            h.assertTrue(zone.paused(), "the room's zone didn't pause");
            known = zone;
            a.step();
        }

        /** The room's mixture when it paused, which doesn't change. */
        GasMixture known() {
            return known.mixture();
        }

        void assertUnchanged(String what) {
            ZoneInfo now = a.zone(12, 2, 2);
            h.assertTrue(now.paused(), what + ": the zone resumed");
            h.assertValueEqual(now.mixture().moles(), known().moles(), what + ": the zone's moles");
            a.close(now.mixture().temperature(), known().temperature(), what + ": the zone's temperature");
        }

        private int index(ResourceLocation gas) {
            return atmos.registry().indexOf(gas.toString());
        }

        /** The next atmos tick's check, which is due once a step has run. */
        CompletableFuture<ConservationReport> check() {
            return atmos.checkConservation();
        }

        double voided(ConservationReport r, ResourceLocation gas) {
            return r.voided(index(gas));
        }

        double supplied(ConservationReport r, ResourceLocation gas) {
            return r.supplied(index(gas));
        }

        @Override
        public void close() {
            if (paused) {
                atmos.simulateLoad(east, saved);
                a.tick();
            }
        }
    }

    /**
     * The check every exchange shares: one step of an open end at the zone's last-known state moves
     * what {@link PipeBlockEntity#openEndFlow} says. The gas that leaves is voided, the gas that
     * enters is supplied, and the zone stays as it was.
     */
    private static void assertsOneOpenEndStep(Room r, GasStore net, String what) {
        GasMixture known = r.known();
        PipeBlockEntity.Flow flow = PipeBlockEntity.openEndFlow(net.moles(), net.volume(), known, false, OPEN_END);
        r.h.assertTrue(
                flow.out().containsKey(N2) && flow.in().containsKey(O2), what + ": expected N2 out and O2 in: " + flow);
        double n2 = net.moles(N2);
        double o2 = net.moles(O2);
        CompletableFuture<ConservationReport> check = r.check();
        r.a.step();
        r.a.close(net.moles(N2), n2 - flow.out().get(N2), what + ": N2 in the network");
        r.a.close(net.moles(O2), o2 + flow.in().get(O2), what + ": O2 in the network");
        r.assertUnchanged(what);
        r.h.assertTrue(check.isDone(), what + ": no check");
        ConservationReport report = check.join();
        r.h.assertTrue(report.conserved(), what + ": " + report);
        r.a.close(r.voided(report, N2), flow.out().get(N2), what + ": N2 voided");
        r.a.close(r.supplied(report, O2), flow.in().get(O2), what + ": O2 supplied");
    }

    /**
     * A passive vent exchanges with a paused zone at the one-connection ratio, against its last-known
     * state. A network above it settles to it, and so does an empty one, while the zone doesn't
     * change. So a network keeps running, and no pressure builds up (GEO-035, GEO-036, GEO-038).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_passive_vent_settles_on_a_paused_zones_last_known_state(GameTestHelper h) {
        try (Room r = new Room(h, 960, Map.of(N2, 1_000.0))) {
            r.a.place(10, 2, 2, PassiveVentTests.passiveVent(Direction.EAST));
            r.a.tick();
            PassiveVentBlockEntity v = r.a.entity(10, 2, 2);
            r.settle();
            r.pause();
            double known = r.known().pressure();

            PassiveVentTests.network(v, N2, known + 300);
            assertsOneOpenEndStep(r, v.network().gas(), "a network above");
            for (int i = 0; i < 60; i++) {
                r.a.step();
            }
            double above = v.network().gas().pressure() - known;
            h.assertTrue(
                    Math.abs(above) <= PassiveVentBlockEntity.THRESHOLD_KPA,
                    "a network above stopped " + above + " kPa from the zone");
            r.assertUnchanged("after the network above settled");

            v.network().gas().drain();
            for (int i = 0; i < 60; i++) {
                r.a.step();
            }
            double below = v.network().gas().pressure() - known;
            h.assertTrue(
                    Math.abs(below) <= PassiveVentBlockEntity.THRESHOLD_KPA,
                    "an empty network stopped " + below + " kPa from the zone");
            r.assertUnchanged("after the empty network settled");
        }
        h.succeed();
    }

    /** A pipe's open end exchanges with a paused zone's last-known state as with any zone (GEO-035). */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_an_open_pipe_end_exchanges_with_a_paused_zones_last_known_state(GameTestHelper h) {
        try (Room r = new Room(h, 961, Map.of(N2, 1_000.0))) {
            PipeTests.pipe(r.a, 12, 1, 2);
            r.a.tick();
            PipeBlockEntity pipe = r.a.entity(12, 1, 2);
            h.assertTrue(pipe.isOpen(), "a lone pipe isn't open");
            r.settle();
            r.pause();

            GasStore net = pipe.network().gas();
            net.drain();
            net.add(new GasParcel(Map.of(N2, 40.0), T));
            assertsOneOpenEndStep(r, net, "an open end");
        }
        h.succeed();
    }

    /**
     * A vent compares its external bound with a paused zone's last-known pressure. Blowing below the
     * zone's pressure or siphoning above it, it stays idle. Otherwise it moves what it would at that
     * pressure, and goes on doing so, because the zone never reaches the bound (GEO-035, GEO-036,
     * GEO-038).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_vent_compares_its_bound_with_the_last_known_pressure(GameTestHelper h) {
        try (Room r = new Room(h, 962, Map.of(N2, 1_000.0))) {
            r.a.place(10, 2, 2, GasDeviceTests.vent(Direction.EAST));
            r.a.tick();
            VentBlockEntity vent = r.a.entity(10, 2, 2);
            vent.power().setTestPowered(true);
            vent.command(VentSettings.blow(VentSettings.DEFAULT_EXTERNAL));
            r.settle();
            r.pause();
            GasMixture known = r.known();
            GasStore net = vent.network().gas();
            net.drain();
            net.add(new GasParcel(Map.of(N2, 200.0), T));

            vent.command(VentSettings.blow(known.pressure() - 10));
            r.a.step();
            h.assertValueEqual(vent.status(), VentStatus.ROOM_BOUND, "blowing below the zone's pressure");
            r.a.close(net.totalMoles(), 200, "the network, blowing below the zone's pressure");

            VentSettings blow = VentSettings.blow(known.pressure() + 50);
            vent.command(blow);
            for (int i = 0; i < 3; i++) {
                double before = net.totalMoles();
                double expected =
                        VentFlow.blow(known.pressure(), known.volume(), before, net.volume(), net.temperature(), blow);
                h.assertTrue(expected > 0, "the vent has nothing to blow on step " + i);
                r.a.step();
                h.assertValueEqual(vent.status(), VentStatus.MOVING, "blowing above the zone's pressure, step " + i);
                r.a.close(net.totalMoles(), before - expected, "the network after blowing, step " + i);
                r.assertUnchanged("blowing, step " + i);
            }

            double held = net.totalMoles();
            vent.command(VentSettings.siphon(known.pressure() + 10));
            r.a.step();
            h.assertValueEqual(vent.status(), VentStatus.ROOM_BOUND, "siphoning above the zone's pressure");
            r.a.close(net.totalMoles(), held, "the network, siphoning above the zone's pressure");

            vent.command(VentSettings.siphon(known.pressure() - 10));
            r.a.step();
            h.assertValueEqual(vent.status(), VentStatus.MOVING, "siphoning below the zone's pressure");
            h.assertTrue(net.totalMoles() > held, "the siphon drew nothing");
            r.assertUnchanged("siphoning");
        }
        h.succeed();
    }

    /** A scrubber samples a paused zone's last-known air and is supplied what it takes (GEO-035, GEO-038). */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_scrubber_is_supplied_from_a_paused_zone(GameTestHelper h) {
        try (Room r = new Room(h, 963, Map.of(N2, 800.0, CO2, 300.0))) {
            r.a.place(10, 2, 2, ScrubberTests.scrubber(Direction.EAST));
            r.a.tick();
            ScrubberBlockEntity s = r.a.entity(10, 2, 2);
            s.power().setTestPowered(true);
            s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
            r.settle();
            r.pause();
            GasStore net = s.network().gas();
            for (int i = 0; i < 3; i++) {
                double before = net.moles(CO2);
                double want = ScrubberMath.take(
                                r.known(),
                                ScrubberMode.SCRUB.litres() * AtmosTicks.SECONDS,
                                GasSelection.DEFAULT,
                                net.mixture())
                        .get(CO2);
                r.a.step();
                h.assertValueEqual(s.status(), ScrubberStatus.MOVING, "the scrubber on step " + i);
                r.a.close(net.moles(CO2), before + want, "the network's CO2 on step " + i);
                r.assertUnchanged("scrubbing, step " + i);
            }
        }
        h.succeed();
    }

    /**
     * A canister's valve compares its release pressure with a paused zone's last-known pressure, and
     * goes on releasing into it above that (GEO-035, GEO-036).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_canister_releases_against_the_last_known_pressure(GameTestHelper h) {
        try (Room r = new Room(h, 964, Map.of(N2, 1_000.0))) {
            r.a.place(12, 1, 2, GasDeviceTests.canister());
            r.a.tick();
            CanisterBlockEntity can = r.a.entity(12, 1, 2);
            can.network().gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
            r.settle();
            r.pause();
            GasMixture known = r.known();
            GasStore net = can.network().gas();
            double held = net.totalMoles();

            can.setReleasePressure(known.pressure() - 10);
            can.setValveOpen(true);
            r.a.step();
            r.a.close(net.totalMoles(), held, "the canister, with its release pressure below the zone's");

            can.setReleasePressure(known.pressure() + 5);
            for (int i = 0; i < 3; i++) {
                double before = net.totalMoles();
                double out = CanisterRelease.moles(
                        before,
                        net.volume(),
                        known.totalMoles(),
                        known.volume(),
                        known.pressure(),
                        net.temperature(),
                        can.releasePressure());
                h.assertTrue(out > 0, "the canister has nothing to release on step " + i);
                r.a.step();
                r.a.close(net.totalMoles(), before - out, "the canister after step " + i);
                r.assertUnchanged("releasing, step " + i);
            }
            can.setValveOpen(false);
        }
        h.succeed();
    }

    /** A portable scrubber still has a room in a paused zone, and is supplied from it (GEO-035, GEO-038). */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_portable_scrubber_works_in_a_paused_zone(GameTestHelper h) {
        try (Room r = new Room(h, 965, Map.of(N2, 800.0, CO2, 300.0))) {
            r.a.place(
                    12,
                    1,
                    2,
                    AtmosBlocks.PORTABLE_SCRUBBER
                            .get()
                            .defaultBlockState()
                            .setValue(PortableBlock.FACING, Direction.NORTH));
            r.a.tick();
            PortableScrubberBlockEntity s = r.a.entity(12, 1, 2);
            BatteryTests.swap(s.battery(), AtmosItems.BATTERY.get().full());
            s.setOn(true);
            r.settle();
            r.pause();
            r.a.step();
            h.assertValueEqual(s.status(), PortableStatus.WORKING, "the scrubber in a paused zone");
            double before = s.store().moles(CO2);
            double want = PortableScrubberMath.take(
                            r.known(), GasSelection.DEFAULT, s.store().mixture())
                    .get(CO2);
            r.a.step();
            r.a.close(s.store().moles(CO2), before + want, "the store's CO2");
            r.assertUnchanged("scrubbing");
            s.setOn(false);
        }
        h.succeed();
    }

    /**
     * At an unresolved cell, whose ambient reading is only a guess, a device holds and its network
     * keeps its gas, as a breather's check holds (LIFE-048). Room A is in the west chunk; breaking
     * its wall into room B, which reaches the unloaded east chunk, leaves B's loaded part unresolved
     * (GEO-026). Once the chunk loads, B joins A's zone and the device acts.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_device_holds_at_an_unresolved_cell(GameTestHelper h) {
        Area a = new Area(h, 966);
        DimensionAtmos atmos = a.service().dimensionFor(a.level);
        ChunkPos east = new ChunkPos(a.abs(16, 0, 0));
        a.clear(0, -1, 23, 5);
        shell(a, 2, 9);
        shell(a, 9, 21);
        a.fill(5, 2, 2, Map.of(N2, 50.0));
        // In B's north wall, in the west chunk, facing in: at the ambient's pressure it has nothing to do.
        a.place(12, 2, 0, PassiveVentTests.passiveVent(Direction.SOUTH));
        a.tick();
        PassiveVentBlockEntity v = a.entity(12, 2, 0);
        double ambient = Atmospheres.at(a.level, a.abs(12, 2, 1)).mixture().pressure();
        PassiveVentTests.network(v, N2, ambient);
        for (int i = 0; i < 5; i++) {
            a.step();
        }
        byte[] saved = atmos.simulateUnload(east);
        try {
            a.tick();
            a.set(9, 2, 2, Area.AIR);
            a.tick();
            h.assertTrue(a.zone(5, 2, 2).paused(), "room A didn't pause at the held breach");
            PassiveVentTests.network(v, N2, ambient + 300);
            double held = v.network().gas().totalMoles();
            for (int i = 0; i < 3; i++) {
                a.step();
                h.assertValueEqual(
                        Atmospheres.at(a.level, a.abs(12, 2, 1)).state(),
                        AtmosphereState.UNRESOLVED,
                        "the cell in front of the vent, step " + i);
                a.close(v.network().gas().totalMoles(), held, "the network at an unresolved cell, step " + i);
            }
        } finally {
            atmos.simulateLoad(east, saved);
        }
        double held = v.network().gas().totalMoles();
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        h.assertValueEqual(
                Atmospheres.at(a.level, a.abs(12, 2, 1)).state(), AtmosphereState.ZONED, "B once the chunk loads");
        h.assertTrue(v.network().gas().totalMoles() < held, "the vent didn't act once the region resolved");
        h.succeed();
    }

    /** A hollow stone box with corners (x0, 0, 0) and (x1, 4, 4), built without clearing around it. */
    private static void shell(Area a, int x0, int x1) {
        for (BlockPos p : BlockPos.betweenClosed(a.abs(x0, 0, 0), a.abs(x1, 4, 4))) {
            int x = p.getX() - a.origin.getX();
            int y = p.getY() - a.origin.getY();
            int z = p.getZ() - a.origin.getZ();
            boolean wall = x == x0 || x == x1 || y == 0 || y == 4 || z == 0 || z == 4;
            a.level.setBlockAndUpdate(p, wall ? Area.STONE : Area.AIR);
        }
    }
}
