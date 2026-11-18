package io.github.west3436.zas.atmos.compat.cc;

import com.google.gson.JsonObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.device.Comparators;
import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.device.RedstoneControlled;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.gauge.GaugeBlockEntity;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What R2's automation gives computers (COMPAT-023 to COMPAT-028): the redstone setting on each
 * device that has one, and the peripherals of the devices with no window but something to read or
 * set: the gauge, the air sensor, the large tank, the tank holder, the recharger, the igniter, the
 * fire alarm, a firelock and a placed TTV. Most call the peripherals' methods directly, as CC:
 * Tweaked calls them on the server thread; two use real computers.
 */
@GameTestHolder(CcComputerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcAutomationTests {
    private static final String BATCH = "zas_cc_automation";
    private static final String COMPUTER_BATCH = "zas_cc_automation_computers";
    private static final SourceId SOURCE = SourceId.of("zas_atmos", "cc_gametest");

    private CcAutomationTests() {}

    /** A peripheral's read of a word, which may be a Lua error. */
    @FunctionalInterface
    private interface LuaWord {
        String get() throws LuaException;
    }

    /** A peripheral's write of a word, which may be a Lua error. */
    @FunctionalInterface
    private interface LuaSet {
        void set(String word) throws LuaException;
    }

    /**
     * Checks one device's redstone setting through its peripheral: it reads {@code ignored} at first,
     * takes each mode, and refuses a word that names none, keeping the mode it had.
     */
    private static void redstone(Spot s, String what, RedstoneControlled device, LuaWord get, LuaSet set)
            throws LuaException {
        s.h.assertValueEqual(get.get(), "ignored", what + "'s first redstone mode");
        for (RedstoneControl.Mode mode : RedstoneControl.Mode.values()) {
            set.set(mode.word());
            s.h.assertValueEqual(device.redstone().mode(), mode, what + "'s mode after setting " + mode.word());
            s.h.assertValueEqual(get.get(), mode.word(), what + "'s mode read back");
        }
        set.set("low");
        String refused = s.refused(() -> set.set("sideways"), what + ": a mode there isn't");
        s.h.assertValueEqual(refused, "redstone mode must be one of: ignored, high, low", what + "'s refusal");
        s.h.assertValueEqual(device.redstone().mode(), RedstoneControl.Mode.LOW, what + "'s mode after a refusal");
    }

    /**
     * Every device with a redstone setting reads and sets it from a computer, by the words its window
     * shows; a word that names no mode is a Lua error that changes nothing. A vent under a
     * controller's orders still takes it, as redstone only ever stops a device.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_023_a_computer_reads_and_sets_each_devices_redstone_setting(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 58);
        Devices.clear(s);
        int x = 1;
        for (String type : List.of(
                "zas_pressure_pump",
                "zas_volume_pump",
                "zas_filter",
                "zas_mixer",
                "zas_electrolyzer",
                "zas_heater",
                "zas_air_conditioner",
                "zas_portable_scrubber",
                "zas_portable_pump")) {
            RedstoneControlled device = (RedstoneControlled) Devices.place(s, type, x, 1, 1);
            IPeripheral found = s.peripheral(s.abs(x, 1, 1), Direction.NORTH);
            if (found instanceof InlinePeripheral<?> inline) {
                redstone(s, type, device, inline::getRedstoneMode, inline::setRedstoneMode);
            } else if (found instanceof PortablePeripheral<?> portable) {
                redstone(s, type, device, portable::getRedstoneMode, portable::setRedstoneMode);
            } else {
                h.fail(type + " has no redstone setting: " + found);
            }
            x += 2;
        }
        VentBlockEntity vent = (VentBlockEntity) Devices.place(s, "zas_vent", x, 1, 1);
        VentPeripheral v = s.peripheral(x, 1, 1, VentPeripheral.class);
        vent.control(s.abs(x, 1, 5), VentSettings.siphon(40));
        redstone(s, "an ordered vent", vent, v::getRedstoneMode, v::setRedstoneMode);
        h.assertTrue(vent.controlled(), "setting its redstone mode ended the controller's orders");
        x += 2;
        RedstoneControlled scrubber = (RedstoneControlled) Devices.place(s, "zas_scrubber", x, 1, 1);
        ScrubberPeripheral sp = s.peripheral(x, 1, 1, ScrubberPeripheral.class);
        redstone(s, "a scrubber", scrubber, sp::getRedstoneMode, sp::setRedstoneMode);
        x += 2;
        RedstoneControlled generator = (RedstoneControlled) Devices.place(s, "zas_generator", x, 1, 1);
        GeneratorPeripheral g = s.peripheral(x, 1, 1, GeneratorPeripheral.class);
        redstone(s, "a generator", generator, g::getRedstoneMode, g::setRedstoneMode);
        x += 2;
        RedstoneControlled teg = (RedstoneControlled) Devices.place(s, "zas_teg", x, 1, 1);
        TegPeripheral tp = s.peripheral(x, 1, 1, TegPeripheral.class);
        redstone(s, "a TEG core", teg, tp::getRedstoneMode, tp::setRedstoneMode);
        x += 2;
        RedstoneControlled turbine = (RedstoneControlled) Devices.place(s, "zas_turbine_controller", x, 1, 1);
        TurbineControllerPeripheral t = s.peripheral(x, 1, 1, TurbineControllerPeripheral.class);
        redstone(s, "a turbine controller", turbine, t::getRedstoneMode, t::setRedstoneMode);
        x += 2;
        RedstoneControlled miner = (RedstoneControlled) Devices.place(s, "zas_gas_miner", x, 1, 1);
        GasMinerPeripheral gm = s.peripheral(x, 1, 1, GasMinerPeripheral.class);
        redstone(s, "a gas miner", miner, gm::getRedstoneMode, gm::setRedstoneMode);
        Devices.clear(s);
        h.succeed();
    }

    /**
     * Each device of {@link Devices#READERS} is a peripheral from every side, of its own type and of
     * that device, and two lookups are equal. The large tank is one from either half; a firelock door
     * is one from its lower half, which works the firelock, and not its upper.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_024_every_reader_is_a_peripheral_of_its_own_type(GameTestHelper h) {
        Spot s = new Spot(h, 59);
        Devices.clear(s);
        int i = 0;
        for (String type : Devices.READERS.keySet()) {
            BlockEntity device = Devices.placeReader(s, type, 1 + 3 * i, 2, 3);
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
        BlockPos tank = s.abs(1 + 3 * List.copyOf(Devices.READERS.keySet()).indexOf("zas_large_gas_tank"), 2, 3);
        IPeripheral lower = s.peripheral(tank, Direction.EAST);
        IPeripheral upper = s.peripheral(tank.above(), Direction.EAST);
        h.assertTrue(upper instanceof LargeTankPeripheral, "a large tank's upper half is " + upper);
        h.assertTrue(upper.equals(lower), "a large tank's halves aren't one peripheral");

        // A firelock door, on stone, its upper half first: the lower would go at once with nothing above.
        BlockState door = AtmosBlocks.FIRELOCK_DOOR.get().defaultBlockState();
        s.set(28, 0, 3, Spot.STONE);
        s.set(28, 2, 3, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        s.set(28, 1, 3, door);
        h.assertTrue(
                s.level.getBlockEntity(s.abs(28, 1, 3)) instanceof FirelockDoorBlockEntity,
                "no firelock door was placed");
        IPeripheral found = s.peripheral(s.abs(28, 1, 3), Direction.WEST);
        h.assertTrue(found instanceof FirelockPeripheral<?>, "a firelock door's lower half is " + found);
        h.assertTrue(s.peripheral(s.abs(28, 2, 3), Direction.WEST) == null, "a firelock door's upper half is one");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * A gauge on a canister reads what the canister holds, and a computer turns its dial as a player's
     * use does: its value and its comparator signal follow at once. A reading there isn't is a Lua
     * error.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_024_a_gauge_reads_its_part_and_turns(GameTestHelper h) throws LuaException {
        Spot s = new Spot(h, 60);
        Devices.clear(s);
        GaugeBlockEntity gauge = (GaugeBlockEntity) Devices.placeReader(s, "zas_gauge", 2, 1, 2);
        CanisterBlockEntity canister = (CanisterBlockEntity) s.level.getBlockEntity(s.abs(2, 1, 1));
        canister.network().gas().add(new GasParcel(Map.of(Spot.O2, 30.0, Spot.N2, 10.0), Spot.T));
        gauge.step();
        GaugePeripheral p = s.peripheral(2, 1, 2, GaugePeripheral.class);
        double kpa = canister.network().gas().pressure();
        h.assertTrue(kpa > 0, "the canister holds nothing");
        s.close(p.getPressure(), kpa, "its pressure, kPa");
        s.close(p.getTemperature(), Spot.T, "its temperature, K");
        s.close(p.getContents().get(Spot.O2.toString()), 30, "its oxygen, mol");
        h.assertValueEqual(p.getReading(), "pressure", "a new gauge's reading");
        s.close(p.getValue(), kpa, "its pressure as its value");
        h.assertValueEqual(p.getSignal(), gauge.signal(), "its signal");

        p.setReading("oxygen");
        h.assertValueEqual(gauge.reading(), GaugeReading.OXYGEN, "the gauge's reading");
        h.assertValueEqual(
                gauge.getBlockState().getValue(GaugeReading.PROPERTY), GaugeReading.OXYGEN, "the dial's face");
        s.close(p.getValue(), 0.75, "its oxygen's share");
        h.assertValueEqual(p.getSignal(), 11, "its signal at three quarters, without a step");
        String refused = s.refused(() -> p.setReading("loudness"), "a reading there isn't");
        h.assertTrue(refused.startsWith("reading must be one of: pressure, temperature, oxygen"), refused);
        h.assertValueEqual(gauge.reading(), GaugeReading.OXYGEN, "the reading after a refusal");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * An air sensor that no airlock controller links reads the room in front of its wall, and a
     * computer turns what a comparator reads of it: the pressure on 0 to 2 atm, a gas's share on 0 to
     * all. A reading there isn't is a Lua error.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_025_an_unlinked_air_sensor_reads_its_room_and_turns(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 61);
        s.load();
        Devices.clear(s);
        s.room(0);
        AirSensorBlockEntity sensor = (AirSensorBlockEntity) Devices.placeReader(s, "zas_air_sensor", 2, 2, 1);
        AirSensorPeripheral p = s.peripheral(2, 2, 1, AirSensorPeripheral.class);
        h.assertTrue(p.getPressure() == null && p.getValue() == null, "a reading before its first step");
        h.assertValueEqual(p.getSignal(), 0, "its signal before its first step");
        CompletableFuture<?> added = GasMutations.add(
                s.level, s.point(2, 2, 2), new GasParcel(Map.of(Spot.N2, 80.0, Spot.O2, 20.0), Spot.T), SOURCE);
        for (int i = 0; i < 8 && !added.isDone(); i++) {
            s.tick();
        }
        h.assertTrue(added.isDone(), "the gas wasn't added within 8 atmos ticks");
        sensor.step();
        double kpa = sensor.pressure();
        h.assertTrue(kpa > 0, "the sensor read no pressure");
        s.close(p.getPressure(), kpa, "its pressure, kPa");
        s.close(p.getTemperature(), Spot.T, "its temperature, K");
        // The room's air with what was added: its oxygen's share is a little over a fifth.
        double oxygen = sensor.sample().mixture().moles(Spot.O2)
                / sensor.sample().mixture().totalMoles();
        h.assertTrue(oxygen > 0.19 && oxygen < 0.22, "the room's oxygen share: " + oxygen);
        s.close(p.getGases().get(Spot.O2.toString()), oxygen, "its oxygen's share");
        h.assertTrue(p.getController() == null, "an unlinked sensor has a controller");
        h.assertValueEqual(p.getReading(), "pressure", "a new sensor's reading");
        s.close(p.getValue(), kpa, "its pressure as its value");
        h.assertValueEqual(p.getSignal(), Comparators.dial(kpa / GaugeReading.ROOM_TOP_KPA), "its pressure's signal");

        p.setReading("oxygen");
        h.assertValueEqual(sensor.reading(), GaugeReading.OXYGEN, "the sensor's reading");
        s.close(p.getValue(), oxygen, "its oxygen's share as its value");
        h.assertValueEqual(p.getSignal(), 3, "its signal at about a fifth");
        String refused = s.refused(() -> p.setReading("loudness"), "a reading there isn't");
        h.assertTrue(refused.startsWith("reading must be one of: pressure, temperature, oxygen"), refused);
        h.assertValueEqual(sensor.reading(), GaugeReading.OXYGEN, "the reading after a refusal");
        Devices.clear(s);
        h.succeed();
    }

    /**
     * The large tank, from either half, the tank holder and the recharger read what they hold
     * (COMPAT-026); the igniter, the fire alarm and the firelock read their state (COMPAT-027); and a
     * placed TTV its tanks, valve and timer, read only (COMPAT-028).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_026_to_028_the_containers_and_safety_devices_read_what_they_hold(GameTestHelper h)
            throws LuaException {
        Spot s = new Spot(h, 62);
        Devices.clear(s);
        LargeTankBlockEntity tank = (LargeTankBlockEntity) Devices.placeReader(s, "zas_large_gas_tank", 1, 1, 2);
        tank.network().gas().add(new GasParcel(Map.of(Spot.N2, 40.0), Spot.T));
        for (LargeTankPeripheral p : List.of(
                s.peripheral(1, 1, 2, LargeTankPeripheral.class), s.peripheral(1, 2, 2, LargeTankPeripheral.class))) {
            s.close(p.getPressure(), tank.network().gas().pressure(), "the tank's pressure, kPa");
            s.close(p.getAmount(), 40, "the tank's amount, mol");
            s.close(p.getTemperature(), Spot.T, "the tank's temperature, K");
            s.close(p.getContents().get(Spot.N2.toString()), 40, "the tank's nitrogen, mol");
        }

        TankHolderBlockEntity holder = (TankHolderBlockEntity) Devices.placeReader(s, "zas_tank_holder", 4, 1, 2);
        TankHolderPeripheral hp = s.peripheral(4, 1, 2, TankHolderPeripheral.class);
        h.assertValueEqual(hp.getCount(), 0, "an empty holder's tanks");
        h.assertValueEqual(hp.getTanks().size(), 0, "an empty holder's list");
        ItemStack oxygen = new ItemStack(AtmosItems.OXYGEN_TANK.get());
        GasTankItem.setGas(oxygen, new GasParcel(Map.of(Spot.O2, 2.0), Spot.T));
        h.assertTrue(holder.put(oxygen.copy()), "the holder took no tank");
        h.assertValueEqual(hp.getCount(), 1, "the holder's tanks");
        h.assertValueEqual(hp.getCapacity(), TankHolderBlockEntity.PLACES, "its places");
        Map<String, Object> read = hp.getTanks().get(0);
        h.assertValueEqual(
                read.get("name"),
                BuiltInRegistries.ITEM.getKey(AtmosItems.OXYGEN_TANK.get()).toString(),
                "its name");
        s.close((Double) read.get("pressure"), GasTankItem.contents(oxygen).pressure(), "its tank's pressure");

        RechargerBlockEntity recharger = (RechargerBlockEntity) Devices.placeReader(s, "zas_recharger", 7, 1, 2);
        RechargerPeripheral rp = s.peripheral(7, 1, 2, RechargerPeripheral.class);
        h.assertFalse(rp.hasBattery(), "an empty recharger has a battery");
        ItemStack battery = new ItemStack(AtmosItems.BATTERY.get());
        Batteries.energy(battery).receiveEnergy(1234, false);
        recharger.battery().setStackInSlot(0, battery.copy());
        h.assertTrue(rp.hasBattery(), "the recharger has no battery");
        h.assertValueEqual(rp.getEnergy(), 1234, "its battery's FE");
        h.assertValueEqual(rp.getEnergyCapacity(), Batteries.energy(battery).getMaxEnergyStored(), "its size");
        h.assertValueEqual(rp.getState(), Lua.word(recharger.status()), "its state");

        IgniterBlockEntity igniter = (IgniterBlockEntity) Devices.placeReader(s, "zas_igniter", 10, 1, 2);
        IgniterPeripheral ip = s.peripheral(10, 1, 2, IgniterPeripheral.class);
        h.assertValueEqual(ip.hasSignal(), igniter.signal(), "the igniter's signal");
        h.assertFalse(ip.isSparking(), "an igniter with no signal sparks");

        FireAlarmBlockEntity alarm = (FireAlarmBlockEntity) Devices.placeReader(s, "zas_fire_alarm", 13, 1, 2);
        FireAlarmPeripheral ap = s.peripheral(13, 1, 2, FireAlarmPeripheral.class);
        h.assertValueEqual(ap.getState(), "unpowered", "a new fire alarm's state");
        h.assertValueEqual(ap.isTriggered(), alarm.triggered(), "whether it is triggered");

        FirelockBlockEntity firelock = (FirelockBlockEntity) Devices.placeReader(s, "zas_firelock", 16, 1, 2);
        FirelockPeripheral<?> fp = (FirelockPeripheral<?>) s.peripheral(firelock.getBlockPos(), Direction.WEST);
        h.assertTrue(fp.isOpen(), "an open firelock reads closed");
        h.assertFalse(fp.isAlarmed() || fp.isClosedByAlarm(), "a firelock with no controller is alarmed");
        h.assertTrue(fp.getLockdown() == null, "a firelock between nothing is locked down");

        TransferValveBlockEntity ttv =
                (TransferValveBlockEntity) Devices.placeReader(s, "zas_tank_transfer_valve", 19, 1, 2);
        TransferValvePeripheral tp = s.peripheral(19, 1, 2, TransferValvePeripheral.class);
        h.assertValueEqual(tp.getTanks().size(), 0, "an empty TTV's tanks");
        h.assertTrue(ttv.fit(oxygen.copy()), "the TTV took no tank");
        h.assertValueEqual(tp.getTanks().size(), 1, "the TTV's tanks");
        s.close(
                (Double) tp.getTanks().get(0).get("pressure"),
                GasTankItem.contents(oxygen).pressure(),
                "its tank");
        h.assertFalse(tp.isValveOpen(), "a TTV with one tank is open");
        h.assertValueEqual(tp.getTimer(), TransferValveItem.timer(ttv.item()), "its timer");
        Devices.clear(s);
        h.succeed();
    }

    /** Ends a test with a computer: its spot cleared and its chunks released. */
    private static void done(Spot s) {
        Devices.clear(s);
        s.forceLoad(false);
    }

    /**
     * Each reader beside a computer of its own: every type is there, with every method, so CC:
     * Tweaked took each one's arguments and results as written.
     */
    @GameTest(template = "empty", batch = COMPUTER_BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_024_a_computer_finds_every_reader_with_every_method(GameTestHelper h) {
        Spot s = new Spot(h, 63);
        s.forceLoad(true);
        Devices.clear(s);
        String program = """
                local out = {}
                for _, name in ipairs(peripheral.getNames()) do
                  local methods = peripheral.getMethods(name)
                  table.sort(methods)
                  out[peripheral.getType(name)] = methods
                end
                put("out", json(out))
                """;
        List<Computers.Computer> computers = new ArrayList<>();
        int i = 0;
        for (String type : Devices.READERS.keySet()) {
            int x = 1 + 3 * i++;
            Devices.placeReader(s, type, x, 2, 3);
            computers.add(Computers.start(s, x + 1, 2, 3, program));
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    Map<String, List<String>> found = new TreeMap<>();
                    for (Computers.Computer computer : computers) {
                        JsonObject out = computer.json("out").getAsJsonObject();
                        for (String type : out.keySet()) {
                            List<String> methods = new ArrayList<>();
                            out.get(type).getAsJsonArray().forEach(m -> methods.add(m.getAsString()));
                            found.put(type, methods);
                        }
                    }
                    Map<String, List<String>> expected = new TreeMap<>();
                    Devices.READERS.forEach((type, methods) ->
                            expected.put(type, methods.stream().sorted().toList()));
                    h.assertValueEqual(found, expected, "the peripherals the computers found");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }

    /**
     * A real computer sets a pump's redstone setting and turns a gauge and an air sensor, and is
     * refused a mode or a reading there isn't, as Lua has them.
     */
    @GameTest(template = "empty", batch = COMPUTER_BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_023_a_computer_sets_a_redstone_mode_and_turns_the_readers(GameTestHelper h) {
        Spot s = new Spot(h, 64);
        s.forceLoad(true);
        Devices.clear(s);
        // West of the computer the pump, east the sensor on its stone, above it the gauge on its canister.
        PressurePumpBlockEntity pump = (PressurePumpBlockEntity) Devices.place(s, "zas_pressure_pump", 1, 1, 2);
        AirSensorBlockEntity sensor = (AirSensorBlockEntity) Devices.placeReader(s, "zas_air_sensor", 3, 1, 2);
        GaugeBlockEntity gauge = (GaugeBlockEntity) Devices.placeReader(s, "zas_gauge", 2, 2, 2);
        Computers.Computer computer = Computers.start(s, 2, 1, 2, CcComputerTests.FIND + """
                        local p = find("zas_pressure_pump")
                        local g = find("zas_gauge")
                        local a = find("zas_air_sensor")
                        local out = { mode = p.getRedstoneMode() }
                        out.set = call(p.setRedstoneMode, "high")
                        out.wrong = call(p.setRedstoneMode, "sideways")
                        out.after = p.getRedstoneMode()
                        out.turn = call(g.setReading, "temperature")
                        out.gauge = g.getReading()
                        out.badReading = call(g.setReading, "loudness")
                        out.sense = call(a.setReading, "nitrogen")
                        out.sensor = a.getReading()
                        out.signal = a.getSignal()
                        put("out", json(out))
                        """);
        h.startSequence()
                .thenWaitUntil(() -> {
                    JsonObject out = computer.json("out").getAsJsonObject();
                    h.assertValueEqual(out.get("mode").getAsString(), "ignored", "the pump's first mode");
                    h.assertTrue(CcComputerTests.ok(out, "set"), "setting the mode was refused: " + out);
                    h.assertValueEqual(
                            CcComputerTests.error(out, "wrong"),
                            "redstone mode must be one of: ignored, high, low",
                            "a mode there isn't");
                    h.assertValueEqual(out.get("after").getAsString(), "high", "the mode read back");
                    h.assertValueEqual(pump.redstone().mode(), RedstoneControl.Mode.HIGH, "the pump's mode");
                    h.assertTrue(CcComputerTests.ok(out, "turn"), "turning the gauge was refused: " + out);
                    h.assertValueEqual(out.get("gauge").getAsString(), "temperature", "the gauge's reading");
                    h.assertValueEqual(gauge.reading(), GaugeReading.TEMPERATURE, "the gauge's dial");
                    h.assertTrue(
                            CcComputerTests.error(out, "badReading").contains("reading must be one of: pressure"),
                            "a reading there isn't: " + out.get("badReading"));
                    h.assertTrue(CcComputerTests.ok(out, "sense"), "turning the sensor was refused: " + out);
                    h.assertValueEqual(out.get("sensor").getAsString(), "nitrogen", "the sensor's reading");
                    h.assertValueEqual(sensor.reading(), GaugeReading.NITROGEN, "the sensor's");
                    h.assertValueEqual(out.get("signal").getAsInt(), sensor.signal(), "the sensor's signal");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }
}
