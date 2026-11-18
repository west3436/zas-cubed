package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;

/**
 * An igniter (COMPAT-027): whether it gets a signal, has power and is sparking. Read only: redstone
 * fires it, and a computer gives redstone with its own {@code redstone} API.
 */
public final class IgniterPeripheral extends DevicePeripheral<IgniterBlockEntity> {
    IgniterPeripheral(IgniterBlockEntity igniter) {
        super("zas_igniter", igniter);
    }

    /** Whether it gets a redstone signal. */
    @LuaFunction(mainThread = true)
    public boolean hasSignal() throws LuaException {
        return device().signal();
    }

    /** Whether it is sparking: a signal and power. */
    @LuaFunction(mainThread = true)
    public boolean isSparking() throws LuaException {
        return device().lit();
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return device().power().isPowered();
    }
}
