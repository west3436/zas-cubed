package io.github.west3436.zas.atmos.inline;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of a {@link SidePortBlock}: an inline device with three ports and a target of
 * 0–4,500 kPa, one atmosphere when new.
 */
public abstract class SidePortEntity extends InlineDeviceEntity {
    /** The port at the back. */
    public static final int BACK = 0;

    /** The port at the front, the way its arrows point. */
    public static final int FRONT = 1;

    /** The port on its side. */
    public static final int SIDE = 2;

    /** A new device's target, kPa. */
    public static final double DEFAULT_TARGET = 101.325;

    private static final String TARGET = "Target";
    private static final double[] STEPS = {-100, -10, 10, 100};

    private double target = DEFAULT_TARGET;

    protected SidePortEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 3);
    }

    /** Its target, kPa. */
    public double target() {
        return target;
    }

    /** Sets its target, clamped to 0–4,500 kPa. Server side. */
    public void setTarget(double kpa) {
        double next = PumpFlow.clampTarget(kpa);
        if (next != target) {
            target = next;
            setChanged();
        }
    }

    /** Applies one of the GUI's steps, from the largest down to the largest up. */
    void step(int index) {
        setTarget(target + STEPS[index]);
    }

    /**
     * The back, front and side ports' networks, valid. Building one can dissolve another that it
     * reaches, so it asks twice: the second answers are the networks all three settled in.
     *
     * @return the networks, by port
     */
    protected final PipeNetwork[] networks() {
        port(BACK).network();
        port(FRONT).network();
        port(SIDE).network();
        return new PipeNetwork[] {
            port(BACK).network(), port(FRONT).network(), port(SIDE).network()
        };
    }

    /** Whether pipes join all three of its ports into one network, so it has nothing to do. */
    protected static boolean oneNetwork(PipeNetwork[] networks) {
        return networks[BACK] == networks[FRONT] && networks[BACK] == networks[SIDE];
    }

    /** Whether an input holds too little for even a step's rate to reach the least flow, which counts as none. */
    protected static boolean runsDry(GasStore in) {
        return PumpFlow.molesInVolume(PumpFlow.MAX_RATE * AtmosTicks.SECONDS, PumpFlow.Side.of(in))
                < PipeBlockEntity.MIN_FLOW;
    }

    @Override
    protected int dataCount() {
        return SidePortMenu.DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case SidePortMenu.DATA_TARGET -> (int) Math.round(target);
            case SidePortMenu.DATA_MIRRORED -> getBlockState().getValue(SidePortBlock.MIRRORED) ? 1 : 0;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(TARGET, target);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TARGET)) {
            target = PumpFlow.clampTarget(tag.getDouble(TARGET));
        }
    }
}
