package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The worker's decoded copies of the sections it needs (architecture §4.3): sections holding or
 * bordering zoned cells, and sections around operations waiting for classification. An entry is
 * replaced when a newer snapshot of its section arrives and dropped when nothing needs it.
 *
 * <p>Owned by one {@link DimensionEngine}, on the worker. Hash maps are used for lookups only;
 * everything that is listed is sorted first (CORE-072).
 */
public final class SectionMirror implements SectionLookup {
    private final Map<Long, SectionSnapshot> sections = new HashMap<>();
    private final Set<Long> unloadedChunks = new HashSet<>();
    private final Set<Long> unavailable = new HashSet<>();
    private final int minSectionY;
    private final int maxSectionY;

    /**
     * @param minSectionY the dimension's lowest section y
     * @param maxSectionY its highest section y
     */
    public SectionMirror(int minSectionY, int maxSectionY) {
        if (maxSectionY < minSectionY) {
            throw new IllegalArgumentException("maxSectionY " + maxSectionY + " < minSectionY " + minSectionY);
        }
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
    }

    @Override
    public SectionSnapshot section(long key) {
        return sections.get(key);
    }

    @Override
    public boolean isUnloaded(long key) {
        return !sections.containsKey(key)
                && (unavailable.contains(key) || unloadedChunks.contains(SectionKeys.chunkOf(key)));
    }

    @Override
    public int minSectionY() {
        return minSectionY;
    }

    @Override
    public int maxSectionY() {
        return maxSectionY;
    }

    /**
     * Whether a chunk the mirror holds or held sections of is unloaded: its sections are kept while
     * they hold cells of zones in memory, which are paused (GEO-030).
     */
    public boolean isChunkUnloaded(long chunk) {
        return unloadedChunks.contains(chunk);
    }

    /** Whether any chunk is known to be unloaded. */
    boolean hasUnloadedChunks() {
        return !unloadedChunks.isEmpty();
    }

    /** The number of mirrored sections. */
    public int size() {
        return sections.size();
    }

    /** Whether a section is mirrored. */
    public boolean contains(long key) {
        return sections.containsKey(key);
    }

    /** The mirrored section keys, in {@link SectionKeys#compare} order. */
    public long[] keys() {
        long[] out = new long[sections.size()];
        int i = 0;
        for (long k : sections.keySet()) {
            out[i++] = k;
        }
        sortSections(out);
        return out;
    }

    /** Sorts section keys in {@link SectionKeys#compare} order. */
    static void sortSections(long[] keys) {
        Long[] boxed = new Long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            boxed[i] = keys[i];
        }
        Arrays.sort(boxed, SectionKeys::compare);
        for (int i = 0; i < keys.length; i++) {
            keys[i] = boxed[i];
        }
    }

    // ---- Changes (the engine's geometry phase) ----------------------------------

    /** Stores a snapshot, returning the one it replaced or null. */
    SectionSnapshot put(SectionSnapshot snapshot) {
        unavailable.remove(snapshot.key());
        unloadedChunks.remove(SectionKeys.chunkOf(snapshot.key()));
        return sections.put(snapshot.key(), snapshot);
    }

    void remove(long key) {
        sections.remove(key);
    }

    void chunkLoaded(long chunk) {
        unloadedChunks.remove(chunk);
        unavailable.removeIf(key -> SectionKeys.chunkOf(key) == chunk);
    }

    /**
     * Notes an unload. Only chunks the mirror holds sections of are remembered, so exploring
     * doesn't grow the set; a fill reaching any other unloaded chunk finds it missing, asks for it,
     * and learns it is unloaded from the next batch.
     */
    void chunkUnloaded(long chunk) {
        for (long key : sections.keySet()) {
            if (SectionKeys.chunkOf(key) == chunk) {
                unloadedChunks.add(chunk);
                return;
            }
        }
    }

    void unavailable(long section) {
        if (!sections.containsKey(section)) {
            unavailable.add(section);
        }
    }
}
