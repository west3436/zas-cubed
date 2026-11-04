package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.heater.AirConditionerBlock;
import io.github.west3436.zas.atmos.heater.AirConditionerBlockEntity;
import io.github.west3436.zas.atmos.heater.AirConditionerMath;
import io.github.west3436.zas.atmos.heater.AirConditionerMenu;
import io.github.west3436.zas.atmos.heater.HeaterMath;
import io.github.west3436.zas.atmos.heater.ThermoStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The air conditioner, the requirements' freezer (CONT-019, CONT-087, D-73). Each test builds in its
 * own {@link Site} and runs within one game tick, in a batch of its own so other tests' devices never
 * meet these.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirConditionerTests {
    private static final String BATCH = "zas_atmos_air_conditioner";

    /** How close a room is held above the setpoint, K: what the walls let in over a step, and room for a change in their rates. */
    private static final double HELD = 0.5;

    private AirConditionerTests() {}

    private static AirConditionerBlockEntity airConditioner(Site s, int x, int y, int z) {
        BlockState state = AtmosBlocks.AIR_CONDITIONER
                .get()
                .defaultBlockState()
                .setValue(AirConditionerBlock.FACING, Direction.NORTH);
        return s.place(x, y, z, state, AirConditionerBlockEntity.class);
    }

    private static double temperature(Site s, int x, int y, int z) {
        return s.zone(x, y, z).mixture().temperature();
    }

    private static boolean lit(Site s, AirConditionerBlockEntity ac) {
        return s.level.getBlockState(ac.getBlockPos()).getValue(AirConditionerBlock.LIT);
    }

    /** The FE a step of cooling at this power costs the battery (CONT-087). */
    private static int cost(double watts) {
        return HeaterMath.energy(
                watts * AtmosTicks.SECONDS,
                AtmosTicks.SECONDS,
                AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_WATTS),
                AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_DRAW),
                AtmosTicks.PERIOD);
    }

    /**
     * An air conditioner in a sealed room of air, on a battery, cools the room to its setpoint, set
     * through its GUI, and no further: 5 kW at most, drawing 60 FE/t from its battery at full power
     * and in proportion below it. It does nothing off, with no battery, at or below the setpoint, or
     * in open air; its slot refuses what isn't a battery; with too little charge it takes what the
     * charge pays for, then stops. Its GUI refuses unknown buttons and clamps the setpoint to 73–293
     * K, and broken it drops its battery (CONT-019, CONT-074, CONT-087, CONT-112).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_019_air_conditioner_cools_its_room_on_its_battery(GameTestHelper h) {
        Site s = new Site(h, 80);
        try {
            BatteryTests.clear(s);
            s.room(0, 4); // interior x 1–3
            AirConditionerBlockEntity ac = airConditioner(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2); // the room holds the Overworld's air
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            AirConditionerMenu menu = (AirConditionerMenu) ac.createMenu(1, hand.getInventory(), hand);
            h.assertValueEqual(
                    AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_WATTS),
                    5000,
                    "the configured power (CONT-087)");
            h.assertValueEqual(
                    AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_DRAW), 60, "the configured draw");
            h.assertValueEqual(
                    cost(AirConditionerMath.DEFAULT_MAX_WATTS), 60 * AtmosTicks.PERIOD, "a step at full power");
            h.assertTrue(
                    Atmospheres.at(s.level, ac.point())
                            .zone()
                            .equals(Atmospheres.at(s.level, s.abs(2, 2, 2)).zone()),
                    "the air conditioner isn't in the room");

            // Off, it does nothing.
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.OFF, "the new air conditioner's status");
            s.close(ac.setpoint(), AirConditionerMath.DEFAULT_SETPOINT, 0, "the new air conditioner's setpoint");

            // The GUI: on, setpoint down 10 K; unknown buttons refused.
            h.assertTrue(menu.clickMenuButton(hand, AirConditionerMenu.TOGGLE), "switching on");
            h.assertTrue(menu.clickMenuButton(hand, AirConditionerMenu.SETPOINT_DOWN_10), "setpoint -10");
            h.assertFalse(menu.clickMenuButton(hand, -1), "button -1");
            h.assertFalse(menu.clickMenuButton(hand, AirConditionerMenu.LAST_BUTTON + 1), "button 5");
            h.assertTrue(ac.on(), "the air conditioner is off");
            double setpoint = AirConditionerMath.DEFAULT_SETPOINT - 10;
            s.close(ac.setpoint(), setpoint, 1e-9, "the setpoint");

            // On with no battery, it does nothing; the slot refuses coal.
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.NO_BATTERY, "the air conditioner with no battery");
            h.assertValueEqual(
                    ac.battery().insertItem(0, new ItemStack(Items.COAL), false).getCount(), 1, "coal went in");

            // A full battery: 5 kW, 60 FE/t over the step.
            BatteryTests.swap(ac.battery(), AtmosItems.BATTERY.get().full());
            double c = GasProperties.heatCapacity(s.zone(2, 2, 2).mixture().moles());
            double before = temperature(s, 2, 2, 2);
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.COOLING, "the cooling air conditioner's status");
            s.close(ac.watts(), AirConditionerMath.DEFAULT_MAX_WATTS, 1e-9, "the air conditioner's power");
            h.assertValueEqual(ac.energy(), 100_000 - 60 * AtmosTicks.PERIOD, "the battery after a step");
            h.assertTrue(lit(s, ac), "not lit while cooling");
            s.close(
                    before - temperature(s, 2, 2, 2),
                    AirConditionerMath.DEFAULT_MAX_WATTS * AtmosTicks.SECONDS / c,
                    0.02,
                    "the room's fall over a step of 5 kJ");
            h.assertValueEqual(menu.energy(), ac.energy(), "the GUI's charge");
            h.assertValueEqual(menu.capacity(), 100_000, "the GUI's capacity");

            // It reaches the setpoint and never passes it, paying in proportion to the heat.
            boolean reached = false;
            for (int i = 0; i < 120; i++) {
                int charge = ac.energy();
                s.cycle(ac);
                double t = temperature(s, 2, 2, 2);
                h.assertTrue(t >= setpoint - 1e-6, "the room fell below the setpoint: " + t + " K");
                h.assertValueEqual(charge - ac.energy(), cost(ac.watts()), "the step's cost at " + ac.watts() + " W");
                reached |= t < setpoint + 0.5;
            }
            h.assertTrue(reached, "the room never came within 0.5 K of the setpoint");
            h.assertTrue(ac.watts() < AirConditionerMath.DEFAULT_MAX_WATTS, "still at full power at the setpoint");
            s.close(menu.value(AirConditionerMenu.DATA_ROOM) / 10.0, ac.roomTemperature(), 0.06, "the GUI's room");

            // An air conditioner never heats: set above the room, it takes nothing and draws nothing.
            menu.clickMenuButton(hand, AirConditionerMenu.SETPOINT_DOWN_10 + 3);
            s.close(ac.setpoint(), AirConditionerMath.MAX_SETPOINT, 1e-9, "the highest setpoint");
            int charge = ac.energy();
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.AT_SETPOINT, "the status below the setpoint");
            h.assertValueEqual(ac.energy(), charge, "the battery below the setpoint");
            h.assertFalse(lit(s, ac), "lit at the setpoint");

            // Too little charge: it takes what the charge pays for, then stops.
            for (int i = 0; i < 40; i++) {
                menu.clickMenuButton(hand, AirConditionerMenu.SETPOINT_DOWN_10);
            }
            s.close(ac.setpoint(), AirConditionerMath.MIN_SETPOINT, 0, "the lowest setpoint");
            BatteryTests.swap(ac.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), 300));
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.COOLING, "the air conditioner on its last 300 FE");
            s.close(
                    ac.watts(),
                    AirConditionerMath.DEFAULT_MAX_WATTS * 300 / (60.0 * AtmosTicks.PERIOD),
                    1e-6,
                    "the power 300 FE pays for");
            h.assertValueEqual(ac.energy(), 0, "the battery after its last step");
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.BATTERY_EMPTY, "the air conditioner on an empty battery");
            h.assertValueEqual(ac.watts(), 0.0, "the power on an empty battery");
            h.assertFalse(lit(s, ac), "lit on an empty battery");

            // In open air it has no room, and leaves its battery alone.
            AirConditionerBlockEntity outside = airConditioner(s, 8, 1, 2);
            BatteryTests.swap(outside.battery(), AtmosItems.BATTERY.get().full());
            outside.setSetpoint(AirConditionerMath.MIN_SETPOINT);
            outside.setOn(true);
            s.cycle(outside);
            h.assertValueEqual(outside.status(), ThermoStatus.NO_ROOM, "the air conditioner in open air");
            h.assertValueEqual(outside.energy(), 100_000, "the battery in open air");
            AirConditionerMenu outsideMenu = (AirConditionerMenu) outside.createMenu(2, hand.getInventory(), hand);
            h.assertValueEqual(
                    outsideMenu.value(AirConditionerMenu.DATA_ROOM),
                    AirConditionerMenu.NO_ROOM,
                    "the GUI's room in open air");

            // Switched off, it stops; broken, it drops its battery.
            h.assertTrue(menu.clickMenuButton(hand, AirConditionerMenu.TOGGLE), "switching off");
            s.cycle(ac);
            h.assertValueEqual(ac.status(), ThermoStatus.OFF, "the switched-off air conditioner");
            s.set(8, 1, 2, Site.AIR);
            h.assertTrue(
                    !s.level
                            .getEntitiesOfClass(
                                    net.minecraft.world.entity.item.ItemEntity.class,
                                    new net.minecraft.world.phys.AABB(s.abs(8, 1, 2)).inflate(1),
                                    e -> e.getItem().is(AtmosItems.BATTERY.get()))
                            .isEmpty(),
                    "the broken air conditioner dropped no battery");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * An air conditioner holds a room that warms through its walls at its setpoint. A sealed stone
     * room of the Overworld's air, set 5 K under it, cools to the setpoint and stays there, steady
     * and within {@link #HELD} K above it, never below it, while the walls let heat back in. On a
     * nearly empty battery it holds the room until the battery runs out, then stops, and the room
     * warms (CONT-019, CONT-087, D-73).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_019_air_conditioner_holds_a_room_at_its_setpoint(GameTestHelper h) {
        Site s = new Site(h, 81);
        try {
            BatteryTests.clear(s);
            s.room(0, 4); // interior x 1–3
            AirConditionerBlockEntity ac = airConditioner(s, 2, 1, 2);
            BatteryTests.swap(
                    ac.battery(), AtmosItems.HIGH_CAPACITY_BATTERY.get().full());
            double setpoint = 288;
            ac.setSetpoint(setpoint);
            ac.setOn(true);
            BatteryTests.formZone(s, 2, 2, 2);

            double lowest = Double.MAX_VALUE;
            int reached = -1;
            for (int sec = 0; sec < 300; sec++) {
                s.cycle(ac);
                double t = temperature(s, 2, 2, 2);
                lowest = Math.min(lowest, t);
                if (reached < 0 && t < setpoint + HELD) {
                    reached = sec;
                }
            }
            h.assertTrue(reached >= 0, "the room never came within " + HELD + " K of the setpoint");
            double highest = 0;
            double lastLowest = Double.MAX_VALUE;
            for (int sec = 0; sec < 60; sec++) {
                s.cycle(ac);
                double t = temperature(s, 2, 2, 2);
                lowest = Math.min(lowest, t);
                lastLowest = Math.min(lastLowest, t);
                highest = Math.max(highest, t);
            }
            h.assertTrue(lowest >= setpoint - 1e-6, "the room fell below the setpoint: " + lowest + " K");
            h.assertTrue(
                    highest < setpoint + HELD,
                    "the room isn't held within " + HELD + " K of its setpoint: " + highest + " K");
            h.assertTrue(
                    highest - lastLowest < 0.05,
                    "the room swung over the last minute: " + lastLowest + " K to " + highest + " K");
            h.assertValueEqual(ac.status(), ThermoStatus.COOLING, "the air conditioner holding the room");
            h.assertTrue(
                    ac.watts() > 0 && ac.watts() < AirConditionerMath.DEFAULT_MAX_WATTS,
                    "holding the room should take some of the power: " + ac.watts() + " W");

            // On 500 FE it holds the room, then stops, and the walls warm the room.
            BatteryTests.swap(ac.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), 500));
            int cooled = 0;
            for (int sec = 0; sec < 120 && ac.status() != ThermoStatus.BATTERY_EMPTY; sec++) {
                s.cycle(ac);
                cooled++;
            }
            h.assertValueEqual(ac.status(), ThermoStatus.BATTERY_EMPTY, "the air conditioner after 500 FE");
            h.assertValueEqual(ac.energy(), 0, "the battery after it ran out");
            h.assertTrue(cooled > 2, "500 FE lasted only " + cooled + " s");
            double empty = temperature(s, 2, 2, 2);
            for (int sec = 0; sec < 60; sec++) {
                s.cycle(ac);
            }
            h.assertTrue(temperature(s, 2, 2, 2) > empty + 0.1, "the room didn't warm once the battery ran out");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * An air conditioner in a controller's room runs on its own battery. The controller doesn't power
     * it, list it or pay for it, and a link to it doesn't hold (CONT-018, CONT-019, CONT-087, D-73).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_019_a_controller_leaves_an_air_conditioner_in_its_room_alone(GameTestHelper h) {
        Site s = new Site(h, 82);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            AirConditionerBlockEntity ac = airConditioner(s, 2, 1, 3);
            BatteryTests.swap(ac.battery(), AtmosItems.BATTERY.get().full());
            ac.setSetpoint(AirConditionerMath.MIN_SETPOINT);
            ac.setOn(true);
            Object part = ac;
            h.assertFalse(part instanceof io.github.west3436.zas.atmos.power.PoweredDevice, "it is a part");
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            for (int i = 0; i < 4 && ac.status() != ThermoStatus.COOLING; i++) {
                s.cycle(c, ac);
            }
            h.assertValueEqual(ac.status(), ThermoStatus.COOLING, "the air conditioner in the controller's room");
            c.toggleLink(ac.getBlockPos());
            int before = ac.energy();
            s.cycle(c, ac);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertValueEqual(ac.energy(), before - 60 * AtmosTicks.PERIOD, "the battery after a step");
            h.assertValueEqual(c.buffer().getEnergyStored(), 10_000 - own, "the buffer paid for the air conditioner");
            h.assertTrue(
                    c.parts().stream().noneMatch(p -> p.pos().equals(ac.getBlockPos())),
                    "the controller lists the air conditioner");
            h.assertFalse(c.links().contains(ac.getBlockPos()), "the link to the air conditioner held");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }
}
