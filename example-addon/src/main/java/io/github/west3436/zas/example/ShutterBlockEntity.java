package io.github.west3436.zas.example;

import io.github.west3436.zas.api.mutation.BlockGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a {@link ShutterBlock} is open. It's held here and not in the block state, so ZAS³ can't see
 * it change: {@link BlockGeometry#markDirty} tells it to work out the block's gas geometry again
 * (GEO-014, API-028). Without ZAS³ that call does nothing.
 */
public final class ShutterBlockEntity extends BlockEntity {
    private boolean open;

    public ShutterBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleContent.SHUTTER_ENTITY.get(), pos, state);
    }

    public boolean isOpen() {
        return open;
    }

    /** Opens or closes it. Server thread only, like {@code markDirty}. */
    public void setOpen(boolean open) {
        this.open = open;
        setChanged();
        if (level instanceof ServerLevel server) {
            BlockGeometry.markDirty(server, worldPosition);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("open", open);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("open");
    }

    // A client works out collisions too, so it needs to know.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
