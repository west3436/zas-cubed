package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.gauge.GaugeBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.ttv.TransferValveBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** The devices the tests place, and what each is to a computer. */
final class Devices {
    /** What every device in a pipe line has. */
    private static final List<String> INLINE =
            List.of("isOn", "setOn", "getState", "isPowered", "getRedstoneMode", "setRedstoneMode");

    /** What every portable device has. */
    private static final List<String> PORTABLE =
            List.of("isOn", "setOn", "getEnergy", "getEnergyCapacity", "getRedstoneMode", "setRedstoneMode");

    /**
     * Every peripheral type and its methods, written out here and not read from the adapter, so a
     * method CC: Tweaked turned down, or one renamed by accident, fails a test.
     */
    static final Map<String, List<String>> SURFACE = surface();

    private Devices() {}

    private static Map<String, List<String>> surface() {
        Map<String, List<String>> surface = new LinkedHashMap<>();
        surface.put(
                "zas_canister",
                List.of(
                        "getPressure",
                        "getAmount",
                        "getContents",
                        "isOnPort",
                        "isValveOpen",
                        "setValveOpen",
                        "getReleasePressure",
                        "setReleasePressure"));
        surface.put(
                "zas_vent",
                List.of(
                        "getMode",
                        "setMode",
                        "getState",
                        "getHeatingPower",
                        "getNetworkPressure",
                        "getExternalBound",
                        "setExternalBound",
                        "getInternalBound",
                        "setInternalBound",
                        "isPowered",
                        "isControlled",
                        "getController",
                        "getRedstoneMode",
                        "setRedstoneMode"));
        surface.put(
                "zas_scrubber",
                List.of(
                        "getMode",
                        "setMode",
                        "getState",
                        "getCoolingPower",
                        "getNetworkPressure",
                        "getScrubbed",
                        "setScrubbed",
                        "isPowered",
                        "isControlled",
                        "getController",
                        "getRedstoneMode",
                        "setRedstoneMode"));
        surface.put(
                "zas_pressure_pump",
                with(INLINE, "getInputPressure", "getOutputPressure", "getTargetPressure", "setTargetPressure"));
        surface.put("zas_volume_pump", with(INLINE, "getInputPressure", "getOutputPressure", "getRate", "setRate"));
        surface.put(
                "zas_filter",
                with(
                        INLINE,
                        "getInputPressure",
                        "getOutputPressure",
                        "getFilteredPressure",
                        "getTargetPressure",
                        "setTargetPressure",
                        "getFiltered",
                        "setFiltered"));
        surface.put(
                "zas_mixer",
                with(
                        INLINE,
                        "getInput1Pressure",
                        "getInput2Pressure",
                        "getOutputPressure",
                        "getTargetPressure",
                        "setTargetPressure",
                        "getMix",
                        "setMix"));
        surface.put(
                "zas_electrolyzer",
                with(INLINE, "getOxygenPressure", "getHydrogenPressure", "getWater", "getWaterCapacity"));
        List<String> thermo =
                with(PORTABLE, "getState", "getWatts", "getRoomTemperature", "getSetpoint", "setSetpoint");
        surface.put("zas_heater", thermo);
        surface.put("zas_air_conditioner", thermo);
        List<String> gas = with(PORTABLE, "getState", "getStorePressure", "isOnTank", "getAirPressure");
        surface.put("zas_portable_scrubber", gas);
        surface.put("zas_portable_pump", with(gas, "getMode", "setMode", "getTargetPressure", "setTargetPressure"));
        surface.put(
                "zas_generator",
                List.of(
                        "getFuel",
                        "getBurnTime",
                        "getBurnTotal",
                        "getEnergy",
                        "getEnergyCapacity",
                        "getRedstoneMode",
                        "setRedstoneMode"));
        surface.put(
                "zas_area_air_controller",
                List.of(
                        "getStatus",
                        "getEnergy",
                        "getEnergyCapacity",
                        "getRoom",
                        "getMode",
                        "setMode",
                        "getPreset",
                        "isPresetEdited",
                        "loadPreset",
                        "getReadings",
                        "getThresholds",
                        "setThreshold",
                        "getParts",
                        "getCentral",
                        "hasBreach",
                        "hasFire",
                        "getTargetTemperature",
                        "setTargetTemperature",
                        "getThermostat"));
        surface.put(
                "zas_central_atmos_controller",
                List.of(
                        "getStatus",
                        "getEnergy",
                        "getEnergyCapacity",
                        "getRange",
                        "getMaxRange",
                        "setRange",
                        "getControllers",
                        "setPriority"));
        surface.put(
                "zas_airlock_controller",
                List.of(
                        "getState",
                        "getCycle",
                        "isPowered",
                        "getChamberPressure",
                        "getInnerPressure",
                        "getOuterPressure",
                        "getSlots",
                        "getDoors",
                        "getArmed",
                        "setArmed",
                        "clear",
                        "cycle",
                        "stop"));
        surface.put(
                "zas_teg",
                List.of(
                        "isOn",
                        "setOn",
                        "getState",
                        "getPower",
                        "getHot",
                        "getCold",
                        "getEnergy",
                        "getEnergyCapacity",
                        "getRedstoneMode",
                        "setRedstoneMode"));
        surface.put(
                "zas_turbine",
                List.of(
                        "isOn",
                        "setOn",
                        "getState",
                        "getRpm",
                        "getMaxRpm",
                        "getChamber",
                        "getPower",
                        "getEnergy",
                        "getEnergyCapacity",
                        "getRedstoneMode",
                        "setRedstoneMode"));
        surface.put(
                "zas_gas_miner",
                List.of(
                        "isOn",
                        "setOn",
                        "getState",
                        "getGas",
                        "setGas",
                        "getGases",
                        "getTargetPressure",
                        "setTargetPressure",
                        "getTemperature",
                        "setTemperature",
                        "getRoomPressure",
                        "getRedstoneMode",
                        "setRedstoneMode"));
        return surface;
    }

    /**
     * Every peripheral type of a device with no window but something to read or set (COMPAT-024 to
     * COMPAT-028), and its methods, as {@link #SURFACE} has the others'. Each is placed by {@link
     * #placeReader}.
     */
    static final Map<String, List<String>> READERS = readers();

    private static Map<String, List<String>> readers() {
        Map<String, List<String>> readers = new LinkedHashMap<>();
        readers.put(
                "zas_gauge",
                List.of(
                        "getPressure",
                        "getTemperature",
                        "getContents",
                        "getReading",
                        "setReading",
                        "getValue",
                        "getSignal"));
        readers.put(
                "zas_air_sensor",
                List.of(
                        "getPressure",
                        "getTemperature",
                        "getGases",
                        "getStatus",
                        "getController",
                        "getReading",
                        "setReading",
                        "getValue",
                        "getSignal"));
        readers.put("zas_large_gas_tank", List.of("getPressure", "getTemperature", "getAmount", "getContents"));
        readers.put("zas_tank_holder", List.of("getCount", "getCapacity", "getTanks"));
        readers.put("zas_recharger", List.of("getState", "hasBattery", "getEnergy", "getEnergyCapacity", "isPowered"));
        readers.put("zas_igniter", List.of("hasSignal", "isSparking", "isPowered"));
        readers.put("zas_fire_alarm", List.of("getState", "isTriggered", "isPowered"));
        readers.put("zas_firelock", List.of("isOpen", "isAlarmed", "isClosedByAlarm", "getLockdown"));
        readers.put("zas_tank_transfer_valve", List.of("isValveOpen", "getTimer", "getTanks"));
        return readers;
    }

    /**
     * Places a device of {@link #READERS} at a point, with what it needs: the gauge on a canister to
     * its north, the air sensor and the fire alarm on stone to their north, the large tank and the
     * firelock with their upper halves above, the firelock on stone below. Nothing it needs is east,
     * west or south of the point. Returns its block entity.
     */
    static BlockEntity placeReader(Spot s, String type, int x, int y, int z) {
        return switch (type) {
            case "zas_gauge" -> {
                s.place(x, y, z - 1, AtmosBlocks.CANISTER.get().defaultBlockState(), BlockEntity.class);
                yield s.place(x, y, z, facingSouth(AtmosBlocks.GAUGE.get(), GaugeBlock.FACING), BlockEntity.class);
            }
            case "zas_air_sensor" -> {
                s.set(x, y, z - 1, Spot.STONE);
                yield s.place(
                        x, y, z, facingSouth(AtmosBlocks.AIR_SENSOR.get(), AirSensorBlock.FACING), BlockEntity.class);
            }
            case "zas_fire_alarm" -> {
                s.set(x, y, z - 1, Spot.STONE);
                yield s.place(
                        x, y, z, facingSouth(AtmosBlocks.FIRE_ALARM.get(), FireAlarmBlock.FACING), BlockEntity.class);
            }
            case "zas_large_gas_tank" -> {
                BlockState lower = AtmosBlocks.LARGE_GAS_TANK.get().defaultBlockState();
                s.set(x, y + 1, z, lower.setValue(LargeTankBlock.HALF, DoubleBlockHalf.UPPER));
                yield s.place(x, y, z, lower, BlockEntity.class);
            }
            case "zas_firelock" -> {
                BlockState lower = AtmosBlocks.FIRELOCK
                        .get()
                        .defaultBlockState()
                        .setValue(FirelockBlock.FACING, Direction.EAST)
                        .setValue(FirelockBlock.OPEN, true);
                s.set(x, y - 1, z, Spot.STONE);
                s.set(x, y, z, lower);
                s.set(x, y + 1, z, lower.setValue(FirelockBlock.HALF, DoubleBlockHalf.UPPER));
                yield s.place(x, y, z, lower, BlockEntity.class);
            }
            case "zas_tank_holder" ->
                s.place(x, y, z, AtmosBlocks.TANK_HOLDER.get().defaultBlockState(), BlockEntity.class);
            case "zas_recharger" ->
                s.place(x, y, z, AtmosBlocks.RECHARGER.get().defaultBlockState(), BlockEntity.class);
            case "zas_igniter" -> s.place(x, y, z, AtmosBlocks.IGNITER.get().defaultBlockState(), BlockEntity.class);
            case "zas_tank_transfer_valve" ->
                s.place(
                        x,
                        y,
                        z,
                        AtmosBlocks.TANK_TRANSFER_VALVE
                                .get()
                                .defaultBlockState()
                                .setValue(TransferValveBlock.FACING, Direction.EAST),
                        BlockEntity.class);
            default -> throw new GameTestAssertException("no reader " + type);
        };
    }

    private static BlockState facingSouth(Block block, DirectionProperty facing) {
        return block.defaultBlockState().setValue(facing, Direction.SOUTH);
    }

    private static List<String> with(List<String> base, String... more) {
        List<String> all = new ArrayList<>(base);
        all.addAll(List.of(more));
        return List.copyOf(all);
    }

    /**
     * Places the device of a peripheral type at a point, with what it needs to stay there: a wall
     * panel gets the stone behind it, to the north, and the console its other three blocks, to the
     * west and above. Returns its block entity.
     */
    static BlockEntity place(Spot s, String type, int x, int y, int z) {
        return switch (type) {
            case "zas_area_air_controller" -> areaAirController(s, x, y, z);
            case "zas_airlock_controller" -> airlockController(s, x, y, z);
            case "zas_central_atmos_controller" -> central(s, x, y, z);
            default -> s.place(x, y, z, block(type).defaultBlockState(), BlockEntity.class);
        };
    }

    private static Block block(String type) {
        return switch (type) {
            case "zas_canister" -> AtmosBlocks.CANISTER.get();
            case "zas_vent" -> AtmosBlocks.VENT.get();
            case "zas_scrubber" -> AtmosBlocks.SCRUBBER.get();
            case "zas_pressure_pump" -> AtmosBlocks.PRESSURE_PUMP.get();
            case "zas_volume_pump" -> AtmosBlocks.VOLUME_PUMP.get();
            case "zas_filter" -> AtmosBlocks.FILTER.get();
            case "zas_mixer" -> AtmosBlocks.MIXER.get();
            case "zas_electrolyzer" -> AtmosBlocks.ELECTROLYZER.get();
            case "zas_heater" -> AtmosBlocks.HEATER.get();
            case "zas_air_conditioner" -> AtmosBlocks.AIR_CONDITIONER.get();
            case "zas_portable_scrubber" -> AtmosBlocks.PORTABLE_SCRUBBER.get();
            case "zas_portable_pump" -> AtmosBlocks.PORTABLE_PUMP.get();
            case "zas_generator" -> AtmosBlocks.GENERATOR.get();
            case "zas_teg" -> AtmosBlocks.TEG.get();
            case "zas_turbine" -> AtmosBlocks.TURBINE.get();
            case "zas_gas_miner" -> AtmosBlocks.GAS_MINER.get();
            default -> throw new GameTestAssertException("no block for " + type);
        };
    }

    /** An area air controller facing south, on stone placed to its north. */
    static AreaAirControllerBlockEntity areaAirController(Spot s, int x, int y, int z) {
        s.set(x, y, z - 1, Spot.STONE);
        BlockState state = AtmosBlocks.AREA_AIR_CONTROLLER
                .get()
                .defaultBlockState()
                .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH);
        return s.place(x, y, z, state, AreaAirControllerBlockEntity.class);
    }

    /** An airlock controller facing south, on stone placed to its north. */
    static AirlockControllerBlockEntity airlockController(Spot s, int x, int y, int z) {
        s.set(x, y, z - 1, Spot.STONE);
        BlockState state = AtmosBlocks.AIRLOCK_CONTROLLER
                .get()
                .defaultBlockState()
                .setValue(AirlockControllerBlock.FACING, Direction.SOUTH);
        return s.place(x, y, z, state, AirlockControllerBlockEntity.class);
    }

    /** A whole console facing north, its lower left block at the point and the rest west of it and above. */
    static CentralControllerBlockEntity central(Spot s, int x, int y, int z) {
        BlockState state = AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get().defaultBlockState();
        List<BlockPos> parts = CentralControllerBlock.parts(s.abs(x, y, z), Direction.NORTH);
        for (int i = 0; i < parts.size(); i++) {
            s.level.setBlockAndUpdate(parts.get(i), CentralControllerBlock.part(state, i));
        }
        if (!(s.level.getBlockEntity(s.abs(x, y, z)) instanceof CentralControllerBlockEntity central)) {
            throw new GameTestAssertException("no console block entity");
        }
        return central;
    }

    /**
     * Clears a spot, each console first by its lower left block, which takes the rest with no drop:
     * clearing another block of it first would drop the console's item.
     */
    static void clear(Spot s) {
        for (BlockPos p : BlockPos.betweenClosed(s.abs(-8, 0, 0), s.abs(31, 6, 6))) {
            BlockState state = s.level.getBlockState(p);
            if (state.getBlock() instanceof CentralControllerBlock
                    && CentralControllerBlock.origin(state, p).equals(p)) {
                s.level.setBlockAndUpdate(p.immutable(), Spot.AIR);
            }
        }
        s.clear();
    }

    /** A computer that only records the events queued to it, for a test that needs no Lua. */
    static final class FakeComputer implements IComputerAccess {
        /** One event: its name, then its arguments. */
        final List<List<Object>> events = new CopyOnWriteArrayList<>();

        private final String name;

        FakeComputer(String name) {
            this.name = name;
        }

        @Override
        public void queueEvent(String event, Object... arguments) {
            List<Object> all = new ArrayList<>();
            all.add(event);
            all.addAll(List.of(arguments));
            events.add(all);
        }

        @Override
        public String getAttachmentName() {
            return name;
        }

        @Override
        public String mount(String desiredLocation, Mount mount, String driveName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unmount(String location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getID() {
            return 0;
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of();
        }

        @Override
        public IPeripheral getAvailablePeripheral(String name) {
            return null;
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            throw new UnsupportedOperationException();
        }
    }
}
