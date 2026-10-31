package io.github.west3436.zas.engine.zone;

/**
 * What the graph knows about one cell: its zone, its volume as last read, and
 * its stored connections.
 *
 * <p>The graph stores every open pair of cells in which at least one cell is
 * zoned, on both cells. A zoned cell therefore lists all its open neighbours,
 * and an unzoned cell bordering a zone lists only its zoned neighbours. Unzoned
 * cells with nothing stored are dropped.
 */
final class Cell {
    private static final long[] NO_CELLS = new long[0];
    private static final boolean[] NO_FLAGS = new boolean[0];

    final long id;
    /** The zone, or null when unzoned. */
    Zone zone;
    /** Index in the zone's cell array. */
    int zoneIndex = -1;
    /** Gas volume when the cell joined or was last refreshed, in L. */
    double volume;

    long[] neighbours = NO_CELLS;
    boolean[] direct = NO_FLAGS;
    int degree;

    /** Scratch for searches: the search epoch that last visited this cell, and the search. */
    int visitEpoch;

    int visitSearch;

    Cell(long id) {
        this.id = id;
    }

    int indexOf(long neighbour) {
        for (int i = 0; i < degree; i++) {
            if (neighbours[i] == neighbour) {
                return i;
            }
        }
        return -1;
    }

    void addLink(long neighbour, boolean isDirect) {
        if (degree == neighbours.length) {
            int capacity = Math.max(6, degree * 2);
            neighbours = java.util.Arrays.copyOf(neighbours, capacity);
            direct = java.util.Arrays.copyOf(direct, capacity);
        }
        neighbours[degree] = neighbour;
        direct[degree] = isDirect;
        degree++;
    }

    /** Removes a link, keeping the order of the others so iteration stays deterministic. */
    void removeLinkAt(int index) {
        int tail = degree - index - 1;
        if (tail > 0) {
            System.arraycopy(neighbours, index + 1, neighbours, index, tail);
            System.arraycopy(direct, index + 1, direct, index, tail);
        }
        degree--;
    }
}
