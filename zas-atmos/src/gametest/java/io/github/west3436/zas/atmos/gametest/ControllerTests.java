package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.controller.ScrubberOrder;
import io.github.west3436.zas.atmos.controller.Thresholds;
import io.github.west3436.zas.atmos.controller.VentOrder;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The area air controller in a real level (CONT-040 to CONT-049, CONT-062 to CONT-069, CONT-074,
 * CONT-110), with the stand-in parts of {@link TestParts}. Each test builds sealed 3 × 3 × 3 stone
 * rooms ({@link Site#room}) with the controller on the north wall, steps the controller itself and
 * drives the atmos ticks, all within one game tick, in a batch of its own so other tests' devices
 * never meet these.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ControllerTests {
    private static final String BATCH = "zas_atmos_controller";
    private static final ResourceLocation CO2 = ControllerPreset.CO2;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    private ControllerTests() {}

    /** A room with a controller whose wall block is the test FE block, so it always has power. */
    private static AreaAirControllerBlockEntity poweredRoom(Site s, int x0) {
        s.room(x0, x0 + 4);
        s.set(x0 + 2, 2, 0, TestParts.battery().defaultBlockState());
        return s.controller(x0 + 2);
    }

    /** Runs a test's checks, then clears its area even if they fail. */
    private static void run(GameTestHelper h, Site s, Runnable checks) {
        try {
            checks.run();
        } finally {
            s.clear();
        }
        h.succeed();
    }

    // ---- CONT-110 -------------------------------------------------------------------------

    /**
     * Placed in a sealed Overworld room, a controller pins the zone at its cell, forming one holding
     * the ambient air; the pinned zone outlasts its room being opened, which dissolves an unpinned
     * one; and removing the controller unpins it, so it dissolves (CONT-110).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_110_controller_forms_and_pins_its_zone(GameTestHelper h) {
        Site s = new Site(h, 0);
        run(h, s, () -> {
            s.clear();
            s.room(0, 4);
            s.ticks(2);
            h.assertValueEqual(Atmospheres.at(s.level, s.abs(2, 2, 2)).state(), AtmosphereState.AMBIENT, "before");
            AreaAirControllerBlockEntity c = s.controller(2);
            s.cycle(c);
            h.assertValueEqual(c.lastPin(), PinOutcome.ZONE, "the controller's pin");
            ZoneInfo zone = s.zone(2, 2, 2);
            h.assertValueEqual(zone.cellCount(), 27, "cells of the room");
            h.assertValueEqual(s.pins(zone.id()), List.of(c.owner().toString()), "the zone's pins");
            h.assertValueEqual(
                    c.owner().toString(),
                    "zas_atmos:area_air_controller/" + c.getBlockPos().getX() + "_102_"
                            + c.getBlockPos().getZ(),
                    "the owner id");
            s.close(
                    zone.mixture().pressure(),
                    Ambients.ambient(Level.OVERWORLD).pressure(),
                    1e-6,
                    "the room's pressure");

            s.set(2, 4, 2, Site.AIR); // open the roof
            s.cycles(12, c);
            ZoneInfo open = Atmospheres.zone(s.level, zone.id())
                    .orElseThrow(() -> new GameTestAssertException("the pinned zone was dissolved"));
            h.assertTrue(open.breached(), "the opened room isn't breached");

            s.set(2, 2, 1, Site.AIR); // remove the controller
            s.ticks(12);
            h.assertTrue(Atmospheres.zone(s.level, zone.id()).isEmpty(), "the zone outlived its controller");
            h.assertTrue(s.pins(zone.id()).isEmpty(), "pins left after removal");
        });
    }

    /**
     * In an open room no zone forms; once the last block of the room goes in, the controller forms
     * its zone on its next step, with nothing broken and replaced (CONT-110).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_110_no_zone_in_the_open_until_the_room_is_sealed(GameTestHelper h) {
        Site s = new Site(h, 1);
        run(h, s, () -> {
            s.clear();
            s.room(0, 4);
            s.set(2, 4, 2, Site.AIR); // a hole in the roof
            AreaAirControllerBlockEntity c = s.controller(2);
            s.cycles(3, c);
            h.assertValueEqual(c.lastPin(), PinOutcome.OPEN, "the pin in the open room");
            h.assertFalse(s.zoned(2, 2, 2), "a zone formed in the open room");

            s.set(2, 4, 2, Site.STONE); // the last block
            s.tick();
            s.cycle(c);
            h.assertValueEqual(c.lastPin(), PinOutcome.ZONE, "the pin once sealed");
            ZoneInfo zone = s.zone(2, 2, 2);
            h.assertValueEqual(zone.cellCount(), 27, "cells of the sealed room");
            h.assertValueEqual(s.pins(zone.id()), List.of(c.owner().toString()), "the zone's pins");
        });
    }

    /**
     * In a vacuum dimension, a controller in a sealed room forms a zone holding nothing (CONT-110).
     * The GameTest server has no datapack dimensions, so the End borrows the test datapack's vacuum
     * ambient for the test, in a batch of its own.
     */
    @GameTest(template = "empty", batch = "zas_atmos_vacuum")
    public static void cont_110_controller_forms_a_zone_in_a_vacuum(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, 8);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            run(h, s, () -> {
                s.clear();
                s.room(0, 4);
                s.ticks(2);
                AreaAirControllerBlockEntity c = s.controller(2);
                s.cycle(c);
                h.assertValueEqual(c.lastPin(), PinOutcome.ZONE, "the pin in the vacuum room");
                ZoneInfo zone = s.zone(2, 2, 2);
                h.assertValueEqual(zone.cellCount(), 27, "cells of the room");
                s.close(zone.mixture().pressure(), 0, 1e-9, "the vacuum room's pressure");
                s.cycles(12, c);
                h.assertTrue(Atmospheres.zone(end, zone.id()).isPresent(), "the vacuum zone was dissolved");
            });
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
    }

    // ---- CONT-040, CONT-046, CONT-048 -----------------------------------------------------

    /**
     * The controller rates its zone's CO₂ through each band: safe, danger 1 at once, danger 2 at
     * once, and danger 2 still after one lower reading (CONT-040, CONT-046); its panel shows each,
     * and it emits 0, 7 and 15 into the wall behind it (CONT-048).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_040_danger_follows_the_readings(GameTestHelper h) {
        Site s = new Site(h, 2);
        run(h, s, () -> {
            s.clear();
            AreaAirControllerBlockEntity c = poweredRoom(s, 0);
            BlockPos wall = s.abs(2, 2, 0);
            s.cycle(c);
            s.cycle(c);
            h.assertTrue(c.powered(), "the controller has no power");
            check(h, s, c, wall, 0, ControllerStatus.SAFE, 0);

            s.addKpa(2, 2, 2, CO2, 6);
            s.cycle(c);
            check(h, s, c, wall, 1, ControllerStatus.DANGER1, 7);
            s.close(c.values()[4], 6, 1e-6, "the CO2 reading");

            s.addKpa(2, 2, 2, CO2, 5);
            s.cycle(c);
            check(h, s, c, wall, 2, ControllerStatus.DANGER2, 15);

            s.removeGas(2, 2, 2, CO2);
            s.cycle(c);
            h.assertValueEqual(c.ratings()[4], 0, "the CO2 rating once it is gone");
            check(h, s, c, wall, 2, ControllerStatus.DANGER2, 15);
        });
    }

    private static void check(
            GameTestHelper h,
            Site s,
            AreaAirControllerBlockEntity c,
            BlockPos wall,
            int danger,
            ControllerStatus status,
            int signal) {
        h.assertValueEqual(c.danger(), danger, "danger");
        h.assertValueEqual(
                c.getBlockState().getValue(io.github.west3436.zas.atmos.controller.AreaAirControllerBlock.STATUS),
                status,
                "the panel");
        h.assertValueEqual(s.level.getDirectSignalTo(wall), signal, "the signal into the wall");
        h.assertValueEqual(s.level.getSignal(c.getBlockPos(), Direction.EAST), signal, "the signal beside it");
    }

    // ---- CONT-047, CONT-074 ---------------------------------------------------------------

    /**
     * The GUI's buttons, as the server checks them: unknown buttons are refused, a player too far
     * away can't use the menu, edits are clamped to stay in order, an off bound can't be nudged,
     * and an edited preset changes the controller's ratings (CONT-047, CONT-074).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_074_gui_settings_are_checked_and_an_edited_preset_rates(GameTestHelper h) {
        Site s = new Site(h, 3);
        run(h, s, () -> {
            s.clear();
            AreaAirControllerBlockEntity c = poweredRoom(s, 0);
            s.cycle(c);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ControllerMenu menu = new ControllerMenu(0, player.getInventory(), c);
            h.assertFalse(menu.stillValid(player), "a player far away can use the menu");
            player.setPos(s.point(2, 1, 2));
            h.assertTrue(menu.stillValid(player), "a player in the room can't use the menu");

            for (int bad : new int[] {-1, 6, 9, 12, 19, 24, 29, 37, 1000}) {
                h.assertFalse(menu.clickMenuButton(player, bad), "button " + bad + " was accepted");
            }
            h.assertValueEqual(c.preset(), ControllerPreset.HUMAN, "a refused button changed the preset");

            h.assertTrue(menu.clickMenuButton(player, ControllerMode.PANIC.ordinal()), "the Panic button");
            h.assertValueEqual(c.mode(), ControllerMode.PANIC, "mode");

            for (int i = 0; i < 4; i++) {
                menu.clickMenuButton(player, ControllerMenu.NEXT_READING); // to CO2
            }
            h.assertValueEqual(c.preset().gas(menu.reading()), CO2, "the selected reading");
            h.assertValueEqual(menu.bound(), 2, "max1 is selected first");
            for (int i = 0; i < 3; i++) {
                h.assertTrue(menu.clickMenuButton(player, ControllerMenu.ADJUST + 1), "-1"); // 5 -> 2 kPa
            }
            h.assertValueEqual(c.preset().thresholds(4), Thresholds.max(2, 10), "CO2 after the edit");
            for (int i = 0; i < 5; i++) {
                menu.clickMenuButton(player, ControllerMenu.ADJUST + 5); // +10, clamped at max2
            }
            h.assertValueEqual(c.preset().thresholds(4).max1(), 10.0, "max1 clamped at max2");
            for (int i = 0; i < 8; i++) {
                menu.clickMenuButton(player, ControllerMenu.ADJUST + 1);
            }
            h.assertValueEqual(c.preset().thresholds(4).max1(), 2.0, "CO2 max1 after the edits");
            h.assertTrue(c.preset().edited(), "the preset isn't marked edited");

            s.addKpa(2, 2, 2, CO2, 2.5);
            s.cycle(c);
            h.assertValueEqual(c.danger(), 1, "2.5 kPa of CO2 under the edited preset");
            h.assertValueEqual(ControllerPreset.HUMAN.danger(s.zone(2, 2, 2).mixture()), 0, "under Human");

            menu.clickMenuButton(player, ControllerMenu.PREV_BOUND); // min1, off
            h.assertFalse(menu.clickMenuButton(player, ControllerMenu.ADJUST + 4), "an off bound was nudged");
            h.assertTrue(menu.clickMenuButton(player, ControllerMenu.TOGGLE_BOUND), "turning min1 on");
            h.assertValueEqual(c.preset().thresholds(4).min1(), 2.0, "min1 turned on starts at its neighbour");

            h.assertTrue(menu.clickMenuButton(player, ControllerMenu.LOAD_PRESET), "loading Human");
            h.assertValueEqual(c.preset(), ControllerPreset.HUMAN, "loading Human discards the edits");
            h.assertTrue(menu.clickMenuButton(player, ControllerMenu.LOAD_PRESET + 1), "loading Vacuum");
            h.assertValueEqual(c.preset(), ControllerPreset.VACUUM, "the Vacuum preset");
            h.assertValueEqual(menu.sync().labels().get(4), CO2.toString(), "the GUI's labels");
            player.discard();
        });
    }

    // ---- CONT-041 -------------------------------------------------------------------------

    /**
     * Each mode's orders reach the vents and scrubbers in the zone, and Cycle and Fill switch on by
     * themselves: Cycle to Fill below 5 % of 1 atm, Fill to Scrubbing at 95 % of the target
     * (CONT-041).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_041_modes_command_vents_and_scrubbers(GameTestHelper h) {
        Site s = new Site(h, 4);
        run(h, s, () -> {
            s.clear();
            AreaAirControllerBlockEntity c = poweredRoom(s, 0);
            TestParts.Vent vent =
                    s.place(1, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
            TestParts.Scrubber scrubber =
                    s.place(3, 1, 3, TestParts.Kind.SCRUBBER.block().defaultBlockState(), TestParts.Scrubber.class);
            s.cycle(c);
            s.cycle(c);
            ControllerPreset p = ControllerPreset.HUMAN;
            for (ControllerMode m : List.of(
                    ControllerMode.SCRUBBING, ControllerMode.REPLACEMENT, ControllerMode.PANIC, ControllerMode.OFF)) {
                c.setMode(m);
                s.cycle(c);
                h.assertValueEqual(vent.last(), m.vent(p), m + ": the vent's order");
                h.assertValueEqual(scrubber.last(), m.scrubber(p), m + ": the scrubber's order");
            }
            h.assertValueEqual(ControllerMode.SCRUBBING.vent(p), VentOrder.blow(101), "Scrubbing blows to 101 kPa");

            c.setMode(ControllerMode.CYCLE);
            s.cycle(c);
            h.assertValueEqual(c.mode(), ControllerMode.CYCLE, "Cycle at 101 kPa");
            h.assertValueEqual(vent.last(), VentOrder.OFF, "Cycle: the vent");
            h.assertValueEqual(scrubber.last(), ScrubberOrder.PANIC, "Cycle: the scrubber");

            s.empty(2, 2, 2);
            s.cycle(c);
            h.assertValueEqual(c.mode(), ControllerMode.FILL, "Cycle once the room is empty");
            h.assertValueEqual(vent.last(), VentOrder.blow(101), "Fill: the vent");
            h.assertValueEqual(scrubber.last(), ScrubberOrder.OFF, "Fill: the scrubber");

            s.addKpa(2, 2, 2, StandardAir.NITROGEN, 90);
            s.cycle(c);
            h.assertValueEqual(c.mode(), ControllerMode.FILL, "Fill at 90 kPa");
            s.addKpa(2, 2, 2, StandardAir.OXYGEN, 6);
            s.cycle(c);
            h.assertValueEqual(c.mode(), ControllerMode.SCRUBBING, "Fill at 96 kPa");
            h.assertValueEqual(scrubber.last().mode(), ScrubberOrder.Mode.SCRUB, "Scrubbing: the scrubber");
        });
    }

    // ---- CONT-042, CONT-049, CONT-068, CONT-069 -------------------------------------------

    /**
     * A controller powers and lists the parts in its zone, not those elsewhere; a part in another
     * room linked with the multitool is powered and listed as linked; and a part beside a burning
     * generator with no controller has no power (CONT-042, CONT-049, CONT-068, CONT-069).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_068_controller_powers_its_zone_and_its_links(GameTestHelper h) {
        Site s = new Site(h, 5);
        run(h, s, () -> {
            s.clear();
            AreaAirControllerBlockEntity c = poweredRoom(s, 0);
            TestParts.Vent vent =
                    s.place(2, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
            s.room(6, 10);
            TestParts.Part pump =
                    s.place(8, 1, 2, TestParts.Kind.PUMP.block().defaultBlockState(), TestParts.Part.class);
            TestParts.Part heater =
                    s.place(8, 3, 2, TestParts.Kind.HEATER.block().defaultBlockState(), TestParts.Part.class);
            s.set(
                    9,
                    1,
                    2,
                    io.github.west3436.zas.atmos.registry.AtmosBlocks.GENERATOR
                            .get()
                            .defaultBlockState());
            var generator = s.entity(9, 1, 2, io.github.west3436.zas.atmos.power.GeneratorBlockEntity.class);
            generator.fuel().insertItem(0, new ItemStack(net.minecraft.world.item.Items.COAL), false);
            generator.serverTick();
            s.cycle(c, vent, pump, heater);
            s.cycle(c, vent, pump, heater);
            h.assertTrue(vent.power().isPowered(), "the vent in the zone has no power");
            h.assertTrue(vent.power().source() == c, "the vent's power isn't the controller's");
            h.assertTrue(vent.poweredSteps > 0, "the vent never ran");
            h.assertFalse(pump.power().isPowered(), "the pump beside a generator, with no controller, has power");
            h.assertValueEqual(pump.poweredSteps, 0, "the pump ran");
            h.assertValueEqual(c.parts().size(), 1, "parts listed");
            h.assertTrue(c.parts().get(0).inZone() && !c.parts().get(0).linked(), "the vent is listed from the zone");

            ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
            h.assertTrue(MultitoolItem.selected(tool, s.level).isEmpty(), "a new multitool has a selection");
            MultitoolItem.link(tool, s.level, pump.getBlockPos());
            h.assertTrue(c.links().isEmpty(), "linked with no controller selected");
            MultitoolItem.select(tool, s.level, c.getBlockPos());
            MultitoolItem.link(tool, s.level, pump.getBlockPos());
            h.assertValueEqual(c.links(), java.util.Set.of(pump.getBlockPos()), "the controller's links");

            s.cycle(c, vent, pump, heater);
            h.assertTrue(pump.power().isPowered(), "the linked pump has no power");
            h.assertFalse(heater.power().isPowered(), "the unlinked heater has power");
            h.assertValueEqual(c.parts().size(), 2, "parts listed");
            AreaAirControllerBlockEntity.Part listed = c.parts().get(1);
            h.assertTrue(
                    !listed.inZone() && listed.linked() && listed.powered(), "the pump is listed as linked: " + listed);
            h.assertValueEqual(
                    listed.block(), ResourceLocation.fromNamespaceAndPath("zas_atmos", "test_pump"), "the pump's id");

            MultitoolItem.link(tool, s.level, pump.getBlockPos());
            h.assertTrue(c.links().isEmpty(), "using the multitool on a linked part again didn't unlink it");
        });
    }

    /**
     * A vent that draws nothing until it is ordered on is paid for on the atmos tick the controller
     * first orders it on, not first on the tick after (CONT-065, CONT-068): the controller gives its
     * orders before it pays its parts.
     *
     * <p>The game clock doesn't move during a GameTest, so a part supplied once counts as supplied
     * for the rest of the test ({@code DevicePower#suppliedThisAtmosTick}); the test therefore checks
     * the vent's first covered tick, which is also its switch-on tick.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_068_a_part_switched_on_is_paid_on_that_tick(GameTestHelper h) {
        Site s = new Site(h, 13);
        run(h, s, () -> {
            s.clear();
            AreaAirControllerBlockEntity c = poweredRoom(s, 0);
            TestParts.Vent vent =
                    s.place(2, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
            vent.drawsOnlyWhenOn = true;
            h.assertValueEqual(vent.power().draw(), 0, "the vent's draw before any order");
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            int ventCost = TestParts.Kind.VENT.draw * AtmosTicks.PERIOD;
            // The first step pins the zone; the vent is covered, and ordered on, once it has formed.
            for (int i = 0; i < 4 && vent.orders.isEmpty(); i++) {
                s.cycle(c);
            }
            h.assertValueEqual(vent.orders, List.of(VentOrder.blow(101)), "the vent's orders");
            h.assertValueEqual(vent.power().draw(), TestParts.Kind.VENT.draw, "the vent's draw once on");
            h.assertTrue(vent.power().source() == c, "the vent wasn't powered on the tick it was switched on");
            h.assertValueEqual(
                    c.buffer().getEnergyStored(), 10_000 - own - ventCost, "the buffer after the switch-on tick");
        });
    }

    // ---- CONT-062, CONT-064 ---------------------------------------------------------------

    /**
     * The buffer fills from the test FE block and from a generator next to it, up to 10,000 FE, and
     * pays the controller's own 5 FE/t (CONT-062, CONT-065).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_062_buffer_fills_from_fe_blocks_next_to_it(GameTestHelper h) {
        Site s = new Site(h, 6);
        run(h, s, () -> {
            s.clear();
            AreaAirControllerBlockEntity c = poweredRoom(s, 0);
            TestParts.Battery battery = s.entity(2, 2, 0, TestParts.Battery.class);
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            h.assertValueEqual(own, 100, "the controller's own draw per atmos tick");
            s.cycle(c);
            h.assertValueEqual(c.buffer().getMaxEnergyStored(), 10_000, "the buffer's size");
            h.assertValueEqual(c.buffer().getEnergyStored(), 10_000 - own, "the buffer after one step");
            h.assertValueEqual(
                    battery.store.getEnergyStored(), TestParts.BATTERY_FE - 10_000, "taken from the FE block");
            s.cycle(c);
            h.assertValueEqual(c.buffer().getEnergyStored(), 10_000 - own, "the buffer refills");

            // A generator on the floor below the controller, with no FE block behind it.
            s.room(6, 10);
            AreaAirControllerBlockEntity d = s.controller(8);
            s.set(
                    8,
                    1,
                    1,
                    io.github.west3436.zas.atmos.registry.AtmosBlocks.GENERATOR
                            .get()
                            .defaultBlockState());
            var generator = s.entity(8, 1, 1, io.github.west3436.zas.atmos.power.GeneratorBlockEntity.class);
            generator.energy().set(5_000);
            s.cycle(d);
            h.assertValueEqual(d.buffer().getEnergyStored(), 5_000 - own, "the buffer from the generator");
            h.assertValueEqual(generator.energy().getEnergyStored(), 0, "the generator after the pull");
            h.assertTrue(d.powered(), "powered from the generator");
            s.cycle(d);
            h.assertFalse(d.buffer().getEnergyStored() > 5_000, "made FE from nothing");
            h.assertValueEqual(generator.energy().receiveEnergy(100, false), 0, "the generator takes FE in");
            h.assertValueEqual(c.buffer().extractEnergy(100, false), 0, "the controller gives FE out");
        });
    }

    /**
     * With too little in its buffer, a controller powers its life support first; the gas-handling
     * part that misses out stops, and so does the auxiliary one after it. With nothing for its own
     * draw it is unpowered: no danger, no orders, and its parts lose power (CONT-064).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_064_a_starved_controller_powers_life_support_first(GameTestHelper h) {
        Site s = new Site(h, 7);
        run(h, s, () -> {
            s.clear();
            s.room(0, 4);
            AreaAirControllerBlockEntity c = s.controller(2);
            TestParts.Vent vent =
                    s.place(1, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
            TestParts.Scrubber scrubber =
                    s.place(3, 1, 3, TestParts.Kind.SCRUBBER.block().defaultBlockState(), TestParts.Scrubber.class);
            TestParts.Part pump =
                    s.place(1, 1, 2, TestParts.Kind.PUMP.block().defaultBlockState(), TestParts.Part.class);
            TestParts.Part heater =
                    s.place(3, 1, 2, TestParts.Kind.HEATER.block().defaultBlockState(), TestParts.Part.class);
            c.buffer().set(100);
            s.cycle(c); // pins the zone; its own draw only
            // own 100 + vent 200 + scrubber 300 + 50: the pump's 400 doesn't fit, nor the heater's 1,200
            c.buffer().set(650);
            s.cycle(c);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(vent.power().isPowered(), "the vent is starved");
            h.assertTrue(scrubber.power().isPowered(), "the scrubber is starved");
            h.assertFalse(pump.power().isPowered(), "the pump has power");
            h.assertFalse(heater.power().isPowered(), "the heater has power before the pump");
            h.assertValueEqual(c.buffer().getEnergyStored(), 50, "the buffer after paying");
            h.assertValueEqual(c.parts().size(), 4, "parts listed");

            c.buffer().set(0);
            int orders = vent.orders.size();
            s.cycle(c);
            h.assertFalse(c.powered(), "powered with an empty buffer");
            h.assertValueEqual(c.status(), ControllerStatus.UNPOWERED, "the panel");
            h.assertValueEqual(vent.orders.size(), orders, "an unpowered controller gave orders");
        });
    }
}
