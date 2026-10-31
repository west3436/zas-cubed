package io.github.west3436.zas.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * The coalescing set of block positions changed since the last drain, for one dimension
 * (architecture §4.2, GEO-012).
 *
 * <p>Each dirty section holds a 4096-bit set (64 longs), allocated when its first position is
 * marked. Marking is constant work with no sorting, and marking a position again costs nothing
 * more, so any number of changes to a position between two drains comes out as one dirty bit.
 * Draining sorts the sections in {@link SectionKeys#compare} order, so the result never depends on the
 * order of changes.
 *
 * <p>Not thread-safe: the platform owns one per dimension on the server thread.
 */
public final class DirtySections {
    private static final int WORDS = 64;

    private final Map<Long, long[]> sections = new HashMap<>();
    // A one-entry cache: bursts of changes (fills, explosions) mostly hit one section in a row.
    private long lastKey;
    private long[] lastBits;

    /**
     * Marks block {@code (x, y, z)} dirty.
     *
     * @return whether it wasn't dirty already
     */
    public boolean mark(int x, int y, int z) {
        long key = SectionKeys.sectionOfBlock(x, y, z);
        long[] bits = lastBits;
        if (bits == null || lastKey != key) {
            bits = sections.computeIfAbsent(key, k -> new long[WORDS]);
            lastKey = key;
            lastBits = bits;
        }
        int index = SectionKeys.index(x & 15, y & 15, z & 15);
        long mask = 1L << index;
        long word = bits[index >>> 6];
        bits[index >>> 6] = word | mask;
        return (word & mask) == 0;
    }

    /** Whether block {@code (x, y, z)} is dirty. */
    public boolean isDirty(int x, int y, int z) {
        long[] bits = sections.get(SectionKeys.sectionOfBlock(x, y, z));
        int index = SectionKeys.index(x & 15, y & 15, z & 15);
        return bits != null && (bits[index >>> 6] & (1L << index)) != 0;
    }

    /** Whether nothing is dirty. */
    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /** The number of sections with at least one dirty position. */
    public int sectionCount() {
        return sections.size();
    }

    /** Removes every dirty position and returns them by section, in {@link SectionKeys#compare} order. */
    public List<DirtySection> drain() {
        return drain(key -> true);
    }

    /**
     * Removes every dirty position and returns the sections {@code interest} accepts, in {@link
     * SectionKeys#compare} order. The other sections are dropped without any further work: this is the
     * locality filter's batch-close step (architecture §4.2, GEO-011).
     */
    public List<DirtySection> drain(LongPredicate interest) {
        List<DirtySection> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : sections.entrySet()) {
            long key = e.getKey();
            if (interest.test(key)) {
                out.add(new DirtySection(key, e.getValue()));
            }
        }
        sections.clear();
        lastBits = null;
        out.sort((a, b) -> SectionKeys.compare(a.key(), b.key()));
        return out;
    }

    /**
     * Removes the dirty positions inside the block box from {@code (minX, minY, minZ)} to {@code
     * (maxX, maxY, maxZ)}, inclusive, and returns them by section in {@link SectionKeys#compare} order.
     * Positions outside the box stay dirty. For consumers that own a region, such as tests or a
     * targeted re-evaluation.
     */
    public List<DirtySection> drainWithin(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<DirtySection> out = new ArrayList<>();
        for (int sx = minX >> 4; sx <= maxX >> 4; sx++) {
            for (int sz = minZ >> 4; sz <= maxZ >> 4; sz++) {
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    long key = SectionKeys.section(sx, sy, sz);
                    long[] bits = sections.get(key);
                    if (bits == null) {
                        continue;
                    }
                    long[] taken = new long[WORDS];
                    boolean any = false;
                    boolean left = false;
                    for (int index = 0; index < 4096; index++) {
                        long mask = 1L << index;
                        if ((bits[index >>> 6] & mask) == 0) {
                            continue;
                        }
                        int x = (sx << 4) | (index & 15);
                        int z = (sz << 4) | ((index >> 4) & 15);
                        int y = (sy << 4) | (index >> 8);
                        if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                            taken[index >>> 6] |= mask;
                            any = true;
                        } else {
                            left = true;
                        }
                    }
                    if (!any) {
                        continue;
                    }
                    out.add(new DirtySection(key, taken));
                    if (left) {
                        for (int w = 0; w < WORDS; w++) {
                            bits[w] &= ~taken[w];
                        }
                    } else {
                        sections.remove(key);
                        if (bits == lastBits) {
                            lastBits = null;
                        }
                    }
                }
            }
        }
        out.sort((a, b) -> SectionKeys.compare(a.key(), b.key()));
        return out;
    }

    /** Drops every dirty position in chunk column {@code (chunkX, chunkZ)}, as when it unloads. */
    public void removeChunk(int chunkX, int chunkZ) {
        Iterator<Long> it = sections.keySet().iterator();
        while (it.hasNext()) {
            long key = it.next();
            if (SectionKeys.x(key) == chunkX && SectionKeys.z(key) == chunkZ) {
                it.remove();
            }
        }
        lastBits = null;
    }

    /** Drops every dirty position. */
    public void clear() {
        sections.clear();
        lastBits = null;
    }
}
