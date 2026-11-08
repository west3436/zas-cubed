package io.github.west3436.zas.atmos.holder;

import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.TankKind;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A tank holder's tanks (CONT-149 to CONT-151): ten places, which take one personal tank each and
 * nothing else. Tanks go into the first free place and come out last in, first out (CONT-150), so
 * they always fill the first places, as the block's model for each count shows them (CONT-151).
 *
 * <p>The tanks are stored as in a chest. Nothing ticks them here, so their gas doesn't react, leak or
 * fail, and each comes back with the item data it went in with (CONT-149, DATA-016).
 *
 * <p>After a change it saves, sets the block's {@link TankHolderBlock#TANKS} and sends clients each
 * tank's {@link TankKind}, which the block's colour handler tints that tank by. It has no window, no
 * item handler for other blocks and no comparator output.
 */
public class TankHolderBlockEntity extends BlockEntity {
    /** How many tanks it holds. */
    public static final int PLACES = 10;

    private static final String TANKS_KEY = "Tanks";
    private static final String KINDS_KEY = "Kinds";
    private static final TankKind[] KINDS = TankKind.values();

    private final Rack tanks = new Rack(this::settle);

    /** On a client: each tank's tint, ARGB, by place, which the colour handler reads off the main thread. */
    private volatile int[] tints = new int[0];

    public TankHolderBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.TANK_HOLDER.get(), pos, state);
    }

    /** Whether a stack can go in: a personal tank (CONT-030). */
    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof GasTankItem;
    }

    /** Its places. */
    public Rack tanks() {
        return tanks;
    }

    /** How many tanks it holds. */
    public int count() {
        return tanks.count();
    }

    /**
     * Puts one tank from a stack into the first free place.
     *
     * @return whether it went in; the stack is one tank smaller if so
     */
    public boolean put(ItemStack stack) {
        if (!accepts(stack)) {
            return false;
        }
        for (int i = 0; i < PLACES; i++) {
            if (tanks.getStackInSlot(i).isEmpty()) {
                tanks.setStackInSlot(i, stack.split(1));
                return true;
            }
        }
        return false;
    }

    /**
     * Takes out the last tank put in.
     *
     * @return the tank, or an empty stack if it holds none
     */
    public ItemStack takeLast() {
        int count = count();
        if (count == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack tank = tanks.getStackInSlot(count - 1);
        tanks.setStackInSlot(count - 1, ItemStack.EMPTY);
        return tank;
    }

    /**
     * Closes any gap, saves, and shows the tanks: the block's count and, through its update packet,
     * their kinds. A block update sends both, the count first.
     */
    void settle() {
        tanks.compact();
        setChanged();
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            int count = count();
            if (state.hasProperty(TankHolderBlock.TANKS) && state.getValue(TankHolderBlock.TANKS) != count) {
                server.setBlock(worldPosition, state.setValue(TankHolderBlock.TANKS, count), Block.UPDATE_CLIENTS);
            } else {
                server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Whether the block shows another count than it holds, as after a command wrote its tanks. */
    private boolean showsWrongCount() {
        BlockState state = getBlockState();
        return state.hasProperty(TankHolderBlock.TANKS) && state.getValue(TankHolderBlock.TANKS) != count();
    }

    /** A block whose count is wrong settles at its next tick, as a block can't be set while its chunk loads. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && showsWrongCount()) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    // ---- Saving -----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceData.writeVersion(tag);
        tag.put(TANKS_KEY, tanks.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        DeviceData.migrate(tag);
        tanks.deserializeNBT(registries, tag.getCompound(TANKS_KEY));
        tanks.compact();
        if (level instanceof ServerLevel server && showsWrongCount()) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    // ---- What clients are sent --------------------------------------------------------------

    /** Each tank's kind, by place: all a client needs to tint them. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        int count = count();
        byte[] kinds = new byte[count];
        for (int i = 0; i < count; i++) {
            kinds[i] = (byte) TankKind.of(tanks.getStackInSlot(i)).ordinal();
        }
        tag.putByteArray(KINDS_KEY, kinds);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readKinds(tag);
    }

    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readKinds(packet.getTag());
    }

    /** Takes the kinds a server sent, and has the block drawn again with them. */
    private void readKinds(CompoundTag tag) {
        byte[] kinds = tag.getByteArray(KINDS_KEY);
        int[] read = new int[Math.min(kinds.length, PLACES)];
        for (int i = 0; i < read.length; i++) {
            int kind = kinds[i];
            read[i] = 0xFF000000 | (kind >= 0 && kind < KINDS.length ? KINDS[kind] : TankKind.OTHER).tint();
        }
        tints = read;
        if (level != null && level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    /**
     * The tint of the tank in a place, on a client, for the block's colour handler.
     *
     * @param place 0 to 9
     * @return its ARGB, or -1, no tint, if no tank there is known
     */
    public int tint(int place) {
        int[] now = tints;
        return place >= 0 && place < now.length ? now[place] : -1;
    }

    /** Ten places that take one personal tank each and nothing else. */
    public static final class Rack extends ItemStackHandler {
        private final Runnable changed;

        /** @param changed called after each change made through the handler */
        public Rack(Runnable changed) {
            super(PLACES);
            this.changed = changed;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        /** A save always has ten places, whatever size it names. */
        @Override
        public void setSize(int size) {
            super.setSize(PLACES);
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed.run();
        }

        /** How many tanks it holds. */
        public int count() {
            int n = 0;
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    n++;
                }
            }
            return n;
        }

        /**
         * Moves the tanks down into the first places, keeping their order, without a change call: a save
         * or a command can leave a gap, which play never does.
         */
        void compact() {
            int to = 0;
            for (int from = 0; from < stacks.size(); from++) {
                ItemStack stack = stacks.get(from);
                if (stack.isEmpty()) {
                    continue;
                }
                if (from != to) {
                    stacks.set(to, stack);
                    stacks.set(from, ItemStack.EMPTY);
                }
                to++;
            }
        }
    }
}
