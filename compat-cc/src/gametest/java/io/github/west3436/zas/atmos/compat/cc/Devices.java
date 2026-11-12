package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
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

/** The devices the tests place, and what each is to a computer. */
final class Devices {
    /** What every device in a pipe line has. */
    private static final List<String> INLINE = List.of("isOn", "setOn", "getState", "isPowered");

    /** What every portable device has. */
    private static final List<String> PORTABLE = List.of("isOn", "setOn", "getEnergy", "getEnergyCapacity");

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
                        "getNetworkPressure",
                        "getExternalBound",
                        "setExternalBound",
                        "getInternalBound",
                        "setInternalBound",
                        "isPowered",
                        "isControlled",
                        "getController"));
        surface.put(
                "zas_scrubber",
                List.of(
                        "getMode",
                        "setMode",
                        "getState",
                        "getNetworkPressure",
                        "getScrubbed",
                        "setScrubbed",
                        "isPowered",
                        "isControlled",
                        "getController"));
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
                "zas_generator", List.of("getFuel", "getBurnTime", "getBurnTotal", "getEnergy", "getEnergyCapacity"));
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
                        "hasFire"));
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
        return surface;
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
