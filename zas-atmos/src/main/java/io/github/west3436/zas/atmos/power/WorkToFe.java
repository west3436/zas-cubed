package io.github.west3436.zas.atmos.power;

import io.github.west3436.zas.atmos.device.LoadedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Work, in J, turned into FE in a store, for the TEG and the turbine (CONT-158, CONT-162). Work that
 * doesn't yet make a whole FE is kept, saved with its block, so none is lost: what went in is always
 * the FE made times {@code joules_per_fe}, plus what is kept. A maker asks {@link #room} before it
 * takes work out of its gas or shaft, so it never makes work that has nowhere to go.
 *
 * <p>It also gives the store's FE to the blocks next to it that take FE, as other mods' cables and
 * machines and an area air controller's buffer do ({@link #push}); those that pull take it through
 * the store's capability, as from the generator.
 */
public final class WorkToFe {
    private static final String CARRY = "WorkCarry";

    /** Work not yet a whole FE, in J. */
    private double carry;

    /**
     * The most work, in J, the store has room for now.
     *
     * @param store the store
     * @param joulesPerFe J per FE
     * @return at least 0
     */
    public double room(EnergyStore store, double joulesPerFe) {
        return Math.max(0, store.space() * joulesPerFe - carry);
    }

    /**
     * Turns work into FE in the store.
     *
     * @param joules the work, J, no more than {@link #room}
     * @param store the store
     * @param joulesPerFe J per FE
     * @return the FE made
     */
    public int add(double joules, EnergyStore store, double joulesPerFe) {
        if (joules > 0 && Double.isFinite(joules)) {
            carry += joules;
        }
        int fe = (int) Math.min(store.space(), Math.floor(carry / joulesPerFe));
        if (fe > 0) {
            store.fill(fe);
            carry -= fe * joulesPerFe;
        }
        return fe;
    }

    /** Work not yet a whole FE, in J. */
    public double carry() {
        return carry;
    }

    /** Saves what it keeps. */
    public void save(CompoundTag tag) {
        tag.putDouble(CARRY, carry);
    }

    /** Loads what it kept. */
    public void load(CompoundTag tag) {
        double saved = tag.getDouble(CARRY);
        carry = Double.isFinite(saved) && saved > 0 ? saved : 0;
    }

    /**
     * Gives the store's FE to each loaded neighbour that takes FE, in the order of {@link
     * Direction#values()}. Asking loads nothing.
     *
     * @param level the level
     * @param pos the store's block
     * @param store the store
     * @return the FE given
     */
    public static int push(ServerLevel level, BlockPos pos, EnergyStore store) {
        int given = 0;
        for (Direction dir : Direction.values()) {
            int have = store.getEnergyStored();
            if (have <= 0) {
                break;
            }
            IEnergyStorage sink = LoadedBlocks.capability(
                    level, Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (sink != null && sink != store && sink.canReceive()) {
                int moved = sink.receiveEnergy(have, false);
                if (moved > 0) {
                    store.take(Math.min(moved, have));
                    given += moved;
                }
            }
        }
        return given;
    }
}
