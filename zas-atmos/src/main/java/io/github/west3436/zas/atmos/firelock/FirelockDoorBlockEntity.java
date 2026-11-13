package io.github.west3436.zas.atmos.firelock;

import io.github.west3436.zas.atmos.airlock.AirlockFirelock;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * The block entity on each half of a door with a firelock (CONT-137). Both keep which door it was,
 * which clients draw under the firelock ({@link #DOOR}). The lower half's works the firelock, once
 * per atmos tick, as a standalone firelock's does ({@link FirelockLogic}): its alarm reads the cells
 * on the two sides of the closed door, and a controller links it at the lower half.
 */
public class FirelockDoorBlockEntity extends AtmosDevice implements FirelockLogic.Holder, Labelled {
    /** Which door to draw under the firelock, in the block's model data. */
    public static final ModelProperty<Block> DOOR = new ModelProperty<>();

    private Block door = Blocks.IRON_DOOR;

    private final FirelockLogic logic = new FirelockLogic();

    private final FirelockLogic.Door panel = new FirelockLogic.Door() {
        @Override
        public BlockPos linkPos() {
            return getBlockPos();
        }

        /** The block on the side the closed door lies against. */
        @Override
        public BlockPos front() {
            return getBlockPos().relative(facing().getOpposite());
        }

        /** The block past the door's own block, on the other side. */
        @Override
        public BlockPos back() {
            return getBlockPos().relative(facing());
        }

        @Override
        public boolean isOpen() {
            return getBlockState().getValue(FirelockDoorBlock.FIRELOCK) == AirlockFirelock.OPEN;
        }

        @Override
        public void setOpen(ServerLevel level, boolean open) {
            if (!hasFirelock() || isOpen() == open) {
                return;
            }
            FirelockDoorBlock.setFirelock(level, getBlockPos(), open ? AirlockFirelock.OPEN : AirlockFirelock.CLOSED);
            AtmosSounds.play(level, getBlockPos(), open ? AtmosSounds.FIRELOCK_OPEN : AtmosSounds.FIRELOCK_CLOSE);
            logic.changed(open);
            setChanged();
        }

        @Override
        public void setLocked(ServerLevel level, boolean locked) {
            AirlockFirelock now = getBlockState().getValue(FirelockDoorBlock.FIRELOCK);
            if (now.shut() && (now == AirlockFirelock.LOCKED) != locked) {
                FirelockDoorBlock.setFirelock(
                        level, getBlockPos(), locked ? AirlockFirelock.LOCKED : AirlockFirelock.CLOSED);
            }
        }
    };

    public FirelockDoorBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.FIRELOCK_DOOR.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(DoorBlock.FACING);
    }

    /** The door it was. */
    public Block door() {
        return door;
    }

    void setDoor(Block door) {
        this.door = door;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        if (hasFirelock()) {
            logic.step(level, panel);
            setChanged();
        }
    }

    /** Only the lower half works the firelock. */
    @Override
    public boolean hasFirelock() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof FirelockDoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public FirelockLogic firelock() {
        return logic;
    }

    @Override
    public FirelockLogic.Door firelockDoor() {
        return panel;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveDoor(tag);
        logic.save(tag);
    }

    private void saveDoor(CompoundTag tag) {
        tag.putString("Door", BuiltInRegistries.BLOCK.getKey(door).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Door"));
        Block block =
                id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        // A door from a mod that is gone shows, and drops, as an iron door.
        door = block instanceof DoorBlock && !(block instanceof FirelockDoorBlock) ? block : Blocks.IRON_DOOR;
        logic.load(tag);
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    /** Clients get which door it was and its label, and nothing else. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveDoor(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A client reads the door as well as the label, so it loads what it is sent as a whole. */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadWithComponents(tag, registries);
    }

    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        loadWithComponents(packet.getTag(), registries);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(DOOR, door).build();
    }
}
