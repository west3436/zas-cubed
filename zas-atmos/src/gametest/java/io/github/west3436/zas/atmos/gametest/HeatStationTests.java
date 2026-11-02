package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.heater.HeaterMath;
import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.heater.HeaterStatus;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
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
 * vacuum cools through its walls, and a heater on a network that two vents circulate the room's
 * air through holds the room near its setpoint. Each test builds in its own {@link Site} and runs
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

    private static VentBlockEntity vent(Site s, int x, int y, int z, Direction facing) {
        BlockState state = AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, facing);
        return s.place(x, y, z, state, VentBlockEntity.class);
    }

    private static void pipe(Site s, int x, int y, int z) {
        s.place(x, y, z, AtmosBlocks.PIPE.get().defaultBlockState(), Object.class);
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

    /** Fills the zone at a point with standard air at a temperature, from empty, and returns it. */
    private static ZoneInfo fillAir(Site s, int x, int y, int z, double temperature) {
        double moles =
                StandardAir.PRESSURE * 27_000 / (io.github.west3436.zas.api.gas.GasMixture.GAS_CONSTANT * temperature);
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

    /**
     * A test-powered heater on a pipe and cap heats its network's gas to its setpoint, set through
     * its GUI, and no further; a large network takes the full 5 kW a second. It never cools gas
     * above the setpoint, and does nothing off or unpowered. Its GUI refuses unknown buttons and
     * clamps the setpoint to 293–573 K (CONT-019, CONT-074, CONT-087).
     */
    @GameTest(template = "empty")
    public static void cont_019_heater_heats_its_network_to_the_setpoint(GameTestHelper h) {
        Site s = new Site(h, 20);
        try {
            s.clear();
            // Cap, pipe, heater facing east: its back joins the pipe.
            s.place(
                    0,
                    1,
                    2,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST),
                    Object.class);
            pipe(s, 1, 1, 2);
            HeaterBlockEntity heater = heater(s, 2, 1, 2, Direction.EAST);
            h.assertValueEqual(heater.network().members().size(), 3, "the heater's network");
            heater.network().gas().add(new GasParcel(Map.of(N2, 10.0), T));
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            HeaterMenu menu = (HeaterMenu) heater.createMenu(1, hand.getInventory(), hand);

            // Off: it draws nothing and does nothing, powered or not.
            heater.power().setTestPowered(true);
            s.cycles(2, heater);
            h.assertValueEqual(heater.status(), HeaterStatus.OFF, "the new heater's status");
            h.assertValueEqual(heater.power().draw(), 0, "the off heater's draw");
            s.close(heater.network().gas().temperature(), T, 0, "the network with the heater off");

            // The GUI: on, setpoint up 10 K; unknown buttons refused.
            h.assertTrue(menu.clickMenuButton(hand, HeaterMenu.TOGGLE), "switching on");
            h.assertTrue(menu.clickMenuButton(hand, HeaterMenu.SETPOINT_DOWN_10 + 3), "setpoint +10");
            h.assertFalse(menu.clickMenuButton(hand, -1), "button -1");
            h.assertFalse(menu.clickMenuButton(hand, HeaterMenu.LAST_BUTTON + 1), "button 5");
            h.assertTrue(heater.on(), "the heater is off");
            s.close(heater.setpoint(), T + 10, 1e-9, "the setpoint");
            h.assertValueEqual(heater.power().priority(), PowerPriority.AUXILIARY, "the heater's group");
            h.assertValueEqual(
                    heater.power().drawPerAtmosTick(), 60L * AtmosTicks.PERIOD, "60 FE/t (CONT-087) per atmos tick");
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW), 60, "the configured draw");

            // 10 mol of N2 needs 208 J for 10 K: one step reaches the setpoint exactly, and stops.
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.HEATING, "the heating heater's status");
            s.close(heater.network().gas().temperature(), T + 10, 1e-9, "the network at the setpoint");
            h.assertTrue(
                    s.level.getBlockState(heater.getBlockPos()).getValue(HeaterBlock.LIT), "not lit while heating");
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.AT_SETPOINT, "the status at the setpoint");
            s.close(heater.network().gas().temperature(), T + 10, 1e-9, "the network past the setpoint");
            h.assertFalse(s.level.getBlockState(heater.getBlockPos()).getValue(HeaterBlock.LIT), "lit at the setpoint");

            // A heater never cools.
            heater.network().gas().setTemperature(350);
            s.cycle(heater);
            s.close(heater.network().gas().temperature(), 350, 0, "gas above the setpoint");

            // A large network: 5 kW, 5 kJ a second, and no more.
            heater.network().gas().setTemperature(T);
            heater.network().gas().add(new GasParcel(Map.of(N2, 990.0), T));
            double c = heater.network().gas().heatCapacity();
            s.cycle(heater);
            s.close(heater.watts(), HeaterMath.MAX_WATTS, 1e-9, "the heater's power");
            s.close(
                    heater.network().gas().temperature(),
                    T + HeaterMath.MAX_WATTS * AtmosTicks.SECONDS / c,
                    1e-9,
                    "the large network after a second");

            // Unpowered, it waits.
            heater.power().setTestPowered(false);
            double before = heater.network().gas().temperature();
            s.cycle(heater);
            h.assertValueEqual(heater.status(), HeaterStatus.UNPOWERED, "the unpowered heater's status");
            s.close(heater.network().gas().temperature(), before, 0, "the network with the heater unpowered");

            // The setpoint is clamped to 293–573 K.
            for (int i = 0; i < 40; i++) {
                menu.clickMenuButton(hand, HeaterMenu.SETPOINT_DOWN_10 + 3);
            }
            s.close(heater.setpoint(), HeaterMath.MAX_SETPOINT, 0, "the highest setpoint");
            for (int i = 0; i < 40; i++) {
                menu.clickMenuButton(hand, HeaterMenu.SETPOINT_DOWN_10);
            }
            s.close(heater.setpoint(), HeaterMath.MIN_SETPOINT, 0, "the lowest setpoint");
            h.assertTrue(menu.clickMenuButton(hand, HeaterMenu.TOGGLE), "switching off");
            h.assertValueEqual(heater.power().draw(), 0, "the switched-off heater's draw");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * An area air controller powers a heater in its zone like any part, from its buffer, in the
     * auxiliary group (CONT-064, CONT-068, CONT-087).
     */
    @GameTest(template = "empty")
    public static void cont_087_a_controller_powers_a_heater_in_its_zone(GameTestHelper h) {
        Site s = new Site(h, 21);
        try {
            s.clear();
            s.room(0, 4); // interior x 1–3
            AreaAirControllerBlockEntity c = s.controller(2);
            GeneratorBlockEntity g =
                    s.place(2, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.fuel().insertItem(0, new ItemStack(Items.COAL, 2), false);
            HeaterBlockEntity heater = heater(s, 2, 1, 3, Direction.NORTH);
            heater.setOn(true);
            for (int i = 0; i < 12 && !heater.power().isPowered(); i++) {
                for (int t = 0; t < AtmosTicks.PERIOD; t++) {
                    g.serverTick();
                }
                s.cycle(c, heater);
            }
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(heater.power().isPowered(), "the controller didn't power the heater in its zone");
            h.assertTrue(heater.power().source() == c, "the heater's power isn't the controller's");
            h.assertTrue(
                    Atmospheres.at(s.level, heater.airPoint())
                            .zone()
                            .equals(Atmospheres.at(s.level, s.abs(2, 2, 2)).zone()),
                    "the heater's point isn't in the room's zone");
            h.succeed();
        } finally {
            s.clear();
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
        Site s = new Site(h, 22);
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
     * S-05: a heater holds a room in vacuum at its setpoint. A sealed 3 × 3 × 3 room of air at
     * 293.15 K, walled in wool, stands in the 2.7 K vacuum. Two vents in its west wall share a
     * network with a heater outside: one siphons the room into the network, up to 150 kPa there,
     * and the other blows the network back into the room, up to one atmosphere. The heater, set to
     * 303 K, heats the network's gas, which carries the heat in. The room warms toward the
     * setpoint without passing it, and settles within 5 K under it, where the heat the circulation
     * brings in matches what the walls radiate away: the heater brings the network gas, not the
     * room, to its setpoint (CONT-019). With the heater off, the same room cools.
     */
    @GameTest(template = "empty", batch = "zas_atmos_s05_heater", timeoutTicks = 200)
    public static void s_05_heater_holds_a_room_at_its_setpoint(GameTestHelper h) {
        double setpoint = 303;
        inVacuum(h, 24, s -> {
            room(s, 2, 6, Blocks.WHITE_WOOL.defaultBlockState()); // interior x 3–5, y 1–3, z 1–3
            VentBlockEntity siphon = vent(s, 2, 2, 2, Direction.EAST);
            VentBlockEntity blow = vent(s, 2, 2, 3, Direction.EAST);
            pipe(s, 1, 2, 2);
            pipe(s, 1, 2, 3);
            HeaterBlockEntity heater = heater(s, 0, 2, 2, Direction.WEST); // its back, east, joins the pipe
            h.assertTrue(
                    heater.network() == siphon.network() && heater.network() == blow.network(),
                    "the heater and vents aren't one network");
            for (AtmosDevice d : new AtmosDevice[] {siphon, blow, heater}) {
                ((io.github.west3436.zas.atmos.power.PoweredDevice) d).power().setTestPowered(true);
            }
            siphon.command(new VentSettings(VentMode.SIPHON, 0, 150));
            blow.command(VentSettings.blow(StandardAir.PRESSURE));
            heater.setSetpoint(setpoint);
            heater.setOn(true);
            fillAir(s, 4, 2, 2, T);

            double t = T;
            double highest = 0;
            for (int sec = 0; sec < 600; sec++) {
                s.cycle(siphon, blow, heater);
                t = temperature(s, 4, 2, 2);
                highest = Math.max(highest, t);
            }
            double settled = t;
            for (int sec = 0; sec < 60; sec++) {
                s.cycle(siphon, blow, heater);
            }
            t = temperature(s, 4, 2, 2);
            ZoneInfo room = s.zone(4, 2, 2);
            h.assertTrue(highest <= setpoint + 1e-6, "the room passed the setpoint: " + highest + " K");
            s.close(t, settled, 0.2, "the room's temperature over the last minute");
            // The heater brings the network's gas to the setpoint; the room settles under it by its
            // loss over the circulation's heat flow: about 650 W over 8 mol/s of air, about 4.7 K.
            h.assertTrue(
                    t > setpoint - 5 && t < setpoint,
                    "S-05: the room isn't held within 5 K under its setpoint: " + t + " K");
            h.assertValueEqual(siphon.status(), io.github.west3436.zas.atmos.vent.VentStatus.MOVING, "the siphon");
            h.assertValueEqual(blow.status(), io.github.west3436.zas.atmos.vent.VentStatus.MOVING, "the blower");
            h.assertValueEqual(heater.status(), HeaterStatus.HEATING, "the heater");
            h.assertTrue(
                    heater.watts() < HeaterMath.MAX_WATTS, "the heater is at full power: " + heater.watts() + " W");
            s.close(heater.network().gas().temperature(), setpoint, 1.5, "the network gas");
            s.close(room.mixture().pressure(), StandardAir.PRESSURE, 10, "the room's pressure");

            // The heater off: the room cools.
            heater.setOn(false);
            for (int sec = 0; sec < 60; sec++) {
                s.cycle(siphon, blow, heater);
            }
            h.assertTrue(temperature(s, 4, 2, 2) < t - 0.5, "the room didn't cool with the heater off");
        });
        h.succeed();
    }
}
