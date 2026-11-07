package io.github.west3436.zas.atmos.electrolyzer;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The electrolyzer's water tank (CONT-056): four buckets of water, filled by a bucket or through the
 * fluid capability on its back. It only takes water in. What the electrolyzer splits each second is a
 * small part of a millibucket, so the tank keeps its water as a fraction and shows the fluid
 * capability whole millibuckets.
 */
public final class ElectrolyzerTank implements IFluidHandler {
    private final Runnable changed;
    private double water;

    /** @param changed called after the water changes */
    ElectrolyzerTank(Runnable changed) {
        this.changed = changed;
    }

    /** The water in it, mB. */
    public double water() {
        return water;
    }

    /** Sets the water, clamped to the tank, with no call to {@code changed}: for loading. */
    void load(double mb) {
        water = Double.isFinite(mb) ? Math.max(0, Math.min(ElectrolyzerMath.TANK_MB, mb)) : 0;
    }

    /** Uses water as it splits it. */
    void use(double mb) {
        if (mb > 0) {
            water = Math.max(0, water - mb);
            if (water <= ElectrolyzerMath.EMPTY_MB) {
                water = 0;
            }
            changed.run();
        }
    }

    /** How much more it takes, in whole mB. */
    private int room() {
        return (int) Math.floor(ElectrolyzerMath.TANK_MB - water + 1e-9);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int whole = (int) Math.floor(water + 1e-9);
        return tank == 0 && whole > 0 ? new FluidStack(Fluids.WATER, whole) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? ElectrolyzerMath.TANK_MB : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && stack.is(Fluids.WATER);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.is(Fluids.WATER)) {
            return 0;
        }
        int filled = Math.min(resource.getAmount(), room());
        if (filled > 0 && action.execute()) {
            water = Math.min(ElectrolyzerMath.TANK_MB, water + filled);
            changed.run();
        }
        return Math.max(0, filled);
    }

    /** Nothing comes back out: the tank is the electrolyzer's input. */
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    /** Nothing comes back out: the tank is the electrolyzer's input. */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
