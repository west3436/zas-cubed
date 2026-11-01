package io.github.west3436.zas.engine.zone;

import io.github.west3436.zas.engine.gas.GasMixture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * A zone: a connected set of cells sharing one mixture (CORE-020), whose volume
 * is the sum of its cells' volumes (CORE-027). Zones are created, changed and
 * destroyed only by their {@link ZoneGraph}.
 *
 * <p>The id is unique within the graph and never changes while the zone lives
 * (CORE-026). After a zone is destroyed (it emptied, merged into another zone,
 * or dissolved into the ambient) {@link #isAlive()} is false and it holds no
 * cells.
 */
public final class Zone {
    private final long id;
    private final GasMixture mixture;
    private Cell[] cells = new Cell[8];
    private int cellCount;
    private double volume;
    private boolean alive = true;
    /** Set by any change to the mixture; wakes sleeping edges at the next exchange (CORE-066). */
    boolean changed;
    /** Whether the zone is paused (GEO-030): it doesn't exchange, merge or dissolve. */
    boolean paused;
    /** Whether the zone is pinned (D-64): it never dissolves into the ambient. */
    boolean pinned;
    /** Scratch for {@link ZoneGraph}'s search of a group joined through zone boundaries (CORE-067). */
    int groupEpoch;
    /** Sorted: the ambient edge first, then zone edges by the other zone's id. */
    final List<Edge> edges = new ArrayList<>(4);

    private final List<Edge> edgesView = Collections.unmodifiableList(edges);

    Zone(long id, GasMixture mixture) {
        this.id = id;
        this.mixture = mixture;
    }

    /** The zone's id, unique within its graph and stable for its lifetime (CORE-026). */
    public long id() {
        return id;
    }

    /**
     * The zone's mixture. Its volume equals {@link #volume()}.
     *
     * <p>Change it only through the {@link ZoneGraph}, which records each change in
     * the conservation ledger and wakes the zone's edges. Changing it directly is
     * an unattributed change that the invariant checker reports (CORE-073); after
     * one, call {@link ZoneGraph#markChanged(Zone)}.
     */
    public GasMixture mixture() {
        return mixture;
    }

    /** The sum of the cells' gas volumes, in L (CORE-027). */
    public double volume() {
        return volume;
    }

    public int cellCount() {
        return cellCount;
    }

    /**
     * Whether the zone is paused (GEO-030): part of it is in an unloaded chunk, or it opens onto a
     * region that can't be classified yet (GEO-026). A paused zone keeps its mixture: it doesn't
     * exchange, merge or dissolve, and zones across its edges see it as it last was (GEO-035).
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Whether the zone is pinned (D-64): it never dissolves into the ambient (CORE-076), however
     * close to it its mixture gets, and its ambient edges keep exchanging. Set through {@link
     * ZoneGraph#setPinned}. A zone that merges with a pinned zone is pinned, and every part of a
     * pinned zone that splits is pinned.
     */
    public boolean isPinned() {
        return pinned;
    }

    /** Whether the zone still exists in its graph (CORE-065). */
    public boolean isAlive() {
        return alive;
    }

    /** Visits the zone's cells in a deterministic order. */
    public void forEachCell(LongConsumer action) {
        for (int i = 0; i < cellCount; i++) {
            action.accept(cells[i].id);
        }
    }

    /** The zone's cells, in the order of {@link #forEachCell}. */
    public long[] cells() {
        long[] out = new long[cellCount];
        for (int i = 0; i < cellCount; i++) {
            out[i] = cells[i].id;
        }
        return out;
    }

    /** The zone's edges: the ambient edge first if any, then zone edges by the other zone's id. */
    public List<Edge> edges() {
        return edgesView;
    }

    /** The edge to the ambient, or null if the zone has no ambient connection. */
    public Edge ambientEdge() {
        return !edges.isEmpty() && edges.get(0).isAmbient() ? edges.get(0) : null;
    }

    /** The edge to another zone, or null. */
    public Edge edgeTo(Zone other) {
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            if (e.other(this) == other) {
                return e;
            }
        }
        return null;
    }

    // ---- Graph internals ----------------------------------------------------

    Cell cellAt(int index) {
        return cells[index];
    }

    void addCell(Cell cell) {
        if (cellCount == cells.length) {
            cells = java.util.Arrays.copyOf(cells, cellCount * 2);
        }
        cell.zone = this;
        cell.zoneIndex = cellCount;
        cells[cellCount++] = cell;
        setVolume(volume + cell.volume);
    }

    void removeCell(Cell cell) {
        int index = cell.zoneIndex;
        Cell last = cells[--cellCount];
        cells[index] = last;
        last.zoneIndex = index;
        cells[cellCount] = null;
        cell.zone = null;
        cell.zoneIndex = -1;
        if (cellCount == 0) {
            volume = 0;
        } else {
            setVolume(volume - cell.volume);
        }
    }

    void setVolume(double litres) {
        volume = litres;
        if (litres > 0) {
            mixture.setVolume(litres);
        }
    }

    void destroy() {
        alive = false;
        cellCount = 0;
        cells = new Cell[0];
        edges.clear();
    }

    void insertEdge(Edge edge) {
        long key = sortKey(edge);
        int i = 0;
        while (i < edges.size() && sortKey(edges.get(i)) < key) {
            i++;
        }
        edges.add(i, edge);
    }

    private long sortKey(Edge edge) {
        Zone other = edge.other(this);
        return other == null ? Long.MIN_VALUE : other.id;
    }

    @Override
    public String toString() {
        return "Zone#" + id + "[" + cellCount + " cells, " + volume + " L]";
    }
}
