package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;

import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.fire.Ignition;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineMenu;
import io.github.west3436.zas.atmos.turbine.TurbinePartBlock;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.heat.HeatSettings;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The gas turbine: a compressor and a turbine in a wall, with a combustion chamber between them
 * (CONT-160 to CONT-164). Each test builds in its own {@link Area}, 5610 to 5619: a stone box from x
 * = 0 to 12 split by walls at x = 4 and x = 8 into an intake room (x 1 to 3), the chamber (x 5 to 7)
 * and an exhaust room (x 9 to 11), each 3 × 3 × 3. The compressor sits in the first wall at (4, 2,
 * 2) and the turbine in the second at (8, 2, 2), both passing gas east. The tests step the devices
 * themselves, and draw the turbine's FE off each round as a load would.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineTests {
    private static final String BATCH = "zas_atmos_turbine";
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");

    private TurbineTests() {}

    private static BlockState part(boolean compressor) {
        return (compressor ? AtmosBlocks.COMPRESSOR : AtmosBlocks.TURBINE)
                .get()
                .defaultBlockState()
                .setValue(TurbinePartBlock.FACING, Direction.EAST);
    }

    /** The three rooms and the engine, each room's zone formed with the ambient air. */
    private static TurbineBlockEntity build(Area a) {
        a.box(0, 0, 12, 4);
        for (int x : new int[] {4, 8}) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    a.set(x, y, z, Area.STONE);
                }
            }
        }
        a.place(4, 2, 2, part(true));
        a.place(8, 2, 2, part(false));
        for (int x : new int[] {2, 6, 10}) {
            a.fill(x, 2, 2, Map.of(N2, 1.0));
        }
        return a.entity(8, 2, 2);
    }

    private static ZoneInfo intake(Area a) {
        return a.zone(2, 2, 2);
    }

    private static ZoneInfo chamber(Area a) {
        return a.zone(6, 2, 2);
    }

    private static ZoneInfo exhaust(Area a) {
        return a.zone(10, 2, 2);
    }

    /** Moles of gas in the three rooms and held by the turbine between them. */
    private static double moles(Area a, TurbineBlockEntity t) {
        return intake(a).mixture().totalMoles()
                + chamber(a).mixture().totalMoles()
                + exhaust(a).mixture().totalMoles()
                + t.held().totalMoles();
    }

    /** Raises the chamber to {@code kpa} with N2. */
    private static void pressurise(Area a, double kpa) {
        GasMixture m = chamber(a).mixture();
        double extra = (kpa - m.pressure()) * m.volume() / (GasMixture.GAS_CONSTANT * m.temperature());
        a.settle(GasMutations.add(
                a.level, a.point(6, 2, 2), new GasParcel(Map.of(N2, extra), m.temperature()), Area.SOURCE));
    }

    /** One round, its FE drawn off after. */
    private static void step(Area a, TurbineBlockEntity t) {
        a.step();
        t.energy().extractEnergy(Integer.MAX_VALUE, false);
    }

    private static int spin(Area a, int x) {
        return a.get(x, 2, 2).getValue(TurbinePartBlock.SPIN);
    }

    /** A chamber at the rooms' pressure, with nothing burning, makes nothing: the shaft stays still (CONT-161). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_no_power_with_nothing_to_burn(GameTestHelper h) {
        Area a = new Area(h, 5610);
        TurbineBlockEntity t = build(a);
        h.assertTrue(t.compressor() != null, "the turbine didn't find its compressor");
        t.setOn(true);
        int made = 0;
        for (int i = 0; i < 20; i++) {
            a.step();
            made += t.energy().extractEnergy(Integer.MAX_VALUE, false);
        }
        h.assertValueEqual(made, 0, "FE made with nothing to burn");
        h.assertValueEqual(t.rpm(), 0.0, "the shaft's speed");
        h.assertValueEqual(t.status(), TurbineStatus.IDLE, "the turbine's status");
        h.assertValueEqual(spin(a, 8), 0, "the turbine's blades");
        h.succeed();
    }

    /**
     * A pressed chamber spins the shaft up and makes FE; the compressor turns with it and draws from
     * the intake; once the pressures even out the shaft spins down to a stop. Through it all no gas
     * is made or lost: the rooms' moles and what the turbine holds between them stay the same
     * (CONT-160, CONT-161, CONT-162, CONT-164).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_a_pressed_chamber_spins_it_up_and_down_and_keeps_its_moles(GameTestHelper h) {
        Area a = new Area(h, 5611);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            TurbineBlockEntity t = build(a);
            pressurise(a, 300);
            double before = moles(a, t);
            double intakeBefore = intake(a).mixture().totalMoles();
            t.setOn(true);
            double top = 0;
            double compressorWork = 0;
            int made = 0;
            int maxSpin = 0;
            for (int i = 0; i < 40; i++) {
                a.step();
                made += t.energy().extractEnergy(Integer.MAX_VALUE, false);
                top = Math.max(top, t.rpm());
                compressorWork += t.compressorWork();
                maxSpin = Math.max(maxSpin, spin(a, 4));
                a.close(moles(a, t), before, "moles at round " + i);
            }
            h.assertTrue(top > 0, "the shaft never turned");
            h.assertTrue(made > 0, "no FE made");
            h.assertTrue(maxSpin > 0, "the compressor's blades never turned");
            h.assertTrue(compressorWork > 0, "the compressor did no work");
            h.assertTrue(intake(a).mixture().totalMoles() < intakeBefore, "the compressor drew nothing in");

            // It runs down as the pressures even out.
            int i = 0;
            for (; i < 300 && t.rpm() > 0; i++) {
                step(a, t);
                a.close(moles(a, t), before, "moles at round " + (40 + i));
            }
            h.assertValueEqual(t.rpm(), 0.0, "the shaft's speed after " + (40 + i) + " rounds");
            h.assertValueEqual(spin(a, 8), 0, "the turbine's blades at rest");
            h.assertValueEqual(spin(a, 4), 0, "the compressor's blades at rest");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /**
     * Hydrogen burning in the chamber, lit by the engine's own fire, spins the turbine up and makes
     * FE; switched off, it spins down to a stop and makes nothing more after (CONT-161, CONT-162,
     * CONT-164).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_162_a_burning_chamber_makes_fe_and_spins_down_when_off(GameTestHelper h) {
        Area a = new Area(h, 5612);
        TurbineBlockEntity t = build(a);
        GasMixture m = chamber(a).mixture();
        a.settle(GasMutations.add(
                a.level,
                a.point(6, 2, 2),
                new GasParcel(Map.of(H2, 0.1 * m.totalMoles()), m.temperature()),
                Area.SOURCE));
        t.setOn(true);
        for (int i = 0; i < 4; i++) {
            step(a, t); // the turbine's cell watches settle
        }
        Ignition lit = a.settle(Fires.ignite(a.level, a.abs(6, 2, 2), Area.SOURCE));
        h.assertTrue(lit.burning(), "the chamber didn't light: " + lit.outcome());
        double top = 0;
        double hottest = 0;
        int made = 0;
        // Until it spins and makes FE, so it is switched off while it turns.
        for (int i = 0; i < 30 && (made == 0 || t.rpm() == 0 || hottest <= 400); i++) {
            a.step();
            made += t.energy().extractEnergy(Integer.MAX_VALUE, false);
            top = Math.max(top, t.rpm());
            hottest = Math.max(hottest, t.chamberTemperature());
        }
        h.assertTrue(hottest > 400, "the chamber never got hot: " + hottest + " K");
        h.assertTrue(top > 0, "the shaft never turned");
        h.assertTrue(made > 0, "no FE made from the fire");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        TurbineMenu menu = new TurbineMenu(0, t, new SimpleContainerData(TurbineMenu.DATA_COUNT));
        menu.clickMenuButton(player, TurbineMenu.TOGGLE);
        h.assertFalse(t.on(), "the window's button didn't switch it off");
        h.assertTrue(t.rpm() > 0, "the shaft stopped before it was switched off");
        double last = t.shaftEnergy();
        int i = 0;
        for (; i < 300 && t.rpm() > 0; i++) {
            step(a, t);
            h.assertTrue(t.shaftEnergy() <= last, "the shaft sped up while off");
            last = t.shaftEnergy();
        }
        h.assertValueEqual(t.rpm(), 0.0, "the shaft's speed " + i + " rounds after switching off");
        h.assertValueEqual(t.status(), TurbineStatus.OFF, "the turbine's status");
        h.assertValueEqual(spin(a, 8), 0, "the turbine's blades at rest");
        made = 0;
        for (int j = 0; j < 5; j++) {
            a.step();
            made += t.energy().extractEnergy(Integer.MAX_VALUE, false);
        }
        h.assertValueEqual(made, 0, "FE made while off and still");
        h.succeed();
    }

    /** Without its compressor the turbine says so and does nothing (CONT-161). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_no_compressor_no_turbine(GameTestHelper h) {
        Area a = new Area(h, 5613);
        TurbineBlockEntity t = build(a);
        a.set(4, 2, 2, Area.STONE);
        pressurise(a, 300);
        t.setOn(true);
        for (int i = 0; i < 5; i++) {
            a.step();
        }
        h.assertValueEqual(t.status(), TurbineStatus.NO_COMPRESSOR, "the turbine's status");
        h.assertValueEqual(t.rpm(), 0.0, "the shaft's speed");
        h.succeed();
    }

    /** The turbine keeps its switch, its shaft's energy, its FE and the gas it holds through a save and a load (CONT-161). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_a_turbine_keeps_its_state_through_save_and_load(GameTestHelper h) {
        Area a = new Area(h, 5614);
        TurbineBlockEntity t = build(a);
        t.setOn(true);
        t.setShaftEnergy(250_000);
        a.step();
        h.assertTrue(t.energy().getEnergyStored() > 0, "no FE to save");
        HolderLookup.Provider registries = a.level.registryAccess();
        CompoundTag saved = t.saveCustomOnly(registries);
        TurbineBlockEntity copy = new TurbineBlockEntity(BlockPos.ZERO, t.getBlockState());
        copy.loadCustomOnly(saved, registries);
        h.assertTrue(copy.on(), "the copy is off");
        a.close(copy.shaftEnergy(), t.shaftEnergy(), "the copy's shaft energy");
        h.assertValueEqual(copy.energy().getEnergyStored(), t.energy().getEnergyStored(), "the copy's FE");
        a.close(copy.held().totalMoles(), t.held().totalMoles(), "the gas the copy holds");
        a.close(copy.workCarry(), t.workCarry(), "the copy's carried work");
        h.succeed();
    }

    /** The turbine's window shows its switch, state, speed, chamber, output and store as they are (CONT-163). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_163_the_turbine_window_shows_its_speed_chamber_and_power(GameTestHelper h) {
        Area a = new Area(h, 5615);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            TurbineBlockEntity t = build(a);
            pressurise(a, 300);
            t.setOn(true);
            for (int i = 0; i < 6; i++) {
                a.step();
            }
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            TurbineMenu menu = (TurbineMenu) t.createMenu(1, player.getInventory(), player);
            h.assertTrue(t.rpm() > 0, "the shaft isn't turning");
            h.assertTrue(menu.on(), "the window shows it off");
            h.assertValueEqual(menu.status(), TurbineStatus.RUNNING, "the window's state");
            h.assertValueEqual(menu.value(TurbineMenu.DATA_RPM), (int) Math.round(t.rpm()), "the window's rpm");
            h.assertValueEqual(
                    menu.value(TurbineMenu.DATA_CHAMBER_PRESSURE),
                    (int) Math.round(t.chamberPressure()),
                    "the window's chamber pressure");
            h.assertValueEqual(
                    menu.value(TurbineMenu.DATA_CHAMBER_TEMPERATURE),
                    (int) Math.round(t.chamberTemperature()),
                    "the window's chamber temperature");
            h.assertValueEqual(
                    menu.value(TurbineMenu.DATA_POWER), (int) Math.round(t.powerPerTick()), "the window's output");
            h.assertValueEqual(menu.energy(), t.energy().getEnergyStored(), "the window's store");
            h.assertValueEqual(menu.capacity(), t.energy().getMaxEnergyStored(), "the window's capacity");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }
}
