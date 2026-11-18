package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * A tank holder (COMPAT-026): how many tanks it holds and what is in each, first place first. A
 * computer can't take a tank out or put one in: a player does that by hand.
 */
public final class TankHolderPeripheral extends DevicePeripheral<TankHolderBlockEntity> {
    TankHolderPeripheral(TankHolderBlockEntity holder) {
        super("zas_tank_holder", holder);
    }

    /** How many tanks it holds. */
    @LuaFunction(mainThread = true)
    public int getCount() throws LuaException {
        return device().count();
    }

    /** How many it can hold. */
    @LuaFunction(mainThread = true)
    public int getCapacity() throws LuaException {
        device();
        return TankHolderBlockEntity.PLACES;
    }

    /** Each tank it holds, first place first, as {@link Lua#tank} reads one. */
    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getTanks() throws LuaException {
        TankHolderBlockEntity.Rack rack = device().tanks();
        List<Map<String, Object>> tanks = new ArrayList<>();
        for (int i = 0; i < rack.getSlots(); i++) {
            ItemStack tank = rack.getStackInSlot(i);
            if (!tank.isEmpty()) {
                tanks.add(Lua.tank(tank));
            }
        }
        return tanks;
    }
}
