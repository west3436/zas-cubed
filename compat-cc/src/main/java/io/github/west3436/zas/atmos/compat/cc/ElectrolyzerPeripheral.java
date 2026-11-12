package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMath;

/** An electrolyzer: its switch and state, its water, and the pressure at each of its two outlets. */
public final class ElectrolyzerPeripheral extends InlinePeripheral<ElectrolyzerBlockEntity> {
    ElectrolyzerPeripheral(ElectrolyzerBlockEntity electrolyzer) {
        super("zas_electrolyzer", electrolyzer);
    }

    /** The pressure of the network at its oxygen outlet, kPa. */
    @LuaFunction(mainThread = true)
    public double getOxygenPressure() throws LuaException {
        return device().portPressure(ElectrolyzerBlockEntity.OXYGEN);
    }

    /** The pressure of the network at its hydrogen outlet, kPa. */
    @LuaFunction(mainThread = true)
    public double getHydrogenPressure() throws LuaException {
        return device().portPressure(ElectrolyzerBlockEntity.HYDROGEN);
    }

    /** The water in its tank, mB, rounded up as its window shows it. */
    @LuaFunction(mainThread = true)
    public int getWater() throws LuaException {
        return device().shownWater();
    }

    /** How much water its tank holds, mB. */
    @LuaFunction(mainThread = true)
    public int getWaterCapacity() throws LuaException {
        device();
        return ElectrolyzerMath.TANK_MB;
    }
}
