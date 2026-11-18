package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * A large gas tank (COMPAT-026), from either half: what it holds, its share of its network's gas, as
 * a canister's peripheral reads a canister's. It has no settings.
 */
public final class LargeTankPeripheral extends DevicePeripheral<LargeTankBlockEntity> {
    LargeTankPeripheral(LargeTankBlockEntity tank) {
        super("zas_large_gas_tank", tank);
    }

    /** Its pressure, kPa: its network's. */
    @LuaFunction(mainThread = true)
    public double getPressure() throws LuaException {
        return device().network().gas().pressure();
    }

    /** Its temperature, K; nil with no gas. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getTemperature() throws LuaException {
        GasParcel share = device().share();
        return share.isEmpty() ? null : share.temperature();
    }

    /** The gas it holds, mol. */
    @LuaFunction(mainThread = true)
    public double getAmount() throws LuaException {
        return device().share().totalMoles();
    }

    /** The moles of each gas it holds, by gas id. */
    @LuaFunction(mainThread = true)
    public Map<String, Double> getContents() throws LuaException {
        return Lua.gases(device().share().moles());
    }
}
