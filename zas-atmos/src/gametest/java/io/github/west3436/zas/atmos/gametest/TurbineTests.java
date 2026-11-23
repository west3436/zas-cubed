package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.TurbineRig.H2;
import static io.github.west3436.zas.atmos.gametest.TurbineRig.O2;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.turbine.CompressorBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlock;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity.Part;
import io.github.west3436.zas.atmos.turbine.TurbineControllerMenu;
import io.github.west3436.zas.atmos.turbine.TurbinePartBlock;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.heat.HeatSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The gas turbine: a compressor fed from a pipe network and a turbine in a wall, a combustion chamber
 * between them, and the controller that drives them (CONT-160 to CONT-164, CONT-190, CONT-191). Each
 * test builds a {@link TurbineRig} in its own {@link Area}, 5610 to 5621, steps the devices itself, and
 * draws the controller's FE off each round as a load would.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineTests {
    private static final String BATCH = "zas_atmos_turbine";

    private TurbineTests() {}

    // ---- The compressor's feed (CONT-160) ----------------------------------------------------------

    /**
     * Nothing burns unlinked or without a feed (CONT-160): with fuel and oxidizer in its network, a
     * compressor its controller doesn't link releases nothing, and its turbine says so; linked, with its
     * network empty, it releases nothing either. The chamber never gets fuel, nothing lights and no FE is
     * made, though the igniter is linked and auto relight is on.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_160_nothing_burns_unlinked_or_without_a_feed(GameTestHelper h) {
        Area a = new Area(h, 5610);
        TurbineRig r = TurbineRig.build(a);
        r.controller.toggleLink(a.level, r.turbine.getBlockPos());
        r.controller.toggleLink(a.level, r.igniter.getBlockPos());
        r.burn(0.5, true);
        r.feed(20, 10);
        double fed = r.feedGas().totalMoles();
        int made = 0;
        for (int i = 0; i < 20; i++) {
            made += r.step();
            h.assertFalse(r.burning(), "the chamber lit with no compressor linked, at round " + i);
        }
        h.assertValueEqual(r.turbine.status(), TurbineStatus.NO_COMPRESSOR, "the turbine with no compressor linked");
        h.assertValueEqual(r.controller.status(), TurbineStatus.NO_COMPRESSOR, "the controller's state");
        a.close(r.feedGas().totalMoles(), fed, "the feed of an unlinked compressor");
        h.assertValueEqual(r.chamber().mixture().moles(H2), 0.0, "fuel in the chamber");

        r.controller.toggleLink(a.level, r.turbine.getBlockPos());
        h.assertTrue(r.turbine.controller() == null, "the turbine kept its controller");
        r.step();
        h.assertValueEqual(r.turbine.status(), TurbineStatus.UNLINKED, "an unlinked turbine");
        h.assertValueEqual(r.controller.status(), TurbineStatus.UNLINKED, "a controller with no turbine");

        r.link(false);
        r.feedGas().drain();
        for (int i = 0; i < 20; i++) {
            made += r.step();
            h.assertValueEqual(
                    r.compressor.lastFuel() + r.compressor.lastOxidizer(), 0.0, "released from an empty feed");
            h.assertFalse(r.burning(), "the chamber lit with nothing fed, at round " + i);
        }
        h.assertValueEqual(r.chamber().mixture().moles(H2), 0.0, "fuel in the chamber");
        h.assertValueEqual(r.turbine.rpm(), 0.0, "the shaft's speed");
        h.assertValueEqual(made, 0, "FE made with nothing to burn");
        h.assertValueEqual(r.turbine.status(), TurbineStatus.IDLE, "the turbine with nothing to burn");
        h.succeed();
    }

    /**
     * The compressor releases each gas on its own (CONT-160): a lean chamber, air with no fuel, gets fuel
     * and no oxygen; a rich one, with more hydrogen than its oxygen burns, gets oxygen and no fuel.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_160_fuel_when_lean_oxidizer_when_rich(GameTestHelper h) {
        Area a = new Area(h, 5611);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            TurbineRig r = TurbineRig.build(a).link(false).burn(0.5, false);
            r.feed(50, 50);
            for (int i = 0; i < 10 && r.compressor.lastFuel() + r.compressor.lastOxidizer() == 0; i++) {
                r.step();
            }
            h.assertTrue(r.compressor.lastFuel() > 0, "a lean chamber got no fuel");
            h.assertValueEqual(r.compressor.lastOxidizer(), 0.0, "the oxygen a lean chamber got");

            double o2 = r.chamber().mixture().moles(O2);
            r.addToChamber(H2, 4 * o2);
            double gotOxygen = 0;
            for (int i = 0; i < 5; i++) {
                r.step();
                h.assertValueEqual(r.compressor.lastFuel(), 0.0, "the fuel a rich chamber got at round " + i);
                gotOxygen += r.compressor.lastOxidizer();
            }
            h.assertTrue(gotOxygen > 0, "a rich chamber got no oxygen");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /**
     * No gas is made or lost as the compressor feeds the chamber (CONT-160): the feed network's moles,
     * the chamber's, the exhaust room's and what the turbine holds always sum to the same, and the
     * chamber gets the hydrogen the feed gave.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_160_the_compressor_keeps_its_moles(GameTestHelper h) {
        Area a = new Area(h, 5612);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            TurbineRig r = TurbineRig.build(a).link(false).burn(1.0, false);
            r.feed(20, 20);
            double before = r.moles();
            double fed = r.feedGas().moles(H2);
            for (int i = 0; i < 30; i++) {
                r.step();
                a.close(r.moles(), before, "moles at round " + i);
            }
            double given = fed - r.feedGas().moles(H2);
            h.assertTrue(given > 1, "the compressor gave little fuel: " + given + " mol");
            double away = r.chamber().mixture().moles(H2)
                    + r.exhaust().mixture().moles(H2)
                    + r.turbine.held().moles(H2);
            a.close(away, given, "the hydrogen past the compressor");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /**
     * A pipe-fed compressor sustains a fire (CONT-160, CONT-162, CONT-191): set from the controller's
     * window to burn hydrogen in oxygen, with its igniter linked and auto relight on, the chamber lights
     * by itself once it holds enough O2 for a fire (FIRE-030) and keeps burning as the compressor feeds it, the shaft spins and FE comes out of the
     * controller. Switched off from the window, the compressor feeds nothing, the shaft runs down and no
     * more FE is made.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_160_a_pipe_fed_compressor_sustains_a_fire(GameTestHelper h) {
        try (TurbineRig.Nozzle wide = TurbineRig.wideNozzle()) {
            Area a = new Area(h, 5613);
            TurbineRig r = TurbineRig.build(a).link(true);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            TurbineControllerMenu window =
                    (TurbineControllerMenu) r.controller.createMenu(1, player.getInventory(), player);
            h.assertTrue(
                    window.clickMenuButton(
                            player, TurbineControllerMenu.FUEL + window.fuels().indexOf(H2)),
                    "the fuel's box");
            h.assertTrue(
                    window.clickMenuButton(
                            player,
                            TurbineControllerMenu.OXIDIZER + window.oxidizers().indexOf(O2)),
                    "the oxidizer's box");
            r.controller.setBurnRate(0.2); // the rig's big chamber, fed slowly
            for (int i = 0; i < 3; i++) {
                h.assertTrue(
                        window.clickMenuButton(player, TurbineControllerMenu.RATE_DOWN_TENTH + 3), "the rate's +0.1");
            }
            h.assertValueEqual(r.controller.burnRate(), 0.5, "the burn rate the window set");
            h.assertTrue(r.controller.autoIgnite(), "a new controller's auto relight");
            h.assertTrue(window.clickMenuButton(player, TurbineControllerMenu.TOGGLE), "the window's switch");
            r.feed(200, 100);
            double fed = r.feedGas().moles(H2);

            // The rig's chamber starts nearly empty: it lights once the compressor has brought its O2 up to
            // what a fire needs (FIRE-030), then must keep burning.
            int lit = -1;
            for (int i = 0; i < 300 && lit < 0; i++) {
                r.step();
                if (r.burning()) {
                    lit = i;
                }
            }
            h.assertTrue(lit >= 0, "the chamber never lit");
            int rounds = 80;
            int burningAfter = 0;
            int made = 0;
            double top = 0;
            boolean ran = false;
            StringBuilder pattern = new StringBuilder();
            for (int i = 0; i < rounds; i++) {
                made += r.step();
                ran |= r.controller.status() == TurbineStatus.RUNNING;
                boolean burning = r.burning();
                pattern.append(burning ? '#' : '.');
                burningAfter += burning ? 1 : 0;
                top = Math.max(top, r.turbine.rpm());
            }
            h.assertTrue(
                    burningAfter >= rounds * 0.9,
                    "the chamber burned " + burningAfter + " of " + rounds + " rounds after lighting: " + pattern);
            h.assertTrue(made > 0, "no FE in the " + rounds + " rounds after lighting");
            h.assertTrue(top > 0, "the shaft never turned");
            h.assertTrue(fed - r.feedGas().moles(H2) > 5, "the compressor fed little hydrogen");
            // The rig's exhaust is a sealed room, which in time fills to the chamber's pressure and stops the
            // shaft: the controller showed Running while the gas went through.
            h.assertTrue(ran, "the controller never showed Running");

            h.assertTrue(window.clickMenuButton(player, TurbineControllerMenu.TOGGLE), "the window's switch off");
            h.assertFalse(r.controller.on(), "the window didn't switch it off");
            double left = r.feedGas().totalMoles();
            int i = 0;
            for (; i < 600 && r.turbine.rpm() > 0; i++) {
                r.step();
            }
            h.assertValueEqual(r.turbine.rpm(), 0.0, "the shaft's speed " + i + " rounds after switching off");
            a.close(r.feedGas().totalMoles(), left, "the feed while off");
            h.assertValueEqual(r.turbine.status(), TurbineStatus.OFF, "the turbine's state");
            h.assertValueEqual(r.spin(8), 0, "the turbine's blades at rest");
            int madeOff = 0;
            for (int j = 0; j < 5; j++) {
                madeOff += r.step();
            }
            h.assertValueEqual(madeOff, 0, "FE made while off and still");
            h.succeed();
        }
    }

    // ---- The turbine (CONT-161) --------------------------------------------------------------------

    /**
     * A pressed chamber spins the shaft up and makes FE; once the pressures even out the shaft spins
     * down to a stop. With nothing fed the compressor does no work. Through it all no gas is made or
     * lost (CONT-161, CONT-162, CONT-164).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_a_pressed_chamber_spins_it_up_and_down_and_keeps_its_moles(GameTestHelper h) {
        Area a = new Area(h, 5614);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try (TurbineRig.Nozzle wide = TurbineRig.wideNozzle()) {
            TurbineRig r = TurbineRig.build(a).link(false);
            r.pressurise(300);
            double before = r.moles();
            r.controller.setOn(true);
            double top = 0;
            int made = 0;
            int maxSpin = 0;
            for (int i = 0; i < 40; i++) {
                made += r.step();
                top = Math.max(top, r.turbine.rpm());
                maxSpin = Math.max(maxSpin, r.spin(4));
                h.assertValueEqual(r.turbine.compressorWork(), 0.0, "the compressor's work with nothing fed");
                a.close(r.moles(), before, "moles at round " + i);
            }
            h.assertTrue(top > 0, "the shaft never turned");
            h.assertTrue(made > 0, "no FE made");
            h.assertTrue(maxSpin > 0, "the compressor's blades never turned");

            int i = 0;
            for (; i < 300 && r.turbine.rpm() > 0; i++) {
                r.step();
                a.close(r.moles(), before, "moles at round " + (40 + i));
            }
            h.assertValueEqual(r.turbine.rpm(), 0.0, "the shaft's speed after " + (40 + i) + " rounds");
            h.assertValueEqual(r.spin(8), 0, "the turbine's blades at rest");
            h.assertValueEqual(r.spin(4), 0, "the compressor's blades at rest");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /** The turbine keeps its controller, its shaft's energy and the gas it holds through a save and a load (CONT-161). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_161_a_turbine_keeps_its_state_through_save_and_load(GameTestHelper h) {
        Area a = new Area(h, 5615);
        TurbineRig r = TurbineRig.build(a).link(false);
        r.controller.setOn(true);
        r.turbine.setShaftEnergy(250_000);
        a.step();
        h.assertTrue(r.controller.energy().getEnergyStored() > 0, "no FE made");
        HolderLookup.Provider registries = a.level.registryAccess();
        CompoundTag saved = r.turbine.saveCustomOnly(registries);
        TurbineBlockEntity copy = new TurbineBlockEntity(BlockPos.ZERO, r.turbine.getBlockState());
        copy.loadCustomOnly(saved, registries);
        h.assertValueEqual(copy.controllerPos(), r.controller.getBlockPos(), "the copy's controller");
        a.close(copy.shaftEnergy(), r.turbine.shaftEnergy(), "the copy's shaft energy");
        a.close(copy.held().totalMoles(), r.turbine.held().totalMoles(), "the gas the copy holds");
        a.close(copy.workCarry(), r.turbine.workCarry(), "the copy's carried work");
        CompressorBlockEntity compressorCopy = new CompressorBlockEntity(BlockPos.ZERO, r.compressor.getBlockState());
        compressorCopy.loadCustomOnly(r.compressor.saveCustomOnly(registries), registries);
        h.assertValueEqual(
                compressorCopy.controllerPos(), r.controller.getBlockPos(), "the compressor copy's controller");
        h.succeed();
    }

    // ---- Output through the controller (CONT-162) --------------------------------------------------

    /**
     * The engine's FE comes out of its controller (CONT-162): the generator fills the controller's
     * store, which gives it to an area air controller beside it. The turbine has no FE store of its own
     * and offers FE to no one.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_162_fe_goes_out_through_the_controller(GameTestHelper h) {
        Area a = new Area(h, 5616);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try (TurbineRig.Nozzle wide = TurbineRig.wideNozzle()) {
            TurbineRig r = TurbineRig.build(a).link(false);
            a.set(7, 5, 1, Area.STONE);
            a.place(
                    7,
                    5,
                    2,
                    AtmosBlocks.AREA_AIR_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH));
            AreaAirControllerBlockEntity load = a.entity(7, 5, 2);
            h.assertTrue(
                    a.level.getCapability(Capabilities.EnergyStorage.BLOCK, r.turbine.getBlockPos(), Direction.UP)
                            == null,
                    "the turbine offers FE");
            h.assertTrue(
                    a.level.getCapability(Capabilities.EnergyStorage.BLOCK, r.compressor.getBlockPos(), Direction.UP)
                            == null,
                    "the compressor offers FE");
            h.assertTrue(
                    a.level.getCapability(Capabilities.EnergyStorage.BLOCK, r.controller.getBlockPos(), Direction.EAST)
                            == r.controller.energy(),
                    "the controller doesn't offer its store");
            r.pressurise(300);
            r.controller.setOn(true);
            for (int i = 0; i < 10; i++) {
                a.step();
            }
            int stored = r.controller.energy().getEnergyStored();
            h.assertTrue(stored > 0, "the controller's store is empty");
            // The game's own ticks may have filled the area air controller already: empty it first.
            load.buffer().set(0);
            r.controller.serverTick();
            int given = load.buffer().getEnergyStored();
            h.assertTrue(given > 0, "the controller gave the area air controller nothing");
            h.assertValueEqual(
                    r.controller.energy().getEnergyStored(), stored - given, "the controller's store after giving");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    // ---- The controller's window (CONT-163) --------------------------------------------------------

    /**
     * The controller's window shows the engine as it is (CONT-163): its switch, state, links, speed,
     * chamber, buffer, output, store, fuel, oxidizer, burn rate and auto relight; its buttons set them,
     * the burn rate in steps and within 0 to {@code turbine_max_burn}.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_163_the_controller_window_shows_and_sets_the_engine(GameTestHelper h) {
        Area a = new Area(h, 5617);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try (TurbineRig.Nozzle wide = TurbineRig.wideNozzle()) {
            TurbineRig r = TurbineRig.build(a).link(false);
            r.feed(10, 5);
            r.pressurise(300);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            TurbineControllerMenu menu =
                    (TurbineControllerMenu) r.controller.createMenu(1, player.getInventory(), player);
            h.assertFalse(menu.on(), "a new controller is on");
            h.assertTrue(menu.fuels().contains(H2), "hydrogen isn't offered as a fuel");
            h.assertTrue(menu.oxidizers().contains(O2), "oxygen isn't offered as an oxidizer");
            h.assertFalse(menu.fuels().contains(Area.N2), "nitrogen is offered as a fuel");
            h.assertValueEqual(menu.value(TurbineControllerMenu.DATA_FUEL), -1, "a new controller's fuel");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_RATE),
                    (int) Math.round(TurbineControllerBlockEntity.DEFAULT_BURN_RATE * 100),
                    "a new controller's rate");
            h.assertTrue(menu.clickMenuButton(player, TurbineControllerMenu.TOGGLE), "the switch");
            h.assertTrue(
                    menu.clickMenuButton(
                            player, TurbineControllerMenu.FUEL + menu.fuels().indexOf(H2)),
                    "a fuel");
            h.assertTrue(
                    menu.clickMenuButton(
                            player,
                            TurbineControllerMenu.OXIDIZER + menu.oxidizers().indexOf(O2)),
                    "an oxidizer");
            h.assertFalse(
                    menu.clickMenuButton(
                            player, TurbineControllerMenu.FUEL + menu.fuels().size()),
                    "a fuel past the list");
            h.assertTrue(menu.clickMenuButton(player, TurbineControllerMenu.AUTO), "the auto box");
            h.assertFalse(r.controller.autoIgnite(), "the auto box didn't switch it off");
            h.assertTrue(menu.clickMenuButton(player, TurbineControllerMenu.RATE_DOWN_TENTH + 2), "the rate's +0.01");
            h.assertValueEqual(
                    r.controller.burnRate(),
                    TurbineControllerBlockEntity.DEFAULT_BURN_RATE + 0.01,
                    "the rate after +0.01");
            for (int i = 0; i < 25; i++) {
                menu.clickMenuButton(player, TurbineControllerMenu.RATE_DOWN_TENTH);
            }
            h.assertValueEqual(r.controller.burnRate(), 0.0, "the rate stepped below 0");
            for (int i = 0; i < 200; i++) {
                menu.clickMenuButton(player, TurbineControllerMenu.RATE_DOWN_TENTH + 3);
            }
            h.assertValueEqual(
                    r.controller.burnRate(),
                    TurbineControllerBlockEntity.maxBurnRate(),
                    "the rate stepped past the most");
            r.controller.setBurnRate(0.2);
            for (int i = 0; i < 6; i++) {
                r.step();
            }
            h.assertTrue(r.turbine.rpm() > 0, "the shaft isn't turning");
            h.assertTrue(menu.on(), "the window shows it off");
            h.assertValueEqual(menu.status(), TurbineStatus.RUNNING, "the window's state");
            for (Part part : Part.values()) {
                h.assertValueEqual(menu.linked(part), part != Part.IGNITER, "the window's " + part + " link");
            }
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_FUEL), menu.fuels().indexOf(H2), "the window's fuel");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_OXIDIZER),
                    menu.oxidizers().indexOf(O2),
                    "the window's oxidizer");
            h.assertValueEqual(menu.value(TurbineControllerMenu.DATA_RATE), 20, "the window's rate");
            h.assertValueEqual(menu.value(TurbineControllerMenu.DATA_AUTO), 0, "the window's auto box");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_RPM), (int) Math.round(r.turbine.rpm()), "the window's rpm");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_CHAMBER_PRESSURE),
                    (int) Math.round(r.turbine.chamberPressure()),
                    "the window's chamber pressure");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_CHAMBER_TEMPERATURE),
                    (int) Math.round(r.turbine.chamberTemperature()),
                    "the window's chamber temperature");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_BUFFER_PRESSURE),
                    (int) Math.round(r.compressor.buffer().pressure()),
                    "the window's buffer");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_BUFFER_FUEL),
                    (int) Math.round(r.compressor.buffer().partialPressure(H2)),
                    "the window's buffer fuel");
            h.assertValueEqual(
                    menu.value(TurbineControllerMenu.DATA_POWER),
                    (int) Math.round(r.turbine.powerPerTick()),
                    "the window's output");
            h.assertValueEqual(menu.energy(), r.controller.energy().getEnergyStored(), "the window's store");
            h.assertValueEqual(menu.capacity(), r.controller.energy().getMaxEnergyStored(), "the window's capacity");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    // ---- The controller and its links (CONT-190) ---------------------------------------------------

    private static String key(Component message) {
        return message.getContents() instanceof TranslatableContents t ? t.getKey() : message.getString();
    }

    /**
     * The multitool links a turbine, a compressor and an igniter to the controller it selected, and
     * unlinks one used again; it links nothing else (CONT-190). A part linked to a second controller
     * leaves the first. Each part knows the controller that links it, and an unlinked one knows none.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_190_the_multitool_links_and_unlinks_the_parts(GameTestHelper h) {
        Area a = new Area(h, 5618);
        TurbineRig r = TurbineRig.build(a);
        ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
        MultitoolItem.select(tool, a.level, r.controller.getBlockPos());
        BlockPos[] parts = {r.turbine.getBlockPos(), r.compressor.getBlockPos(), r.igniter.getBlockPos()};
        for (BlockPos part : parts) {
            h.assertValueEqual(
                    key(MultitoolItem.link(tool, a.level, part)),
                    "item.zas_atmos.multitool.linked_turbine",
                    "linking " + part);
        }
        for (Part part : Part.values()) {
            h.assertTrue(r.controller.linked(part), "the " + part + " isn't linked");
        }
        h.assertValueEqual(r.turbine.controllerPos(), r.controller.getBlockPos(), "the turbine's controller");
        h.assertValueEqual(r.compressor.controllerPos(), r.controller.getBlockPos(), "the compressor's controller");
        h.assertTrue(
                TurbinePartBlock.controllerOf(a.level, r.turbine.getBlockPos()) == r.controller, "the turbine's link");
        h.assertTrue(
                TurbinePartBlock.controllerOf(a.level, r.compressor.getBlockPos()) == r.controller,
                "the compressor's link");
        h.assertValueEqual(
                key(MultitoolItem.link(tool, a.level, a.abs(0, 2, 2))),
                "item.zas_atmos.multitool.not_linkable_turbine",
                "linking stone");

        h.assertValueEqual(
                key(MultitoolItem.link(tool, a.level, r.compressor.getBlockPos())),
                "item.zas_atmos.multitool.unlinked_turbine",
                "unlinking the compressor");
        h.assertFalse(r.controller.linked(Part.COMPRESSOR), "the compressor is still linked");
        h.assertTrue(r.compressor.controllerPos() == null, "the compressor kept its controller");
        h.assertTrue(
                TurbinePartBlock.controllerOf(a.level, r.compressor.getBlockPos()) == null,
                "an unlinked compressor's link");
        r.controller.setOn(true);
        a.step();
        h.assertValueEqual(r.controller.status(), TurbineStatus.NO_COMPRESSOR, "a controller with no compressor");

        // A second controller beside the first takes the turbine.
        a.place(
                5,
                5,
                2,
                AtmosBlocks.TURBINE_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(TurbineControllerBlock.FACING, Direction.SOUTH));
        TurbineControllerBlockEntity second = a.entity(5, 5, 2);
        MultitoolItem.select(tool, a.level, second.getBlockPos());
        MultitoolItem.link(tool, a.level, r.turbine.getBlockPos());
        h.assertTrue(second.linked(Part.TURBINE), "the second controller didn't take the turbine");
        h.assertFalse(r.controller.linked(Part.TURBINE), "the first controller kept the turbine");
        h.assertTrue(TurbinePartBlock.controllerOf(a.level, r.turbine.getBlockPos()) == second, "the turbine's link");
        h.succeed();
    }

    /**
     * The controller keeps its links and settings through a save and a load (CONT-190): its switch,
     * fuel, oxidizer, burn rate, auto relight, FE and the three links, which hold again in the level.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_190_links_and_settings_survive_save_and_load(GameTestHelper h) {
        Area a = new Area(h, 5619);
        TurbineRig r = TurbineRig.build(a).link(true).burn(0.37, false);
        r.controller.energy().set(1234);
        HolderLookup.Provider registries = a.level.registryAccess();
        CompoundTag saved = r.controller.saveCustomOnly(registries);
        TurbineControllerBlockEntity copy =
                new TurbineControllerBlockEntity(r.controller.getBlockPos(), r.controller.getBlockState());
        copy.loadCustomOnly(saved, registries);
        copy.setLevel(a.level);
        h.assertTrue(copy.on(), "the copy is off");
        h.assertValueEqual(copy.fuel(), H2, "the copy's fuel");
        h.assertValueEqual(copy.oxidizer(), O2, "the copy's oxidizer");
        h.assertValueEqual(copy.burnRate(), 0.37, "the copy's burn rate");
        h.assertFalse(copy.autoIgnite(), "the copy's auto relight");
        h.assertValueEqual(copy.energy().getEnergyStored(), 1234, "the copy's FE");
        for (Part part : Part.values()) {
            h.assertValueEqual(copy.link(part), r.controller.link(part), "the copy's " + part + " link");
            h.assertTrue(copy.linked(part), "the copy's " + part + " link doesn't hold");
        }
        h.succeed();
    }

    // ---- The igniter (CONT-191) --------------------------------------------------------------------

    /**
     * The controller sparks its linked igniter (CONT-191): from its window, an ignitable chamber lights;
     * the spark ends by itself, and an unpowered igniter with no signal stays dark after. With auto
     * relight on, a chamber made ignitable lights with no command. With no igniter linked there is
     * nothing to spark.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_191_the_controller_sparks_its_igniter(GameTestHelper h) {
        Area a = new Area(h, 5620);
        TurbineRig r = TurbineRig.build(a).link(false).burn(0.2, false);
        h.assertFalse(r.controller.ignite(), "a spark with no igniter");
        r.controller.toggleLink(a.level, r.igniter.getBlockPos());
        r.addToChamber(H2, 0.1 * r.chamber().mixture().totalMoles());
        r.settle();
        h.assertFalse(r.burning(), "the chamber lit by itself");
        h.assertFalse(r.igniter.lit(), "the igniter sparked by itself");
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        TurbineControllerMenu menu = (TurbineControllerMenu) r.controller.createMenu(1, player.getInventory(), player);
        h.assertTrue(menu.clickMenuButton(player, TurbineControllerMenu.IGNITE), "the ignite button");
        h.assertTrue(r.igniter.lit(), "the igniter didn't spark");
        for (int i = 0; i < 5 && !r.burning(); i++) {
            r.step();
        }
        h.assertTrue(r.burning(), "the spark didn't light the chamber");
        for (int i = 0; i < IgniterBlockEntity.COMMAND_STEPS + 1; i++) {
            r.step();
        }
        h.assertFalse(r.igniter.lit(), "the spark didn't end");

        Area b = new Area(h, 5621);
        TurbineRig auto = TurbineRig.build(b).link(true).burn(0.2, true);
        auto.settle();
        h.assertFalse(auto.burning(), "an air-filled chamber lit");
        auto.addToChamber(H2, 0.1 * auto.chamber().mixture().totalMoles());
        for (int i = 0; i < 10 && !auto.burning(); i++) {
            auto.step();
        }
        h.assertTrue(auto.burning(), "auto relight didn't light an ignitable chamber");
        h.succeed();
    }
}
