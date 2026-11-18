package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.firelock.FirelockLogic;
import io.github.west3436.zas.atmos.firelock.Lockdown;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A firelock (COMPAT-027), standalone or fitted to a door: whether it is open, whether its alarm
 * holds, and why it is locked down. Read only: a player opens it by hand or with a crowbar, and the
 * alarm closes it.
 */
public final class FirelockPeripheral<T extends BlockEntity & FirelockLogic.Holder> extends DevicePeripheral<T> {
    FirelockPeripheral(T firelock) {
        super("zas_firelock", firelock);
    }

    @LuaFunction(mainThread = true)
    public boolean isOpen() throws LuaException {
        return device().firelockDoor().isOpen();
    }

    /** Whether a controller covering either side was at danger 2 at its last step. */
    @LuaFunction(mainThread = true)
    public boolean isAlarmed() throws LuaException {
        return device().firelock().alarm();
    }

    /** Whether the alarm closed it, so it opens again when the alarm ends. */
    @LuaFunction(mainThread = true)
    public boolean isClosedByAlarm() throws LuaException {
        return device().firelock().closedByAlarm();
    }

    /**
     * Why it is locked down, or nil: a table of its {@code reason}, {@code pressure}, {@code hot} or
     * {@code cold}, and the {@code value} that reached it, the difference in kPa or the side's K.
     */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Object> getLockdown() throws LuaException {
        Optional<Lockdown> lockdown = device().firelock().lockdown();
        if (lockdown.isEmpty()) {
            return null;
        }
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("reason", Lua.word(lockdown.get().reason()));
        table.put("value", lockdown.get().value());
        return table;
    }
}
