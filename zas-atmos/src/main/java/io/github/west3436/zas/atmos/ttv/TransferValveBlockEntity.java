package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.gear.TankKind;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A placed tank transfer valve's tanks, valve and timer (CONT-103, D-86). They are held as the item
 * holds them, in a TTV stack's {@link AtmosComponents#TTV_TANKS}, {@link AtmosComponents#TTV_OPEN}
 * and {@link AtmosComponents#TTV_TIMER}, so the item's rules and its screen work on both, and placing
 * the item and breaking the block carry them across unchanged.
 *
 * <p>Its tanks' gas reacts every atmos tick (CONT-100) and fails as an item's does (CONT-038): a tank
 * that leaks stays on, one that ruptures leaves its place empty, and one that explodes takes the TTV
 * with it. Its timer doesn't count down here: starting it primes the TTV where it stands (CONT-104).
 *
 * <p>After a change it saves, sets the block's {@link TransferValveBlock#TANKS} and {@link
 * TransferValveBlock#OPEN}, and sends clients the kinds of its tanks, which the block's colour handler
 * tints them by.
 */
public class TransferValveBlockEntity extends AtmosDevice {
    private static final String TANKS_KEY = "Tanks";
    private static final String OPEN_KEY = "Open";
    private static final String TIMER_KEY = "Timer";
    private static final String KINDS_KEY = "Kinds";
    private static final TankKind[] KINDS = TankKind.values();

    /** Its tanks, valve and timer, as a TTV stack's data: the stack the screen reads and changes. */
    private final ItemStack ttv = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());

    /** While one of its tanks fails: an explosion that breaks the block then has nothing to drop. */
    private boolean goingOff;

    /** On a client: its tanks' tints, ARGB, the first or lone tank first, read off the main thread. */
    private volatile int[] tints = new int[0];

    public TransferValveBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.TANK_TRANSFER_VALVE.get(), pos, state);
    }

    /** Its tanks, valve and timer, live: whoever changes them calls {@link #changed} after. */
    ItemStack stack() {
        return ttv;
    }

    /** What it holds, as a copy: a TTV stack with its tanks, valve and timer. */
    public ItemStack item() {
        return ttv.copy();
    }

    /** Whether one of its tanks is failing now. */
    boolean goingOff() {
        return goingOff;
    }

    /**
     * Fits a tank in the first free place, as using a tank on it does.
     *
     * @param tank the tank, which the caller takes away
     * @return whether it went on: not if both places are full or the valve is open
     */
    public boolean fit(ItemStack tank) {
        if (!TransferValveItem.fit(ttv, tank)) {
            return false;
        }
        changed();
        return true;
    }

    /**
     * Saves, and shows what it holds: the block's tank count and valve, and through its update packet,
     * its tanks' kinds. A block update sends both, the state first.
     */
    void changed() {
        setChanged();
        if (level instanceof ServerLevel server && !isRemoved()) {
            BlockState state = getBlockState();
            BlockState shown = TransferValveBlock.showing(state, ttv);
            if (shown != state) {
                server.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
            } else {
                server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Starts its timer where it stands (CONT-104): the block goes, and a {@link PrimedTransferValve}
     * takes its place with its tanks, valve and timer, its timer facing the same way. An open valve
     * has nothing left for a timer to do, so it refuses.
     *
     * @return the primed TTV, or null if it refused
     */
    public @Nullable PrimedTransferValve prime() {
        if (!(level instanceof ServerLevel server)
                || isRemoved()
                || TransferValveItem.valve(ttv).isOpen()) {
            return null;
        }
        ItemStack stack = ttv.copy();
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(TransferValveBlock.FACING)
                ? state.getValue(TransferValveBlock.FACING)
                : Direction.NORTH;
        BlockPos pos = worldPosition;
        server.removeBlock(pos, false);
        return PrimedTransferValve.prime(server, pos, facing, stack);
    }

    /**
     * One atmos tick of its tanks' gas, at the block's centre, as a held TTV's is at its holder. A
     * tank that explodes removes the block, unless the blast already broke it.
     */
    @Override
    protected void atmosTick(ServerLevel level) {
        TransferValve valve = TransferValveItem.valve(ttv);
        TransferValve.Step step;
        goingOff = true;
        try {
            step = valve.step(level, Vec3.atCenterOf(worldPosition), null);
        } finally {
            goingOff = false;
        }
        if (step == TransferValve.Step.DESTROYED) {
            if (!isRemoved() && level.getBlockEntity(worldPosition) == this) {
                level.removeBlock(worldPosition, false);
            }
            return;
        }
        if (step == TransferValve.Step.CHANGED) {
            TransferValveItem.save(ttv, valve);
            changed();
        } else if (TransferValveBlock.showing(getBlockState(), ttv) != getBlockState()) {
            changed(); // a command wrote its data
        }
    }

    // ---- What the item carries --------------------------------------------------------------

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        apply(input, AtmosComponents.TTV_TANKS.get());
        apply(input, AtmosComponents.TTV_OPEN.get());
        apply(input, AtmosComponents.TTV_TIMER.get());
    }

    private <T> void apply(DataComponentInput input, DataComponentType<T> type) {
        ttv.set(type, input.get(type));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        collect(components, AtmosComponents.TTV_TANKS.get());
        collect(components, AtmosComponents.TTV_OPEN.get());
        collect(components, AtmosComponents.TTV_TIMER.get());
    }

    private <T> void collect(DataComponentMap.Builder components, DataComponentType<T> type) {
        T value = ttv.get(type);
        if (value != null) {
            components.set(type, value);
        }
    }

    /** What the item carries isn't saved twice: a picked block's item has it as components. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TANKS_KEY);
        tag.remove(OPEN_KEY);
        tag.remove(TIMER_KEY);
    }

    // ---- Saving -----------------------------------------------------------------------------

    /** Each of the item's three components, as the item saves it; one the stack lacks is left out. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        write(tag, ops, TANKS_KEY, AtmosComponents.TTV_TANKS.get());
        write(tag, ops, OPEN_KEY, AtmosComponents.TTV_OPEN.get());
        write(tag, ops, TIMER_KEY, AtmosComponents.TTV_TIMER.get());
    }

    private <T> void write(CompoundTag tag, RegistryOps<Tag> ops, String key, DataComponentType<T> type) {
        T value = ttv.get(type);
        if (value != null) {
            type.codecOrThrow().encodeStart(ops, value).ifSuccess(t -> tag.put(key, t));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        read(tag, ops, TANKS_KEY, AtmosComponents.TTV_TANKS.get());
        read(tag, ops, OPEN_KEY, AtmosComponents.TTV_OPEN.get());
        read(tag, ops, TIMER_KEY, AtmosComponents.TTV_TIMER.get());
        Integer timer = ttv.get(AtmosComponents.TTV_TIMER.get());
        if (timer != null) {
            TransferValveItem.setTimer(ttv, timer);
        }
    }

    private <T> void read(CompoundTag tag, RegistryOps<Tag> ops, String key, DataComponentType<T> type) {
        T value = tag.contains(key)
                ? type.codecOrThrow().parse(ops, tag.get(key)).result().orElse(null)
                : null;
        ttv.set(type, value);
    }

    // ---- What clients are sent --------------------------------------------------------------

    /** Its tanks' kinds, the first or lone tank first: all a client needs to tint them. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        TransferValve valve = TransferValveItem.valve(ttv);
        List<ItemStack> tanks =
                valve.first().isEmpty() ? List.of(valve.second()) : List.of(valve.first(), valve.second());
        byte[] kinds = new byte[(int) tanks.stream().filter(s -> !s.isEmpty()).count()];
        int i = 0;
        for (ItemStack tank : tanks) {
            if (!tank.isEmpty()) {
                kinds[i++] = (byte) TankKind.of(tank).ordinal();
            }
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
        int[] read = new int[Math.min(kinds.length, 2)];
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
     * A tank's tint, on a client, for the block's colour handler: as on the item, tint index 1 is the
     * first tank, or a lone one, and 2 the second.
     *
     * @return its ARGB, or -1, no tint, if no tank there is known
     */
    public int tint(int tintIndex) {
        int[] now = tints;
        int place = tintIndex - 1;
        return place >= 0 && place < now.length ? now[place] : -1;
    }
}
