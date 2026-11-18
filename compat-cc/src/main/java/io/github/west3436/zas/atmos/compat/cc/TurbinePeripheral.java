package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A gas turbine (CONT-161, CONT-162): its switch, what it is doing, its shaft's speed, its chamber
 * and its FE. Its compressor is the same peripheral.
 */
public final class TurbinePeripheral extends DevicePeripheral<TurbineBlockEntity> {
    TurbinePeripheral(TurbineBlockEntity turbine) {
        super("zas_turbine", turbine);
    }

    @LuaFunction(mainThread = true)
    public boolean isOn() throws LuaException {
        return device().on();
    }

    @LuaFunction(mainThread = true)
    public void setOn(boolean on) throws LuaException {
        device().setOn(on);
    }

    /** What it did at its last step: off, no_compressor, chamber_open, idle or running. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        return Lua.word(device().status());
    }

    /** Its shaft's speed, rpm. */
    @LuaFunction(mainThread = true)
    public double getRpm() throws LuaException {
        return device().rpm();
    }

    /** Its shaft's top speed, rpm. */
    @LuaFunction(mainThread = true)
    public int getMaxRpm() throws LuaException {
        device();
        return AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_RPM);
    }

    /** Its chamber at its last step: {@code pressure} (kPa) and {@code temperature} (K), 0 while open. */
    @LuaFunction(mainThread = true)
    public Map<String, Double> getChamber() throws LuaException {
        TurbineBlockEntity turbine = device();
        Map<String, Double> table = new LinkedHashMap<>();
        table.put("pressure", turbine.chamberPressure());
        table.put("temperature", turbine.chamberTemperature());
        return table;
    }

    /** What it made over its last step, FE/t. */
    @LuaFunction(mainThread = true)
    public double getPower() throws LuaException {
        return device().powerPerTick();
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
