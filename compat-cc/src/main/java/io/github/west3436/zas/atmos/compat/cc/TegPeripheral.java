package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.teg.TegBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thermoelectric generator's core (CONT-158): its switch, what it is doing, its two streams as its
 * window shows them, its FE, and its redstone setting (COMPAT-023).
 */
public final class TegPeripheral extends DevicePeripheral<TegBlockEntity> {
    TegPeripheral(TegBlockEntity core) {
        super("zas_teg", core);
    }

    @LuaFunction(mainThread = true)
    public boolean isOn() throws LuaException {
        return device().on();
    }

    @LuaFunction(mainThread = true)
    public void setOn(boolean on) throws LuaException {
        device().setOn(on);
    }

    /** What it did at its last step: off, no_circulators, no_flow, no_difference, full or running. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        return Lua.word(device().status());
    }

    /** What it made over its last step, FE/t. */
    @LuaFunction(mainThread = true)
    public double getPower() throws LuaException {
        return device().powerPerTick();
    }

    /** The hot stream at its last step: {@code temperatureIn}, {@code temperatureOut} (K), {@code pressureIn}, {@code pressureOut} (kPa). */
    @LuaFunction(mainThread = true)
    public Map<String, Double> getHot() throws LuaException {
        return stream(0);
    }

    /** The cold stream at its last step, as {@link #getHot}. */
    @LuaFunction(mainThread = true)
    public Map<String, Double> getCold() throws LuaException {
        return stream(1);
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

    private Map<String, Double> stream(int side) throws LuaException {
        TegBlockEntity core = device();
        Map<String, Double> table = new LinkedHashMap<>();
        table.put("temperatureIn", core.temperature(side, true));
        table.put("temperatureOut", core.temperature(side, false));
        table.put("pressureIn", core.pressure(side, true));
        table.put("pressureOut", core.pressure(side, false));
        return table;
    }

    /** Its redstone setting (COMPAT-023): {@code ignored}, {@code high} or {@code low}. */
    @LuaFunction(mainThread = true)
    public final String getRedstoneMode() throws LuaException {
        return Lua.redstoneMode(device());
    }

    @LuaFunction(mainThread = true)
    public final void setRedstoneMode(String mode) throws LuaException {
        Lua.setRedstoneMode(device(), mode);
    }
}
