package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity.Slot;
import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralMenu;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ScrubberOrder;
import io.github.west3436.zas.atmos.controller.Thresholds;
import io.github.west3436.zas.atmos.controller.ZonePriority;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.heater.ThermoMachineBlockEntity;
import io.github.west3436.zas.atmos.heater.ThermoMachineMenu;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.SidePortMenu;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.mixer.MixerMenu;
import io.github.west3436.zas.atmos.portable.PortableMenu;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.portable.PortablePumpMenu;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpMenu;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a computer can set (COMPAT-021), against the device's own window (CONT-074): a number is
 * taken from the lowest to the highest value the window's own buttons reach and refused beyond
 * them, a word is one of the window's choices, and what the window refuses a player is refused a
 * computer. A peripheral's methods are called here directly, as CC: Tweaked calls them for a
 * computer on the server thread; {@link CcComputerTests} shows a real computer setting a device.
 */
@GameTestHolder(CcComputerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcWriteTests {
    private static final String BATCH = "zas_cc_writes";

    private CcWriteTests() {}

    /** A number a computer gives a peripheral. */
    @FunctionalInterface
    private interface Setter {
        void set(double value) throws LuaException;
    }

    private static Player player(GameTestHelper h) {
        return h.makeMockPlayer(GameType.SURVIVAL);
    }

    /** Presses one of a window's buttons until the value stops changing, and returns where it stopped. */
    private static double pressed(
            Spot s, AbstractContainerMenu window, Player player, int button, DoubleSupplier value) {
        for (int i = 0; i < 5000; i++) {
            double before = value.getAsDouble();
            s.h.assertTrue(window.clickMenuButton(player, button), "the window refused its button " + button);
            if (value.getAsDouble() == before) {
                return before;
            }
        }
        throw new GameTestAssertException("button " + button + " never reached an end");
    }

    /**
     * A numeric setting: the peripheral takes the lowest and the highest value the window's buttons
     * reach and one between its steps, and refuses the next value beyond either end and what is no
     * number, saying the range, with the setting left as it was.
     *
     * @param downBig the window's button for its largest step down; its largest step up is three on
     */
    private static void bounded(
            Spot s,
            String what,
            String unit,
            AbstractContainerMenu window,
            Player player,
            int downBig,
            DoubleSupplier value,
            Setter set) {
        double low = pressed(s, window, player, downBig, value);
        double high = pressed(s, window, player, downBig + 3, value);
        s.h.assertTrue(low < high, what + ": the window reaches " + low + " to " + high);
        String range = what + " must be " + Lua.number(low) + " to " + Lua.number(high) + " " + unit;
        s.allowed(() -> set.set(low), what + " at the window's lowest, " + low);
        s.close(value.getAsDouble(), low, what + " set to the lowest");
        s.allowed(() -> set.set(high), what + " at the window's highest, " + high);
        s.close(value.getAsDouble(), high, what + " set to the highest");
        double between = low + (high - low) * 0.37;
        s.allowed(() -> set.set(between), what + " between the window's steps, " + between);
        s.close(value.getAsDouble(), between, what + " set between the window's steps");
        double[] beyond = {
            Math.nextUp(high), high + 1, Math.nextDown(low), low - 1, Double.NaN, Double.POSITIVE_INFINITY, -1e300
        };
        for (double v : beyond) {
            s.h.assertValueEqual(s.refused(() -> set.set(v), what + " at " + v), range, what + " at " + v);
            s.close(value.getAsDouble(), between, what + " after " + v + " was refused");
        }
    }

    /** Every number a window sets: the range a computer has is the one the window's buttons have. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_021_a_number_is_taken_within_the_windows_range_and_refused_beyond(GameTestHelper h) {
        Spot s = new Spot(h, 40);
        Devices.clear(s);
        Player player = player(h);

        CanisterBlockEntity canister = (CanisterBlockEntity) Devices.place(s, "zas_canister", 1, 1, 1);
        CanisterPeripheral cp = s.peripheral(1, 1, 1, CanisterPeripheral.class);
        bounded(
                s,
                "release pressure",
                "kPa",
                canister.createMenu(1, player.getInventory(), player),
                player,
                CanisterMenu.RELEASE_DOWN_100,
                canister::releasePressure,
                cp::setReleasePressure);

        VentBlockEntity vent = (VentBlockEntity) Devices.place(s, "zas_vent", 3, 1, 1);
        VentPeripheral vp = s.peripheral(3, 1, 1, VentPeripheral.class);
        AbstractContainerMenu ventWindow = vent.createMenu(1, player.getInventory(), player);
        bounded(
                s,
                "external bound",
                "kPa",
                ventWindow,
                player,
                VentMenu.EXTERNAL_DOWN_100,
                () -> vent.settings().external(),
                vp::setExternalBound);
        bounded(
                s,
                "internal bound",
                "kPa",
                ventWindow,
                player,
                VentMenu.INTERNAL_DOWN_100,
                () -> vent.settings().internal(),
                vp::setInternalBound);

        PressurePumpBlockEntity pump = (PressurePumpBlockEntity) Devices.place(s, "zas_pressure_pump", 5, 1, 1);
        bounded(
                s,
                "target pressure",
                "kPa",
                pump.createMenu(1, player.getInventory(), player),
                player,
                PumpMenu.STEP_DOWN_BIG,
                pump::setting,
                s.peripheral(5, 1, 1, PumpPeripheral.Pressure.class)::setTargetPressure);

        VolumePumpBlockEntity volume = (VolumePumpBlockEntity) Devices.place(s, "zas_volume_pump", 7, 1, 1);
        bounded(
                s,
                "rate",
                "L/s",
                volume.createMenu(1, player.getInventory(), player),
                player,
                PumpMenu.STEP_DOWN_BIG,
                volume::setting,
                s.peripheral(7, 1, 1, PumpPeripheral.Volume.class)::setRate);

        FilterBlockEntity filter = (FilterBlockEntity) Devices.place(s, "zas_filter", 9, 1, 1);
        bounded(
                s,
                "target pressure",
                "kPa",
                filter.createMenu(1, player.getInventory(), player),
                player,
                SidePortMenu.STEP_DOWN_BIG,
                filter::target,
                s.peripheral(9, 1, 1, SidePortPeripheral.Filter.class)::setTargetPressure);

        MixerBlockEntity mixer = (MixerBlockEntity) Devices.place(s, "zas_mixer", 11, 1, 1);
        SidePortPeripheral.Mixer mp = s.peripheral(11, 1, 1, SidePortPeripheral.Mixer.class);
        AbstractContainerMenu mixerWindow = mixer.createMenu(1, player.getInventory(), player);
        bounded(
                s,
                "target pressure",
                "kPa",
                mixerWindow,
                player,
                SidePortMenu.STEP_DOWN_BIG,
                mixer::target,
                mp::setTargetPressure);
        // The mix is a whole number of percent.
        int least = (int) pressed(s, mixerWindow, player, MixerMenu.MIX_DOWN_BIG, mixer::backPercent);
        int most = (int) pressed(s, mixerWindow, player, MixerMenu.MIX_DOWN_BIG + 3, mixer::backPercent);
        String mix = "mix must be " + least + " to " + most + " %";
        s.allowed(() -> mp.setMix(least), "the window's lowest mix");
        s.allowed(() -> mp.setMix(most), "the window's highest mix");
        s.allowed(() -> mp.setMix(37), "a mix between the window's steps");
        h.assertValueEqual(s.refused(() -> mp.setMix(most + 1), "a mix past the highest"), mix, "the mix's range");
        h.assertValueEqual(s.refused(() -> mp.setMix(least - 1), "a mix past the lowest"), mix, "the mix's range");
        h.assertValueEqual(mixer.backPercent(), 37, "the mix after the refusals");

        for (String type : List.of("zas_heater", "zas_air_conditioner")) {
            int x = type.equals("zas_heater") ? 13 : 15;
            ThermoMachineBlockEntity machine = (ThermoMachineBlockEntity) Devices.place(s, type, x, 1, 1);
            bounded(
                    s,
                    "setpoint",
                    "K",
                    machine.createMenu(1, player.getInventory(), player),
                    player,
                    ThermoMachineMenu.SETPOINT_DOWN_10,
                    machine::setpoint,
                    s.peripheral(x, 1, 1, PortablePeripheral.Thermo.class)::setSetpoint);
        }

        PortablePumpBlockEntity portable = (PortablePumpBlockEntity) Devices.place(s, "zas_portable_pump", 17, 1, 1);
        bounded(
                s,
                "target pressure",
                "kPa",
                portable.createMenu(1, player.getInventory(), player),
                player,
                PortablePumpMenu.TARGET_DOWN_100,
                portable::target,
                s.peripheral(17, 1, 1, PortablePeripheral.Pump.class)::setTargetPressure);
        Devices.clear(s);
        h.succeed();
    }

    /**
     * Every switch and choice a window sets: a computer's goes where the window's button does, and
     * a word that is none of the choices is refused with the choices.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_021_a_switch_or_a_choice_is_set_as_the_windows_button_sets_it(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 41);
        Devices.clear(s);
        Player player = player(h);

        CanisterBlockEntity canister = (CanisterBlockEntity) Devices.place(s, "zas_canister", 1, 1, 1);
        CanisterPeripheral cp = s.peripheral(1, 1, 1, CanisterPeripheral.class);
        cp.setValveOpen(true);
        h.assertTrue(canister.valveOpen(), "the canister's valve didn't open");
        canister.createMenu(1, player.getInventory(), player).clickMenuButton(player, CanisterMenu.TOGGLE_VALVE);
        h.assertFalse(cp.isValveOpen(), "the window's button didn't close the valve the computer opened");

        VentBlockEntity vent = (VentBlockEntity) Devices.place(s, "zas_vent", 3, 1, 1);
        VentPeripheral vp = s.peripheral(3, 1, 1, VentPeripheral.class);
        for (VentMode mode : VentMode.values()) {
            vp.setMode(Lua.word(mode));
            h.assertValueEqual(vent.settings().mode(), mode, "the vent's mode");
        }
        h.assertValueEqual(
                s.refused(() -> vp.setMode("Blow"), "a mode in capitals"),
                "mode must be one of: off, blow, siphon",
                "the vent's modes");

        ScrubberBlockEntity scrubber = (ScrubberBlockEntity) Devices.place(s, "zas_scrubber", 5, 1, 1);
        ScrubberPeripheral sp = s.peripheral(5, 1, 1, ScrubberPeripheral.class);
        ScrubberMenu scrubberWindow = (ScrubberMenu) scrubber.createMenu(1, player.getInventory(), player);
        for (ScrubberMode mode : ScrubberMode.values()) {
            sp.setMode(Lua.word(mode));
            h.assertValueEqual(scrubber.mode(), mode, "the scrubber's mode");
        }
        h.assertValueEqual(
                s.refused(() -> sp.setMode("vacuum"), "a mode there isn't"),
                "mode must be one of: off, scrub, siphon, panic",
                "the scrubber's modes");
        for (int option = 0; option < ScrubberMenu.OPTIONS; option++) {
            String name = option < ScrubberMenu.GASES.size()
                    ? ScrubberMenu.GASES.get(option).toString()
                    : "#"
                            + ScrubberMenu.TAGS
                                    .get(option - ScrubberMenu.GASES.size())
                                    .id();
            boolean was = scrubberWindow.selected(option);
            sp.setScrubbed(name, !was);
            h.assertValueEqual(scrubberWindow.selected(option), !was, "whether it scrubs " + name);
            sp.setScrubbed(name, !was);
            h.assertValueEqual(scrubberWindow.selected(option), !was, name + " set to what it was");
            // The window's own button for it puts it back.
            h.assertTrue(scrubberWindow.clickMenuButton(player, ScrubberMenu.FIRST_OPTION + option), "the button");
            h.assertValueEqual(sp.getScrubbed().get(name), was, name + " after the window's button");
        }
        h.assertValueEqual(
                s.refused(() -> sp.setScrubbed("zas:plasma", true), "a gas the window doesn't offer"),
                "a scrubber has no option zas:plasma",
                "a gas that isn't offered");
        h.assertValueEqual(
                s.refused(() -> sp.setScrubbed(GasTags.TOXIC.id().toString(), true), "a tag without its #"),
                "a scrubber has no option " + GasTags.TOXIC.id(),
                "a tag named as a gas");

        // Each device in a pipe line: its switch, as the window's toggle.
        int x = 7;
        for (String type :
                List.of("zas_pressure_pump", "zas_volume_pump", "zas_filter", "zas_mixer", "zas_electrolyzer")) {
            InlineDeviceEntity device = (InlineDeviceEntity) Devices.place(s, type, x, 1, 1);
            InlinePeripheral<?> p = s.peripheral(x, 1, 1, InlinePeripheral.class);
            p.setOn(true);
            h.assertTrue(device.on(), type + " didn't switch on");
            h.assertTrue(
                    device.createMenu(1, player.getInventory(), player)
                            .clickMenuButton(player, InlineDeviceMenu.TOGGLE),
                    type + "'s window refused its switch");
            h.assertFalse(p.isOn(), type + " is still on after the window's switch");
            x += 2;
        }

        FilterBlockEntity filter = (FilterBlockEntity) s.level.getBlockEntity(s.abs(11, 1, 1));
        SidePortPeripheral.Filter fp = s.peripheral(11, 1, 1, SidePortPeripheral.Filter.class);
        String oxygen = Spot.O2.toString();
        boolean was = fp.getFiltered().get(oxygen);
        fp.setFiltered(oxygen, !was);
        h.assertValueEqual(filter.selection().gases().contains(Spot.O2), !was, "whether the filter takes oxygen");
        fp.setFiltered("#" + GasTags.TOXIC.id(), true);
        h.assertTrue(filter.selection().tags().contains(GasTags.TOXIC), "the filter doesn't take the toxic gases");
        h.assertValueEqual(
                s.refused(() -> fp.setFiltered("zas:plasma", true), "a gas the window doesn't offer"),
                "this filter has no option zas:plasma",
                "a gas that isn't offered");

        // The portables: the switch, and the pump's direction.
        ThermoMachineBlockEntity heater = (ThermoMachineBlockEntity) Devices.place(s, "zas_heater", 19, 1, 1);
        PortablePeripheral.Thermo hp = s.peripheral(19, 1, 1, PortablePeripheral.Thermo.class);
        hp.setOn(true);
        h.assertTrue(heater.on(), "the heater didn't switch on");
        h.assertTrue(
                heater.createMenu(1, player.getInventory(), player).clickMenuButton(player, PortableMenu.TOGGLE),
                "the heater's window refused its switch");
        h.assertFalse(hp.isOn(), "the heater is still on after the window's switch");
        PortablePumpBlockEntity pump = (PortablePumpBlockEntity) Devices.place(s, "zas_portable_pump", 21, 1, 1);
        PortablePeripheral.Pump pp = s.peripheral(21, 1, 1, PortablePeripheral.Pump.class);
        pp.setMode("siphon");
        h.assertTrue(pump.siphon(), "the portable pump doesn't siphon");
        pp.setMode("release");
        h.assertFalse(pump.siphon(), "the portable pump still siphons");
        h.assertValueEqual(
                s.refused(() -> pp.setMode("blow"), "a vent's mode"),
                "mode must be one of: release, siphon",
                "the portable pump's modes");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * A vent and a scrubber that an area air controller orders refuse a computer as they refuse
     * their windows (CONT-074), and keep what they were ordered.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_021_a_controlled_vent_or_scrubber_refuses_a_computer_as_its_window(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 42);
        Devices.clear(s);
        Player player = player(h);
        BlockPos orders = s.abs(5, 1, 1);

        VentBlockEntity vent = (VentBlockEntity) Devices.place(s, "zas_vent", 1, 1, 1);
        VentPeripheral vp = s.peripheral(1, 1, 1, VentPeripheral.class);
        AbstractContainerMenu ventWindow = vent.createMenu(1, player.getInventory(), player);
        vp.setMode("blow");
        vent.control(orders, VentSettings.siphon(40));
        VentSettings ordered = vent.settings();
        String refusal = "an area air controller controls this vent";
        h.assertFalse(ventWindow.clickMenuButton(player, VentMenu.SET_MODE), "the window set a controlled vent");
        h.assertFalse(
                ventWindow.clickMenuButton(player, VentMenu.EXTERNAL_DOWN_100), "the window set a controlled vent");
        h.assertValueEqual(s.refused(() -> vp.setMode("off"), "a mode"), refusal, "a controlled vent");
        h.assertValueEqual(s.refused(() -> vp.setExternalBound(10), "a bound"), refusal, "a controlled vent");
        h.assertValueEqual(s.refused(() -> vp.setInternalBound(10), "a bound"), refusal, "a controlled vent");
        h.assertValueEqual(vent.settings(), ordered, "the vent's settings after the refusals");
        // A value out of range is still told so first.
        h.assertValueEqual(
                s.refused(() -> vp.setExternalBound(-5), "a bound out of range"),
                "external bound must be 0 to 5066 kPa",
                "a controlled vent, out of range");

        ScrubberBlockEntity scrubber = (ScrubberBlockEntity) Devices.place(s, "zas_scrubber", 3, 1, 1);
        ScrubberPeripheral sp = s.peripheral(3, 1, 1, ScrubberPeripheral.class);
        AbstractContainerMenu scrubberWindow = scrubber.createMenu(1, player.getInventory(), player);
        scrubber.scrubberOrder(new ScrubberOrder(ScrubberOrder.Mode.PANIC, List.of(), List.of(), orders));
        String scrubberRefusal = "an area air controller controls this scrubber";
        h.assertFalse(
                scrubberWindow.clickMenuButton(player, ScrubberMenu.SET_MODE), "the window set a controlled scrubber");
        h.assertValueEqual(s.refused(() -> sp.setMode("off"), "a mode"), scrubberRefusal, "a controlled scrubber");
        h.assertValueEqual(
                s.refused(() -> sp.setScrubbed(Spot.CO2.toString(), false), "a gas"),
                scrubberRefusal,
                "a controlled scrubber");
        h.assertValueEqual(scrubber.mode(), ScrubberMode.PANIC, "the scrubber's mode after the refusals");
        h.assertValueEqual(sp.getController(), Lua.position(orders), "the controller it names");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * An area air controller takes a mode, a preset and thresholds from a computer as from its
     * window: a bound goes as far as its neighbours let the window's buttons take it, and no further.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_021_an_area_air_controller_takes_a_mode_a_preset_and_thresholds(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 43);
        Devices.clear(s);
        Player player = player(h);
        AreaAirControllerBlockEntity controller = Devices.areaAirController(s, 2, 2, 2);
        AreaAirControllerPeripheral p = s.peripheral(2, 2, 2, AreaAirControllerPeripheral.class);
        ControllerMenu window = new ControllerMenu(1, player.getInventory(), controller);

        for (ControllerMode mode : ControllerMode.values()) {
            p.setMode(Lua.word(mode));
            h.assertValueEqual(controller.mode(), mode, "its mode");
        }
        h.assertTrue(
                s.refused(() -> p.setMode("party"), "a mode there isn't").startsWith("mode must be one of: "),
                "the modes aren't listed");

        p.loadPreset("vacuum");
        h.assertValueEqual(controller.preset(), ControllerPreset.VACUUM, "the preset loaded");
        p.loadPreset("human");
        h.assertValueEqual(controller.preset(), ControllerPreset.HUMAN, "the preset loaded");
        h.assertValueEqual(
                s.refused(() -> p.loadPreset("martian"), "a preset there isn't"),
                "preset must be one of: human, vacuum",
                "the presets");

        // The temperature's upper warning: the window's buttons take it down to the bound below it and
        // up to the bound above it.
        int reading = controller.preset().indexOf("temperature");
        h.assertValueEqual(reading, 1, "the temperature's place");
        h.assertTrue(window.clickMenuButton(player, ControllerMenu.SELECT_BOUND + reading * 4 + 2), "selecting max1");
        DoubleSupplier max1 = () -> controller.preset().thresholds(reading).bound(2);
        double low = pressed(s, window, player, ControllerMenu.ADJUST, max1);
        double high = pressed(s, window, player, ControllerMenu.ADJUST + 5, max1);
        Thresholds human = ControllerPreset.HUMAN.thresholds(reading);
        h.assertValueEqual(low, human.min1(), "how far down the window takes max1");
        h.assertValueEqual(high, human.max2(), "how far up the window takes max1");
        String range = "max1 must be " + Lua.number(low) + " to " + Lua.number(high);
        p.setThreshold("temperature", "max1", Optional.of(high));
        h.assertValueEqual(max1.getAsDouble(), high, "max1 at the window's highest");
        p.setThreshold("temperature", "max1", Optional.of(low));
        h.assertValueEqual(max1.getAsDouble(), low, "max1 at the window's lowest");
        p.setThreshold("temperature", "max1", Optional.of(300.1234));
        h.assertValueEqual(max1.getAsDouble(), 300.123, "max1 to thousandths, as the window keeps it");
        for (double beyond : new double[] {high + 0.001, low - 0.001, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            h.assertValueEqual(
                    s.refused(() -> p.setThreshold("temperature", "max1", Optional.of(beyond)), "max1 at " + beyond),
                    range,
                    "max1 at " + beyond);
        }
        h.assertValueEqual(max1.getAsDouble(), 300.123, "max1 after the refusals");
        h.assertTrue(p.isPresetEdited(), "the preset isn't marked as edited");

        // Off, and on again, as the window's toggle.
        p.setThreshold("temperature", "max1", Optional.empty());
        h.assertValueEqual(max1.getAsDouble(), Thresholds.OFF, "max1 turned off");
        h.assertFalse(p.getThresholds().get("temperature").containsKey("max1"), "an off bound is listed");
        h.assertFalse(window.clickMenuButton(player, ControllerMenu.ADJUST + 5), "the window stepped an off bound");
        p.setThreshold("temperature", "max1", Optional.of(310.0));
        h.assertValueEqual(p.getThresholds().get("temperature").get("max1"), 310.0, "max1 turned on at a value");

        h.assertValueEqual(
                s.refused(() -> p.setThreshold("humidity", "max1", Optional.of(1.0)), "a reading there isn't"),
                "the controller has no reading humidity",
                "a reading there isn't");
        h.assertValueEqual(
                s.refused(() -> p.setThreshold("temperature", "max3", Optional.of(1.0)), "a bound there isn't"),
                "bound must be one of: min2, min1, max1, max2",
                "a bound there isn't");
        // A gas's bounds, by the gas's id.
        p.setThreshold(ControllerPreset.CO2.toString(), "max2", Optional.of(7.5));
        h.assertValueEqual(
                controller
                        .preset()
                        .thresholds(controller.preset().indexOf(ControllerPreset.CO2.toString()))
                        .max2(),
                7.5,
                "carbon dioxide's max2");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * A central atmospherics controller takes a range and a zone's priority from a computer as from
     * its window, and refuses a range the window can't reach and a controller it hasn't claimed.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_021_a_central_controller_takes_a_range_and_a_priority(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 44);
        Devices.clear(s);
        Player player = player(h);
        AreaAirControllerBlockEntity mine = Devices.areaAirController(s, 2, 2, 2);
        AreaAirControllerBlockEntity other = Devices.areaAirController(s, 5, 2, 2);
        CentralControllerBlockEntity central = Devices.central(s, 9, 1, 2);
        CentralControllerPeripheral p = s.peripheral(8, 2, 2, CentralControllerPeripheral.class);
        CentralMenu window = new CentralMenu(1, player.getInventory(), central);
        h.assertTrue(central.take(mine.getBlockPos()), "the console couldn't take the controller");

        // The window's buttons reach 1 to the server's maximum.
        int highest = (int) pressedUntilRefused(s, window, player, CentralMenu.RANGE_UP, central::range);
        int lowest = (int) pressedUntilRefused(s, window, player, CentralMenu.RANGE_DOWN, central::range);
        h.assertValueEqual(lowest, 1, "the window's shortest range");
        h.assertValueEqual(highest, CentralControllerBlockEntity.maxRange(), "the window's longest range");
        String range = "range must be 1 to " + highest + " chunks";
        p.setRange(highest);
        h.assertValueEqual(central.range(), highest, "its range at the longest");
        p.setRange(2);
        h.assertValueEqual(central.range(), 2, "its range");
        h.assertValueEqual(s.refused(() -> p.setRange(0), "no range"), range, "a range of 0");
        h.assertValueEqual(s.refused(() -> p.setRange(highest + 1), "too long a range"), range, "a range too long");
        h.assertValueEqual(central.range(), 2, "its range after the refusals");
        h.assertTrue(
                CentralClaims.of(s.level).centralOf(mine.getBlockPos()).isPresent(),
                "a range that still reaches the controller let it go");

        BlockPos at = mine.getBlockPos();
        for (ZonePriority priority : ZonePriority.values()) {
            p.setPriority(at.getX(), at.getY(), at.getZ(), Lua.word(priority));
            h.assertValueEqual(p.getControllers().get(0).get("priority"), Lua.word(priority), "the zone's priority");
            h.assertValueEqual(window.sync().rows().get(0).priority(), priority.ordinal(), "the window's priority");
        }
        h.assertValueEqual(
                s.refused(() -> p.setPriority(at.getX(), at.getY(), at.getZ(), "urgent"), "a priority there isn't"),
                "priority must be one of: critical, normal, low",
                "the priorities");
        BlockPos not = other.getBlockPos();
        h.assertValueEqual(
                s.refused(() -> p.setPriority(not.getX(), not.getY(), not.getZ(), "low"), "a controller not claimed"),
                "it has no area air controller at " + not.getX() + ", " + not.getY() + ", " + not.getZ(),
                "a controller it hasn't claimed");
        Devices.clear(s);
        h.succeed();
    }

    /** As {@link #pressed}, for a button the window refuses at its end instead of leaving the value alone. */
    private static double pressedUntilRefused(
            Spot s, AbstractContainerMenu window, Player player, int button, DoubleSupplier value) {
        for (int i = 0; i < 5000; i++) {
            if (!window.clickMenuButton(player, button)) {
                return value.getAsDouble();
            }
        }
        throw new GameTestAssertException("button " + button + " never reached an end");
    }

    /**
     * An airlock controller arms and clears its slots for a computer as for its window, and refuses
     * a cycle it can't start: with no power, as the window's button is refused.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_021_an_airlock_controller_arms_clears_and_refuses_a_cycle_it_cannot_start(
            GameTestHelper h) throws LuaException {
        Spot s = new Spot(h, 45);
        Devices.clear(s);
        Player player = player(h);
        AirlockControllerBlockEntity controller = Devices.airlockController(s, 2, 2, 2);
        AirlockControllerPeripheral p = s.peripheral(2, 2, 2, AirlockControllerPeripheral.class);
        AbstractContainerMenu window = controller.createMenu(1, player.getInventory(), player);

        for (Slot slot : Slot.values()) {
            p.setArmed(Optional.of(slot.key()));
            h.assertValueEqual(controller.armed(), slot, "the armed slot");
            p.setArmed(Optional.of(slot.key()));
            h.assertValueEqual(controller.armed(), slot, "the slot armed twice");
        }
        p.setArmed(Optional.empty());
        h.assertTrue(controller.armed() == null, "a slot is still armed");
        p.setArmed(Optional.empty());
        h.assertTrue(controller.armed() == null, "disarming twice armed a slot");
        h.assertValueEqual(
                s.refused(() -> p.setArmed(Optional.of("middle_door")), "a slot there isn't"),
                "slot must be one of: inner_door, outer_door, inner_sensor, outer_sensor, chamber_sensor",
                "the slots");

        // A sensor linked into a slot, then cleared.
        s.set(4, 2, 1, Spot.STONE);
        s.set(
                4,
                2,
                2,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH));
        p.setArmed(Optional.of("inner_sensor"));
        controller.toggleLink(s.level, s.abs(4, 2, 2));
        h.assertValueEqual(p.getSlots(), Map.of("inner_sensor", Lua.position(s.abs(4, 2, 2))), "its slots");
        h.assertTrue(p.clear("inner_sensor"), "clearing a filled slot");
        h.assertFalse(p.clear("inner_sensor"), "clearing an empty slot");
        h.assertFalse(
                window.clickMenuButton(player, AirlockControllerMenu.CLEAR + Slot.INNER_SENSOR.ordinal()),
                "the window cleared an empty slot");
        h.assertValueEqual(p.getSlots().size(), 0, "its slots, cleared");

        // No power and no doors: the window's button is refused, and the computer is told why.
        h.assertFalse(window.clickMenuButton(player, AirlockControllerMenu.CYCLE), "the window started a cycle");
        h.assertValueEqual(s.refused(() -> p.cycle("inner"), "a cycle with no power"), "it has no power", "no power");
        h.assertValueEqual(s.refused(() -> p.cycle("outer"), "a cycle with no power"), "it has no power", "no power");
        h.assertValueEqual(
                s.refused(() -> p.cycle("up"), "a side there isn't"), "side must be one of: inner, outer", "the sides");
        h.assertFalse(p.stop(), "stopping with no cycle");
        h.assertValueEqual(p.getState(), "idle", "its state after the refusals");
        Devices.clear(s);
        h.succeed();
    }
}
