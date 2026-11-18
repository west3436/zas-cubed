package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * A placed tank transfer valve (COMPAT-028): its tanks, its valve and its timer. Read only: a
 * computer can't open its valve or start its timer, so a TTV goes off only by a player's hand, or by
 * what is fitted to it for that.
 */
public final class TransferValvePeripheral extends DevicePeripheral<TransferValveBlockEntity> {
    TransferValvePeripheral(TransferValveBlockEntity ttv) {
        super("zas_tank_transfer_valve", ttv);
    }

    @LuaFunction(mainThread = true)
    public boolean isValveOpen() throws LuaException {
        return TransferValveItem.valve(device().item()).isOpen();
    }

    /** What its timer is set to, s. */
    @LuaFunction(mainThread = true)
    public int getTimer() throws LuaException {
        return TransferValveItem.timer(device().item());
    }

    /** Its tanks, the first place first, as {@link Lua#tank} reads one; an empty place is left out. */
    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getTanks() throws LuaException {
        TransferValve valve = TransferValveItem.valve(device().item());
        List<Map<String, Object>> tanks = new ArrayList<>();
        for (ItemStack tank : List.of(valve.first(), valve.second())) {
            if (!tank.isEmpty()) {
                tanks.add(Lua.tank(tank));
            }
        }
        return tanks;
    }
}
