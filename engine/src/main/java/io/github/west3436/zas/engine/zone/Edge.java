package io.github.west3436.zas.engine.zone;

/**
 * Every connection between one pair of zones, or between a zone and the
 * ambient, aggregated (CORE-030). A connection is one open pair of cells.
 *
 * <p>An edge counts its connections and how many are direct, that is not
 * through a zone boundary such as an open door (CORE-037). It sleeps while its
 * two sides are equivalent (CORE-033) and wakes when either changes (CORE-066).
 * It disappears when its last connection closes.
 */
public final class Edge {
    private final Zone a;
    private final Zone b;
    int connections;
    int direct;
    boolean sleeping;
    boolean removed;

    /** @param b the other zone, or null for an ambient edge */
    Edge(Zone a, Zone b) {
        this.a = a;
        this.b = b;
    }

    /** The zone with the lower id, or the only zone of an ambient edge. */
    public Zone first() {
        return a;
    }

    /** The zone with the higher id, or null for an ambient edge. */
    public Zone second() {
        return b;
    }

    /** Whether this edge leads to the ambient. */
    public boolean isAmbient() {
        return b == null;
    }

    /** The other side seen from {@code zone}: a zone, or null for the ambient. */
    public Zone other(Zone zone) {
        return zone == a ? b : a;
    }

    /** The number of connections (CORE-037). */
    public int connections() {
        return connections;
    }

    /** The number of direct connections (CORE-037). */
    public int directConnections() {
        return direct;
    }

    public boolean isSleeping() {
        return sleeping;
    }

    /** Whether the edge still exists: false once its last connection closed or a side was destroyed. */
    public boolean isAlive() {
        return !removed;
    }

    @Override
    public String toString() {
        return "Edge[" + a.id() + " - " + (b == null ? "ambient" : Long.toString(b.id())) + ", " + connections
                + " connections, " + direct + " direct" + (sleeping ? ", sleeping" : "") + "]";
    }
}
