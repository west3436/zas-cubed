package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.teg.CirculatorBlockEntity;
import io.github.west3436.zas.atmos.teg.TegBlock;
import io.github.west3436.zas.atmos.teg.TegBlockEntity;
import io.github.west3436.zas.atmos.teg.TegMenu;
import io.github.west3436.zas.atmos.teg.TegStatus;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.heat.HeatSettings;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The thermoelectric generator: its circulators and its core (CONT-157 to CONT-159). Each test builds
 * in its own {@link Area}, 5600 to 5609, and steps the devices itself. A core at (4, 2, 4) faces
 * north, so its sides are west and east; a circulator on each side passes gas south, from a capped
 * pipe north of it (its inlet) to a capped pipe south of it (its outlet). The framework's heat is
 * off, so nothing but the core moves heat between the streams.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TegTests {
    private static final String BATCH = "zas_atmos_teg";

    /** The west (left) and east (right) circulators' x. */
    private static final int LEFT = 3;

    private static final int RIGHT = 5;

    private TegTests() {}

    /** A core and its two circulators, each with a capped inlet and outlet pipe. */
    private static TegBlockEntity build(Area a, boolean core) {
        a.clear(0, 0, 8, 8);
        if (core) {
            a.place(4, 2, 4, AtmosBlocks.TEG.get().defaultBlockState().setValue(TegBlock.FACING, Direction.NORTH));
        }
        for (int x : new int[] {LEFT, RIGHT}) {
            PipeTests.cap(a, x, 2, 2, Direction.SOUTH);
            PipeTests.pipe(a, x, 2, 3);
            a.place(
                    x,
                    2,
                    4,
                    AtmosBlocks.CIRCULATOR
                            .get()
                            .defaultBlockState()
                            .setValue(InlineDeviceBlock.FACING, Direction.SOUTH));
            PipeTests.pipe(a, x, 2, 5);
            PipeTests.cap(a, x, 2, 6, Direction.NORTH);
        }
        return core ? a.entity(4, 2, 4) : null;
    }

    private static PipeNetwork in(Area a, int x) {
        return PipeTests.network(a, x, 2, 3);
    }

    private static PipeNetwork out(Area a, int x) {
        return PipeTests.network(a, x, 2, 5);
    }

    private static void add(PipeNetwork net, double moles, double kelvin) {
        net.gas().add(new GasParcel(Map.of(N2, moles), kelvin));
    }

    /** The four networks' thermal energy, J. */
    private static double gasEnergy(Area a) {
        double e = 0;
        for (int x : new int[] {LEFT, RIGHT}) {
            e += in(a, x).gas().thermalEnergy() + out(a, x).gas().thermalEnergy();
        }
        return e;
    }

    /** The core's work so far, J: its FE and what it carries toward the next. */
    private static double work(TegBlockEntity teg) {
        return teg.energy().getEnergyStored() * AtmosServerConfig.get(AtmosServerConfig.JOULES_PER_FE)
                + teg.workCarry();
    }

    private static double moles(Area a) {
        double n = 0;
        for (int x : new int[] {LEFT, RIGHT}) {
            n += in(a, x).gas().totalMoles() + out(a, x).gas().totalMoles();
        }
        return n;
    }

    private static void run(GameTestHelper h, Runnable body) {
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            body.run();
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /**
     * A circulator on its own passes gas from its inlet to its outlet until its inlet is 5 kPa above
     * its outlet, its friction, and never back (CONT-157).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_157_a_circulator_moves_gas_from_its_high_side_to_its_low_side(GameTestHelper h) {
        Area a = new Area(h, 5600);
        run(h, () -> {
            build(a, false);
            CirculatorBlockEntity c = a.entity(LEFT, 2, 4);
            h.assertTrue(c.running(), "a circulator needs no switch or power");
            add(in(a, LEFT), 20, 293.15);
            double before = moles(a);
            for (int i = 0; i < 5; i++) {
                a.step();
            }
            GasStore inlet = in(a, LEFT).gas();
            GasStore outlet = out(a, LEFT).gas();
            h.assertTrue(outlet.totalMoles() > 1, "nothing reached the outlet: " + outlet.totalMoles());
            double friction = AtmosServerConfig.get(AtmosServerConfig.CIRCULATOR_FRICTION_KPA);
            h.assertTrue(
                    Math.abs(inlet.pressure() - outlet.pressure() - friction) < 0.5,
                    "the inlet is " + (inlet.pressure() - outlet.pressure()) + " kPa above the outlet");
            a.close(moles(a), before, "moles in the four networks");

            // Gas on the outlet side never flows back through it.
            add(out(a, RIGHT), 20, 293.15);
            for (int i = 0; i < 3; i++) {
                a.step();
            }
            h.assertTrue(in(a, RIGHT).gas().isEmpty(), "gas went back through a circulator");
        });
    }

    /**
     * A core with a hot stream on one side and a cold one on the other makes FE, cools the hot stream
     * and warms the cold one, lights its front and says it runs; heat out of the hot stream is the
     * heat into the cold stream plus the work, and the gas's energy and the work together don't
     * change (CONT-158, CONT-159).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_158_a_core_between_hot_and_cold_makes_fe_and_moves_heat(GameTestHelper h) {
        Area a = new Area(h, 5601);
        run(h, () -> {
            TegBlockEntity teg = build(a, true);
            h.assertValueEqual(teg.circulatorCount(), 2, "circulators serving the core");
            add(in(a, LEFT), 20, 600);
            add(in(a, RIGHT), 20, 250);
            double total = gasEnergy(a);
            double moles = moles(a);
            teg.setOn(true);
            a.step();
            h.assertTrue(teg.heatFromHot() > 0, "no heat taken from the hot stream");
            double jpf = AtmosServerConfig.get(AtmosServerConfig.JOULES_PER_FE);
            a.close(
                    teg.heatFromHot(),
                    teg.heatToCold() + teg.powerPerTick() * 20 * AtmosTicks.SECONDS * jpf,
                    "heat out = heat in + work");
            h.assertTrue(work(teg) > 0, "no FE made");
            h.assertValueEqual(teg.status(), TegStatus.RUNNING, "the core's status");
            h.assertTrue(a.get(4, 2, 4).getValue(TegBlock.OUTPUT) > 0, "the core's front isn't lit");
            h.assertTrue(teg.temperature(0, true) > teg.temperature(0, false), "the hot stream didn't cool");
            h.assertTrue(teg.temperature(1, false) > teg.temperature(1, true), "the cold stream didn't warm");
            for (int i = 0; i < 5; i++) {
                a.step();
            }
            h.assertTrue(out(a, LEFT).gas().temperature() < 600, "the hot outlet isn't cooler");
            h.assertTrue(out(a, RIGHT).gas().temperature() > 250, "the cold outlet isn't warmer");
            // CONT-158: energy is conserved, gas's and the work's.
            double after = gasEnergy(a) + work(teg);
            h.assertTrue(
                    Math.abs(after - total) <= 1e-6 * total, "energy before " + total + " J, after " + after + " J");
            a.close(moles(a), moles, "moles in the four networks");
        });
    }

    /** With both streams at one temperature the core makes nothing, though the gas still flows (CONT-158). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_158_no_fe_without_a_temperature_difference(GameTestHelper h) {
        Area a = new Area(h, 5602);
        run(h, () -> {
            TegBlockEntity teg = build(a, true);
            add(in(a, LEFT), 20, 293.15);
            add(in(a, RIGHT), 20, 293.15);
            teg.setOn(true);
            for (int i = 0; i < 3; i++) {
                a.step();
                h.assertValueEqual(teg.energy().getEnergyStored(), 0, "FE made at ΔT 0");
            }
            h.assertValueEqual(teg.workCarry(), 0.0, "work made at ΔT 0");
            h.assertTrue(out(a, LEFT).gas().totalMoles() > 1, "the gas didn't flow");
            h.assertValueEqual(a.get(4, 2, 4).getValue(TegBlock.OUTPUT), 0, "the front lit");
        });
    }

    /**
     * Switched off the core makes nothing and its circulators pass their gas untouched; it is off
     * until switched on, and its window's button switches it (CONT-158, CONT-159).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_159_off_it_makes_nothing_and_its_window_switches_it(GameTestHelper h) {
        Area a = new Area(h, 5603);
        run(h, () -> {
            TegBlockEntity teg = build(a, true);
            h.assertFalse(teg.on(), "a new core is on");
            add(in(a, LEFT), 20, 600);
            add(in(a, RIGHT), 20, 250);
            for (int i = 0; i < 3; i++) {
                a.step();
            }
            h.assertValueEqual(teg.status(), TegStatus.OFF, "the core's status");
            h.assertValueEqual(teg.energy().getEnergyStored(), 0, "FE made while off");
            a.close(out(a, LEFT).gas().temperature(), 600, "the hot stream's temperature through an idle core");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            TegMenu menu = new TegMenu(0, teg, new SimpleContainerData(TegMenu.DATA_COUNT));
            menu.clickMenuButton(player, TegMenu.TOGGLE);
            h.assertTrue(teg.on(), "the window's button didn't switch it on");

            // On, with fresh gas, the window shows what it does.
            add(in(a, LEFT), 20, 600);
            add(in(a, RIGHT), 20, 250);
            a.step();
            TegMenu window = (TegMenu) teg.createMenu(1, player.getInventory(), player);
            h.assertValueEqual(window.status(), TegStatus.RUNNING, "the window's state");
            h.assertValueEqual(window.power(), (int) Math.round(teg.powerPerTick()), "the window's output");
            h.assertValueEqual(window.energy(), teg.energy().getEnergyStored(), "the window's store");
            for (int side = 0; side < 2; side++) {
                for (boolean in : new boolean[] {true, false}) {
                    String what = " of side " + side + (in ? " in" : " out");
                    h.assertValueEqual(
                            window.temperature(side, in),
                            (int) Math.round(teg.temperature(side, in)),
                            "the window's temperature" + what);
                    h.assertValueEqual(
                            window.pressure(side, in),
                            (int) Math.round(teg.pressure(side, in)),
                            "the window's pressure" + what);
                }
            }
        });
    }

    /** A core keeps its switch, its FE and its carried work through a save and a load (CONT-158). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_158_a_core_keeps_its_state_through_save_and_load(GameTestHelper h) {
        Area a = new Area(h, 5604);
        run(h, () -> {
            TegBlockEntity teg = build(a, true);
            add(in(a, LEFT), 20, 600);
            add(in(a, RIGHT), 20, 250);
            teg.setOn(true);
            a.step();
            h.assertTrue(teg.energy().getEnergyStored() > 0, "no FE to save");
            HolderLookup.Provider registries = a.level.registryAccess();
            CompoundTag saved = teg.saveCustomOnly(registries);
            TegBlockEntity copy = new TegBlockEntity(BlockPos.ZERO, teg.getBlockState());
            copy.loadCustomOnly(saved, registries);
            h.assertTrue(copy.on(), "the copy is off");
            h.assertValueEqual(copy.energy().getEnergyStored(), teg.energy().getEnergyStored(), "the copy's FE");
            a.close(copy.workCarry(), teg.workCarry(), "the copy's carried work");
        });
    }
}
