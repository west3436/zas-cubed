package io.github.west3436.zas.atmos.pump;

import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of a pump: an inline device with an input and an output, and one setting, that
 * moves gas from the input's network to the output's. The gas mixes in conserving thermal energy
 * (CONT-008), and a pump adds no heat of its own.
 */
public abstract class PumpBlockEntity extends InlineDeviceEntity {
    /** The input's port: at the back. */
    public static final int INPUT = 0;

    /** The output's port: at the front, the way its arrows point. */
    public static final int OUTPUT = 1;

    private static final String SETTING = "Setting";

    private final double max;
    private final double[] steps;
    private double setting;

    /**
     * @param initial its setting when new
     * @param max the highest it can be set to; the lowest is 0
     * @param bigStep the GUI's larger step
     * @param smallStep the GUI's smaller step
     */
    protected PumpBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state,
            double initial,
            double max,
            double bigStep,
            double smallStep) {
        super(type, pos, state, 2);
        this.setting = initial;
        this.max = max;
        this.steps = new double[] {-bigStep, -smallStep, smallStep, bigStep};
    }

    /**
     * The moles to move in this atmos tick.
     *
     * @param in the input
     * @param out the output
     * @return the moles, at least 0
     */
    protected abstract double moles(PumpFlow.Side in, PumpFlow.Side out);

    /** The input's port. */
    public PipePort input() {
        return port(INPUT);
    }

    /** The output's port. */
    public PipePort output() {
        return port(OUTPUT);
    }

    /** Its setting: a pressure pump's target, in kPa. */
    public double setting() {
        return setting;
    }

    /** Sets its setting, clamped to what it allows. Server side. */
    public void set(double value) {
        double next = Mth.clamp(value, 0, max);
        if (Double.isFinite(next) && next != setting) {
            setting = next;
            setChanged();
        }
    }

    /** Applies one of the GUI's steps, from the largest down to the largest up. */
    void step(int index) {
        set(setting + steps[index]);
    }

    @Override
    protected InlineStatus work(ServerLevel level) {
        PipeNetwork in = input().network();
        PipeNetwork out = output().network();
        if (in == out || !in.valid()) {
            return InlineStatus.SAME_NETWORK;
        }
        GasStore from = in.gas();
        PumpFlow.Side input = PumpFlow.Side.of(from);
        double n = moles(input, PumpFlow.Side.of(out.gas()));
        if (n < PipeBlockEntity.MIN_FLOW) {
            // Too little gas for even the rate to reach the least flow counts as none.
            return PumpFlow.molesInVolume(PumpFlow.MAX_RATE, input) < PipeBlockEntity.MIN_FLOW
                    ? InlineStatus.INPUT_EMPTY
                    : InlineStatus.TARGET_REACHED;
        }
        out.gas().add(from.takeFraction(n / input.moles()));
        return InlineStatus.MOVING;
    }

    @Override
    protected int dataCount() {
        return PumpMenu.DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == PumpMenu.DATA_SETTING ? (int) Math.round(setting) : super.dataValue(index);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(SETTING, setting);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(SETTING)) {
            set(tag.getDouble(SETTING));
        }
    }
}
