package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A fire alarm (COMPAT-027): what it shows, and whether it has power. Read only. */
public final class FireAlarmPeripheral extends DevicePeripheral<FireAlarmBlockEntity> {
    FireAlarmPeripheral(FireAlarmBlockEntity alarm) {
        super("zas_fire_alarm", alarm);
    }

    /** What it shows: {@code unpowered}, {@code normal} or {@code alarm}. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        BlockState state = device().getBlockState();
        return state.hasProperty(FireAlarmBlock.STATUS)
                ? state.getValue(FireAlarmBlock.STATUS).getSerializedName()
                : "unpowered";
    }

    /** Whether it is powered and saw a fire or heat at its last step. */
    @LuaFunction(mainThread = true)
    public boolean isTriggered() throws LuaException {
        return device().triggered();
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return device().power().isPowered();
    }
}
