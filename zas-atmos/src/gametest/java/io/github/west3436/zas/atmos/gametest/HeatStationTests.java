package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.heater.HeaterMath;
import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.heater.HeaterStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The heater (CONT-019, CONT-087) and S-05, heat: a room beside lava warms, a room in a 2.7 K
 * vacuum cools through its walls, and a heater on a battery in a room holds the room at its
 * setpoint until the battery runs out. Each test builds in its own {@link Site} and runs
 * within one game tick. The vacuum tests borrow the End, as S-01 does, each in a batch of its own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeatStationTests {
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final double T = StandardAir.TEMPERATURE;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    private HeatStationTests() {}

    private static HeaterBlockEntity heater(Site s, int x, int y, int z, Direction facing) {
        BlockState state = AtmosBlocks.HEATER.get().defaultBlockState().setValue(HeaterBlock.FACING, facing);
        return s.place(x, y, z, state, HeaterBlockEntity.class);
    }

    /** A hollow box from (x0, 0, 0) to (x1, 4, 4) with walls of {@code wall}: a 3 × 3 × 3 room for x0 + 4 = x1. */
    private static void room(Site s, int x0, int x1, BlockState wall) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    boolean edge = x == x0 || x == x1 || y == 0 || y == 4 || z == 0 || z == 4;
                    s.set(x, y, z, edge ? wall : Site.AIR);
                }
            }
        }
    }

    /**
     * Fills the zone at a point with standard air at a temperature, from empty, and returns it: a
     * trace of nitrogen forms the zone, and its volume sets the moles.
     */
    private static ZoneInfo fillAir(Site s, int x, int y, int z, double temperature) {
        s.settle(
                GasMutations.add(s.level, s.point(x, y, z), new GasParcel(Map.of(N2, 1e-6), temperature), Site.SOURCE));
        double litres = s.zone(x, y, z).mixture().volume();
        double moles =
                StandardAir.PRESSURE * litres / (io.github.west3436.zas.api.gas.GasMixture.GAS_CONSTANT * temperature);
        GasParcel air = new GasParcel(
                Map.of(N2, moles * (1 - StandardAir.OXYGEN_FRACTION), O2, moles * StandardAir.OXYGEN_FRACTION),
                temperature);
        s.settle(GasMutations.add(s.level, s.point(x, y, z), air, Site.SOURCE));
        return s.zone(x, y, z);
    }

    private static double temperature(Site s, int x, int y, int z) {
        return s.zone(x, y, z).mixture().temperature();
    }

    /** Runs a body in the End with the test datapack's 2.7 K vacuum ambient. */
    private static void inVacuum(GameTestHelper h, int site, java.util.function.Consumer<Site> body) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        h.assertTrue(
                Math.abs(vacuum.temperature() - Ambient.COSMIC_BACKGROUND) < 1e-9 && vacuum.temperature() < 3,
                "the vacuum's background isn't 2.7 K: " + vacuum.temperature());
        Site s = new Site(h, end, site);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            s.clear();
            body.accept(s);
        } finally {
            s.clear();
            Ambients.overrideForTest(Level.END, null);
        }
    }

    // ---- CONT-019, CONT-087 ---------------------------------------------------------------

    /** The FE a step of heat at this power costs the battery (CONT-087). */
    private static int cost(double watts) {
        return HeaterMath.energy(
                watts * AtmosTicks.SECONDS,
                AtmosTicks.SECONDS,
                AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS),
                AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW),
                AtmosTicks.PERIOD);
    }

    /**
     * A heater in a sealed room of air, on a battery, heats the room to its setpoint, set through
     * its GUI, and no further: 5 kW at most, drawing 60 FE/t from its battery at full power and in
     * proportion below it. It does nothing off, with no battery, at or above the setpoint, or in
     * open air; its slot refuses what isn't a battery; with too little charge it adds what the
     * charge pays for, then stops. Its GUI refuses unknown buttons and clamps the setpoint to
     * 293–573 K, and broken it drops its battery (CONT-019, CONT-074, CONT-087, CONT-112).
     */
    @GameTest(template = "empty")
    public static void cont_019_heater_heats_its_room_on_its_battery(GameTestHelper h) {
        Site s = new Site(h, 18);
        try {
            BatteryTests.clear(s);
            s.room(0, 4); // interior x 1–3
            HeaterBlockEntity heater = heater(s, 2, 1, 2, Direction.NORTH);
            BatteryTests.formZone(s, 2, 2, 2); // the room holds the Overworld's air
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            HeaterMenu menu = (HeaterMenu) heater.createMenu(1, hand.getInventory(), hand);
            h.assertValueEqual(
                    AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS), 5000, "the configured power (CONT-087)");
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW), 60, "the configured draw");
            h.assertValueEqual(cost(HeaterMath.DEFAULT_MAX_WATTS), 60 * AtmosTicks.PERIOD, "a step at full power");

            // Off, it does nothing.
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.OFF, "the new heater's status");

            // The GUI: on, setpoint up 10 K; unknown buttons refused.
            h.assertTrue(menu.clickMenuButton(hand, HeaterMenu.TOGGLE), "switching on");
            h.assertTrue(menu.clickMenuButton(hand, HeaterMenu.SETPOINT_DOWN_10 + 3), "setpoint +10");
            h.assertFalse(menu.clickMenuButton(hand, -1), "button -1");
            h.assertFalse(menu.clickMenuButton(hand, HeaterMenu.LAST_BUTTON + 1), "button 5");
            h.assertTrue(heater.on(), "the heater is off");
            double setpoint = T + 10;
            s.close(heater.setpoint(), setpoint, 1e-9, "the setpoint");

            // On with no battery, it does nothing; the slot refuses coal.
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.NO_BATTERY, "the heater with no battery");
            h.assertValueEqual(
                    heater.battery()
                            .insertItem(0, new ItemStack(Items.COAL), false)
                            .getCount(),
                    1,
                    "coal went in");

            // A full battery: 5 kW, 60 FE/t over the step.
            BatteryTests.swap(heater.battery(), AtmosItems.BATTERY.get().full());
            double c = GasProperties.heatCapacity(s.zone(2, 2, 2).mixture().moles());
            double before = temperature(s, 2, 2, 2);
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.HEATING, "the heating heater's status");
            s.close(heater.watts(), HeaterMath.DEFAULT_MAX_WATTS, 1e-9, "the heater's power");
            h.assertValueEqual(heater.energy(), 100_000 - 60 * AtmosTicks.PERIOD, "the battery after a step");
            h.assertTrue(
                    s.level.getBlockState(heater.getBlockPos()).getValue(HeaterBlock.LIT), "not lit while heating");
            s.close(
                    temperature(s, 2, 2, 2) - before,
                    HeaterMath.DEFAULT_MAX_WATTS * AtmosTicks.SECONDS / c,
                    0.02,
                    "the room's rise over a step of 5 kJ");
            h.assertValueEqual(menu.energy(), heater.energy(), "the GUI's charge");
            h.assertValueEqual(menu.capacity(), 100_000, "the GUI's capacity");

            // It reaches the setpoint and never passes it, paying in proportion to the heat.
            boolean reached = false;
            for (int i = 0; i < 120; i++) {
                int charge = heater.energy();
                s.cycle(heater);
                double t = temperature(s, 2, 2, 2);
                h.assertTrue(t <= setpoint + 1e-6, "the room passed the setpoint: " + t + " K");
                h.assertValueEqual(
                        charge - heater.energy(), cost(heater.watts()), "the step's cost at " + heater.watts() + " W");
                reached |= t > setpoint - 0.5;
            }
            h.assertTrue(reached, "the room never came within 0.5 K of the setpoint");
            h.assertTrue(heater.watts() < HeaterMath.DEFAULT_MAX_WATTS, "still at full power at the setpoint");
            s.close(menu.value(HeaterMenu.DATA_ROOM) / 10.0, heater.roomTemperature(), 0.06, "the GUI's room");

            // A heater never cools: set below the room, it adds nothing and draws nothing.
            for (int i = 0; i < 40; i++) {
                menu.clickMenuButton(hand, HeaterMenu.SETPOINT_DOWN_10);
            }
            s.close(heater.setpoint(), HeaterMath.MIN_SETPOINT, 0, "the lowest setpoint");
            int charge = heater.energy();
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.AT_SETPOINT, "the status above the setpoint");
            h.assertValueEqual(heater.energy(), charge, "the battery above the setpoint");
            h.assertFalse(s.level.getBlockState(heater.getBlockPos()).getValue(HeaterBlock.LIT), "lit at the setpoint");

            // Too little charge: it adds what the charge pays for, then stops.
            for (int i = 0; i < 40; i++) {
                menu.clickMenuButton(hand, HeaterMenu.SETPOINT_DOWN_10 + 3);
            }
            s.close(heater.setpoint(), HeaterMath.MAX_SETPOINT, 0, "the highest setpoint");
            BatteryTests.swap(heater.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), 300));
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.HEATING, "the heater on its last 300 FE");
            s.close(
                    heater.watts(),
                    HeaterMath.DEFAULT_MAX_WATTS * 300 / (60.0 * AtmosTicks.PERIOD),
                    1e-6,
                    "the power 300 FE pays for");
            h.assertValueEqual(heater.energy(), 0, "the battery after its last step");
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.BATTERY_EMPTY, "the heater on an empty battery");
            h.assertValueEqual(heater.watts(), 0.0, "the power on an empty battery");

            // In open air it has no room, and leaves its battery alone.
            HeaterBlockEntity outside = heater(s, 8, 1, 2, Direction.NORTH);
            BatteryTests.swap(outside.battery(), AtmosItems.BATTERY.get().full());
            outside.setSetpoint(HeaterMath.MAX_SETPOINT);
            outside.setOn(true);
            s.cycle(outside);
            h.assertValueEqual(outside.status(), HeaterStatus.NO_ROOM, "the heater in open air");
            h.assertValueEqual(outside.energy(), 100_000, "the battery in open air");
            HeaterMenu outsideMenu = (HeaterMenu) outside.createMenu(2, hand.getInventory(), hand);
            h.assertValueEqual(
                    outsideMenu.value(HeaterMenu.DATA_ROOM), HeaterMenu.NO_ROOM, "the GUI's room in open air");

            // Switched off, it stops; broken, it drops its battery.
            h.assertTrue(menu.clickMenuButton(hand, HeaterMenu.TOGGLE), "switching off");
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.OFF, "the switched-off heater");
            s.set(8, 1, 2, Site.AIR);
            h.assertTrue(
                    !s.level
                            .getEntitiesOfClass(
                                    net.minecraft.world.entity.item.ItemEntity.class,
                                    new net.minecraft.world.phys.AABB(s.abs(8, 1, 2)).inflate(1),
                                    e -> e.getItem().is(AtmosItems.BATTERY.get()))
                            .isEmpty(),
                    "the broken heater dropped no battery");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    // ---- S-05 -----------------------------------------------------------------------------

    /**
     * S-05: a sealed stone room in the Overworld, with a lava pool behind its east wall, warms
     * every atmos tick: each of the wall's nine faces conducts to lava's 1,450 K through the wall
     * and the lava's own layer (HEAT-003, HEAT-016).
     */
    @GameTest(template = "empty")
    public static void s_05_room_beside_lava_warms(GameTestHelper h) {
        Site s = new Site(h, 19);
        try {
            s.clear();
            s.room(2, 6); // interior x 3–5; the east wall is x = 6
            // A stone basin behind the east wall, then the lava, which can't flow anywhere.
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    s.set(7, y, z, Site.STONE);
                    s.set(8, y, z, Site.STONE);
                }
            }
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    s.set(7, y, z, Blocks.LAVA.defaultBlockState());
                }
            }
            // The room holds the Overworld's air; a little more forms its zone.
            s.settle(GasMutations.add(s.level, s.point(4, 2, 2), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));
            double start = temperature(s, 4, 2, 2);
            double t = start;
            for (int i = 0; i < 30; i++) {
                s.tick();
                double next = temperature(s, 4, 2, 2);
                h.assertTrue(next > t, "second " + i + ": the room didn't warm (" + t + " K to " + next + " K)");
                t = next;
            }
            // Nine faces at U = 1/(1/1.7 + 1/1.7) = 0.85 W/K each, about 1.2 kK below the lava:
            // nearly 9 kW into about 23 kJ/K of air, less what the other walls lose.
            h.assertTrue(t - start > 5, "30 s beside lava warmed the room only " + (t - start) + " K");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * S-05: a sealed stone room of air in the End, given the test datapack's 2.7 K vacuum, cools
     * every atmos tick: its walls radiate from their outer faces (HEAT-001, HEAT-004).
     */
    @GameTest(template = "empty", batch = "zas_atmos_s05_vacuum")
    public static void s_05_room_in_vacuum_cools(GameTestHelper h) {
        inVacuum(h, 23, s -> {
            s.room(2, 6);
            fillAir(s, 4, 2, 2, T);
            double start = temperature(s, 4, 2, 2);
            double t = start;
            for (int i = 0; i < 30; i++) {
                s.tick();
                double next = temperature(s, 4, 2, 2);
                h.assertTrue(next < t, "second " + i + ": the room didn't cool (" + t + " K to " + next + " K)");
                t = next;
            }
            h.assertTrue(start - t > 2, "30 s in vacuum cooled the room only " + (start - t) + " K");
        });
        h.succeed();
    }

    /**
     * S-05: a heater on a battery holds a room in vacuum at its setpoint. A sealed 3 × 3 × 3 room of
     * air at 293.15 K, walled in wool, stands in the 2.7 K vacuum, with a heater on its floor on a
     * full high-capacity battery. Set to 303 K, the heater reads the room each step and adds what
     * brings it to the setpoint, up to 5 kW. The room warms to the setpoint without passing it, and
     * holds there, steady and within {@link #HELD} K under it, while its walls radiate about 420 W
     * away (CONT-019, D-73). The GUI shows the room's temperature. On a nearly empty battery it
     * holds the room until the battery runs out, then stops, and the room cools.
     */
    @GameTest(template = "empty", batch = "zas_atmos_s05_heater", timeoutTicks = 200)
    public static void s_05_heater_holds_a_room_at_its_setpoint(GameTestHelper h) {
        double setpoint = 303;
        inVacuum(h, 24, s -> {
            room(s, 2, 6, Blocks.WHITE_WOOL.defaultBlockState()); // interior x 3–5, y 1–3, z 1–3
            HeaterBlockEntity heater = heater(s, 4, 1, 2, Direction.EAST);
            BatteryTests.swap(
                    heater.battery(), AtmosItems.HIGH_CAPACITY_BATTERY.get().full());
            heater.setSetpoint(setpoint);
            heater.setOn(true);
            fillAir(s, 4, 2, 2, T);
            h.assertTrue(
                    Atmospheres.at(s.level, heater.point())
                            .zone()
                            .equals(Atmospheres.at(s.level, s.abs(4, 2, 2)).zone()),
                    "the heater isn't in the room");

            double highest = 0;
            int reached = -1;
            for (int sec = 0; sec < 600; sec++) {
                s.cycle(heater);
                double t = temperature(s, 4, 2, 2);
                highest = Math.max(highest, t);
                if (reached < 0 && t > setpoint - HELD) {
                    reached = sec;
                }
            }
            h.assertTrue(reached >= 0, "the room never came within " + HELD + " K of the setpoint");
            double lowest = Double.MAX_VALUE;
            double lastHighest = 0;
            for (int sec = 0; sec < 60; sec++) {
                s.cycle(heater);
                double t = temperature(s, 4, 2, 2);
                highest = Math.max(highest, t);
                lowest = Math.min(lowest, t);
                lastHighest = Math.max(lastHighest, t);
            }
            double t = temperature(s, 4, 2, 2);
            ZoneInfo room = s.zone(4, 2, 2);
            h.assertTrue(highest <= setpoint + 1e-6, "the room passed the setpoint: " + highest + " K");
            h.assertTrue(
                    lowest > setpoint - HELD,
                    "S-05: the room isn't held within " + HELD + " K of its setpoint: " + lowest + " K");
            h.assertTrue(
                    lastHighest - lowest < 0.05,
                    "the room swung over the last minute: " + lowest + " K to " + lastHighest + " K");
            h.assertValueEqual(heater.status(), HeaterStatus.HEATING, "the heater holding the room");
            h.assertTrue(
                    heater.watts() < HeaterMath.DEFAULT_MAX_WATTS,
                    "the heater is at full power: " + heater.watts() + " W");
            s.close(room.mixture().pressure(), StandardAir.PRESSURE * setpoint / T, 5, "the room's pressure");
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            HeaterMenu menu = (HeaterMenu) heater.createMenu(1, hand.getInventory(), hand);
            s.close(menu.value(HeaterMenu.DATA_ROOM) / 10.0, heater.roomTemperature(), 0.06, "the GUI's room");
            s.close(heater.roomTemperature(), t, 0.5, "the heater's reading of the room");

            // On 2,000 FE, about 20 s at the walls' loss, it holds the room, then stops.
            BatteryTests.swap(heater.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), 2000));
            int heated = 0;
            for (int sec = 0; sec < 120 && heater.status() != HeaterStatus.BATTERY_EMPTY; sec++) {
                s.cycle(heater);
                heated++;
            }
            h.assertValueEqual(heater.status(), HeaterStatus.BATTERY_EMPTY, "the heater after 2,000 FE");
            h.assertValueEqual(heater.energy(), 0, "the battery after it ran out");
            h.assertTrue(heated > 5, "2,000 FE lasted only " + heated + " s");
            double empty = temperature(s, 4, 2, 2);
            for (int sec = 0; sec < 60; sec++) {
                s.cycle(heater);
            }
            h.assertTrue(temperature(s, 4, 2, 2) < empty - 0.5, "the room didn't cool once the battery ran out");
        });
        h.succeed();
    }

    /**
     * How close S-05's heater holds its room under the setpoint, K: each step it tops the room up to
     * the setpoint, and the walls take about 0.02 K away by the next, so this leaves room for a
     * change in the walls' rates.
     */
    private static final double HELD = 0.5;
}
