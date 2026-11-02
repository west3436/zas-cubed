package io.github.west3436.zas.atmos.power;

import java.util.function.IntSupplier;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * An FE store with a configured capacity, exposed through NeoForge's FE capability (D-25). Its own
 * block fills or drains it through {@link #fill} and {@link #take}; other blocks see only the side
 * it allows: the controller's buffer takes FE in (CONT-062), and the generator's store gives FE
 * out (CONT-061).
 */
public final class EnergyStore implements IEnergyStorage {
    private final IntSupplier capacity;
    private final boolean receives;
    private final boolean extracts;
    private final Runnable changed;
    private int energy;

    /**
     * @param capacity the capacity in FE, read each time, so a config change takes effect
     * @param receives whether other blocks may put FE in
     * @param extracts whether other blocks may take FE out
     * @param changed called after the store changes, to mark its block entity dirty
     */
    public EnergyStore(IntSupplier capacity, boolean receives, boolean extracts, Runnable changed) {
        this.capacity = capacity;
        this.receives = receives;
        this.extracts = extracts;
        this.changed = changed;
    }

    /** Puts FE in from the block itself; returns what fit. */
    public int fill(int amount) {
        int moved = Math.max(0, Math.min(amount, space()));
        if (moved > 0) {
            energy += moved;
            changed.run();
        }
        return moved;
    }

    /** Takes FE out for the block itself; returns false, taking nothing, if there isn't enough. */
    public boolean take(long amount) {
        if (amount <= 0) {
            return true;
        }
        if (amount > energy) {
            return false;
        }
        energy -= (int) amount;
        changed.run();
        return true;
    }

    /** The room left, in FE. */
    public int space() {
        return Math.max(0, getMaxEnergyStored() - energy);
    }

    /** Sets the stored FE, clamped to the capacity: for loading and tests. */
    public void set(int amount) {
        energy = Math.max(0, Math.min(amount, getMaxEnergyStored()));
        changed.run();
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!receives || toReceive <= 0) {
            return 0;
        }
        int moved = Math.min(toReceive, space());
        if (!simulate && moved > 0) {
            energy += moved;
            changed.run();
        }
        return moved;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!extracts || toExtract <= 0) {
            return 0;
        }
        int moved = Math.min(toExtract, energy);
        if (!simulate && moved > 0) {
            energy -= moved;
            changed.run();
        }
        return moved;
    }

    @Override
    public int getEnergyStored() {
        return Math.min(energy, getMaxEnergyStored());
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(0, capacity.getAsInt());
    }

    @Override
    public boolean canExtract() {
        return extracts;
    }

    @Override
    public boolean canReceive() {
        return receives;
    }
}
