package io.github.west3436.zas.atmos.gauge;

import io.github.west3436.zas.atmos.analyzer.AnalyzerSync;
import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A gauge's work, once per atmos tick: it reads the gas of what it is on, the network's own mixture,
 * with no network walked and nothing copied, and sets its needle. The needle moves in {@linkplain
 * #STEPS steps}, and clients are sent it only when it moves a step, so a gauge whose reading holds
 * costs no traffic. A client sweeps its needle to each new step ({@link #shown}). The needle is saved,
 * so a loaded gauge shows its reading at once; the reading it shows is in its block state.
 */
public class GaugeBlockEntity extends AtmosDevice {
    /** The needle's steps from rest to the top of the arc: 2° each on a half-round dial. */
    public static final int STEPS = 90;

    private static final String NEEDLE = "Needle";

    /** How quickly a client's needle closes on its step: the fraction of the way left per second. */
    private static final double SWEEP_RATE = 6.0;

    private int needle;

    /** A client's needle as drawn, 0 to 1, NaN before its first frame. */
    private float shown = Float.NaN;

    private long shownAt;

    public GaugeBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.GAUGE.get(), pos, state);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        read(level);
    }

    /**
     * Reads now and moves the needle, telling clients if it moved a step. A gauge whose part has gone,
     * as when it was taken away without telling its neighbours, drops.
     */
    void read(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof GaugeBlock)) {
            return;
        }
        BlockPos host = GaugeBlock.host(state, worldPosition);
        if (LoadedBlocks.chunk(level, host) == null) {
            return; // what it is on is in a chunk that isn't loaded: the needle holds
        }
        AnalyzerTarget target = GaugeBlock.reads(level, worldPosition, state);
        GasStore gas = target == null ? null : target.gas(level);
        if (gas == null) {
            level.destroyBlock(worldPosition, true);
            return;
        }
        GaugeReading reading = state.getValue(GaugeReading.PROPERTY);
        double along = reading.along(reading.value(gas), target.kind() == AnalyzerSync.Kind.CANISTER);
        setNeedle(level, (int) Math.round(along * STEPS));
    }

    private void setNeedle(ServerLevel level, int step) {
        if (step == needle) {
            return;
        }
        needle = step;
        setChanged(); // saves it, and tells a comparator
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The needle's step, 0 at rest to {@link #STEPS} at the top. */
    public int needle() {
        return needle;
    }

    /** What a comparator reads: the needle's place, 0 at rest to 15 at the top. */
    public int signal() {
        return (needle * 15 + STEPS / 2) / STEPS;
    }

    /**
     * Where a client draws the needle now, 0 to 1: sweeping toward its step at {@link #SWEEP_RATE},
     * by the time since the last frame, or at it on the first.
     */
    public float shown() {
        float target = needle / (float) STEPS;
        long now = System.nanoTime();
        if (Float.isNaN(shown)) {
            shown = target;
        } else {
            double seconds = Math.min(1.0, (now - shownAt) / 1e9);
            shown += (float) ((target - shown) * (1 - Math.exp(-SWEEP_RATE * seconds)));
        }
        shownAt = now;
        return shown;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(NEEDLE, needle);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        needle = Math.max(0, Math.min(STEPS, tag.getInt(NEEDLE)));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NEEDLE, needle);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        needle = Math.max(0, Math.min(STEPS, tag.getInt(NEEDLE)));
    }

    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
