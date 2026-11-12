package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A generator: what it burns, how long that lasts and the FE it has stored. It has no settings: its
 * window's only control is the fuel slot, which a player fills by hand.
 */
public final class GeneratorPeripheral extends DevicePeripheral<GeneratorBlockEntity> {
    private static final double TICKS_PER_SECOND = 20;

    GeneratorPeripheral(GeneratorBlockEntity generator) {
        super("zas_generator", generator);
    }

    /** The fuel in its slot: a table of its item's id as {@code name} and its {@code count}, or nil. */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Object> getFuel() throws LuaException {
        ItemStack fuel = device().fuel().getStackInSlot(0);
        if (fuel.isEmpty()) {
            return null;
        }
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString());
        table.put("count", fuel.getCount());
        return table;
    }

    /** How long the piece of fuel it is burning still lasts, s; 0 while it burns nothing. */
    @LuaFunction(mainThread = true)
    public double getBurnTime() throws LuaException {
        return device().burnLeft() / TICKS_PER_SECOND;
    }

    /** How long the piece of fuel it is burning lasts in all, s. */
    @LuaFunction(mainThread = true)
    public double getBurnTotal() throws LuaException {
        return device().burnTotal() / TICKS_PER_SECOND;
    }

    /** The FE it has stored. */
    @LuaFunction(mainThread = true)
    public int getEnergy() throws LuaException {
        return device().energy().getEnergyStored();
    }

    /** The FE it can store. */
    @LuaFunction(mainThread = true)
    public int getEnergyCapacity() throws LuaException {
        return device().energy().getMaxEnergyStored();
    }
}
