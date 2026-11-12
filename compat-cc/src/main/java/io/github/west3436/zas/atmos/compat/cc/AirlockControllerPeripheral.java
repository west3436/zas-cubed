package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity.Slot;
import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.airlock.AirlockCycle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * An airlock controller: whether it is cycling and toward which door, the pressure at its three
 * sensors, its five slots and the armed one, and its doors. A cycle is asked for through the
 * controller's own request, which refuses one it can't start as it does the window's.
 *
 * <p>A side is {@code inner} or {@code outer}. A slot is {@code inner_door}, {@code outer_door},
 * {@code inner_sensor}, {@code outer_sensor} or {@code chamber_sensor}.
 */
public final class AirlockControllerPeripheral extends DevicePeripheral<AirlockControllerBlockEntity> {
    /** The sides' names, by {@link AirlockCycle#INNER} and {@link AirlockCycle#OUTER}. */
    private static final List<String> SIDES = List.of("inner", "outer");

    AirlockControllerPeripheral(AirlockControllerBlockEntity controller) {
        super("zas_airlock_controller", controller);
    }

    /** {@code idle}, or what its vents are doing in a cycle: {@code siphoning}, {@code blowing}, or {@code cycling} for neither. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        AirlockControllerBlockEntity controller = device();
        if (controller.cycling() < 0) {
            return "idle";
        }
        AirlockCycle.Step step = controller.cycleStep();
        return step == AirlockCycle.Step.SIPHON ? "siphoning" : step == AirlockCycle.Step.BLOW ? "blowing" : "cycling";
    }

    /** The side whose door it is cycling toward, or nil while idle. */
    @LuaFunction(mainThread = true)
    public @Nullable String getCycle() throws LuaException {
        int side = device().cycling();
        return side < 0 ? null : SIDES.get(side);
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return device().power().isPowered();
    }

    /** The chamber's pressure at its sensor, kPa, or nil with no reading. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getChamberPressure() throws LuaException {
        return Lua.orNil(device().chamberKpa());
    }

    /** The pressure past the inner door at its sensor, kPa, or nil with no reading. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getInnerPressure() throws LuaException {
        return Lua.orNil(device().beyondKpa(AirlockCycle.INNER));
    }

    /** The pressure past the outer door at its sensor, kPa, or nil with no reading. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getOuterPressure() throws LuaException {
        return Lua.orNil(device().beyondKpa(AirlockCycle.OUTER));
    }

    /** Where the airlock or sensor in each filled slot is, by the slot's name. */
    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Integer>> getSlots() throws LuaException {
        AirlockControllerBlockEntity controller = device();
        Map<String, Map<String, Integer>> table = new LinkedHashMap<>();
        for (Slot slot : Slot.values()) {
            if (controller.slot(slot) != null) {
                table.put(slot.key(), Lua.position(controller.slot(slot)));
            }
        }
        return table;
    }

    /** Each side's door, if it has one: whether it is {@code open} and whether it is {@code bolted}. */
    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Boolean>> getDoors() throws LuaException {
        AirlockControllerBlockEntity controller = device();
        Map<String, Map<String, Boolean>> table = new LinkedHashMap<>();
        for (int side = 0; side < SIDES.size(); side++) {
            if (controller.slot(Slot.door(side)) == null) {
                continue;
            }
            int look = AirlockControllerMenu.door(controller, side);
            Map<String, Boolean> door = new LinkedHashMap<>();
            door.put("open", (look & 1) != 0);
            door.put("bolted", (look & 2) != 0);
            table.put(SIDES.get(side), door);
        }
        return table;
    }

    /** The slot the next airlock or sensor linked with the multitool fills, or nil. */
    @LuaFunction(mainThread = true)
    public @Nullable String getArmed() throws LuaException {
        Slot armed = device().armed();
        return armed == null ? null : armed.key();
    }

    /** Arms a slot, or with no slot disarms the armed one. */
    @LuaFunction(mainThread = true)
    public void setArmed(Optional<String> slot) throws LuaException {
        AirlockControllerBlockEntity controller = device();
        Slot wanted = slot.isPresent() ? slot(slot.get()) : null;
        Slot armed = controller.armed();
        if (wanted != armed) {
            // The window's button arms a slot, and disarms the armed one.
            controller.arm(wanted != null ? wanted : armed);
        }
    }

    /**
     * Empties a slot, as the window's ✕ does.
     *
     * @return false if it was empty already
     */
    @LuaFunction(mainThread = true)
    public boolean clear(String slot) throws LuaException {
        return device().clear(slot(slot));
    }

    /** Starts a cycle toward a side's door. */
    @LuaFunction(mainThread = true)
    public void cycle(String side) throws LuaException {
        AirlockControllerBlockEntity controller = device();
        int toward = SIDES.indexOf(side);
        if (toward < 0) {
            throw new LuaException("side must be one of: " + String.join(", ", SIDES));
        }
        if (!controller.requestCycle(toward)) {
            // The controller refused it. Which of its reasons it was is only said here.
            throw new LuaException(
                    controller.cycling() >= 0
                            ? "it is cycling already"
                            : !controller.power().isPowered()
                                    ? "it has no power"
                                    : "it lacks a door or a sensor for that cycle");
        }
    }

    /**
     * Stops a cycle.
     *
     * @return false if it wasn't cycling
     */
    @LuaFunction(mainThread = true)
    public boolean stop() throws LuaException {
        return device().stop();
    }

    private static Slot slot(String name) throws LuaException {
        List<String> names = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            if (slot.key().equals(name)) {
                return slot;
            }
            names.add(slot.key());
        }
        throw new LuaException("slot must be one of: " + String.join(", ", names));
    }
}
