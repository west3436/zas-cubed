package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.TurbineRig.H2;
import static io.github.west3436.zas.atmos.gametest.TurbineRig.O2;

import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.turbine.CompressorBlockEntity;
import io.github.west3436.zas.atmos.turbine.CompressorMenu;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlock;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineMenu;
import io.github.west3436.zas.atmos.turbine.TurbinePartBlock;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The gas turbine as a player builds and uses it (CONT-161 to CONT-163): each part's own window, and a
 * small stone chamber at a new controller's settings reaching the generator's top output, then
 * spinning down when switched off. Areas 6450 and 6451.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineStationTests {
    private static final String BATCH = "zas_atmos_turbine_station";

    private TurbineStationTests() {}

    /**
     * Using the turbine or the compressor opens its own window, linked or not (CONT-163): the turbine's
     * shows whether a controller links it and where, and the compressor's the gases its controller set.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_163_the_turbine_and_compressor_open_their_own_windows(GameTestHelper h) {
        Area a = new Area(h, 6450);
        TurbineRig r = TurbineRig.build(a);
        ServerPlayer p = ListeningPlayer.client(a.level, Vec3.atCenterOf(a.abs(6, 1, 6)), false)
                .player();

        TurbineMenu turbine = use(a, p, r.turbine.getBlockPos(), TurbineMenu.class);
        h.assertFalse(turbine.linked(), "an unlinked turbine's window says it is linked");
        h.assertTrue(turbine.controller().isEmpty(), "an unlinked turbine's window names a controller");
        CompressorMenu compressor = use(a, p, r.compressor.getBlockPos(), CompressorMenu.class);
        h.assertFalse(compressor.linked(), "an unlinked compressor's window says it is linked");
        h.assertValueEqual(compressor.status(), TurbineStatus.UNLINKED, "an unlinked compressor's state");

        r.link(false);
        r.controller.setFuel(H2);
        r.controller.setOxidizer(O2);
        r.feed(10, 5);
        r.settle();
        turbine = use(a, p, r.turbine.getBlockPos(), TurbineMenu.class);
        h.assertTrue(turbine.linked(), "a linked turbine's window says it isn't linked");
        h.assertValueEqual(
                turbine.controller(), Optional.of(r.controller.getBlockPos()), "the controller its window names");
        h.assertValueEqual(turbine.status(), TurbineStatus.OFF, "the turbine's state with its controller off");
        h.assertValueEqual(
                turbine.value(TurbineMenu.DATA_TOP_RPM),
                (int) Math.round(AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_RPM)),
                "the top speed its window shows");
        compressor = use(a, p, r.compressor.getBlockPos(), CompressorMenu.class);
        h.assertTrue(compressor.linked(), "a linked compressor's window says it isn't linked");
        h.assertValueEqual(compressor.fuel(), Optional.of(H2), "the fuel the compressor's window shows");
        h.assertValueEqual(compressor.oxidizer(), Optional.of(O2), "the oxidizer the compressor's window shows");
        h.assertTrue(compressor.value(CompressorMenu.DATA_BUFFER_FUEL) > 0, "its window shows no fuel in the buffer");
        h.assertTrue(
                compressor.value(CompressorMenu.DATA_BUFFER_PRESSURE)
                        >= compressor.value(CompressorMenu.DATA_BUFFER_FUEL)
                                + compressor.value(CompressorMenu.DATA_BUFFER_OXIDIZER)
                                - 1,
                "the buffer's pressure is less than its gases'");
        p.closeContainer();
        h.succeed();
    }

    /**
     * A 1-by-2 stone chamber with its igniter inside, at a new controller's burn rate and the server's
     * defaults, lights by itself, reaches top speed and the generator's top output within 30 s of
     * lighting and holds it (CONT-161, CONT-162). The turbine's window shows the flow and the output.
     * Switched off, the blades spin down and stop, the compressor's with them, and the controller's
     * front goes dark (CONT-163, CONT-164).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_161_a_small_chamber_reaches_top_output_at_a_new_controllers_rate(GameTestHelper h) {
        Area a = new Area(h, 6451);
        Station s = Station.build(a);
        TurbineControllerBlockEntity c = s.controller;
        c.setFuel(H2);
        c.setOxidizer(O2);
        h.assertValueEqual(c.burnRate(), TurbineControllerBlockEntity.DEFAULT_BURN_RATE, "a new controller's rate");
        h.assertTrue(c.autoIgnite(), "a new controller's auto relight");
        s.topUp();
        c.setOn(true);

        int lit = -1;
        for (int i = 0; i < 20 && lit < 0; i++) {
            s.step();
            if (s.burning()) {
                lit = i;
            }
        }
        h.assertTrue(lit >= 0, "the chamber never lit");
        int top = -1;
        for (int i = 1; i <= 30 && top < 0; i++) {
            int fe = s.step();
            if (fe >= 0.9 * s.topFePerRound() && s.turbine.speed() >= 0.98) {
                top = i;
            }
        }
        h.assertTrue(
                top > 0,
                "30 s after lighting it makes " + s.turbine.powerPerTick() + " FE/t at " + Math.round(s.turbine.rpm())
                        + " rpm");
        long made = 0;
        int rounds = 30;
        for (int i = 0; i < rounds; i++) {
            made += s.step();
        }
        h.assertTrue(
                made >= 0.95 * rounds * s.topFePerRound(),
                "it made " + made / rounds / 20 + " FE/t over the " + rounds + " s after top output");
        h.assertTrue(s.burning(), "the chamber went out");
        h.assertValueEqual(c.status(), TurbineStatus.RUNNING, "the controller's state");
        h.assertTrue(
                a.get(s.controllerX(), 5, 2).getValue(TurbineControllerBlock.RUNNING),
                "the controller's front is dark while it runs");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        TurbineMenu window = (TurbineMenu) s.turbine.createMenu(1, player.getInventory(), player);
        h.assertTrue(window.flow() > 0, "the turbine's window shows no flow");
        h.assertTrue(
                window.value(TurbineMenu.DATA_CHAMBER_PRESSURE) > window.value(TurbineMenu.DATA_EXHAUST_PRESSURE),
                "its window shows the chamber at or below the exhaust");
        h.assertTrue(window.power() >= 900, "its window shows " + window.power() + " FE/t");
        h.assertValueEqual(window.status(), TurbineStatus.RUNNING, "its window's state");
        CompressorMenu feed = (CompressorMenu) s.compressor.createMenu(2, player.getInventory(), player);
        h.assertTrue(feed.released(true) + feed.released(false) > 0, "the compressor's window shows nothing released");

        c.setOn(false);
        s.step();
        h.assertFalse(
                a.get(s.controllerX(), 5, 2).getValue(TurbineControllerBlock.RUNNING),
                "the controller's front still lit after switching off");
        int i = 0;
        for (; i < 120 && s.turbine.rpm() > 0; i++) {
            s.step();
        }
        h.assertValueEqual(s.turbine.rpm(), 0.0, "the shaft's speed " + i + " s after switching off");
        h.assertValueEqual(s.spin(s.turbineX()), 0, "the turbine's blades at rest");
        h.assertValueEqual(s.spin(2), 0, "the compressor's blades at rest");
        h.assertValueEqual(s.turbine.status(), TurbineStatus.OFF, "the turbine's state");
        h.succeed();
    }

    /**
     * Switched off with nowhere to send its power, its controller's store full, the shaft still stops:
     * its brake takes it from top speed to still within 30 s, its blades and the compressor's show
     * still, and the controller's front goes dark at once (CONT-161, CONT-163).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_switched_off_with_its_store_full_the_shaft_stops(GameTestHelper h) {
        Area a = new Area(h, 6452);
        TurbineRig r = TurbineRig.build(a).link(false);
        r.controller.setOn(true);
        r.controller.energy().set(r.controller.energy().getMaxEnergyStored());
        r.turbine.setShaftEnergy(Double.MAX_VALUE);
        a.step();
        h.assertValueEqual(r.spin(8), 3, "the turbine's blades at top speed");
        h.assertValueEqual(
                r.controller.energy().getEnergyStored(),
                r.controller.energy().getMaxEnergyStored(),
                "the controller's store isn't full");

        r.controller.setOn(false);
        int i = 0;
        for (; i < 30 && r.turbine.rpm() > 0; i++) {
            a.step();
            h.assertFalse(
                    a.get(6, 5, 2).getValue(TurbineControllerBlock.RUNNING), "the controller's front lit while off");
        }
        h.assertValueEqual(r.turbine.rpm(), 0.0, "the shaft's speed 30 s after switching off");
        h.assertValueEqual(r.spin(8), 0, "the turbine's blades at rest");
        h.assertValueEqual(r.spin(4), 0, "the compressor's blades at rest");
        h.succeed();
    }

    private static <M> M use(Area a, ServerPlayer p, BlockPos pos, Class<M> type) {
        a.level
                .getBlockState(pos)
                .useWithoutItem(a.level, p, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        a.h.assertTrue(type.isInstance(p.containerMenu), "using it opened " + p.containerMenu);
        return type.cast(p.containerMenu);
    }

    /**
     * A station as a player would build it: a cap at (0, 2, 2) and a pipe at (1, 2, 2) behind the
     * compressor at (2, 2, 2), a chamber of two cells at (3, 2, 2) and (4, 2, 2) in solid stone with the
     * igniter in the first, the turbine at (5, 2, 2), both facing east, and the open air in front of it.
     * The controller stands on the stone at (3, 5, 2). The feed is kept at 930 kPa, 40 % hydrogen and
     * 60 % oxygen, as from big tanks.
     */
    private record Station(
            Area a,
            TurbineControllerBlockEntity controller,
            TurbineBlockEntity turbine,
            CompressorBlockEntity compressor) {
        private static final int LEN = 2;
        private static final double FEED_KPA = 930;
        private static final double FEED_H2 = 0.4;

        static Station build(Area a) {
            int x1 = 3 + LEN;
            a.clear(-1, -1, x1 + 6, 5);
            for (int x = 0; x <= x1; x++) {
                for (int y = 0; y <= 4; y++) {
                    for (int z = 0; z <= 4; z++) {
                        boolean inside = x >= 3 && x < x1 && y == 2 && z == 2;
                        a.set(x, y, z, inside ? Area.AIR : Area.STONE);
                    }
                }
            }
            PipeTests.cap(a, 0, 2, 2, Direction.EAST);
            PipeTests.pipe(a, 1, 2, 2);
            a.place(2, 2, 2, TurbineRig.part(true));
            a.place(x1, 2, 2, TurbineRig.part(false));
            a.place(3, 2, 2, AtmosBlocks.IGNITER.get().defaultBlockState());
            a.place(
                    3,
                    5,
                    2,
                    AtmosBlocks.TURBINE_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(TurbineControllerBlock.FACING, Direction.SOUTH));
            a.fill(4, 2, 2, Map.of(Area.N2, 0.01));
            TurbineControllerBlockEntity c = a.entity(3, 5, 2);
            TurbineBlockEntity t = a.entity(x1, 2, 2);
            CompressorBlockEntity comp = a.entity(2, 2, 2);
            c.toggleLink(a.level, t.getBlockPos());
            c.toggleLink(a.level, comp.getBlockPos());
            c.toggleLink(a.level, a.abs(3, 2, 2));
            return new Station(a, c, t, comp);
        }

        int turbineX() {
            return 3 + LEN;
        }

        int controllerX() {
            return 3;
        }

        /** Tops each gas in the feed back up to its share of the feed's pressure. */
        void topUp() {
            GasStore g = PipeTests.network(a, 1, 2, 2).gas();
            double perKpa = g.volume() / (GasMixture.GAS_CONSTANT * Area.T);
            Map<ResourceLocation, Double> add = new HashMap<>();
            double h2 = FEED_KPA * FEED_H2 * perKpa - g.moles(H2);
            double o2 = FEED_KPA * (1 - FEED_H2) * perKpa - g.moles(O2);
            if (h2 > 0) {
                add.put(H2, h2);
            }
            if (o2 > 0) {
                add.put(O2, o2);
            }
            if (!add.isEmpty()) {
                g.add(new GasParcel(add, Area.T));
            }
        }

        /** One second: the feed topped up, a round, and the controller's FE drawn off; returns the FE. */
        int step() {
            topUp();
            a.step();
            return controller.energy().extractEnergy(Integer.MAX_VALUE, false);
        }

        /** What the generator makes in a round at its top output, FE. */
        double topFePerRound() {
            return AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_OUTPUT) * DeviceTicks.PERIOD;
        }

        boolean burning() {
            return Fires.isBurning(a.level, a.zone(4, 2, 2).id());
        }

        int spin(int x) {
            return a.get(x, 2, 2).getValue(TurbinePartBlock.SPIN);
        }
    }
}
