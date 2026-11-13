package io.github.west3436.zas.atmos.firelock;

import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A standalone firelock's work, once per atmos tick, on its lower half (CONT-043, CONT-092,
 * CONT-093, architecture §11.8). The work itself is {@link FirelockLogic}'s, which a firelock fitted
 * to an airlock shares.
 */
public class FirelockBlockEntity extends AtmosDevice implements FirelockLogic.Holder, Labelled {
    /** Atmos ticks an opening lasts while the alarm holds, 5 s. */
    public static final int RECLOSE_STEPS = FirelockLogic.RECLOSE_STEPS;

    private final FirelockLogic logic = new FirelockLogic();

    private final FirelockLogic.Door door = new FirelockLogic.Door() {
        @Override
        public BlockPos linkPos() {
            return getBlockPos();
        }

        @Override
        public BlockPos front() {
            return FirelockBlockEntity.this.front();
        }

        @Override
        public BlockPos back() {
            return FirelockBlockEntity.this.back();
        }

        @Override
        public boolean isOpen() {
            return getBlockState().getValue(FirelockBlock.OPEN);
        }

        @Override
        public void setOpen(ServerLevel level, boolean open) {
            FirelockBlock.setOpen(level, getBlockPos(), open, null);
        }

        @Override
        public void setLocked(ServerLevel level, boolean locked) {
            FirelockBlock.setLocked(level, getBlockPos(), locked);
        }
    };

    public FirelockBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.FIRELOCK.get(), pos, state);
    }

    /** The cell in front of the panel, on the side it seals when closed. */
    public BlockPos front() {
        return getBlockPos().relative(getBlockState().getValue(FirelockBlock.FACING));
    }

    /** The cell behind the panel, whose room the firelock's own cells join. */
    public BlockPos back() {
        return getBlockPos()
                .relative(getBlockState().getValue(FirelockBlock.FACING).getOpposite());
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        if (!(getBlockState().getBlock() instanceof FirelockBlock)) {
            return;
        }
        logic.step(level, door);
        setChanged();
    }

    /**
     * Called whenever the firelock opens or closes, by anyone (see {@link FirelockLogic#changed}).
     */
    void changed(boolean open) {
        logic.changed(open);
        setChanged();
    }

    @Override
    public boolean hasFirelock() {
        return getBlockState().getBlock() instanceof FirelockBlock;
    }

    @Override
    public FirelockLogic firelock() {
        return logic;
    }

    @Override
    public FirelockLogic.Door firelockDoor() {
        return door;
    }

    // ---- For the block, the GUI-less checks and tests -----------------------------------

    /** Why it is locked down, if it is (CONT-093); empty while open. */
    public Optional<Lockdown> lockdown() {
        return logic.lockdown();
    }

    /** Whether a controller covering either side was at danger 2 at its last step. */
    public boolean alarm() {
        return logic.alarm();
    }

    /** Whether the alarm closed it, so it reopens when the alarm ends (CONT-092). */
    public boolean closedByAlarm() {
        return logic.closedByAlarm();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        logic.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        logic.load(tag);
    }
}
