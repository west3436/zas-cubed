package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;

/**
 * A recharger (COMPAT-026): its battery's charge and what it is doing. A player puts the battery in
 * and takes it out by hand.
 */
public final class RechargerPeripheral extends DevicePeripheral<RechargerBlockEntity> {
    RechargerPeripheral(RechargerBlockEntity recharger) {
        super("zas_recharger", recharger);
    }

    /** What it did at its last step: {@code no_battery}, {@code full}, {@code unpowered} or {@code charging}. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        return Lua.word(device().status());
    }

    @LuaFunction(mainThread = true)
    public boolean hasBattery() throws LuaException {
        return !device().battery().battery().isEmpty();
    }

    /** The FE in its battery; 0 with none. */
    @LuaFunction(mainThread = true)
    public int getEnergy() throws LuaException {
        return device().energy();
    }

    /** What its battery holds when full, FE; 0 with none. */
    @LuaFunction(mainThread = true)
    public int getEnergyCapacity() throws LuaException {
        return device().capacity();
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return device().power().isPowered();
    }
}
