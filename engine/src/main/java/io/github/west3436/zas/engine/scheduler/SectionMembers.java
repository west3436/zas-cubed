package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import java.util.Arrays;

/**
 * The zone membership of one section (architecture §4.4): per position a {@code short} local
 * index into a small palette of zones, and a side table for positions whose zoned cells aren't
 * just their first cell (sub-cells of panes, walls and the like).
 *
 * <ul>
 *   <li>{@code local[i]} is 0 when position {@code i} holds no zoned cell; a palette index when its
 *       only zoned cell is its first cell (sub-cell 0); or {@link #MULTI} when the side table lists
 *       its zoned cells.
 *   <li>Palette entry {@code e} ({@code e ≥ 1}) names a zone, with how many of its cells, and how
 *       much volume, the entry holds here. Two entries may name the same zone after a merge, which
 *       renames entries rather than positions; an entry is freed when its last cell leaves.
 * </ul>
 *
 * <p><b>Copy-on-write.</b> A {@link MembershipTracker} changes a section until it publishes it
 * ({@link #frozen}); after that the section never changes, and the tracker changes a {@link
 * #copy} instead. The copy shares the 8 KiB position array until a position changes, so a merge,
 * which only renames palette entries, copies a few small arrays per section.
 */
final class SectionMembers {
    static final short MULTI = -1;
    static final int SIZE = SectionSnapshot.SIZE;

    final long key;
    private short[] local;
    private boolean localShared;

    // Palette; entry 0 is unused. zone[e] == 0 marks a free entry.
    private long[] zone;
    private int[] count;
    private double[] volume;
    private int paletteSize = 1;

    /** Positions holding at least one zoned cell. */
    private int positions;

    // Side table, sorted by (position index, cell id).
    private int[] sideIndex;
    private long[] sideCell;
    private short[] sideEntry;
    private double[] sideVolume;
    private int sideSize;

    /** Published: never changed again. */
    boolean frozen;

    /** Queued for the next publish. */
    boolean dirty;

    private static final int[] NO_INTS = new int[0];
    private static final long[] NO_LONGS = new long[0];
    private static final short[] NO_SHORTS = new short[0];
    private static final double[] NO_DOUBLES = new double[0];

    SectionMembers(long key) {
        this.key = key;
        this.local = new short[SIZE];
        this.zone = new long[4];
        this.count = new int[4];
        this.volume = new double[4];
        this.sideIndex = NO_INTS;
        this.sideCell = NO_LONGS;
        this.sideEntry = NO_SHORTS;
        this.sideVolume = NO_DOUBLES;
    }

    private SectionMembers(SectionMembers from) {
        this.key = from.key;
        this.local = from.local;
        this.localShared = true;
        this.zone = from.zone.clone();
        this.count = from.count.clone();
        this.volume = from.volume.clone();
        this.paletteSize = from.paletteSize;
        this.positions = from.positions;
        this.sideIndex = from.sideIndex.clone();
        this.sideCell = from.sideCell.clone();
        this.sideEntry = from.sideEntry.clone();
        this.sideVolume = from.sideVolume.clone();
        this.sideSize = from.sideSize;
    }

    /** A changeable copy of a published section; the position array is copied on its first change. */
    SectionMembers copy() {
        return new SectionMembers(this);
    }

    /** Whether this section and another share one position array (copy-on-write); for tests. */
    boolean sharesPositions(SectionMembers other) {
        return local == other.local;
    }

    // ---- Reading (any thread, once frozen) ----------------------------------------

    int positions() {
        return positions;
    }

    int paletteSize() {
        return paletteSize;
    }

    /** The zone of palette entry {@code e}, or 0 if it is free. */
    long zoneOfEntry(int e) {
        return zone[e];
    }

    /** The cells palette entry {@code e} holds. */
    int countOfEntry(int e) {
        return count[e];
    }

    /** The volume palette entry {@code e} holds, in L. */
    double volumeOfEntry(int e) {
        return volume[e];
    }

    /** The local value of a position: 0, a palette entry, or {@link #MULTI}. */
    int local(int index) {
        return local[index];
    }

    /** The palette entry holding a cell, or 0. */
    int entryOf(int index, long cell, int sub) {
        int l = local[index];
        if (l == 0) {
            return 0;
        }
        if (l != MULTI) {
            return sub == 0 ? l : 0;
        }
        int i = sideFind(index, cell);
        return i >= 0 ? sideEntry[i] : 0;
    }

    /**
     * The palette entry of a position's largest zoned cell (API-015), the lowest cell id on a tie;
     * 0 if it has none.
     */
    int largestEntry(int index) {
        int l = local[index];
        if (l != MULTI) {
            return l;
        }
        int best = 0;
        double bestVolume = -1;
        for (int i = sideFirst(index); i < sideSize && sideIndex[i] == index; i++) {
            if (sideVolume[i] > bestVolume) {
                bestVolume = sideVolume[i];
                best = sideEntry[i];
            }
        }
        return best;
    }

    /** The side table's size; its entries are read by index with the accessors below. */
    int sideSize() {
        return sideSize;
    }

    int sideIndex(int i) {
        return sideIndex[i];
    }

    long sideCell(int i) {
        return sideCell[i];
    }

    int sideEntry(int i) {
        return sideEntry[i];
    }

    /** The block position ({@link SectionKeys#block}) of a position index in this section. */
    long block(int index) {
        return SectionKeys.block(
                SectionKeys.x(key) * 16 + (index & 15),
                SectionKeys.y(key) * 16 + (index >> 8),
                SectionKeys.z(key) * 16 + ((index >> 4) & 15));
    }

    // ---- Changing (the tracker, before freezing) -----------------------------------

    /** The first entry naming a zone, or 0. */
    int findEntry(long zoneId) {
        for (int e = 1; e < paletteSize; e++) {
            if (zone[e] == zoneId) {
                return e;
            }
        }
        return 0;
    }

    /** A new entry for a zone; reuses a free entry if there is one. */
    int newEntry(long zoneId) {
        int e = 1;
        while (e < paletteSize && zone[e] != 0) {
            e++;
        }
        if (e == paletteSize) {
            if (e > Short.MAX_VALUE - 1) {
                throw new IllegalStateException("section palette overflow");
            }
            if (e == zone.length) {
                int n = zone.length * 2;
                zone = Arrays.copyOf(zone, n);
                count = Arrays.copyOf(count, n);
                volume = Arrays.copyOf(volume, n);
            }
            paletteSize++;
        }
        zone[e] = zoneId;
        count[e] = 0;
        volume[e] = 0;
        return e;
    }

    /** Adds a cell's count and volume to an entry. */
    void addToEntry(int e, double v) {
        count[e]++;
        volume[e] += v;
    }

    /**
     * Takes a cell's count and volume from an entry.
     *
     * @return whether the entry is now empty, and freed
     */
    boolean removeFromEntry(int e, double v) {
        if (--count[e] == 0) {
            zone[e] = 0;
            volume[e] = 0;
            while (paletteSize > 1 && zone[paletteSize - 1] == 0) {
                paletteSize--;
            }
            return true;
        }
        volume[e] -= v;
        return false;
    }

    void resizeEntry(int e, double delta) {
        volume[e] += delta;
    }

    /** Renames every entry of zone {@code from} to {@code into} (a merge). */
    void renameZone(long from, long into) {
        for (int e = 1; e < paletteSize; e++) {
            if (zone[e] == from) {
                zone[e] = into;
            }
        }
    }

    void setLocal(int index, int value) {
        if (localShared) {
            local = local.clone();
            localShared = false;
        }
        int old = local[index];
        local[index] = (short) value;
        if (old == 0 && value != 0) {
            positions++;
        } else if (old != 0 && value == 0) {
            positions--;
        }
    }

    // ---- Side table ----------------------------------------------------------------

    /** The first side entry at or after a position index. */
    int sideFirst(int index) {
        int lo = 0;
        int hi = sideSize;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sideIndex[mid] < index) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** The side entry of a cell, or -1. */
    int sideFind(int index, long cell) {
        for (int i = sideFirst(index); i < sideSize && sideIndex[i] == index; i++) {
            if (sideCell[i] == cell) {
                return i;
            }
        }
        return -1;
    }

    /** How many side entries a position has. */
    int sideCount(int index) {
        int n = 0;
        for (int i = sideFirst(index); i < sideSize && sideIndex[i] == index; i++) {
            n++;
        }
        return n;
    }

    void sideInsert(int index, long cell, int entry, double v) {
        int i = sideFirst(index);
        while (i < sideSize && sideIndex[i] == index && sideCell[i] < cell) {
            i++;
        }
        if (sideSize == sideIndex.length) {
            int n = Math.max(4, sideSize * 2);
            sideIndex = Arrays.copyOf(sideIndex, n);
            sideCell = Arrays.copyOf(sideCell, n);
            sideEntry = Arrays.copyOf(sideEntry, n);
            sideVolume = Arrays.copyOf(sideVolume, n);
        }
        int tail = sideSize - i;
        System.arraycopy(sideIndex, i, sideIndex, i + 1, tail);
        System.arraycopy(sideCell, i, sideCell, i + 1, tail);
        System.arraycopy(sideEntry, i, sideEntry, i + 1, tail);
        System.arraycopy(sideVolume, i, sideVolume, i + 1, tail);
        sideIndex[i] = index;
        sideCell[i] = cell;
        sideEntry[i] = (short) entry;
        sideVolume[i] = v;
        sideSize++;
    }

    void sideRemove(int i) {
        int tail = sideSize - i - 1;
        System.arraycopy(sideIndex, i + 1, sideIndex, i, tail);
        System.arraycopy(sideCell, i + 1, sideCell, i, tail);
        System.arraycopy(sideEntry, i + 1, sideEntry, i, tail);
        System.arraycopy(sideVolume, i + 1, sideVolume, i, tail);
        sideSize--;
    }

    void setSideVolume(int i, double v) {
        sideVolume[i] = v;
    }

    @Override
    public String toString() {
        return "SectionMembers[" + SectionKeys.x(key) + ", " + SectionKeys.y(key) + ", " + SectionKeys.z(key) + ": "
                + positions + " positions, " + (paletteSize - 1) + " entries, " + sideSize + " side]";
    }
}
