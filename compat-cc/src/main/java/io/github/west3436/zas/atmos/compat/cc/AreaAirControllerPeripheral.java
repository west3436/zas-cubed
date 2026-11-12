package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.Thresholds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * An area air controller: what its window's four tabs show, its mode, its preset and its
 * thresholds. A computer attached to it also gets its zone's events ({@link ZoneEvents},
 * COMPAT-022).
 *
 * <p>A reading is named as the controller names it: {@code pressure}, {@code temperature}, a gas's
 * id, or {@code others} for the gases the preset doesn't list. A bound is {@code min2}, {@code
 * min1}, {@code max1} or {@code max2}.
 */
public final class AreaAirControllerPeripheral extends DevicePeripheral<AreaAirControllerBlockEntity> {
    /** The bounds' names, in {@link Thresholds#bound}'s order. */
    private static final List<String> BOUNDS = List.of("min2", "min1", "max1", "max2");

    AreaAirControllerPeripheral(AreaAirControllerBlockEntity controller) {
        super("zas_area_air_controller", controller);
    }

    @Override
    public void attach(IComputerAccess computer) {
        ZoneEvents.attach(held(), computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        ZoneEvents.detach(held(), computer);
    }

    /** {@code unpowered}, {@code safe}, {@code danger1} (a warning) or {@code danger2}. */
    @LuaFunction(mainThread = true)
    public String getStatus() throws LuaException {
        return Lua.word(device().status());
    }

    /** The FE in its buffer. */
    @LuaFunction(mainThread = true)
    public int getEnergy() throws LuaException {
        return device().buffer().getEnergyStored();
    }

    /** The FE its buffer holds when full. */
    @LuaFunction(mainThread = true)
    public int getEnergyCapacity() throws LuaException {
        return device().buffer().getMaxEnergyStored();
    }

    /**
     * What its room is: {@code zone} if it is sealed, {@code open} if it isn't, {@code unresolved}
     * while it reaches an unloaded chunk, or nil before the controller has looked.
     */
    @LuaFunction(mainThread = true)
    public @Nullable String getRoom() throws LuaException {
        PinOutcome room = device().lastPin();
        return room == null ? null : Lua.word(room);
    }

    /** {@code scrubbing}, {@code replacement}, {@code panic}, {@code siphon}, {@code cycle}, {@code fill} or {@code off}. */
    @LuaFunction(mainThread = true)
    public String getMode() throws LuaException {
        return Lua.word(device().mode());
    }

    @LuaFunction(mainThread = true)
    public void setMode(String mode) throws LuaException {
        device().setMode(Lua.named("mode", ControllerMode.class, mode));
    }

    /** The preset its thresholds started from: {@code human} or {@code vacuum}. */
    @LuaFunction(mainThread = true)
    public String getPreset() throws LuaException {
        return device().preset().name();
    }

    /** Whether a threshold has been changed since that preset was loaded. */
    @LuaFunction(mainThread = true)
    public boolean isPresetEdited() throws LuaException {
        return device().preset().edited();
    }

    /** Loads a shipped preset, discarding every edit. */
    @LuaFunction(mainThread = true)
    public void loadPreset(String name) throws LuaException {
        AreaAirControllerBlockEntity controller = device();
        List<String> names = new ArrayList<>();
        for (ControllerPreset preset : ControllerPreset.SHIPPED) {
            if (preset.name().equals(name)) {
                controller.setPreset(preset);
                return;
            }
            names.add(preset.name());
        }
        throw new LuaException("preset must be one of: " + String.join(", ", names));
    }

    /**
     * Its readings at its last step, by name: each a table of its {@code value}, in kPa or K, and
     * its {@code danger}, 0 to 2. Empty until it has read its room.
     */
    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Object>> getReadings() throws LuaException {
        AreaAirControllerBlockEntity controller = device();
        ControllerPreset preset = controller.preset();
        double[] values = controller.values();
        int[] ratings = controller.ratings();
        Map<String, Map<String, Object>> table = new LinkedHashMap<>();
        for (int i = 0; i < preset.readings() && i < values.length && i < ratings.length; i++) {
            Map<String, Object> reading = new LinkedHashMap<>();
            reading.put("value", values[i]);
            reading.put("danger", ratings[i]);
            table.put(preset.label(i), reading);
        }
        return table;
    }

    /** Each reading's bounds, by the reading's name and then the bound's. A bound that is off is left out. */
    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Double>> getThresholds() throws LuaException {
        ControllerPreset preset = device().preset();
        Map<String, Map<String, Double>> table = new LinkedHashMap<>();
        for (int i = 0; i < preset.readings(); i++) {
            Thresholds thresholds = preset.thresholds(i);
            Map<String, Double> bounds = new LinkedHashMap<>();
            for (int b = 0; b < BOUNDS.size(); b++) {
                if (thresholds.bound(b) != Thresholds.OFF) {
                    bounds.put(BOUNDS.get(b), thresholds.bound(b));
                }
            }
            table.put(preset.label(i), bounds);
        }
        return table;
    }

    /**
     * Sets one bound of a reading, or turns it off with no value. The preset keeps a reading's bounds
     * in order, so a value past a neighbouring bound is refused.
     */
    @LuaFunction(mainThread = true)
    public void setThreshold(String reading, String bound, Optional<Double> value) throws LuaException {
        AreaAirControllerBlockEntity controller = device();
        ControllerPreset preset = controller.preset();
        int r = preset.indexOf(reading);
        if (r < 0) {
            throw new LuaException("the controller has no reading " + reading);
        }
        int b = BOUNDS.indexOf(bound);
        if (b < 0) {
            throw new LuaException("bound must be one of: " + String.join(", ", BOUNDS));
        }
        if (value.isEmpty()) {
            controller.setPreset(preset.with(r, preset.thresholds(r).with(b, Thresholds.OFF)));
            return;
        }
        // The preset's own rule says how far the bound can go: what it keeps of a value far below
        // and of one far above.
        double low = preset.withBound(r, b, -Double.MAX_VALUE).thresholds(r).bound(b);
        double high = preset.withBound(r, b, Double.MAX_VALUE).thresholds(r).bound(b);
        double given = value.get();
        if (Double.isNaN(given) || given < low || given > high) {
            throw new LuaException(bound + " must be " + Lua.number(low) + " to " + Lua.number(high));
        }
        controller.setPreset(preset.withBound(r, b, given));
    }

    /**
     * Its parts at its last step: each a table of its {@code block}'s id, its {@code x}, {@code y}
     * and {@code z}, whether it is {@code inZone}, whether it is {@code linked}, whether it is
     * {@code powered}, and the {@code label} a player gave it, left out if it has none.
     */
    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getParts() throws LuaException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AreaAirControllerBlockEntity.Part part : device().parts()) {
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("block", part.block().toString());
            table.put("x", part.pos().getX());
            table.put("y", part.pos().getY());
            table.put("z", part.pos().getZ());
            table.put("inZone", part.inZone());
            table.put("linked", part.linked());
            table.put("powered", part.powered());
            if (!part.label().isEmpty()) {
                table.put("label", part.label());
            }
            list.add(table);
        }
        return list;
    }

    /** Where the central atmospherics controller that has claimed it is, or nil. */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Integer> getCentral() throws LuaException {
        AreaAirControllerBlockEntity controller = device();
        return controller.getLevel() instanceof ServerLevel level
                ? Lua.position(CentralClaims.of(level)
                        .centralOf(controller.getBlockPos())
                        .orElse(null))
                : null;
    }

    /** Whether its last reading was a breach. */
    @LuaFunction(mainThread = true)
    public boolean hasBreach() throws LuaException {
        return device().breach();
    }

    /** Whether a fire alarm it covers was triggered at its last reading. */
    @LuaFunction(mainThread = true)
    public boolean hasFire() throws LuaException {
        return device().fire();
    }
}
