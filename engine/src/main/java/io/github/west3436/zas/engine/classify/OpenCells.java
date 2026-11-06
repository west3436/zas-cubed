package io.github.west3436.zas.engine.classify;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cells classified as open, so that classifying them again takes no fill (GEO-027).
 *
 * <p><b>What invalidates one.</b> An open result rests on the cells that were looked at: the way out
 * runs through them, or there are more of them than the bound. Whether two cells connect depends only
 * on their own blocks, so only a block changing in a section they lie in can make the result wrong.
 * Each cell is kept with those sections, and goes when a block changes in one of them or its chunk
 * unloads, since a new fill might then find the region {@linkplain FillResult.Unresolved unresolved}
 * (GEO-024). A zone can't close the way out by itself: no zone forms on cells that reach open air,
 * and one that was already there was a wall when the cell was classified.
 *
 * <p><b>Bound.</b> At most {@value #MAX_LINKS} links between a cell and a section are kept. Past that
 * everything is forgotten, which costs only the fills that find it all again.
 *
 * <p><b>Determinism.</b> The answers are what a new fill would give, so whether a cell is here changes
 * how long a classification takes and nothing else, and nothing here is in the state hash. The hash
 * collections are for lookups only, and removing a set's entries in any order leaves the same result
 * (CORE-072).
 *
 * <p>Not thread-safe; one instance per worker, like the fill.
 */
public final class OpenCells {
    /** The most links between a cell and a section that are kept. */
    static final int MAX_LINKS = 1 << 16;

    private final Map<Long, long[]> sectionsOf = new HashMap<>();
    private final Map<Long, Set<Long>> cellsIn = new HashMap<>();
    private int links;

    /** Whether {@code cell} is known to be open. */
    public boolean contains(long cell) {
        return sectionsOf.containsKey(cell);
    }

    /**
     * Remembers that {@code cell} is open.
     *
     * @param sections the sections of the cells the result rests on, without duplicates
     */
    public void add(long cell, long[] sections) {
        forget(cell);
        if (sections.length > MAX_LINKS) {
            return;
        }
        if (links + sections.length > MAX_LINKS) {
            clear();
        }
        sectionsOf.put(cell, sections);
        for (long section : sections) {
            cellsIn.computeIfAbsent(section, k -> new HashSet<>()).add(cell);
        }
        links += sections.length;
    }

    /** A block changed in {@code section} or its chunk unloaded, whether the mirror holds it or not. */
    public void sectionChanged(long section) {
        Set<Long> cells = cellsIn.get(section);
        if (cells != null) {
            for (long cell : Set.copyOf(cells)) {
                forget(cell);
            }
        }
    }

    /** Forgets everything: what's open has changed in some other way than a block (GEO-031). */
    public void clear() {
        sectionsOf.clear();
        cellsIn.clear();
        links = 0;
    }

    /** How many cells are known to be open. */
    public int size() {
        return sectionsOf.size();
    }

    private void forget(long cell) {
        long[] sections = sectionsOf.remove(cell);
        if (sections == null) {
            return;
        }
        links -= sections.length;
        for (long section : sections) {
            Set<Long> cells = cellsIn.get(section);
            cells.remove(cell);
            if (cells.isEmpty()) {
                cellsIn.remove(section);
            }
        }
    }
}
