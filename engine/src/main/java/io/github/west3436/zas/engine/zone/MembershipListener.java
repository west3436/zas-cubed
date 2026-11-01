package io.github.west3436.zas.engine.zone;

/**
 * Hears every change to which zone a cell is in, so an index of zone membership kept outside
 * the graph (the scheduler's per-section palettes, architecture §4.4) can follow the graph change
 * by change instead of being rebuilt over every zoned cell.
 *
 * <p>The graph calls it synchronously, on the thread that changes the graph, in the order the
 * changes happen. The volumes are the graph's own record of each cell (the topology's volume when
 * the cell joined or was last refreshed), so sums of them match {@link Zone#volume()} up to
 * rounding.
 */
public interface MembershipListener {
    /** Hears nothing. */
    MembershipListener NONE = new MembershipListener() {
        @Override
        public void cellAdded(long cell, Zone zone, double volume) {}

        @Override
        public void cellRemoved(long cell, Zone zone, double volume) {}

        @Override
        public void cellResized(long cell, Zone zone, double oldVolume, double newVolume) {}

        @Override
        public void zoneMerged(Zone from, Zone into) {}
    };

    /** A cell joined a zone: created with it, joined it, split off into it, or was restored or loaded into it. */
    void cellAdded(long cell, Zone zone, double volume);

    /** A cell left a zone: it was filled, left, dissolved, evicted, or is moving to another zone. */
    void cellRemoved(long cell, Zone zone, double volume);

    /** A zoned cell's volume changed while it stayed in its zone (a refresh after a geometry change). */
    void cellResized(long cell, Zone zone, double oldVolume, double newVolume);

    /**
     * Every cell of {@code from} is about to move to {@code into}, which absorbs it (CORE-023). No
     * per-cell calls follow for those cells; {@code from} is then destroyed with no cells.
     */
    void zoneMerged(Zone from, Zone into);
}
