package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.heater.ThermoMachineBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableDevice;
import io.github.west3436.zas.atmos.portable.PortableGasDevice;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.portable.PortablePumpMath;
import org.jetbrains.annotations.Nullable;

/**
 * What every portable device's window has: its switch and its battery's charge. A computer can't
 * move its battery or its tank: those are slots, which a player fills by hand.
 */
public abstract class PortablePeripheral<T extends PortableDevice> extends DevicePeripheral<T> {
    PortablePeripheral(String type, T device) {
        super(type, device);
    }

    @LuaFunction(mainThread = true)
    public final boolean isOn() throws LuaException {
        return device().on();
    }

    @LuaFunction(mainThread = true)
    public final void setOn(boolean on) throws LuaException {
        device().setOn(on);
    }

    /** The FE in its battery; 0 with none. */
    @LuaFunction(mainThread = true)
    public final int getEnergy() throws LuaException {
        return device().energy();
    }

    /** What its battery holds when full, FE; 0 with none. */
    @LuaFunction(mainThread = true)
    public final int getEnergyCapacity() throws LuaException {
        return device().capacity();
    }

    /** A space heater or an air conditioner: a portable with a setpoint for its room's temperature. */
    public static final class Thermo extends PortablePeripheral<ThermoMachineBlockEntity> {
        Thermo(String type, ThermoMachineBlockEntity machine) {
            super(type, machine);
        }

        /** What it did at its last step. */
        @LuaFunction(mainThread = true)
        public String getState() throws LuaException {
            return Lua.word(device().status());
        }

        /** The heat it moved at its last step, W; 0 unless it is heating or cooling. */
        @LuaFunction(mainThread = true)
        public double getWatts() throws LuaException {
            ThermoMachineBlockEntity machine = device();
            return machine.status().working() ? machine.watts() : 0;
        }

        /** Its room's temperature at its last step, K; nil with no room, or while it is off. */
        @LuaFunction(mainThread = true)
        public @Nullable Double getRoomTemperature() throws LuaException {
            return Lua.orNil(device().roomTemperature());
        }

        /** The temperature it heats or cools its room to, K. */
        @LuaFunction(mainThread = true)
        public double getSetpoint() throws LuaException {
            return device().setpoint();
        }

        @LuaFunction(mainThread = true)
        public void setSetpoint(double kelvin) throws LuaException {
            ThermoMachineBlockEntity machine = device();
            machine.setSetpoint(Lua.inRange("setpoint", "K", kelvin, machine::clampSetpoint));
        }
    }

    /** A portable scrubber or pump: a portable with a store, working on a held tank or on its room. */
    public abstract static class Gas<T extends PortableGasDevice> extends PortablePeripheral<T> {
        Gas(String type, T device) {
            super(type, device);
        }

        /** What it did at its last step. */
        @LuaFunction(mainThread = true)
        public final String getState() throws LuaException {
            return Lua.word(device().status());
        }

        /** Its store's pressure, kPa. */
        @LuaFunction(mainThread = true)
        public final double getStorePressure() throws LuaException {
            return device().store().pressure();
        }

        /** Whether it works on a tank it holds, rather than on its room. */
        @LuaFunction(mainThread = true)
        public final boolean isOnTank() throws LuaException {
            return !device().tank().tank().isEmpty();
        }

        /** The pressure of the air it works on at its last step, kPa: nil with none, or while it is off. */
        @LuaFunction(mainThread = true)
        public final @Nullable Double getAirPressure() throws LuaException {
            return Lua.orNil(device().airPressure());
        }
    }

    /** A portable scrubber. */
    public static final class Scrubber extends Gas<PortableGasDevice> {
        Scrubber(PortableGasDevice scrubber) {
            super("zas_portable_scrubber", scrubber);
        }
    }

    /** A portable pump: which way it pumps, and the pressure it pumps to. */
    public static final class Pump extends Gas<PortablePumpBlockEntity> {
        private static final String RELEASE = "release";
        private static final String SIPHON = "siphon";

        Pump(PortablePumpBlockEntity pump) {
            super("zas_portable_pump", pump);
        }

        /** {@code release}, from its store to the air, or {@code siphon}, from the air to its store. */
        @LuaFunction(mainThread = true)
        public String getMode() throws LuaException {
            return device().siphon() ? SIPHON : RELEASE;
        }

        @LuaFunction(mainThread = true)
        public void setMode(String mode) throws LuaException {
            PortablePumpBlockEntity pump = device();
            if (!mode.equals(RELEASE) && !mode.equals(SIPHON)) {
                throw new LuaException("mode must be one of: " + RELEASE + ", " + SIPHON);
            }
            pump.setSiphon(mode.equals(SIPHON));
        }

        /** The pressure it pumps to, kPa: the air's when it releases, its store's when it siphons. */
        @LuaFunction(mainThread = true)
        public double getTargetPressure() throws LuaException {
            return device().target();
        }

        @LuaFunction(mainThread = true)
        public void setTargetPressure(double kPa) throws LuaException {
            device().setTarget(Lua.inRange("target pressure", "kPa", kPa, PortablePumpMath::clampTarget));
        }
    }
}
