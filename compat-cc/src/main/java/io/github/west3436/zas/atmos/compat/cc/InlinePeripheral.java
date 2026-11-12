package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;

/** What every device in a pipe line's window has: its switch, what it is doing and its power. */
public abstract class InlinePeripheral<T extends InlineDeviceEntity> extends DevicePeripheral<T> {
    InlinePeripheral(String type, T device) {
        super(type, device);
    }

    @LuaFunction(mainThread = true)
    public final boolean isOn() throws LuaException {
        return device().on();
    }

    @LuaFunction(mainThread = true)
    public final void setOn(boolean on) throws LuaException {
        device().setOn(on);
    }

    /** What it did at its last step. */
    @LuaFunction(mainThread = true)
    public final String getState() throws LuaException {
        return Lua.word(device().status());
    }

    @LuaFunction(mainThread = true)
    public final boolean isPowered() throws LuaException {
        return device().power().isPowered();
    }
}
