package io.github.west3436.zas.atmos.airlock;

import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.firelock.FirelockLogic;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An airlock's block entity, on its origin (CONT-133, CONT-135, CONT-137). It keeps when the
 * open airlock closes itself ({@link AirlockBlock#AUTO_CLOSE_TICKS}), and works the airlock's fitted
 * firelock, if it has one, once per atmos tick, as a standalone firelock's does ({@link
 * FirelockLogic}): its alarm reads the cells in front of the origin's leaf and behind it, and a
 * controller links it at the origin.
 */
public class AirlockBlockEntity extends AtmosDevice implements FirelockLogic.Holder {
    private final FirelockLogic logic = new FirelockLogic();

    /** The game time the open airlock closes itself at, or -1 if it isn't waiting to. */
    private long closeAt = -1;

    private final FirelockLogic.Door door = new FirelockLogic.Door() {
        @Override
        public BlockPos linkPos() {
            return getBlockPos();
        }

        @Override
        public BlockPos front() {
            return getBlockPos().relative(getBlockState().getValue(AirlockBlock.FACING));
        }

        @Override
        public BlockPos back() {
            return getBlockPos()
                    .relative(getBlockState().getValue(AirlockBlock.FACING).getOpposite());
        }

        @Override
        public boolean isOpen() {
            return getBlockState().getValue(AirlockBlock.FIRELOCK) == AirlockFirelock.OPEN;
        }

        @Override
        public void setOpen(ServerLevel level, boolean open) {
            BlockState state = getBlockState();
            if (!state.getValue(AirlockBlock.FIRELOCK).fitted() || isOpen() == open) {
                return;
            }
            AirlockBlock.setFirelock(level, getBlockPos(), open ? AirlockFirelock.OPEN : AirlockFirelock.CLOSED);
            AtmosSounds.play(level, getBlockPos(), open ? AtmosSounds.FIRELOCK_OPEN : AtmosSounds.FIRELOCK_CLOSE);
            logic.changed(open);
            setChanged();
        }

        @Override
        public void setLocked(ServerLevel level, boolean locked) {
            AirlockFirelock now = getBlockState().getValue(AirlockBlock.FIRELOCK);
            if (now.shut() && (now == AirlockFirelock.LOCKED) != locked) {
                AirlockBlock.setFirelock(
                        level, getBlockPos(), locked ? AirlockFirelock.LOCKED : AirlockFirelock.CLOSED);
            }
        }
    };

    public AirlockBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.AIRLOCK.get(), pos, state);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        if (hasFirelock()) {
            logic.step(level, door);
            setChanged();
        }
    }

    /** The game time the open airlock closes itself at, or -1 if it isn't waiting to (CONT-135). */
    public long closeAt() {
        return closeAt;
    }

    void setCloseAt(long time) {
        closeAt = time;
        setChanged();
    }

    @Override
    public boolean hasFirelock() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof AirlockBlock
                && state.getValue(AirlockBlock.FIRELOCK).fitted();
    }

    @Override
    public FirelockLogic firelock() {
        return logic;
    }

    @Override
    public FirelockLogic.Door firelockDoor() {
        return door;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        logic.save(tag);
        tag.putLong("CloseAt", closeAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        logic.load(tag);
        closeAt = tag.contains("CloseAt") ? tag.getLong("CloseAt") : -1;
    }
}
