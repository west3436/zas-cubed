package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.SectionKeys;
import java.util.Arrays;
import java.util.function.LongPredicate;

/**
 * The bounded enclosure test (GEO-020, GEO-024; architecture §4.3): a breadth-first fill from a
 * seed cell through every open neighbour, direct or not (gas crosses zone boundaries), that stops
 * at a configurable number of cells.
 *
 * <ul>
 *   <li>More cells than the bound, or a face leading outside the world: {@link FillResult.Open}.
 *   <li>Otherwise, any reached face into a section the view doesn't hold: {@link
 *       FillResult.Unresolved}, never open. The fill still explores everything else it can reach,
 *       so one result lists every missing section.
 *   <li>Otherwise: {@link FillResult.Enclosed}, with the region's cells.
 * </ul>
 *
 * <p>Deterministic: the result depends only on the topology's answers. Allocation-light: the
 * visited set and queue are kept between fills and only grow, and nothing is allocated per cell; an
 * enclosed result allocates its cell array. Not thread-safe; one instance per worker.
 */
public final class EnclosureFill {
    /** The default bound, in cells (D-23). */
    public static final int DEFAULT_MAX_CELLS = 65_536;

    private final int maxCells;
    private final Visitor visitor = new Visitor();

    // Visited set: open addressing, a slot is used when its stamp equals the current epoch.
    private long[] keys;
    private int[] stamps;
    private int epoch;

    private long[] queue;
    private int tail;

    private long[] missing = new long[8];
    private int missingCount;
    private boolean outside;
    private boolean overflow;

    private ResolvableTopology topology;
    private LongPredicate stopAt;

    /** A fill with the default bound of {@value #DEFAULT_MAX_CELLS} cells. */
    public EnclosureFill() {
        this(DEFAULT_MAX_CELLS);
    }

    /**
     * @param maxCells the most cells an enclosed region can have, at least 1
     */
    public EnclosureFill(int maxCells) {
        if (maxCells < 1 || maxCells > (1 << 28)) {
            throw new IllegalArgumentException("maxCells must be 1 to 2^28, got " + maxCells);
        }
        this.maxCells = maxCells;
        int initial = Math.min(Integer.highestOneBit(maxCells + 1) << 1, 4096);
        keys = new long[Math.max(initial, 16)];
        stamps = new int[keys.length];
        queue = new long[Math.min(maxCells + 1, 1024)];
    }

    public int maxCells() {
        return maxCells;
    }

    /** {@link #fill(ResolvableTopology, long, LongPredicate)} with nothing to stop at. */
    public FillResult fill(ResolvableTopology topology, long start) {
        return fill(topology, start, null);
    }

    /**
     * Classifies the region around {@code start}.
     *
     * @param topology the view of the world
     * @param start the seed cell
     * @param stopAt cells the fill treats as sealed walls, such as zoned cells when classifying the
     *     unzoned side of a breach; null for none. Asked once per opening it is tested on, so it
     *     may record what it sees. Never asked about {@code start}.
     * @return the outcome; an unknown {@code start} is {@link FillResult.Unresolved} on its section
     * @throws IllegalArgumentException if {@code start} is known but not a cell
     */
    public FillResult fill(ResolvableTopology topology, long start, LongPredicate stopAt) {
        nextEpoch();
        tail = 0;
        missingCount = 0;
        outside = false;
        overflow = false;
        if (!topology.isKnown(start)) {
            return new FillResult.Unresolved(new long[] {topology.sectionOf(start)}, 0);
        }
        if (!(topology.volume(start) > 0)) {
            throw new IllegalArgumentException("fill seed " + start + " is not a cell");
        }
        this.topology = topology;
        this.stopAt = stopAt;
        try {
            insert(start);
            queue[tail++] = start;
            int head = 0;
            while (head < tail) {
                topology.forEachNeighbour(queue[head++], visitor);
                if (outside) {
                    return new FillResult.Open(FillResult.Open.Reason.OUTSIDE_WORLD, tail);
                }
                if (overflow) {
                    return new FillResult.Open(FillResult.Open.Reason.BOUND, tail);
                }
            }
        } finally {
            this.topology = null;
            this.stopAt = null;
        }
        if (missingCount > 0) {
            long[] sections = Arrays.copyOf(missing, missingCount);
            sortSections(sections);
            return new FillResult.Unresolved(sections, tail);
        }
        long[] cells = Arrays.copyOf(queue, tail);
        Arrays.sort(cells);
        double volume = 0;
        for (long c : cells) {
            volume += topology.volume(c);
        }
        return new FillResult.Enclosed(cells, volume);
    }

    /**
     * Whether the last fill visited {@code cell}: a cell of its region, as far as it got. Valid
     * until the next fill. Cells it stopped at are not visited.
     */
    public boolean visitedInLastFill(long cell) {
        return contains(cell);
    }

    private final class Visitor implements ResolvableTopology.FillVisitor {
        @Override
        public void accept(long neighbour, boolean direct) {
            if (overflow || outside || contains(neighbour)) {
                return;
            }
            if (stopAt != null && stopAt.test(neighbour)) {
                return;
            }
            if (tail == maxCells) {
                overflow = true;
                return;
            }
            insert(neighbour);
            if (tail == queue.length) {
                queue = Arrays.copyOf(queue, Math.min(queue.length * 2, maxCells + 1));
            }
            queue[tail++] = neighbour;
        }

        @Override
        public void unknown(long section) {
            if (missingCount > 0 && missing[missingCount - 1] == section) {
                return;
            }
            for (int i = 0; i < missingCount; i++) {
                if (missing[i] == section) {
                    return;
                }
            }
            if (missingCount == missing.length) {
                missing = Arrays.copyOf(missing, missingCount * 2);
            }
            missing[missingCount++] = section;
        }

        @Override
        public void outsideWorld() {
            outside = true;
        }
    }

    // ---- Visited set ----------------------------------------------------------

    private void nextEpoch() {
        if (++epoch == 0) {
            Arrays.fill(stamps, 0);
            epoch = 1;
        }
    }

    private boolean contains(long key) {
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        while (stamps[slot] == epoch) {
            if (keys[slot] == key) {
                return true;
            }
            slot = (slot + 1) & mask;
        }
        return false;
    }

    /** Adds a key known to be absent. */
    private void insert(long key) {
        if ((tail + 1) * 2 > keys.length) {
            grow();
        }
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        while (stamps[slot] == epoch) {
            slot = (slot + 1) & mask;
        }
        keys[slot] = key;
        stamps[slot] = epoch;
    }

    /** Doubles the set, re-inserting the queued cells: they are exactly the set's contents. */
    private void grow() {
        keys = new long[keys.length * 2];
        stamps = new int[keys.length];
        epoch = 1;
        int mask = keys.length - 1;
        for (int i = 0; i < tail; i++) {
            int slot = mix(queue[i]) & mask;
            while (stamps[slot] == epoch) {
                slot = (slot + 1) & mask;
            }
            keys[slot] = queue[i];
            stamps[slot] = epoch;
        }
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 29));
    }

    private static void sortSections(long[] sections) {
        // Insertion sort: the list is short, and SectionKeys.compare is not the natural order.
        for (int i = 1; i < sections.length; i++) {
            long v = sections[i];
            int j = i - 1;
            while (j >= 0 && SectionKeys.compare(sections[j], v) > 0) {
                sections[j + 1] = sections[j];
                j--;
            }
            sections[j + 1] = v;
        }
    }
}
