package io.github.west3436.zas.persistence;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Where each chunk's atmosphere is stored (DATA-002, architecture §4.5): a byte-array tag, {@value
 * #TAG}, in the chunk's own data, written when the chunk is saved and read when it loads. The bytes
 * are a versioned, checksummed {@link io.github.west3436.zas.engine.persist.ChunkRecordCodec
 * record}.
 *
 * <p><b>Saving.</b> At {@link ChunkDataEvent.Save} the chunk's record is built from the dimension's
 * published zone table (through its {@link Store}), so nothing is written per tick. A chunk that
 * unloads has its record built at {@link ChunkEvent.Unload}, before the block-change tracker drops
 * its unseen changes, and the chunk is marked for saving. A chunk whose bytes no engine has taken
 * (the dimension is excluded, or its engine doesn't exist yet) writes them back unchanged.
 *
 * <p><b>Loading.</b> At {@link ChunkDataEvent.Load} the raw bytes are kept; the dimension takes
 * them when the chunk loads, or, for chunks loaded before its engine existed, when the engine is
 * created. Nothing is decoded here, so a corrupt tag can never stop a chunk from loading
 * (DATA-004); decoding happens when the dimension takes the bytes, and a failure resets the chunk
 * to unzoned with one warning naming the dimension and chunk (DATA-013).
 *
 * <p>Also tracks which chunks are loaded, for queries from any thread (API-010's unresolved
 * state). Everything else runs on the server thread.
 */
public final class AtmosPersistence {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The chunk data key. */
    public static final String TAG = "zas:atmos";

    /** What a dimension saves, for the save handlers. Server thread. */
    public interface Store {
        /** The bytes a loaded chunk saves now, or null if it saves nothing. */
        byte[] save(long chunk);

        /**
         * A chunk is about to unload: the bytes it saves, or null. Called before the block-change
         * tracker forgets its unseen changes.
         */
        byte[] unloading(long chunk);

        /** Whether the chunk must be saved at unload even with nothing to write, to clear old data. */
        boolean hadData(long chunk);
    }

    private static final Map<ServerLevel, Map<Long, byte[]>> SAVED = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<Long, byte[]>> OUTGOING = new IdentityHashMap<>();
    private static final Map<ServerLevel, Store> STORES = new IdentityHashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED = new ConcurrentHashMap<>();
    private static final List<String> WARNINGS = new CopyOnWriteArrayList<>();
    private static final List<String> DROPPED = new CopyOnWriteArrayList<>();

    private AtmosPersistence() {}

    /** Registers the chunk and level listeners on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(AtmosPersistence::onDataLoad);
        gameBus.addListener(AtmosPersistence::onDataSave);
        gameBus.addListener(EventPriority.HIGH, AtmosPersistence::onChunkLoad);
        gameBus.addListener(EventPriority.HIGH, AtmosPersistence::onChunkUnload);
        gameBus.addListener(EventPriority.LOW, AtmosPersistence::onLevelUnload);
        gameBus.addListener(AtmosPersistence::onServerStopped);
    }

    // ---- For the dimensions (server thread) ------------------------------------------

    /** Lets a dimension's records be saved; replaces any earlier store for the level. */
    public static synchronized void attach(ServerLevel level, Store store) {
        STORES.put(level, store);
    }

    /** Removes a level's store, if it is {@code store}. */
    public static synchronized void detach(ServerLevel level, Store store) {
        STORES.remove(level, store);
    }

    /** Takes the saved bytes of a chunk that loaded, or null. */
    public static synchronized byte[] take(ServerLevel level, long chunk) {
        Map<Long, byte[]> m = SAVED.get(level);
        return m == null ? null : m.remove(chunk);
    }

    /** Takes the saved bytes of every loaded chunk of a level, by chunk. */
    public static synchronized Map<Long, byte[]> takeLoaded(ServerLevel level) {
        Map<Long, byte[]> out = new HashMap<>();
        Map<Long, byte[]> m = SAVED.get(level);
        if (m != null) {
            Set<Long> loaded = loaded(level.dimension());
            m.entrySet().removeIf(e -> {
                if (loaded.contains(e.getKey())) {
                    out.put(e.getKey(), e.getValue());
                    return true;
                }
                return false;
            });
        }
        return out;
    }

    /**
     * Puts back the saved bytes of a loaded chunk, as if it had just loaded with them, for its
     * dimension to take. For GameTests of save and reload. Server thread.
     */
    public static synchronized void restore(ServerLevel level, long chunk, byte[] bytes) {
        SAVED.computeIfAbsent(level, k -> new HashMap<>()).put(chunk, bytes.clone());
    }

    /** Whether a chunk of a dimension is loaded. Any thread. */
    public static boolean isLoaded(ResourceKey<Level> dimension, long chunk) {
        return loaded(dimension).contains(chunk);
    }

    /**
     * Logs that a chunk's atmos data was corrupt and its cells are reset to unzoned (DATA-004,
     * DATA-013): one warning naming the dimension and chunk.
     */
    public static void warnCorrupt(ResourceKey<Level> dimension, long chunk, String reason) {
        String message = "The atmosphere saved in chunk [" + SectionKeys.chunkX(chunk) + ", "
                + SectionKeys.chunkZ(chunk) + "] of " + dimension.location() + " is unreadable (" + reason
                + "); its cells are reset to unzoned";
        LOGGER.warn(message);
        WARNINGS.add(message);
    }

    /** Every corruption warning logged since the server started, oldest first. For GameTests. */
    public static List<String> corruptionWarnings() {
        return List.copyOf(WARNINGS);
    }

    /**
     * Logs that gas saved in a chunk was dropped because the gas registry no longer defines it
     * (CORE-013, CORE-016). The message, from {@link
     * io.github.west3436.zas.engine.persist.ChunkRecordCodec}, names the gas, the amount, the chunk
     * and the zones that held it; the dimension is added in front.
     */
    public static void warnDroppedGas(ResourceKey<Level> dimension, String message) {
        String line = "[" + dimension.location() + "] " + message;
        LOGGER.warn(line);
        DROPPED.add(line);
    }

    /** Every dropped-gas warning logged since the server started, oldest first. For GameTests. */
    public static List<String> droppedGasWarnings() {
        return List.copyOf(DROPPED);
    }

    private static Set<Long> loaded(ResourceKey<Level> dimension) {
        return LOADED.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
    }

    // ---- Listeners ------------------------------------------------------------------------

    private static void onDataLoad(ChunkDataEvent.Load event) {
        if (event.getType() != ChunkType.LEVELCHUNK
                || !(event.getChunk() instanceof LevelChunk chunk)
                || !(chunk.getLevel() instanceof ServerLevel level)
                || !event.getData().contains(TAG)) {
            return;
        }
        // Kept raw: a tag of the wrong type is stored as nothing readable, so it fails to decode
        // and is reported when the dimension takes it.
        byte[] bytes = event.getData().contains(TAG, Tag.TAG_BYTE_ARRAY)
                ? event.getData().getByteArray(TAG)
                : new byte[0];
        synchronized (AtmosPersistence.class) {
            SAVED.computeIfAbsent(level, k -> new HashMap<>())
                    .put(chunk.getPos().toLong(), bytes);
        }
    }

    private static void onDataSave(ChunkDataEvent.Save event) {
        ChunkAccess chunk = event.getChunk();
        if (!(event.getLevel() instanceof ServerLevel level)
                || chunk.getPersistedStatus().getChunkType() != ChunkType.LEVELCHUNK) {
            return;
        }
        long key = chunk.getPos().toLong();
        boolean unloading = !isLoaded(level.dimension(), key);
        byte[] bytes;
        synchronized (AtmosPersistence.class) {
            Map<Long, byte[]> out = OUTGOING.get(level);
            bytes = out == null ? null : out.remove(key);
            Map<Long, byte[]> saved = SAVED.get(level);
            byte[] untaken = saved == null ? null : (unloading ? saved.remove(key) : saved.get(key));
            if (bytes == null && untaken != null) {
                bytes = untaken; // no engine took it: write it back unchanged
            } else if (bytes == null && !unloading) {
                Store store = STORES.get(level);
                bytes = store == null ? null : store.save(key);
            }
        }
        if (bytes != null) {
            event.getData().putByteArray(TAG, bytes);
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            loaded(level.dimension()).add(event.getChunk().getPos().toLong());
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        long key = pos.toLong();
        loaded(level.dimension()).remove(key);
        if (!level.getServer().isSameThread()) {
            return;
        }
        boolean save;
        synchronized (AtmosPersistence.class) {
            Store store = STORES.get(level);
            byte[] bytes = store == null ? null : store.unloading(key);
            Map<Long, byte[]> saved = SAVED.get(level);
            boolean untaken = saved != null && saved.containsKey(key);
            if (bytes != null) {
                OUTGOING.computeIfAbsent(level, k -> new HashMap<>()).put(key, bytes);
            }
            save = bytes != null || untaken || (store != null && store.hadData(key));
        }
        if (save) {
            event.getChunk().setUnsaved(true);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            synchronized (AtmosPersistence.class) {
                SAVED.remove(level);
                OUTGOING.remove(level);
                STORES.remove(level);
            }
            LOADED.remove(level.dimension());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        synchronized (AtmosPersistence.class) {
            SAVED.clear();
            OUTGOING.clear();
            STORES.clear();
        }
        LOADED.clear();
        WARNINGS.clear();
        DROPPED.clear();
    }
}
