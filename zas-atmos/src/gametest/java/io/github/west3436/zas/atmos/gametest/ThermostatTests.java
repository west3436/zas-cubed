package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.Thermostat;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The area air controller's thermostat (CONT-045, CONT-165 to CONT-167): heaters built into its
 * vents and air conditioners built into its scrubbers, powered as the parts are. Each test builds a
 * sealed room of nine cells (x 1 to 3, y 1 to 3, z 1) in its own {@link Area}, with the controller
 * on its north wall, and drives its steps within one game tick.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermostatTests {
    private static final String BATCH = "zas_atmos_thermostat";
    private static final double TARGET = 293;
    private static final int MAX_STEPS = 400;

    private ThermostatTests() {}

    /**
     * The room and its controller, at (2, 2, 1) facing south. With {@code fe} the wall behind it is
     * the test FE block, so it always has power; without, it has only its buffer.
     */
    private static AreaAirControllerBlockEntity room(Area a, boolean fe) {
        a.box(0, 0, 4, 2);
        if (fe) {
            a.set(2, 2, 0, TestParts.battery().defaultBlockState());
        }
        a.place(
                2,
                2,
                1,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH));
        return a.entity(2, 2, 1);
    }

    /** A vent in the floor at x, blowing up into the room. */
    private static VentBlockEntity vent(Area a, int x) {
        a.place(x, 0, 1, GasDeviceTests.vent(Direction.UP));
        return a.entity(x, 0, 1);
    }

    /** A scrubber in the floor at x, taking from the room above it. */
    private static ScrubberBlockEntity scrubber(Area a, int x) {
        a.place(x, 0, 1, ScrubberTests.scrubber(Direction.UP));
        return a.entity(x, 0, 1);
    }

    private static GasMixture air(Area a) {
        return a.zone(2, 2, 1).mixture();
    }

    /** Brings the room's air to a temperature through the API, its zone having formed. */
    private static void bring(Area a, double kelvin) {
        GasMixture air = air(a);
        double joules = GasProperties.heatCapacity(air.moles()) * (kelvin - air.temperature());
        if (joules > 0) {
            a.settle(HeatMutations.add(a.level, a.point(2, 2, 1), joules, Area.SOURCE));
        } else if (joules < 0) {
            a.settle(HeatMutations.remove(a.level, a.point(2, 2, 1), -joules, Area.SOURCE));
        }
        // Within half a kelvin: the walls take or give a little heat while the mutation settles.
        double t = air(a).temperature();
        a.h.assertTrue(Math.abs(t - kelvin) < 0.5, "the room brought to " + kelvin + " K is at " + t + " K");
    }

    private static double band() {
        return AtmosServerConfig.get(AtmosServerConfig.THERMOSTAT_BAND);
    }

    /**
     * A cold room: the controller's vents heat it to the target, each at most the heater's power, and
     * stop there, inside the band; the thermostat then waits, and doesn't flap. The window and the
     * vent's window show what it does (CONT-165, CONT-166).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_166_a_controller_heats_a_cold_room_through_its_vents_to_the_target(GameTestHelper h) {
        Area a = new Area(h, 5710);
        AreaAirControllerBlockEntity controller = room(a, true);
        VentBlockEntity west = vent(a, 1);
        VentBlockEntity east = vent(a, 3);
        a.step();
        a.step();
        h.assertValueEqual(controller.thermostat(), Thermostat.State.OFF, "a new controller's thermostat");
        h.assertTrue(Double.isNaN(controller.target()), "a new controller has a target");
        bring(a, 280);

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ControllerMenu window = new ControllerMenu(1, player.getInventory(), controller);
        h.assertFalse(window.clickMenuButton(player, ControllerMenu.TARGET + 3), "stepped an off target");
        h.assertTrue(window.clickMenuButton(player, ControllerMenu.THERMOSTAT), "the window's switch");
        h.assertValueEqual(controller.target(), TARGET, "the target the switch starts at, for Human");

        double most = AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS);
        boolean heated = false;
        int steps = 0;
        for (; steps < MAX_STEPS; steps++) {
            a.step();
            double watts = west.heatWatts() + east.heatWatts();
            h.assertTrue(west.heatWatts() <= most + 1e-6, "a vent heated at " + west.heatWatts() + " W");
            if (controller.thermostat() == Thermostat.State.HEATING) {
                heated = true;
                h.assertValueEqual(window.sync().thermostat(), Thermostat.State.HEATING.ordinal(), "the window");
            } else if (heated && watts == 0) {
                break;
            }
        }
        h.assertTrue(heated, "it never heated");
        h.assertTrue(steps < MAX_STEPS, "it never stopped heating");
        double t = air(a).temperature();
        h.assertTrue(t >= TARGET - band() && t <= TARGET + 0.5, "it stopped at " + t + " K");

        for (int i = 0; i < 5; i++) {
            a.step();
            h.assertValueEqual(controller.thermostat(), Thermostat.State.IDLE, "the thermostat in the band");
            h.assertValueEqual(west.heatWatts() + east.heatWatts(), 0.0, "heat in the band");
        }
        VentMenu ventWindow = (VentMenu) west.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(ventWindow.value(VentMenu.DATA_HEAT), 0, "the vent's window in the band");

        // Cooled past the band, it heats again.
        bring(a, TARGET - band() - 1);
        a.step();
        a.step();
        h.assertValueEqual(controller.thermostat(), Thermostat.State.HEATING, "below the band again");
        h.assertTrue(west.heatWatts() > 0, "the vent didn't heat below the band");
        h.assertValueEqual(
                ventWindow.value(VentMenu.DATA_HEAT), (int) Math.round(west.heatWatts()), "the vent's window");
        a.clear(-1, -1, 5, 3);
        h.succeed();
    }

    /** A warm room: the controller's scrubbers cool it to the target and stop there (CONT-165, CONT-167). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_167_a_controller_cools_a_warm_room_through_its_scrubbers(GameTestHelper h) {
        Area a = new Area(h, 5711);
        AreaAirControllerBlockEntity controller = room(a, true);
        ScrubberBlockEntity scrubber = scrubber(a, 2);
        a.step();
        a.step();
        bring(a, 306);
        controller.setTarget(TARGET);

        double most = AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_WATTS);
        boolean cooled = false;
        int steps = 0;
        for (; steps < MAX_STEPS; steps++) {
            a.step();
            h.assertTrue(scrubber.coolingWatts() <= most + 1e-6, "it cooled at " + scrubber.coolingWatts() + " W");
            if (controller.thermostat() == Thermostat.State.COOLING) {
                cooled = true;
            } else if (cooled && scrubber.coolingWatts() == 0) {
                break;
            }
        }
        h.assertTrue(cooled, "it never cooled");
        h.assertTrue(steps < MAX_STEPS, "it never stopped cooling");
        double t = air(a).temperature();
        h.assertTrue(t <= TARGET + band() && t >= TARGET - 0.5, "it stopped at " + t + " K");
        a.step();
        h.assertValueEqual(controller.thermostat(), Thermostat.State.IDLE, "the thermostat in the band");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ScrubberMenu window = (ScrubberMenu) scrubber.createMenu(1, player.getInventory(), player);
        bring(a, TARGET + band() + 3);
        a.step();
        a.step();
        h.assertTrue(scrubber.coolingWatts() > 0, "the scrubber didn't cool above the band");
        h.assertValueEqual(
                window.value(ScrubberMenu.DATA_COOLING), (int) Math.round(scrubber.coolingWatts()), "its window");
        a.clear(-1, -1, 5, 3);
        h.succeed();
    }

    /**
     * Heat costs its controller what the heater's would, from what is left once its parts are paid:
     * at full power the heater's draw; with only a little FE to spare, only what that pays for, the
     * vent keeping its power; with the thermostat off, nothing; and with no power, nothing heats
     * (CONT-166).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_166_heat_is_paid_for_and_no_power_means_no_heat(GameTestHelper h) {
        Area a = new Area(h, 5712);
        AreaAirControllerBlockEntity controller = room(a, false);
        VentBlockEntity vent = vent(a, 2);
        int full = controller.buffer().getMaxEnergyStored();
        controller.buffer().set(full);
        a.step();
        a.step();
        bring(a, 250);
        controller.setTarget(TARGET);

        long own = (long) AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * DeviceTicks.PERIOD;
        long heating = (long) AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW) * DeviceTicks.PERIOD;
        double watts = AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS);
        for (int i = 0; i < 3; i++) {
            controller.buffer().set(full);
            a.step();
        }
        // Every step here runs in one game tick, so the vent's lease, paid at the first, still runs:
        // the controller pays its own draw and the heat. PowerChargeTests show a part paid each
        // atmos tick in game time.
        h.assertTrue(vent.power().suppliedThisStep(), "the vent's lease ran out");
        controller.buffer().set(full);
        a.step();
        h.assertValueEqual((long) (full - controller.buffer().getEnergyStored()), own + heating, "a step at full heat");
        a.close(vent.heatWatts(), watts, "the vent's heat at full power");

        // A quarter of the heater's draw to spare: a quarter of its heat.
        long spare = heating / 4;
        for (int i = 0; i < 3; i++) {
            controller.buffer().set((int) (own + spare));
            a.step();
            h.assertTrue(vent.power().isPowered(), "the vent lost its power to the heater");
        }
        h.assertTrue(Math.abs(vent.heatWatts() - watts / 4) < 1e-3, "the vent heated at " + vent.heatWatts() + " W");
        h.assertValueEqual(controller.buffer().getEnergyStored(), 0, "FE left after a quarter's heat");

        // Off, it costs nothing.
        controller.setTarget(Double.NaN);
        h.assertValueEqual(controller.thermostat(), Thermostat.State.OFF, "the thermostat switched off");
        for (int i = 0; i < 2; i++) {
            controller.buffer().set(full);
            a.step();
        }
        h.assertValueEqual(
                (long) (full - controller.buffer().getEnergyStored()), own, "a step with the thermostat off");
        h.assertValueEqual(vent.heatWatts(), 0.0, "heat with the thermostat off");

        // No power, no heat.
        controller.setTarget(TARGET);
        controller.buffer().set(0);
        a.step(); // what it had ordered and paid for before the power went
        for (int i = 0; i < 4; i++) {
            a.step();
            h.assertValueEqual(vent.heatWatts(), 0.0, "heat with no power");
            h.assertValueEqual(vent.coil().ordered(), 0.0, "heat ordered with no power");
        }
        h.assertFalse(controller.powered(), "the controller had power");
        h.assertTrue(air(a).temperature() < TARGET - band(), "the room isn't cold");
        a.clear(-1, -1, 5, 3);
        h.succeed();
    }

    /** The target outlasts a reload; a controller with none loads with none (CONT-165). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_165_the_target_is_saved(GameTestHelper h) {
        Area a = new Area(h, 5713);
        AreaAirControllerBlockEntity controller = room(a, true);
        var registries = a.level.registryAccess();
        AreaAirControllerBlockEntity copy =
                new AreaAirControllerBlockEntity(controller.getBlockPos(), controller.getBlockState());
        copy.loadWithComponents(controller.saveWithoutMetadata(registries), registries);
        h.assertTrue(Double.isNaN(copy.target()), "no target loaded as " + copy.target());
        controller.setTarget(301.5);
        copy.loadWithComponents(controller.saveWithoutMetadata(registries), registries);
        h.assertValueEqual(copy.target(), 301.5, "the target loaded");
        h.assertValueEqual(copy.thermostat(), Thermostat.State.IDLE, "the thermostat loaded");
        controller.setTarget(9999);
        h.assertValueEqual(controller.target(), Thermostat.MAX_TARGET, "a target past the heater's highest");
        a.clear(-1, -1, 5, 3);
        h.succeed();
    }
}
