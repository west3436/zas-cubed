package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.canister.CanisterRelease;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gametest.TestPlayers;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.Internals;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The real parts together: a generator powers an area air controller, which powers and commands a
 * vent fed by pipes, a connector port and canisters (CONT-041, CONT-060, CONT-064, CONT-065,
 * CONT-069), and S-01 and S-08 end to end. Each test builds in its own {@link Site}. Each test has a batch
 * of its own: S-01 and S-08 because they borrow the End as a vacuum dimension, and the power test because it
 * waits in real time for a lease to run out.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationTests {
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final double T = 293.15;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    /** The Human preset's target, which the controller's vents blow to: the middle of 91–111 kPa. */
    private static final double TARGET = ControllerPreset.HUMAN.targetPressure();

    private StationTests() {}

    private static GeneratorBlockEntity generator(Site s, int x, int y, int z) {
        return s.place(x, y, z, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
    }

    private static VentBlockEntity vent(Site s, int x, int y, int z, Direction facing) {
        BlockState state = AtmosBlocks.VENT
                .get()
                .defaultBlockState()
                .setValue(io.github.west3436.zas.atmos.vent.VentBlock.FACING, facing);
        return s.place(x, y, z, state, VentBlockEntity.class);
    }

    /** Burns a generator for some game ticks, as its ticker would. */
    private static void burn(GeneratorBlockEntity g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.serverTick();
        }
    }

    /** Atmos ticks with a generator burning: each burns it for a second, then steps the devices. */
    private static void seconds(Site s, GeneratorBlockEntity g, int n, AtmosDevice... devices) {
        for (int i = 0; i < n; i++) {
            burn(g, AtmosTicks.PERIOD);
            s.cycle(devices);
        }
    }

    /** Places a creative-tab canister of a gas, as placing the item does (CONT-017). */
    private static CanisterBlockEntity canister(Site s, int x, int y, int z, String gas, CanisterColor color) {
        ItemStack item = AtmosItems.CANISTER.get().filled(ResourceLocation.fromNamespaceAndPath("zas", gas), color);
        CanisterBlockEntity c = s.place(
                x,
                y,
                z,
                AtmosBlocks.CANISTER.get().defaultBlockState().setValue(CanisterColor.PROPERTY, color),
                CanisterBlockEntity.class);
        c.applyComponentsFromItemStack(item);
        return c;
    }

    private static double moles(ZoneInfo zone) {
        return zone.mixture().totalMoles();
    }

    // ---- CONT-014, CONT-041, CONT-060, CONT-064, CONT-065, CONT-069 -----------------------

    /**
     * A real vent beside a burning generator, with no controller, has no power and moves nothing,
     * and its GUI sets it. Linked to a real controller that a real generator powers, it is powered
     * before a pump when FE is short, the controller pays 5 + 10 FE/t for itself and the vent, and
     * the vent blows. Its GUI then shows the controller and refuses hand settings. It follows each
     * mode's order, and draws nothing while ordered off. When the generator goes, the vent's lease
     * runs out and it stops, and its GUI sets it again (CONT-014, CONT-041, CONT-060, CONT-064,
     * CONT-065, CONT-069).
     */
    @GameTest(template = "empty", batch = "zas_atmos_station_power", timeoutTicks = 400)
    public static void cont_069_a_real_vent_runs_only_on_a_controller(GameTestHelper h) {
        Site s = new Site(h, 11);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        // The test waits in real time, so its chunks must stay loaded.
        s.level.setChunkForced(cx, cz, true);
        s.level.setChunkForced(cx + 1, cz, true);
        Runnable cleanup = () -> {
            s.clear();
            s.level.setChunkForced(cx, cz, false);
            s.level.setChunkForced(cx + 1, cz, false);
        };
        AreaAirControllerBlockEntity c;
        VentBlockEntity vent;
        VentMenu menu;
        Player hand = h.makeMockPlayer(GameType.SURVIVAL);
        try {
            s.clear();
            // Room A, 7 × 3 × 3, with a vent on its floor facing east, its pipe and cap behind it, a
            // pump, and a burning generator beside the vent.
            s.room(0, 8);
            vent = vent(s, 4, 1, 2, Direction.EAST);
            s.place(3, 1, 2, AtmosBlocks.PIPE.get().defaultBlockState(), Object.class);
            s.place(
                    2,
                    1,
                    2,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST),
                    Object.class);
            TestParts.Part pump =
                    s.place(6, 1, 3, TestParts.Kind.PUMP.block().defaultBlockState(), TestParts.Part.class);
            GeneratorBlockEntity beside = generator(s, 4, 1, 1);
            beside.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(beside, 20);
            h.assertTrue(s.level.getBlockState(s.abs(4, 1, 1)).getValue(GeneratorBlock.LIT), "the generator isn't lit");
            h.assertTrue(beside.energy().getEnergyStored() > 0, "the generator holds no FE");
            s.ticks(2);
            // Room A's zone, emptied so the vent has something to fill.
            s.settle(GasMutations.add(s.level, s.point(5, 2, 2), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));
            s.empty(5, 2, 2);
            vent.network().gas().add(new GasParcel(Map.of(N2, 1000.0), T));
            // With no controller, the vent's GUI sets it (CONT-014): blowing to 101.325 kPa.
            menu = (VentMenu) vent.createMenu(1, hand.getInventory(), hand);
            h.assertTrue(menu.clickMenuButton(hand, VentMenu.CYCLE_MODE), "the free vent refused its GUI");
            h.assertValueEqual(vent.settings(), VentSettings.blow(StandardAir.PRESSURE), "the hand-set vent");
            h.assertFalse(menu.controlled(), "the GUI shows a free vent controlled");

            // CONT-069: no controller, no power, beside a burning generator.
            s.cycles(4, vent, pump);
            h.assertFalse(vent.power().isPowered(), "the vent has power with no controller");
            h.assertFalse(vent.running(), "the vent runs with no controller");
            s.close(vent.network().gas().totalMoles(), 1000.0, 1e-9, "the network's N2 with no controller");
            s.close(moles(s.zone(5, 2, 2)), 0, 1e-9, "room A with an unpowered vent");

            // Room B, with a controller and a generator under it that has burned just enough for
            // the controller and the vent, not the pump.
            s.room(10, 14);
            c = s.controller(12);
            GeneratorBlockEntity g = generator(s, 12, 1, 1);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            while (g.energy().getEnergyStored() < 400) {
                g.serverTick();
            }
            int fe = g.energy().getEnergyStored();
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            int ventDraw = AtmosServerConfig.get(AtmosServerConfig.VENT_DRAW) * AtmosTicks.PERIOD;
            h.assertValueEqual(own, 100, "the controller's 5 FE/t per atmos tick");
            h.assertValueEqual(ventDraw, 200, "the vent's 10 FE/t per atmos tick");
            h.assertValueEqual(vent.power().drawPerAtmosTick(), (long) ventDraw, "the blowing vent's draw");
            h.assertTrue(fe >= own + ventDraw && fe < own + ventDraw + 400, "the generator's FE: " + fe);

            ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
            MultitoolItem.select(tool, s.level, c.getBlockPos());
            MultitoolItem.link(tool, s.level, vent.getBlockPos());
            MultitoolItem.link(tool, s.level, pump.getBlockPos());
            h.assertValueEqual(
                    c.links(), java.util.Set.of(vent.getBlockPos(), pump.getBlockPos()), "the controller's links");

            // CONT-064, CONT-065: the controller takes the generator's FE, pays itself and the
            // vent, and has too little left for the pump.
            s.cycle(c, vent, pump);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertValueEqual(g.energy().getEnergyStored(), 0, "the generator after the controller pulled");
            h.assertValueEqual(c.buffer().getEnergyStored(), fe - own - ventDraw, "the buffer after paying");
            h.assertTrue(vent.power().isPowered(), "the linked vent has no power");
            h.assertTrue(vent.power().source() == c, "the vent's power isn't the controller's");
            h.assertFalse(pump.power().isPowered(), "the pump was powered before the vent's needs were met");

            // CONT-041: Scrubbing orders the vent to blow to the target, and it does.
            h.assertValueEqual(vent.settings(), VentSettings.blow(TARGET), "the vent's settings in Scrubbing");

            // CONT-014: the controller wins, and the GUI says so, naming the controller from its
            // first order: the order carries the controller's position.
            h.assertTrue(vent.controlled(), "the ordered vent isn't controlled");
            h.assertValueEqual(
                    vent.controller(), java.util.Optional.of(c.getBlockPos()), "the vent's controller, first order");
            h.assertValueEqual(menu.value(VentMenu.DATA_CONTROLLED), 1, "the GUI's control flag, first order");
            h.assertValueEqual(
                    menu.controller(), java.util.Optional.of(c.getBlockPos()), "the GUI's controller, first order");
            seconds(s, g, 1, c, vent, pump);
            h.assertValueEqual(menu.controller(), java.util.Optional.of(c.getBlockPos()), "the GUI's controller");
            h.assertValueEqual(menu.mode(), VentMode.BLOW, "the GUI's order");
            for (int id = VentMenu.CYCLE_MODE; id <= VentMenu.LAST_BUTTON; id++) {
                h.assertFalse(menu.clickMenuButton(hand, id), "the controlled vent took button " + id);
            }
            h.assertValueEqual(vent.settings(), VentSettings.blow(TARGET), "the controlled vent after its GUI");
            double before = 1000.0; // all the N2 there is, once nothing is in flight
            seconds(s, g, 3, c, vent, pump);
            double moved = moles(s.zone(5, 2, 2));
            h.assertTrue(moved > 1, "the vent moved " + moved + " mol into room A");
            s.close(
                    moved + vent.network().gas().totalMoles(),
                    before,
                    1e-9 * before,
                    "the N2 in room A and the network");
            h.assertValueEqual(
                    s.level.getBlockState(vent.getBlockPos()).getValue(VentMode.PROPERTY),
                    VentMode.BLOW,
                    "the vent's block state");

            // Each mode's order reaches the vent, and an order to stop stops it, drawing nothing.
            for (ControllerMode m : List.of(
                    ControllerMode.OFF, ControllerMode.PANIC, ControllerMode.REPLACEMENT, ControllerMode.SCRUBBING)) {
                c.setMode(m);
                seconds(s, g, 1, c, vent, pump);
                VentSettings expected =
                        m.vent(ControllerPreset.HUMAN).on() ? VentSettings.blow(TARGET) : VentSettings.OFF;
                h.assertValueEqual(vent.settings(), expected, m + ": the vent's settings");
                if (!expected.equals(VentSettings.OFF)) {
                    continue;
                }
                h.assertValueEqual(vent.power().draw(), 0, m + ": the vent's draw");
                seconds(s, g, 2, c, vent, pump); // anything in flight lands
                double held = moles(s.zone(5, 2, 2));
                seconds(s, g, 3, c, vent, pump);
                s.close(moles(s.zone(5, 2, 2)), held, 1e-12, m + ": room A while the vent is off");
            }
            h.assertTrue(vent.network().gas().totalMoles() > 1, "the network ran dry");

            // CONT-060: cut the power. The generator goes, and the buffer is spent.
            s.set(12, 1, 1, Site.AIR);
            c.buffer().set(0);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        AreaAirControllerBlockEntity controller = c;
        VentBlockEntity v = vent;
        VentMenu gui = menu;
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                h.assertFalse(controller.powered(), "the controller has power with no generator");
                h.assertValueEqual(controller.status(), ControllerStatus.UNPOWERED, "the controller's panel");
                h.assertFalse(v.power().isPowered(), "the vent's lease outlived its controller's power");
                h.assertValueEqual(v.settings().mode(), VentMode.BLOW, "the vent's last order");
                h.assertTrue(v.network().gas().totalMoles() > 1, "the network ran dry");
                s.cycles(2, v); // anything in flight lands
                double room = moles(s.zone(5, 2, 2));
                s.cycles(4, v);
                s.close(moles(s.zone(5, 2, 2)), room, 1e-12, "room A with the power cut");
                h.assertFalse(v.running(), "the unpowered vent runs");
                h.assertValueEqual(
                        s.level.getBlockState(v.getBlockPos()).getValue(VentMode.PROPERTY),
                        VentMode.OFF,
                        "the unpowered vent's block state");
                // With no orders coming, the vent is the player's again.
                h.assertFalse(v.controlled(), "the vent stayed controlled with its controller unpowered");
                h.assertTrue(gui.clickMenuButton(hand, VentMenu.CYCLE_MODE), "the freed vent refused its GUI");
                h.assertValueEqual(v.settings().mode(), VentMode.SIPHON, "the freed vent's mode");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }

    // ---- S-01 -----------------------------------------------------------------------------

    /**
     * S-01, pressurise a station. In a vacuum dimension, a sealed 3 × 3 × 3 room has an area air
     * controller fed by a generator, and a vent in its west wall fed through a pipe by a connector
     * port outside. The player doses the O₂ first: an O₂ canister in the room, its release pressure
     * turned down by eight 10 kPa steps to 21.325 kPa, releases until the room is there. They close
     * it and stand an N₂ canister on the port, and the controller's vent blows the room up to the
     * Human target, 101 kPa: within 1 kPa of 101.325 kPa at 21.1 % O₂. Then a player without a
     * helmet breathes it for 60 s and takes no harm, while the controller keeps running.
     *
     * <p>The GameTest server has only the vanilla dimensions, so the End borrows the test datapack's
     * vacuum ambient for the test, as the framework's S-01 does. It runs within one game tick: each
     * second burns the generator for 20 game ticks, steps every device in the End, then runs one
     * atmos tick.
     *
     * <p>Heat is off for the test: with no heater, the room's walls radiate its heat to the vacuum
     * (HEAT-004), and it would cool by about 0.2 K/s, losing pressure. S-05 checks a heater holds
     * a room's temperature.
     */
    @GameTest(template = "empty", batch = "zas_atmos_s01")
    public static void s_01_pressurise_station(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, 12);
        Ambients.overrideForTest(Level.END, vacuum);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            s.clear();
            s.room(2, 6); // interior x 3–5, y 1–3, z 1–3
            AreaAirControllerBlockEntity c = s.controller(4); // on the north wall at (4, 2, 1)
            GeneratorBlockEntity g = generator(s, 4, 1, 1); // on the floor under it
            g.fuel().insertItem(0, new ItemStack(Items.COAL, 2), false);
            VentBlockEntity vent = vent(s, 2, 2, 2, Direction.EAST); // in the west wall, facing in
            s.place(1, 2, 2, AtmosBlocks.PIPE.get().defaultBlockState(), Object.class);
            s.place(1, 3, 2, AtmosBlocks.CONNECTOR_PORT.get().defaultBlockState(), Object.class);
            s.ticks(2);
            h.assertValueEqual(Atmospheres.at(end, s.abs(4, 2, 2)).pressure(), 0.0, "the room before the controller");

            Runnable second = () -> {
                burn(g, AtmosTicks.PERIOD);
                DeviceTicker.stepNow(end);
                s.tick();
            };

            // The powered controller pins the room's zone, which holds nothing (CONT-110), and
            // powers and commands the vent in it.
            for (int i = 0;
                    i < 10 && !(c.lastPin() == PinOutcome.ZONE && vent.power().isPowered());
                    i++) {
                second.run();
            }
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertValueEqual(c.lastPin(), PinOutcome.ZONE, "the controller's pin");
            ZoneInfo room = s.zone(4, 2, 2);
            s.close(room.mixture().pressure(), 0, 1e-9, "the vacuum room's pressure");
            h.assertTrue(vent.power().source() == c, "the vent isn't powered by the controller");
            h.assertValueEqual(vent.settings(), VentSettings.blow(TARGET), "the vent's order");

            // Without a helmet, the vacuum is no air.
            FakePlayer gasping = FakePlayerFactory.get(end, new GameProfile(UUID.randomUUID(), "zas_s_01_vacuum"));
            gasping.moveTo(Vec3.atBottomCenterOf(s.abs(3, 1, 3)));
            Breathers.checkAt(gasping, end.getGameTime());
            h.assertTrue(Breathing.of(gasping).reason() != BreathReason.BREATHABLE, "the vacuum room is breathable");

            // The O₂: a canister in the room, its release turned down to 21.325 kPa, opened.
            CanisterBlockEntity oxygen = canister(s, 5, 1, 3, "oxygen", CanisterColor.BLUE);
            CanisterMenu menu = new CanisterMenu(
                    1, new SimpleContainerData(CanisterMenu.DATA_COUNT), ContainerLevelAccess.NULL, oxygen);
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            for (int i = 0; i < 8; i++) {
                h.assertTrue(menu.clickMenuButton(hand, CanisterMenu.RELEASE_DOWN_10), "turning the release down");
            }
            double dose = CanisterRelease.DEFAULT_KPA - 80;
            s.close(oxygen.releasePressure(), dose, 1e-9, "the O2 canister's release pressure");
            h.assertTrue(menu.clickMenuButton(hand, CanisterMenu.TOGGLE_VALVE), "opening the valve");
            h.assertTrue(oxygen.valveOpen(), "the O2 valve is shut");
            for (int i = 0; i < 10 && s.zone(4, 2, 2).mixture().pressure() < dose - 1e-9; i++) {
                second.run();
            }
            room = s.zone(4, 2, 2);
            s.close(room.mixture().pressure(), dose, 1e-6, "the room after the O2 dose");
            h.assertTrue(menu.clickMenuButton(hand, CanisterMenu.TOGGLE_VALVE), "closing the valve");
            hand.discard();

            // The N₂: a canister on the port; the controller's vent fills the room to its target.
            CanisterBlockEntity nitrogen = canister(s, 1, 4, 2, "nitrogen", CanisterColor.RED);
            h.assertTrue(vent.network().members().contains(nitrogen), "the N2 canister isn't in the vent's network");
            int filled = -1;
            for (int i = 0; i < 60; i++) {
                second.run();
                if (s.zone(4, 2, 2).mixture().pressure() >= TARGET - 1e-6) {
                    filled = i + 1;
                    break;
                }
            }
            h.assertTrue(
                    filled > 0,
                    "the room didn't fill in 60 s: " + s.zone(4, 2, 2).mixture());
            room = s.zone(4, 2, 2);
            h.assertTrue(room.enclosed(), "the room isn't enclosed");
            double p = room.mixture().pressure();
            double o2 = room.mixture().composition().get(O2);
            s.close(p, StandardAir.PRESSURE, 1, "S-01: the room's pressure");
            s.close(o2, StandardAir.OXYGEN_FRACTION, 0.005, "S-01: the room's O2 fraction");
            s.close(p, TARGET, 1e-6, "the room's pressure against the controller's target");
            s.close(o2, dose / TARGET, 1e-6, "the O2 fraction against the dose");
            s.close(room.mixture().temperature(), T, 1e-6, "the room's temperature");

            // Without a helmet, a minute in the room does no harm, with the controller running.
            // A player that damage reaches: a plain fake player is invulnerable, and would pass this
            // whatever the room did to it.
            FakePlayer player = TestPlayers.hurtable(end, "zas_s_01");
            player.moveTo(Vec3.atBottomCenterOf(s.abs(3, 1, 3)));
            long t0 = end.getGameTime();
            for (int sec = 0; sec < 60; sec++) {
                second.run();
                if (sec % 2 == 0) {
                    Breathers.checkAt(player, t0 + (long) AtmosTicks.PERIOD * sec);
                }
                for (int i = 0; i < AtmosTicks.PERIOD; i++) {
                    player.baseTick();
                }
            }
            Breathability b = Breathing.of(player);
            h.assertValueEqual(b.reason(), BreathReason.BREATHABLE, "the player's breath");
            h.assertTrue(b.isProtected(), "the player is exposed to " + b.hazards());
            h.assertValueEqual(player.getAirSupply(), player.getMaxAirSupply(), "the player's air after 60 s");
            h.assertTrue(player.getActiveEffects().isEmpty(), "the player's effects: " + player.getActiveEffects());
            h.assertValueEqual(player.getHealth(), player.getMaxHealth(), "the player's health after 60 s");

            room = s.zone(4, 2, 2);
            s.close(room.mixture().pressure(), StandardAir.PRESSURE, 1, "the room's pressure after 60 s");
            s.close(room.mixture().composition().get(O2), StandardAir.OXYGEN_FRACTION, 0.005, "O2 after 60 s");
            h.assertTrue(c.powered(), "the controller lost power");
            h.assertValueEqual(c.status(), ControllerStatus.SAFE, "the controller's rating of the room");
            h.assertTrue(s.level.getBlockState(s.abs(4, 1, 1)).getValue(GeneratorBlock.LIT), "the generator went out");
            h.succeed();
        } finally {
            s.clear();
            Ambients.overrideForTest(Level.END, null);
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    // ---- S-08 -----------------------------------------------------------------------------

    /**
     * S-08, breathing gear. In a vacuum dimension, a player wears the softsuit with an O₂ tank in
     * its chestplate's tank slot. With internals off, the vacuum is no air, though the suit keeps
     * the vacuum's pressure off. With internals switched on, a minute in the vacuum does no harm,
     * and the tank loses LIFE-043's draw. In a room of air with 1 kPa of CO, which harms a suited
     * player off internals, it breathes the tank and takes no toxic gas, and its exhaled breath
     * reaches the room. Then, with an emergency tank that holds only a few seconds above its
     * distribution pressure, the tank runs dry within a few checks: the player breathes the vacuum,
     * hypoxia begins and its air meter drains.
     *
     * <p>Like S-01, the End borrows the vacuum ambient, and heat is off. Checks are 2 s apart.
     */
    @GameTest(template = "empty", batch = "zas_atmos_s08")
    public static void s_08_breathing_gear(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Site s = new Site(h, end, 44);
        Ambients.overrideForTest(Level.END, Ambients.ambient(VACUUM));
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            s.clear();
            // The CO room: x 9–11, air at 1 atm with 1 kPa of CO.
            s.room(8, 12);
            double air = StandardAir.PRESSURE * 27_000 / (GasMixture.GAS_CONSTANT * T);
            double co = 1.0 * 27_000 / (GasMixture.GAS_CONSTANT * T);
            s.settle(GasMutations.add(
                    end,
                    s.point(10, 2, 2),
                    new GasParcel(Map.of(N2, air * 0.79, O2, air * 0.21, CO, co), T),
                    Site.SOURCE));
            s.ticks(2);
            s.close(s.zone(10, 2, 2).mixture().partialPressure(CO), 1.0, 1e-9, "the room's CO");

            FakePlayer p = FakePlayerFactory.get(end, new GameProfile(UUID.randomUUID(), "zas_s_08"));
            p.moveTo(Vec3.atBottomCenterOf(s.abs(3, 1, 2))); // in the open vacuum
            ItemStack chest = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
            SuitItem.setTank(chest, new ItemStack(AtmosItems.OXYGEN_TANK.get()));
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(AtmosItems.SOFTSUIT_HELMET.get()));
            p.setItemSlot(EquipmentSlot.CHEST, chest);
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(AtmosItems.SOFTSUIT_LEGGINGS.get()));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(AtmosItems.SOFTSUIT_BOOTS.get()));

            // Internals off: the suit holds the vacuum off, but there's nothing to breathe.
            long t0 = end.getGameTime();
            Breathers.checkAt(p, t0);
            Breathability off = Breathing.of(p);
            h.assertValueEqual(off.reason(), BreathReason.LOW_OXYGEN, "the vacuum off internals");
            h.assertFalse(off.hazards().contains(Hazard.LOW_PRESSURE), "the suit let the vacuum in");

            // Internals on: a minute in the vacuum.
            h.assertTrue(Internals.toggle(p), "the toggle didn't switch internals on");
            double full = tankMoles(p);
            int k = 0;
            for (int i = 0; i < 30; i++) {
                Breathers.checkAt(p, t0 + 40L * ++k);
                Breathability b = Breathing.of(p);
                h.assertValueEqual(b.source(), BreathSource.INTERNALS, "the breath in the vacuum, check " + k);
                h.assertValueEqual(b.reason(), BreathReason.BREATHABLE, "the vacuum on internals, check " + k);
                h.assertTrue(b.isProtected(), "exposed in the vacuum to " + b.hazards());
            }
            s.close(BreathingRules.assess(p).exposure().decompression(), 0, 0, "decompression in the suit");
            double rate = Internals.drawRate(GasTankItem.OXYGEN_DISTRIBUTION_KPA, T);
            s.close(full - tankMoles(p), rate * 60, 1e-12, "the tank's draw in 60 s");

            // The CO room, on internals: no toxic gas, and the breath goes into the room.
            FakePlayer bare = FakePlayerFactory.get(end, new GameProfile(UUID.randomUUID(), "zas_s_08_bare"));
            bare.moveTo(Vec3.atBottomCenterOf(s.abs(10, 1, 3)));
            bare.setItemSlot(EquipmentSlot.HEAD, new ItemStack(AtmosItems.SOFTSUIT_HELMET.get()));
            h.assertTrue(
                    BreathingRules.assess(bare).verdict().hazards().contains(Hazard.TOXIC_GAS),
                    "the CO room doesn't harm a player off internals");
            p.moveTo(Vec3.atBottomCenterOf(s.abs(10, 1, 2)));
            for (int i = 0; i < 10; i++) {
                Breathers.checkAt(p, t0 + 40L * ++k);
                Breathability b = Breathing.of(p);
                h.assertValueEqual(b.source(), BreathSource.INTERNALS, "the breath in the CO room, check " + k);
                h.assertFalse(b.hazards().contains(Hazard.TOXIC_GAS), "the CO reached the tank's breath");
            }
            s.ticks(2);
            h.assertTrue(s.zone(10, 2, 2).mixture().moles(CO2) > 0, "the exhaled breath didn't reach the room");

            // An emergency tank a few seconds from its distribution pressure runs dry in the vacuum.
            p.moveTo(Vec3.atBottomCenterOf(s.abs(3, 1, 2)));
            ItemStack small = new ItemStack(AtmosItems.EMERGENCY_OXYGEN_TANK.get());
            double floor = GasTankItem.OXYGEN_DISTRIBUTION_KPA * 2 / (GasMixture.GAS_CONSTANT * T);
            GasTankItem.setGas(small, new GasParcel(Map.of(O2, floor + rate * 5), T));
            SuitItem.setTank(p.getItemBySlot(EquipmentSlot.CHEST), small);
            int dry = -1;
            for (int i = 1; i <= 6 && dry < 0; i++) {
                Breathers.checkAt(p, t0 + 40L * ++k);
                if (Breathing.of(p).source() != BreathSource.INTERNALS) {
                    dry = i;
                }
            }
            // 5 s above the floor: after three 2 s draws the tank is below it, so the verdict falls back.
            h.assertTrue(dry == 3, "the tank ran dry after check " + dry + ", not the 3rd");
            Breathability gasping = Breathing.of(p);
            h.assertValueEqual(gasping.source(), BreathSource.ATMOSPHERE, "the breath once the tank is dry");
            h.assertValueEqual(gasping.reason(), BreathReason.LOW_OXYGEN, "hypoxia once the tank is dry");
            int air0 = p.getAirSupply();
            for (int i = 0; i < AtmosTicks.PERIOD; i++) {
                p.baseTick();
            }
            h.assertTrue(p.getAirSupply() < air0, "the air meter didn't drain: " + p.getAirSupply());
            h.succeed();
        } finally {
            s.clear();
            Ambients.overrideForTest(Level.END, null);
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /** The moles in the tank in a player's chestplate slot. */
    private static double tankMoles(Player p) {
        return GasTankItem.contents(SuitItem.tank(p.getItemBySlot(EquipmentSlot.CHEST)))
                .totalMoles();
    }
}
