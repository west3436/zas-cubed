package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralMenu;
import io.github.west3436.zas.atmos.controller.CentralSync;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.controller.ControllerSync;
import io.github.west3436.zas.atmos.controller.ZonePriority;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMath;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMenu;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.filter.FilterMenu;
import io.github.west3436.zas.atmos.filter.FilterOption;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.heater.ThermoMachineMenu;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.inline.SidePortMenu;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.mixer.MixerMenu;
import io.github.west3436.zas.atmos.portable.PortableGasMenu;
import io.github.west3436.zas.atmos.portable.PortableMenu;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.portable.PortablePumpMenu;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorMenu;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpMenu;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What each peripheral reads (COMPAT-020), against the device's own window: the menu a player's
 * window is opened from, on the server, with the values it sends. A peripheral's methods are called
 * here directly, as CC: Tweaked calls them for a computer on the server thread; {@link
 * CcComputerTests} shows a real computer getting them.
 */
@GameTestHolder(CcComputerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcReadTests {
    private static final String BATCH = "zas_cc_reads";

    private CcReadTests() {}

    private static Player player(GameTestHelper h) {
        return h.makeMockPlayer(GameType.SURVIVAL);
    }

    /** A window's whole number for a value the peripheral gives in full. */
    private static int shown(double value) {
        return (int) Math.round(value);
    }

    /**
     * Each device with a window is a peripheral from every side, of its own type and of that device;
     * two lookups of one device are equal, as CC: Tweaked needs to tell a changed peripheral from
     * the same one. A pipe part with no window is none.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_every_device_with_a_window_is_a_peripheral_of_its_own_type(GameTestHelper h) {
        Spot s = new Spot(h, 30);
        Devices.clear(s);
        int i = 0;
        for (String type : Devices.SURFACE.keySet()) {
            // Two rows, three blocks apart, so nothing joins and each wall panel has its stone.
            BlockEntity device = Devices.place(s, type, 1 + 3 * (i % 8), 2, i < 8 ? 2 : 5);
            i++;
            for (Direction side : Direction.values()) {
                IPeripheral found = s.peripheral(device.getBlockPos(), side);
                h.assertTrue(found != null, type + " is no peripheral from " + side);
                h.assertValueEqual(found.getType(), type, "the type from " + side);
                h.assertTrue(found.getTarget() == device, type + "'s peripheral isn't of its block entity");
                IPeripheral again = s.peripheral(device.getBlockPos(), side.getOpposite());
                h.assertTrue(found != again && found.equals(again), type + ": two lookups aren't equal");
            }
        }
        for (Block block : List.of(AtmosBlocks.PIPE.get(), AtmosBlocks.PASSIVE_VENT.get(), AtmosBlocks.IGNITER.get())) {
            s.set(1, 5, 2, block.defaultBlockState());
            for (Direction side : Direction.values()) {
                h.assertTrue(s.peripheral(s.abs(1, 5, 2), side) == null, block + " is a peripheral");
            }
        }
        Devices.clear(s);
        h.succeed();
    }

    /**
     * Any of the console's four blocks is its peripheral, and a computer that looked at one of them
     * before the console stood there finds it once it does, and loses it when the console goes.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_any_block_of_the_console_is_its_peripheral(GameTestHelper h) {
        Spot s = new Spot(h, 31);
        Devices.clear(s);
        List<BlockPos> parts = CentralControllerBlock.parts(s.abs(4, 1, 2), Direction.NORTH);
        // What a computer beside each block holds: CC: Tweaked looks through the game's cache.
        List<BlockCapabilityCache<IPeripheral, Direction>> held = new ArrayList<>();
        for (BlockPos part : parts) {
            BlockCapabilityCache<IPeripheral, Direction> cache =
                    BlockCapabilityCache.create(PeripheralCapability.get(), s.level, part, Direction.NORTH);
            h.assertTrue(cache.getCapability() == null, "air is a peripheral");
            held.add(cache);
        }
        CentralControllerBlockEntity central = Devices.central(s, 4, 1, 2);
        for (BlockCapabilityCache<IPeripheral, Direction> cache : held) {
            IPeripheral found = cache.getCapability();
            h.assertTrue(found instanceof CentralControllerPeripheral, "no console at " + cache.pos() + ": " + found);
            h.assertTrue(found.getTarget() == central, "the peripheral at " + cache.pos() + " isn't the console's");
            h.assertTrue(found.equals(held.get(0).getCapability()), "the console's blocks aren't one peripheral");
        }
        Devices.clear(s);
        for (BlockCapabilityCache<IPeripheral, Direction> cache : held) {
            h.assertTrue(cache.getCapability() == null, "the console's peripheral outlived it at " + cache.pos());
        }
        h.succeed();
    }

    /** A vent and a scrubber read as their windows do, by hand and under a controller's orders. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_a_vent_and_a_scrubber_read_as_their_windows_do(GameTestHelper h) throws LuaException {
        Spot s = new Spot(h, 32);
        Devices.clear(s);
        Player player = player(h);

        VentBlockEntity vent = (VentBlockEntity) Devices.place(s, "zas_vent", 1, 1, 1);
        VentPeripheral v = s.peripheral(1, 1, 1, VentPeripheral.class);
        VentMenu ventWindow = (VentMenu) vent.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(v.getMode(), "off", "a new vent's mode");
        vent.setByHand(new VentSettings(VentMode.BLOW, 120.4, 30.6));
        vent.network().gas().add(new GasParcel(Map.of(Spot.O2, 5.0), Spot.T));
        h.assertValueEqual(v.getMode(), "blow", "the vent's mode");
        h.assertValueEqual(ventWindow.mode(), VentMode.BLOW, "the window's mode");
        s.close(v.getExternalBound(), 120.4, "its external bound");
        s.close(v.getInternalBound(), 30.6, "its internal bound");
        h.assertValueEqual(ventWindow.value(VentMenu.DATA_EXTERNAL), shown(v.getExternalBound()), "the window's");
        h.assertValueEqual(ventWindow.value(VentMenu.DATA_INTERNAL), shown(v.getInternalBound()), "the window's");
        h.assertTrue(v.getNetworkPressure() > 1, "its network has no pressure: " + v.getNetworkPressure());
        s.close(v.getNetworkPressure(), vent.networkPressure(), "its network's pressure");
        h.assertValueEqual(
                ventWindow.value(VentMenu.DATA_NETWORK), shown(v.getNetworkPressure()), "the window's pressure");
        h.assertValueEqual(v.getState(), Lua.word(ventWindow.status()), "its state");
        h.assertValueEqual(v.isPowered(), ventWindow.value(VentMenu.DATA_POWERED) != 0, "its power");
        h.assertFalse(v.isControlled(), "a vent set by hand is controlled");
        h.assertTrue(v.getController() == null, "a vent set by hand has a controller");
        BlockPos orders = s.abs(5, 1, 1);
        vent.control(orders, VentSettings.siphon(40));
        h.assertTrue(v.isControlled() && ventWindow.controlled(), "an ordered vent isn't controlled");
        h.assertValueEqual(v.getController(), Lua.position(orders), "its controller");
        h.assertValueEqual(ventWindow.controller(), Optional.of(orders), "the window's controller");
        h.assertValueEqual(v.getMode(), "siphon", "the mode it was ordered to");

        ScrubberBlockEntity scrubber = (ScrubberBlockEntity) Devices.place(s, "zas_scrubber", 1, 1, 4);
        ScrubberPeripheral p = s.peripheral(1, 1, 4, ScrubberPeripheral.class);
        ScrubberMenu window = (ScrubberMenu) scrubber.createMenu(1, player.getInventory(), player);
        scrubber.setByHand(ScrubberMode.SCRUB, GasSelection.of(List.of(Spot.CO2, Spot.H2), List.of(GasTags.TOXIC)));
        scrubber.network().gas().add(new GasParcel(Map.of(Spot.CO2, 3.0), Spot.T));
        h.assertValueEqual(p.getMode(), "scrub", "the scrubber's mode");
        h.assertValueEqual(window.mode(), ScrubberMode.SCRUB, "the window's mode");
        Map<String, Boolean> scrubbed = p.getScrubbed();
        h.assertValueEqual(scrubbed.size(), ScrubberMenu.OPTIONS, "the options it lists");
        for (int option = 0; option < ScrubberMenu.OPTIONS; option++) {
            String name = option < ScrubberMenu.GASES.size()
                    ? ScrubberMenu.GASES.get(option).toString()
                    : "#"
                            + ScrubberMenu.TAGS
                                    .get(option - ScrubberMenu.GASES.size())
                                    .id();
            h.assertTrue(scrubbed.containsKey(name), "no option " + name + " in " + scrubbed);
            h.assertValueEqual(scrubbed.get(name), window.selected(option), "whether it scrubs " + name);
        }
        h.assertTrue(scrubbed.get(Spot.CO2.toString()) && scrubbed.get("#" + GasTags.TOXIC.id()), "what was set");
        h.assertFalse(scrubbed.get(Spot.O2.toString()), "it scrubs oxygen");
        s.close(p.getNetworkPressure(), scrubber.networkPressure(), "its network's pressure");
        h.assertValueEqual(
                window.value(ScrubberMenu.DATA_NETWORK), shown(p.getNetworkPressure()), "the window's pressure");
        h.assertValueEqual(p.getState(), Lua.word(window.status()), "its state");
        h.assertValueEqual(p.isPowered(), window.value(ScrubberMenu.DATA_POWERED) != 0, "its power");
        h.assertValueEqual(p.isControlled(), window.controlled(), "whether it is controlled");
        h.assertTrue(p.getController() == null, "a scrubber set by hand has a controller");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * The devices in a pipe line read as their windows do: the switch, the state, the power, each
     * port's pressure and each one's own settings.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_the_devices_in_a_pipe_line_read_as_their_windows_do(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 33);
        Devices.clear(s);
        Player player = player(h);
        GasParcel air = new GasParcel(Map.of(Spot.O2, 2.0, Spot.N2, 6.0), Spot.T);

        PressurePumpBlockEntity pump = (PressurePumpBlockEntity) Devices.place(s, "zas_pressure_pump", 1, 1, 1);
        PumpPeripheral.Pressure pp = s.peripheral(1, 1, 1, PumpPeripheral.Pressure.class);
        PumpMenu pumpWindow = (PumpMenu) pump.createMenu(1, player.getInventory(), player);
        pump.set(300);
        pump.setOn(true);
        pump.port(PumpBlockEntity.INPUT).network().gas().add(air);
        h.assertValueEqual(pp.isOn(), true, "the pump's switch");
        h.assertValueEqual(pumpWindow.on(), true, "the window's switch");
        h.assertValueEqual(pp.getState(), Lua.word(pumpWindow.status()), "its state");
        h.assertValueEqual(pp.isPowered(), pumpWindow.powered(), "its power");
        h.assertTrue(pp.getInputPressure() > 1, "its input has no pressure");
        s.close(pp.getOutputPressure(), 0, "its output's pressure");
        h.assertValueEqual(pumpWindow.pressure(PumpBlockEntity.INPUT), shown(pp.getInputPressure()), "the window's");
        h.assertValueEqual(pumpWindow.pressure(PumpBlockEntity.OUTPUT), shown(pp.getOutputPressure()), "the window's");
        s.close(pp.getTargetPressure(), 300, "its target");
        h.assertValueEqual(pumpWindow.value(PumpMenu.DATA_SETTING), shown(pp.getTargetPressure()), "the window's");

        VolumePumpBlockEntity volume = (VolumePumpBlockEntity) Devices.place(s, "zas_volume_pump", 4, 1, 1);
        PumpPeripheral.Volume vp = s.peripheral(4, 1, 1, PumpPeripheral.Volume.class);
        PumpMenu volumeWindow = (PumpMenu) volume.createMenu(1, player.getInventory(), player);
        volume.set(volume.clamp(Double.MAX_VALUE) / 2);
        s.close(vp.getRate(), volume.setting(), "its rate");
        h.assertValueEqual(volumeWindow.value(PumpMenu.DATA_SETTING), shown(vp.getRate()), "the window's rate");
        h.assertValueEqual(vp.isOn(), false, "a new pump's switch");

        FilterBlockEntity filter = (FilterBlockEntity) Devices.place(s, "zas_filter", 7, 1, 1);
        SidePortPeripheral.Filter fp = s.peripheral(7, 1, 1, SidePortPeripheral.Filter.class);
        FilterMenu filterWindow = (FilterMenu) filter.createMenu(1, player.getInventory(), player);
        filter.setTarget(150);
        filter.port(SidePortEntity.BACK).network().gas().add(new GasParcel(Map.of(Spot.CO2, 4.0), Spot.T));
        filter.port(SidePortEntity.SIDE).network().gas().add(air);
        filter.setSelection(GasSelection.of(List.of(Spot.CO2), List.of()));
        s.close(fp.getTargetPressure(), 150, "the filter's target");
        h.assertValueEqual(filterWindow.value(SidePortMenu.DATA_TARGET), shown(fp.getTargetPressure()), "the window's");
        h.assertTrue(fp.getInputPressure() > 1 && fp.getFilteredPressure() > 1, "its ports have no pressure");
        h.assertValueEqual(filterWindow.pressure(SidePortEntity.BACK), shown(fp.getInputPressure()), "its input");
        h.assertValueEqual(filterWindow.pressure(SidePortEntity.FRONT), shown(fp.getOutputPressure()), "its output");
        h.assertValueEqual(filterWindow.pressure(SidePortEntity.SIDE), shown(fp.getFilteredPressure()), "its side");
        Map<String, Boolean> filtered = fp.getFiltered();
        List<FilterOption> offered = filter.offered();
        h.assertValueEqual(filtered.size(), offered.size(), "the options it lists");
        for (FilterOption option : offered) {
            String name = (option.tag() ? "#" : "") + option.id();
            h.assertTrue(filtered.containsKey(name), "no option " + name + " in " + filtered);
            h.assertValueEqual(filtered.get(name), option.in(filter.selection()), "whether it filters " + name);
        }
        h.assertTrue(filtered.get(Spot.CO2.toString()), "it doesn't filter what was set");
        h.assertFalse(filtered.get(Spot.O2.toString()), "it filters oxygen");

        MixerBlockEntity mixer = (MixerBlockEntity) Devices.place(s, "zas_mixer", 10, 1, 1);
        SidePortPeripheral.Mixer mp = s.peripheral(10, 1, 1, SidePortPeripheral.Mixer.class);
        MixerMenu mixerWindow = (MixerMenu) mixer.createMenu(1, player.getInventory(), player);
        mixer.setBackPercent(35);
        mixer.setTarget(220);
        mixer.port(SidePortEntity.BACK).network().gas().add(air);
        h.assertValueEqual(mp.getMix(), 35, "the mixer's mix");
        h.assertValueEqual(mixerWindow.value(MixerMenu.DATA_BACK_PERCENT), mp.getMix(), "the window's mix");
        s.close(mp.getTargetPressure(), 220, "its target");
        h.assertTrue(mp.getInput1Pressure() > 1, "its first input has no pressure");
        h.assertValueEqual(mixerWindow.pressure(SidePortEntity.BACK), shown(mp.getInput1Pressure()), "its input 1");
        h.assertValueEqual(mixerWindow.pressure(SidePortEntity.SIDE), shown(mp.getInput2Pressure()), "its input 2");
        h.assertValueEqual(mixerWindow.pressure(SidePortEntity.FRONT), shown(mp.getOutputPressure()), "its output");

        ElectrolyzerBlockEntity electrolyzer = (ElectrolyzerBlockEntity) Devices.place(s, "zas_electrolyzer", 13, 1, 1);
        ElectrolyzerPeripheral ep = s.peripheral(13, 1, 1, ElectrolyzerPeripheral.class);
        ElectrolyzerMenu electrolyzerWindow =
                (ElectrolyzerMenu) electrolyzer.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(ep.getWater(), 0, "a new electrolyzer's water");
        electrolyzer.tank().fill(new FluidStack(Fluids.WATER, 1500), IFluidHandler.FluidAction.EXECUTE);
        electrolyzer.port(ElectrolyzerBlockEntity.HYDROGEN).network().gas().add(air);
        h.assertValueEqual(ep.getWater(), 1500, "its water, mB");
        h.assertValueEqual(electrolyzerWindow.water(), ep.getWater(), "the window's water");
        h.assertValueEqual(ep.getWaterCapacity(), ElectrolyzerMath.TANK_MB, "its tank");
        h.assertTrue(ep.getHydrogenPressure() > 1, "its hydrogen outlet has no pressure");
        h.assertValueEqual(
                electrolyzerWindow.pressure(ElectrolyzerBlockEntity.HYDROGEN),
                shown(ep.getHydrogenPressure()),
                "the window's hydrogen");
        h.assertValueEqual(
                electrolyzerWindow.pressure(ElectrolyzerBlockEntity.OXYGEN),
                shown(ep.getOxygenPressure()),
                "the window's oxygen");
        h.assertValueEqual(ep.getState(), Lua.word(electrolyzerWindow.status()), "its state");
        h.assertValueEqual(electrolyzerWindow.value(InlineDeviceMenu.DATA_ON) != 0, ep.isOn(), "its switch");
        Devices.clear(s);
        h.succeed();
    }

    /** The portable devices and the generator read as their windows do. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_the_portables_and_the_generator_read_as_their_windows_do(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 34);
        Devices.clear(s);
        Player player = player(h);
        ItemStack battery = new ItemStack(AtmosItems.BATTERY.get());
        Batteries.energy(battery).receiveEnergy(1234, false);

        HeaterBlockEntity heater = (HeaterBlockEntity) Devices.place(s, "zas_heater", 1, 1, 1);
        PortablePeripheral.Thermo hp = s.peripheral(1, 1, 1, PortablePeripheral.Thermo.class);
        ThermoMachineMenu heaterWindow = (ThermoMachineMenu) heater.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(hp.getType(), "zas_heater", "the heater's type");
        h.assertValueEqual(hp.getEnergy(), 0, "its charge with no battery");
        h.assertValueEqual(hp.getEnergyCapacity(), 0, "its battery's size with no battery");
        heater.battery().setStackInSlot(0, battery.copy());
        heater.setSetpoint(heater.clampSetpoint(310.5));
        h.assertValueEqual(hp.getEnergy(), 1234, "its charge");
        h.assertValueEqual(heaterWindow.energy(), hp.getEnergy(), "the window's charge");
        h.assertTrue(hp.getEnergyCapacity() > 1234, "its battery's size: " + hp.getEnergyCapacity());
        h.assertValueEqual(heaterWindow.capacity(), hp.getEnergyCapacity(), "the window's battery size");
        s.close(hp.getSetpoint(), heater.setpoint(), "its setpoint, K");
        h.assertValueEqual(
                heaterWindow.value(ThermoMachineMenu.DATA_SETPOINT),
                shown(hp.getSetpoint() * 10),
                "the window's setpoint, in tenths");
        h.assertValueEqual(hp.getState(), Lua.word(heaterWindow.status()), "its state");
        h.assertValueEqual(hp.isOn(), heaterWindow.value(PortableMenu.DATA_ON) != 0, "its switch");
        h.assertTrue(hp.getRoomTemperature() == null, "a heater that never ran has a room temperature");
        h.assertValueEqual(
                heaterWindow.value(ThermoMachineMenu.DATA_ROOM), ThermoMachineMenu.NO_ROOM, "the window's room");
        s.close(hp.getWatts(), 0, "its watts while off");
        Devices.place(s, "zas_air_conditioner", 4, 1, 1);
        h.assertValueEqual(
                s.peripheral(4, 1, 1, PortablePeripheral.Thermo.class).getType(),
                "zas_air_conditioner",
                "the air conditioner's type");

        PortablePumpBlockEntity pump = (PortablePumpBlockEntity) Devices.place(s, "zas_portable_pump", 7, 1, 1);
        PortablePeripheral.Pump pp = s.peripheral(7, 1, 1, PortablePeripheral.Pump.class);
        PortablePumpMenu pumpWindow = (PortablePumpMenu) pump.createMenu(1, player.getInventory(), player);
        pump.store().add(new GasParcel(Map.of(Spot.O2, 20.0), Spot.T));
        pump.setTarget(180);
        pump.setSiphon(true);
        h.assertValueEqual(pp.getMode(), "siphon", "the portable pump's mode");
        h.assertValueEqual(pumpWindow.value(PortablePumpMenu.DATA_SIPHON), 1, "the window's mode");
        s.close(pp.getTargetPressure(), 180, "its target");
        h.assertValueEqual(
                pumpWindow.value(PortablePumpMenu.DATA_TARGET), shown(pp.getTargetPressure()), "the window's");
        h.assertTrue(pp.getStorePressure() > 1, "its store has no pressure");
        h.assertValueEqual(
                pumpWindow.value(PortableGasMenu.DATA_STORE), shown(pp.getStorePressure()), "the window's store");
        h.assertValueEqual(
                pp.isOnTank(), pumpWindow.value(PortableGasMenu.DATA_ON_TANK) != 0, "whether it holds a tank");
        h.assertValueEqual(pp.getState(), Lua.word(pumpWindow.status()), "its state");
        h.assertTrue(pp.getAirPressure() == null, "a pump that never ran has an air pressure");
        h.assertValueEqual(
                pumpWindow.value(PortableGasMenu.DATA_AIR), PortableGasMenu.NO_AIR, "the window's air pressure");
        Devices.place(s, "zas_portable_scrubber", 10, 1, 1);
        PortablePeripheral.Scrubber sp = s.peripheral(10, 1, 1, PortablePeripheral.Scrubber.class);
        h.assertValueEqual(sp.getType(), "zas_portable_scrubber", "the portable scrubber's type");
        s.close(sp.getStorePressure(), 0, "an empty store's pressure");

        GeneratorBlockEntity generator = (GeneratorBlockEntity) Devices.place(s, "zas_generator", 13, 1, 1);
        GeneratorPeripheral gp = s.peripheral(13, 1, 1, GeneratorPeripheral.class);
        h.assertTrue(gp.getFuel() == null, "an empty generator has fuel");
        generator.fuel().setStackInSlot(0, new ItemStack(Items.COAL, 3));
        generator.serverTick();
        GeneratorMenu generatorWindow = (GeneratorMenu) generator.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(gp.getFuel(), Map.<String, Object>of("name", "minecraft:coal", "count", 2), "its fuel");
        s.close(gp.getBurnTotal(), GeneratorBlockEntity.COAL_BURN_TIME / 20.0, "how long a coal burns, s");
        h.assertTrue(gp.getBurnTime() > 0 && gp.getBurnTime() <= gp.getBurnTotal(), "its burn time");
        s.close(gp.getBurnTime(), generatorWindow.burnLeft() / 20.0, "the window's burn time");
        s.close(gp.getBurnTotal(), generatorWindow.burnTotal() / 20.0, "the window's whole burn");
        h.assertValueEqual(gp.getEnergy(), generatorWindow.energy(), "its FE");
        h.assertValueEqual(gp.getEnergyCapacity(), generatorWindow.capacity(), "its buffer");
        h.assertTrue(gp.getEnergyCapacity() > 0, "its buffer has no size");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * An area air controller in a sealed room, with power and a vent: its status, readings,
     * thresholds, parts and room are what its window is sent.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_an_area_air_controller_reads_as_its_window_does(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 35);
        Devices.clear(s);
        Player player = player(h);
        s.room(0);
        AreaAirControllerBlockEntity controller = Devices.areaAirController(s, 2, 2, 1);
        AreaAirControllerPeripheral p = s.peripheral(2, 2, 1, AreaAirControllerPeripheral.class);
        h.assertValueEqual(p.getReadings().size(), 0, "readings before its first step");
        h.assertTrue(p.getRoom() == null, "a room before its first step");
        VentBlockEntity vent = s.place(
                2,
                0,
                2,
                AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP),
                VentBlockEntity.class);
        for (int i = 0; i < 4; i++) {
            controller.buffer().set(controller.buffer().getMaxEnergyStored());
            controller.step();
            vent.step();
            s.tick();
        }
        ControllerSync window = new ControllerMenu(1, player.getInventory(), controller).sync();
        h.assertTrue(controller.lastPin() == PinOutcome.ZONE, "the room is " + controller.lastPin());

        h.assertValueEqual(p.getStatus(), Lua.word(ControllerStatus.values()[window.status()]), "its status");
        h.assertValueEqual(p.getStatus(), "safe", "its status in good air, with power");
        h.assertValueEqual(p.getEnergy(), window.energy(), "its FE");
        h.assertValueEqual(p.getEnergyCapacity(), window.capacity(), "its buffer");
        h.assertValueEqual(p.getRoom(), "zone", "its room");
        h.assertValueEqual(p.getRoom().toUpperCase(Locale.ROOT), window.pin(), "the window's room");
        h.assertValueEqual(p.getMode(), Lua.word(ControllerMode.values()[window.mode()]), "its mode");
        h.assertValueEqual(p.getPreset(), window.preset(), "its preset");
        h.assertValueEqual(p.isPresetEdited(), window.edited(), "whether its preset is edited");
        h.assertValueEqual(p.hasBreach(), window.breach(), "its breach");
        h.assertValueEqual(p.hasFire(), window.fire(), "its fire");
        h.assertTrue(p.getCentral() == null && window.central() == null, "a central controller");

        Map<String, Map<String, Object>> readings = p.getReadings();
        Map<String, Map<String, Double>> thresholds = p.getThresholds();
        h.assertValueEqual(new ArrayList<>(readings.keySet()), window.labels(), "the readings, in the window's order");
        h.assertValueEqual(new ArrayList<>(thresholds.keySet()), window.labels(), "the thresholds' readings");
        List<String> bounds = List.of("min2", "min1", "max1", "max2");
        for (int i = 0; i < window.labels().size(); i++) {
            String label = window.labels().get(i);
            h.assertValueEqual(readings.get(label).get("value"), window.values()[i], label + "'s value");
            h.assertValueEqual(readings.get(label).get("danger"), window.ratings()[i], label + "'s danger");
            for (int b = 0; b < 4; b++) {
                double bound = window.bounds()[i * 4 + b];
                Double given = thresholds.get(label).get(bounds.get(b));
                h.assertTrue(
                        bound < 0 ? given == null : given != null && given == bound,
                        label + "'s " + bounds.get(b) + " is " + given + ", the window's " + bound);
            }
        }
        s.close((Double) readings.get("pressure").get("value"), 101.325, "the room's pressure, kPa");
        s.close((Double) readings.get("temperature").get("value"), Spot.T, "the room's temperature, K");
        h.assertTrue(readings.containsKey(Spot.O2.toString()), "no oxygen reading in " + readings.keySet());

        List<Map<String, Object>> parts = p.getParts();
        h.assertValueEqual(parts.size(), window.parts().size(), "its parts");
        h.assertTrue(!parts.isEmpty(), "the vent in its room isn't a part");
        for (int i = 0; i < parts.size(); i++) {
            AreaAirControllerBlockEntity.Part part = window.parts().get(i);
            Map<String, Object> expected = Map.of(
                    "block", part.block().toString(),
                    "x", part.pos().getX(),
                    "y", part.pos().getY(),
                    "z", part.pos().getZ(),
                    "inZone", part.inZone(),
                    "linked", part.linked(),
                    "powered", part.powered());
            h.assertValueEqual(parts.get(i), expected, "part " + i);
        }
        h.assertValueEqual(parts.get(0).get("block"), "zas_atmos:vent", "the part's block");

        // With no power its status says so, as the window's does.
        controller.buffer().set(0);
        controller.step();
        s.tick();
        h.assertValueEqual(p.getStatus(), "unpowered", "its status with no power");
        h.assertValueEqual(
                new ControllerMenu(1, player.getInventory(), controller).sync().status(),
                ControllerStatus.UNPOWERED.ordinal(),
                "the window's status with no power");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * A central atmospherics controller that has claimed an area air controller: its status, power,
     * range and rows are what its window is sent, and the area air controller names it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_a_central_controller_reads_as_its_window_does(GameTestHelper h) throws LuaException {
        Spot s = new Spot(h, 36);
        Devices.clear(s);
        Player player = player(h);
        s.room(0);
        AreaAirControllerBlockEntity controller = Devices.areaAirController(s, 2, 2, 1);
        CentralControllerBlockEntity central = Devices.central(s, 8, 1, 2);
        CentralControllerPeripheral p = s.peripheral(8, 1, 2, CentralControllerPeripheral.class);
        h.assertValueEqual(p.getControllers().size(), 0, "its controllers before it has any");
        h.assertTrue(central.take(controller.getBlockPos()), "the console couldn't take the controller");
        for (int i = 0; i < 4; i++) {
            central.buffer().set(central.buffer().getMaxEnergyStored());
            central.step();
            controller.step();
            s.tick();
        }
        CentralSync window = new CentralMenu(1, player.getInventory(), central).sync();
        h.assertValueEqual(p.getStatus(), Lua.word(ControllerStatus.values()[window.status()]), "its status");
        h.assertValueEqual(p.getEnergy(), window.energy(), "its FE");
        h.assertValueEqual(p.getEnergyCapacity(), window.capacity(), "its buffer");
        h.assertValueEqual(p.getRange(), window.range(), "its range");
        h.assertValueEqual(p.getMaxRange(), window.maxRange(), "the server's range");
        List<Map<String, Object>> rows = p.getControllers();
        h.assertValueEqual(rows.size(), 1, "its controllers");
        h.assertValueEqual(window.rows().size(), 1, "the window's rows");
        CentralSync.Row row = window.rows().get(0);
        Map<String, Object> given = rows.get(0);
        h.assertValueEqual(given.get("x"), row.pos().getX(), "the row's x");
        h.assertValueEqual(given.get("y"), row.pos().getY(), "the row's y");
        h.assertValueEqual(given.get("z"), row.pos().getZ(), "the row's z");
        h.assertValueEqual(row.pos(), controller.getBlockPos(), "the row's controller");
        h.assertValueEqual(given.get("pressure"), row.pressure(), "the row's pressure");
        h.assertValueEqual(given.get("temperature"), row.temperature(), "the row's temperature");
        s.close((Double) given.get("pressure"), 101.325, "the room's pressure, kPa");
        h.assertValueEqual(given.get("danger"), row.danger(), "the row's danger");
        h.assertValueEqual(given.get("powered"), row.powered(), "whether the row has power");
        h.assertValueEqual(given.get("priority"), Lua.word(ZonePriority.values()[row.priority()]), "its priority");
        h.assertValueEqual(given.get("contact"), row.contact(), "whether it is in contact");
        h.assertValueEqual(
                s.peripheral(2, 2, 1, AreaAirControllerPeripheral.class).getCentral(),
                Lua.position(central.getBlockPos()),
                "the central controller the area air controller names");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * An airlock controller with a sensor linked and a slot armed: its state, slots, armed slot and
     * pressures are what its window shows.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_020_an_airlock_controller_reads_as_its_window_does(GameTestHelper h) throws LuaException {
        Spot s = new Spot(h, 37);
        Devices.clear(s);
        Player player = player(h);
        AirlockControllerBlockEntity controller = Devices.airlockController(s, 2, 2, 2);
        AirlockControllerPeripheral p = s.peripheral(2, 2, 2, AirlockControllerPeripheral.class);
        AirlockControllerMenu window = (AirlockControllerMenu) controller.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(p.getState(), "idle", "a new controller's state");
        h.assertTrue(p.getCycle() == null, "a new controller cycles");
        h.assertValueEqual(window.value(AirlockControllerMenu.DATA_CYCLING), 0, "the window's cycle, 0 while idle");
        h.assertValueEqual(p.isPowered(), window.value(AirlockControllerMenu.DATA_POWERED) != 0, "its power");
        h.assertValueEqual(p.getSlots().size(), 0, "a new controller's slots");
        h.assertValueEqual(p.getDoors().size(), 0, "a new controller's doors");
        h.assertTrue(p.getArmed() == null && window.armed() == null, "a new controller has a slot armed");
        h.assertTrue(
                p.getChamberPressure() == null && p.getInnerPressure() == null && p.getOuterPressure() == null,
                "a controller with no sensors reads a pressure");

        // A sensor on the same wall, linked into the armed slot as the multitool does.
        s.set(4, 2, 1, Spot.STONE);
        s.set(
                4,
                2,
                2,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH));
        controller.arm(AirlockControllerBlockEntity.Slot.CHAMBER_SENSOR);
        h.assertValueEqual(p.getArmed(), "chamber_sensor", "the armed slot");
        h.assertValueEqual(window.armed(), AirlockControllerBlockEntity.Slot.CHAMBER_SENSOR, "the window's armed slot");
        controller.toggleLink(s.level, s.abs(4, 2, 2));
        h.assertValueEqual(
                p.getSlots(), Map.of("chamber_sensor", Lua.position(s.abs(4, 2, 2))), "its slots, the sensor linked");
        h.assertTrue(window.filled(AirlockControllerBlockEntity.Slot.CHAMBER_SENSOR), "the window's slot is empty");
        h.assertValueEqual(
                window.slotPos(AirlockControllerBlockEntity.Slot.CHAMBER_SENSOR), s.abs(4, 2, 2), "the window's slot");
        controller.step();
        s.tick();
        controller.step();
        Double chamber = p.getChamberPressure();
        h.assertTrue(
                chamber == null ? Double.isNaN(controller.chamberKpa()) : chamber == controller.chamberKpa(),
                "the chamber's pressure: " + chamber + ", the controller's " + controller.chamberKpa());
        Devices.clear(s);
        h.succeed();
    }
}
