package io.github.west3436.zas.persistence;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
 *       {@link io.github.west3436.zas.engine.persist.ChunkRecordCodec record};
 *   <li>the owners whose pins were taken off (API-017) and who haven't pinned since: a zone stored
 *       in chunks keeps the pins it was saved with, so when it loads these owners' pins are taken
 *       off it. The list is optional in the file, so older saves read with none;
 *   <li>the {@linkplain DimensionPause pause} of the dimension's simulation, if it has one
 *       (OPS-005). Also optional in the file, so older saves read as running.
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
    private final TreeSet<String> released = new TreeSet<>();
    private final Set<String> releasedView = Collections.unmodifiableSet(released);
    private DimensionPause pause;

    /** The dimension's data, created empty if it has none. */
    public static DimensionData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /**
     * Replaces the dimension's data with what it would read back from its file, as a restart does.
     * For tests, which can't restart the server.
     */
    public static void reloadForTest(ServerLevel level) {
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag written = of(level).save(new CompoundTag(), registries);
        level.getDataStorage().set(NAME, load(written, registries));
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
        ListTag owners = tag.getList("released_pins", Tag.TAG_STRING);
        for (int i = 0; i < owners.size(); i++) {
            d.released.add(owners.getString(i));
        }
        if (tag.contains("pause", Tag.TAG_COMPOUND)) {
            CompoundTag pause = tag.getCompound("pause");
            d.pause = new DimensionPause(pause.getString("by"), pause.getString("reason"));
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
        if (!released.isEmpty()) {
            ListTag owners = new ListTag();
            for (String owner : released) {
                owners.add(StringTag.valueOf(owner));
            }
            tag.put("released_pins", owners);
        }
        if (pause != null) {
            CompoundTag p = new CompoundTag();
            p.putString("by", pause.by());
            p.putString("reason", pause.reason());
            tag.put("pause", p);
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

    /**
     * The owners whose pins were taken off and who haven't pinned since (API-017), sorted and
     * unmodifiable: their pins come off every zone loaded from a chunk.
     */
    public Set<String> releasedPins() {
        return releasedView;
    }

    /** Records that an owner's pins were taken off (API-017). */
    public void releasePins(String owner) {
        if (released.add(owner)) {
            setDirty();
        }
    }

    /** Records that an owner pins again, so its saved pins load as they are (API-017). */
    public void keepPins(String owner) {
        if (released.remove(owner)) {
            setDirty();
        }
    }

    /** Saves queued additions whose chunk isn't loaded, replacing any; null for none. */
    public void putQueued(byte[] bytes) {
        queued = bytes;
        setDirty();
    }

    /** The pause of the dimension's simulation, or null while it runs (OPS-005). */
    public DimensionPause pause() {
        return pause;
    }

    /** Sets the pause of the dimension's simulation; null for none (OPS-005). */
    public void setPause(DimensionPause pause) {
        this.pause = pause;
        setDirty();
    }
}
