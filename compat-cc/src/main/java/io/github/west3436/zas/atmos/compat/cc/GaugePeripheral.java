package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gauge.GaugeBlock;
import io.github.west3436.zas.atmos.gauge.GaugeBlockEntity;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A gauge (COMPAT-024): the gas of the part it is on, as its dial reads it, and the reading its dial
 * shows, which a computer can turn as a player's use does. The values are of the network, or of the
 * canister or tank, the gauge is on: {@code nil} once that is gone.
 */
public final class GaugePeripheral extends DevicePeripheral<GaugeBlockEntity> {
    GaugePeripheral(GaugeBlockEntity gauge) {
        super("zas_gauge", gauge);
    }

    /** What it reads now, or null if its part is gone. */
    private @Nullable GasStore gas() throws LuaException {
        GaugeBlockEntity gauge = device();
        if (!(gauge.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        AnalyzerTarget target = GaugeBlock.reads(level, gauge.getBlockPos(), gauge.getBlockState());
        return target == null ? null : target.gas(level);
    }

    /** The pressure of what it is on, kPa. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getPressure() throws LuaException {
        GasStore gas = gas();
        return gas == null ? null : gas.pressure();
    }

    /** The temperature of what it is on, K; nil with no gas. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getTemperature() throws LuaException {
        GasStore gas = gas();
        return gas == null || gas.isEmpty() ? null : gas.temperature();
    }

    /** The moles of each gas in what it is on, by gas id. */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Double> getContents() throws LuaException {
        GasStore gas = gas();
        return gas == null ? null : Lua.gases(gas.moles());
    }

    /**
     * What its dial shows: {@code pressure}, {@code temperature}, or a gas's share by its name, such as
     * {@code oxygen} or {@code carbon_dioxide}.
     */
    @LuaFunction(mainThread = true)
    public String getReading() throws LuaException {
        return Lua.word(device().reading());
    }

    @LuaFunction(mainThread = true)
    public void setReading(String reading) throws LuaException {
        GaugeBlockEntity gauge = device();
        gauge.setReading(Lua.named("reading", GaugeReading.class, reading));
    }

    /** The value of its reading: kPa, K, or a share from 0 to 1. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getValue() throws LuaException {
        GaugeBlockEntity gauge = device();
        GasStore gas = gas();
        return gas == null ? null : gauge.reading().value(gas);
    }

    /** What a comparator reads of it, 0 to 15: where its needle stands. */
    @LuaFunction(mainThread = true)
    public int getSignal() throws LuaException {
        return device().signal();
    }
}
