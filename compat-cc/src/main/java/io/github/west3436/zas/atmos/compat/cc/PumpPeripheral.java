package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;

/** A pump: an inline device with an input and an output, and one setting. */
public abstract class PumpPeripheral<T extends PumpBlockEntity> extends InlinePeripheral<T> {
    PumpPeripheral(String type, T pump) {
        super(type, pump);
    }

    /** Its input network's pressure, kPa. */
    @LuaFunction(mainThread = true)
    public final double getInputPressure() throws LuaException {
        return device().portPressure(PumpBlockEntity.INPUT);
    }

    /** Its output network's pressure, kPa. */
    @LuaFunction(mainThread = true)
    public final double getOutputPressure() throws LuaException {
        return device().portPressure(PumpBlockEntity.OUTPUT);
    }

    /** A pressure pump, whose setting is the output pressure it pumps up to. */
    public static final class Pressure extends PumpPeripheral<PressurePumpBlockEntity> {
        Pressure(PressurePumpBlockEntity pump) {
            super("zas_pressure_pump", pump);
        }

        /** The output pressure it pumps up to, kPa. */
        @LuaFunction(mainThread = true)
        public double getTargetPressure() throws LuaException {
            return device().setting();
        }

        @LuaFunction(mainThread = true)
        public void setTargetPressure(double kPa) throws LuaException {
            PressurePumpBlockEntity pump = device();
            pump.set(Lua.inRange("target pressure", "kPa", kPa, pump::clamp));
        }
    }

    /** A volume pump, whose setting is how much of its input it moves each second. */
    public static final class Volume extends PumpPeripheral<VolumePumpBlockEntity> {
        Volume(VolumePumpBlockEntity pump) {
            super("zas_volume_pump", pump);
        }

        /** The volume of its input it moves, L/s. */
        @LuaFunction(mainThread = true)
        public double getRate() throws LuaException {
            return device().setting();
        }

        @LuaFunction(mainThread = true)
        public void setRate(double litresPerSecond) throws LuaException {
            VolumePumpBlockEntity pump = device();
            pump.set(Lua.inRange("rate", "L/s", litresPerSecond, pump::clamp));
        }
    }
}
