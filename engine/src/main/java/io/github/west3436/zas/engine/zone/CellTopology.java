package io.github.west3436.zas.engine.zone;

/**
 * The abstract topology the zone graph runs on: which cells exist, their gas
 * volumes, and which pairs of cells gas can pass between. The platform backs it
 * with section snapshots and the geometry palette (GEO); tests back it with a
 * hand-built grid.
 *
 * <p>A cell is identified by a {@code long} chosen by the implementation, such as
 * a packed block position plus a sub-cell index. The graph never interprets it.
 *
 * <p><b>Contract:</b>
 *
 * <ul>
 *   <li>Answers reflect one consistent state of the world, the state the graph
 *       is being brought up to date with.
 *   <li>Openness is symmetric: if {@code a} reports {@code b} then {@code b}
 *       reports {@code a}, with the same {@code direct} flag.
 *   <li>{@link #forEachOpenNeighbour} visits neighbours in a deterministic order
 *       (architecture §3.5), and only neighbours that are cells.
 *   <li>When a face between two cells opens, closes or changes kind, or a cell's
 *       volume changes, the graph must be told through {@link
 *       ZoneGraph#refresh(long)}: the cell whose volume changed, and at least one
 *       of the two cells of every changed face. For a block change, refreshing
 *       the block's cells and their face neighbours is always enough.
 * </ul>
 */
public interface CellTopology {
    /**
     * The gas volume of a cell, in L (GEO-002). Zero or less means the position
     * is not a cell, for example because a solid block fills it.
     */
    double volume(long cell);

    /**
     * Visits every cell that gas can pass to from {@code cell} across an open
     * face.
     *
     * @param cell the cell whose neighbours to visit
     * @param visitor called once per open neighbour; {@code direct} is false when
     *     the opening is a zone boundary, such as an open door, that gas crosses
     *     but zones never span (CORE-034)
     */
    void forEachOpenNeighbour(long cell, NeighbourVisitor visitor);

    /** Receives the open neighbours of a cell. */
    @FunctionalInterface
    interface NeighbourVisitor {
        void accept(long neighbour, boolean direct);
    }
}
