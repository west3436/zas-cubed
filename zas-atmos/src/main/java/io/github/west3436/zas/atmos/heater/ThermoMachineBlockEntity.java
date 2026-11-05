package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.portable.PortableDevice;
import io.github.west3436.zas.atmos.portable.PortableMenu;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A thermomachine's block entity (CONT-019, CONT-087): a portable that holds the zone it stands in at
 * a setpoint, on the battery in its slot (D-73). The heater and the air conditioner differ only in
 * which way they move heat, their setpoint's range and their settings.
 *
 * <p>Each atmos tick, while it is on and has charge, it reads the zone at its {@link #point()} and
 * moves heat through API-013's heat mutation: what brings the zone to its setpoint, but at most the
 * configured power over the tick, and nothing once the zone has reached the setpoint or passed it.
 * With no zone there (open air, unresolved or paused) it does nothing.
 *
 * <p>It draws from its battery only while it works: its configured draw at full power, and in
 * proportion below it. With too little charge left it moves what the charge pays for, then stops.
 *
 * <p>One heat mutation is outstanding at a time, so each step reads the zone after the last one
 * landed.
 */
public abstract class ThermoMachineBlockEntity extends PortableDevice {
    private static final String SETPOINT = "Setpoint";

    private final double defaultSetpoint;
    private double setpoint;
    private ThermoStatus status = ThermoStatus.OFF;
    private double watts;
    private double room = Double.NaN;
    private CompletableFuture<HeatResult> pending;

    protected ThermoMachineBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state, double defaultSetpoint) {
        super(type, pos, state);
        this.defaultSetpoint = defaultSetpoint;
        this.setpoint = defaultSetpoint;
    }

    /** A setpoint clamped to this machine's range. */
    protected abstract double clampSetpoint(double kelvin);

    /** The most heat it moves, W. */
    protected abstract double maxWatts();

    /** What it draws from its battery at full power, FE/t. */
    protected abstract int drawPerTick();

    /**
     * The heat that brings a zone toward the setpoint this step, in J; 0 at the setpoint or past it.
     *
     * @param heatCapacity the zone's heat capacity, J/K
     * @param temperature its temperature, K
     * @param setpoint the setpoint, K
     * @param seconds the step's length, s
     * @param maxWatts the most it moves, W
     */
    protected abstract double joules(
            double heatCapacity, double temperature, double setpoint, double seconds, double maxWatts);

    /** Moves this much heat at its {@link #point()}. */
    protected abstract CompletableFuture<HeatResult> move(ServerLevel level, double joules);

    /** Its status while it moves heat. */
    protected abstract ThermoStatus working();

    /** A temperature in tenths of a kelvin, capped to fit menu data's shorts. */
    private static int tenths(double kelvin) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, Math.round(kelvin * 10)));
    }

    /** Its setpoint, K. */
    public double setpoint() {
        return setpoint;
    }

    /** Sets its setpoint, clamped to its range (CONT-019). Server side. */
    public void setSetpoint(double kelvin) {
        double next = clampSetpoint(kelvin);
        if (next != setpoint) {
            setpoint = next;
            setChanged();
        }
    }

    /** What it did at its last step. */
    public ThermoStatus status() {
        return status;
    }

    /** The heat it moved at its last step, as a power in W. */
    public double watts() {
        return watts;
    }

    /**
     * The temperature of its room, the zone at its point, as of its last step; NaN when there was
     * none or it was off. Server side.
     */
    public double roomTemperature() {
        return room;
    }

    @Override
    public boolean readyToStep() {
        return pending == null || pending.isDone();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        pending = null;
        watts = 0;
        room = Double.NaN;
        if (on()) {
            AtmosphereSample air = Atmospheres.at(level, point());
            if (air.state() == AtmosphereState.ZONED) {
                room = air.temperature();
            }
            status = step(level, air);
        } else {
            status = ThermoStatus.OFF;
            reportStopped();
        }
        if (status.working()) {
            reportRunning();
        }
        showWorking(level, status.working());
    }

    /** One step's work on its room. */
    private ThermoStatus step(ServerLevel level, AtmosphereSample air) {
        IEnergyStorage energy = battery().energy();
        if (energy == null) {
            return ThermoStatus.NO_BATTERY;
        }
        if (Double.isNaN(room)) {
            return ThermoStatus.NO_ROOM;
        }
        double power = maxWatts();
        double c = GasProperties.heatCapacity(air.mixture().moles());
        double joules = joules(c, room, setpoint, AtmosTicks.SECONDS, power);
        if (joules <= 0) {
            return ThermoStatus.AT_SETPOINT;
        }
        int cost = HeaterMath.energy(joules, AtmosTicks.SECONDS, power, drawPerTick(), AtmosTicks.PERIOD);
        if (cost > 0) {
            int paid = energy.extractEnergy(cost, false);
            if (paid <= 0) {
                return ThermoStatus.BATTERY_EMPTY;
            }
            setChanged();
            joules *= (double) paid / cost;
        }
        pending = move(level, joules);
        watts = joules / AtmosTicks.SECONDS;
        return working();
    }

    @Override
    protected int menuValue(int index) {
        return switch (index) {
            case ThermoMachineMenu.DATA_SETPOINT -> tenths(setpoint);
            case ThermoMachineMenu.DATA_ROOM -> Double.isNaN(room) ? ThermoMachineMenu.NO_ROOM : tenths(room);
            case ThermoMachineMenu.DATA_STATUS -> status.ordinal();
            case ThermoMachineMenu.DATA_WATTS -> (int) Math.round(watts);
            default -> index < PortableMenu.DATA_COUNT ? super.menuValue(index) : 0;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(SETPOINT, setpoint);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setpoint = tag.contains(SETPOINT) ? clampSetpoint(tag.getDouble(SETPOINT)) : defaultSetpoint;
    }
}
