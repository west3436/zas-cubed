package io.github.west3436.zas.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The little atmos state that belongs to a whole dimension rather than a chunk (architecture §4.5),
 * saved with the level as {@value #NAME}:
 *
 * <ul>
 *   <li>the zone-id counter, so ids saved in chunks are never issued again (CORE-026);
 *   <li>queued additions whose target chunk wasn't loaded at shutdown (DATA-011), as a level-wide
 *       {@link io.github.west3436.zas.engine.persist.ChunkRecordCodec record}.
 * </ul>
 *
 * <p>Versioned (DATA-003). Unreadable contents are dropped with a warning (DATA-004). Server
 * thread.
 */
public final class DimensionData extends SavedData {
    /** The file name, under the dimension's {@code data} folder. */
    public static final String NAME = "zas_atmos";

    private static final int VERSION = 1;
    private static final Factory<DimensionData> FACTORY = new Factory<>(DimensionData::new, DimensionData::load);

    private long nextZoneId = 1;
    private byte[] queued;

    /** The dimension's data, created empty if it has none. */
    public static DimensionData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static DimensionData load(CompoundTag tag, HolderLookup.Provider registries) {
        DimensionData d = new DimensionData();
        if (tag.getInt("version") != VERSION) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn(
                            "Unknown {} version {}; the dimension's zone counter starts again",
                            NAME,
                            tag.getInt("version"));
            return d;
        }
        d.nextZoneId = Math.max(1, tag.getLong("next_zone_id"));
        if (tag.contains("queued", Tag.TAG_BYTE_ARRAY)) {
            d.queued = tag.getByteArray("queued");
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", VERSION);
        tag.putLong("next_zone_id", nextZoneId);
        if (queued != null) {
            tag.putByteArray("queued", queued);
        }
        return tag;
    }

    public long nextZoneId() {
        return nextZoneId;
    }

    /** Raises the counter; never lowers it. */
    public void raiseNextZoneId(long next) {
        if (next > nextZoneId) {
            nextZoneId = next;
            setDirty();
        }
    }

    /** Takes the saved queued additions, or null. */
    public byte[] takeQueued() {
        byte[] q = queued;
        if (q != null) {
            queued = null;
            setDirty();
        }
        return q;
    }

    /** Saves queued additions whose chunk isn't loaded, replacing any; null for none. */
    public void putQueued(byte[] bytes) {
        queued = bytes;
        setDirty();
    }
}
