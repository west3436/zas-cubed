package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * The readout's server part: what the device looked at reads now, as a few bytes in Jade's answer
 * to the client. Jade calls {@link #appendServerData} on the server thread, and a reading is a
 * handful of field reads, so it costs the same whatever the size of the network or the room.
 */
enum ReadoutData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    /** The readout's id, which Jade's settings list it under. */
    static final ResourceLocation UID = ZasAtmosMod.id("readout");

    /** The reading's key in Jade's data for the block. */
    static final String KEY = "ZasReadout";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        Reading reading = DeviceReadings.of(accessor.getBlockEntity());
        if (reading != null) {
            data.putByteArray(KEY, ReadingCodec.encode(reading));
        }
    }

    /**
     * Called on the client, each tick the player looks at one of the content mod's block entities.
     * A device with nothing to read asks the server nothing, and its tooltip doesn't wait for an
     * answer.
     */
    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return DeviceReadings.reads(accessor.getBlockEntity());
    }
}
