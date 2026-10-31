package io.github.west3436.zas.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * The two staleness counters of each loaded chunk in one dimension (architecture §3.3).
 *
 * <ul>
 *   <li><b>Lifecycle generation</b> changes when the chunk unloads. Results that span the chunk
 *       are rejected if it differs from the value they were computed against.
 *   <li><b>Block generation</b> changes on every block write in the chunk, whether or not the
 *       write changes geometry. World-write intents aimed at the chunk are dropped if it differs.
 * </ul>
 *
 * <p>Generations are opaque stamps, compared only for equality. Every stamp comes from one
 * counter that never goes back, so a value never repeats: an unloaded chunk reads as {@link
 * #UNLOADED}, and when it loads again it gets stamps it has never had. Entries are removed on
 * unload, so the map only holds loaded chunks.
 *
 * <p>Not thread-safe: the platform owns one per dimension on the server thread.
 */
public final class ChunkGenerations {
    /** Both generations of a chunk that isn't tracked: not loaded, or never seen. */
    public static final long UNLOADED = 0;

    private static final int LIFECYCLE = 0;
    private static final int BLOCK = 1;

    private final Map<Long, long[]> chunks = new HashMap<>();
    private long stamp;
    private long lastKey;
    private long[] lastEntry;

    /** Starts tracking a chunk that has loaded. Does nothing if it is already tracked. */
    public void load(long chunk) {
        entry(chunk);
    }

    /** Stops tracking a chunk that has unloaded; both its generations read {@link #UNLOADED}. */
    public void unload(long chunk) {
        chunks.remove(chunk);
        if (lastEntry != null && lastKey == chunk) {
            lastEntry = null;
        }
    }

    /**
     * Records a block write in {@code chunk}, starting to track it if needed.
     *
     * @return the new block generation
     */
    public long bumpBlock(long chunk) {
        long[] e = entry(chunk);
        e[BLOCK] = ++stamp;
        return e[BLOCK];
    }

    /** The lifecycle generation of {@code chunk}, or {@link #UNLOADED}. */
    public long lifecycle(long chunk) {
        long[] e = chunks.get(chunk);
        return e != null ? e[LIFECYCLE] : UNLOADED;
    }

    /** The block generation of {@code chunk}, or {@link #UNLOADED}. */
    public long block(long chunk) {
        long[] e = chunks.get(chunk);
        return e != null ? e[BLOCK] : UNLOADED;
    }

    /** Whether {@code chunk} is tracked. */
    public boolean isTracked(long chunk) {
        return chunks.containsKey(chunk);
    }

    /** The number of tracked chunks. */
    public int size() {
        return chunks.size();
    }

    private long[] entry(long chunk) {
        long[] e = lastEntry;
        if (e != null && lastKey == chunk) {
            return e;
        }
        e = chunks.get(chunk);
        if (e == null) {
            long first = ++stamp;
            e = new long[] {first, first};
            chunks.put(chunk, e);
        }
        lastKey = chunk;
        lastEntry = e;
        return e;
    }
}
