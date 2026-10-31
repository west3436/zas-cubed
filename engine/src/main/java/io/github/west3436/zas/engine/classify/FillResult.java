package io.github.west3436.zas.engine.classify;

import java.util.Objects;

/**
 * The outcome of an {@link EnclosureFill}: whether the region around a seed cell is enclosed
 * (GEO-020), open, or can't be decided yet (GEO-024).
 *
 * <p>The arrays are owned by the result and must not be modified. Records compare them by
 * identity.
 */
public sealed interface FillResult {
    /** How many cells the fill visited before it stopped, for statistics and benchmarks. */
    int visited();

    /**
     * The region is enclosed: every face of it is gas-tight, leads to a cell the fill was told to
     * stop at, or both, and it has no more cells than the bound.
     *
     * @param cells the region's cells, sorted ascending
     * @param volume the sum of their volumes, in L, added in the order of {@code cells}
     */
    record Enclosed(long[] cells, double volume) implements FillResult {
        public Enclosed {
            Objects.requireNonNull(cells, "cells");
        }

        @Override
        public int visited() {
            return cells.length;
        }

        /** Whether {@code cell} is one of the region's cells. */
        public boolean contains(long cell) {
            return java.util.Arrays.binarySearch(cells, cell) >= 0;
        }
    }

    /**
     * The region is open to ambient (D-23, GEO-031).
     *
     * @param reason why
     * @param visited cells visited before the fill stopped
     */
    record Open(Reason reason, int visited) implements FillResult {
        public Open {
            Objects.requireNonNull(reason, "reason");
        }

        /** Why a region is open. */
        public enum Reason {
            /** It has more cells than the bound (GEO-020). */
            BOUND,
            /** It reaches the edge of the world (GEO-031). */
            OUTSIDE_WORLD
        }
    }

    /**
     * The region reaches sections the view doesn't hold, and stays within the bound as far as it
     * was seen (GEO-024). The fill explored everything it could reach, so these are all the
     * sections it needs to go further.
     *
     * <p>The view can't tell a section that is unloaded from one that is loaded but not yet copied
     * to the worker; the caller decides which each key is (architecture §4.3).
     *
     * @param missingSections the keys of the unknown sections the region reaches, sorted by {@link
     *     io.github.west3436.zas.engine.SectionKeys#compare}, without duplicates
     * @param visited cells visited
     */
    record Unresolved(long[] missingSections, int visited) implements FillResult {
        public Unresolved {
            Objects.requireNonNull(missingSections, "missingSections");
        }
    }
}
