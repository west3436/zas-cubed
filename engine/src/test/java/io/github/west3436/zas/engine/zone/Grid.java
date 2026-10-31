package io.github.west3436.zas.engine.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A hand-built cell topology for tests: a grid of block positions, each with a
 * gas volume (0, the default, means solid), six faces per cell, and faces that
 * can be closed or made zone boundaries (doors). Hash collections are used only
 * for lookups; neighbours are visited in a fixed direction order.
 */
final class Grid implements CellTopology {
    private static final int[][] DIRECTIONS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static final int BITS = 20;
    private static final long MASK = (1L << BITS) - 1;

    private final Map<Long, Double> volumes = new HashMap<>();
    private final Set<Face> closed = new HashSet<>();
    private final Set<Face> doors = new HashSet<>();

    private record Face(long a, long b) {
        static Face of(long x, long y) {
            return x < y ? new Face(x, y) : new Face(y, x);
        }
    }

    static long cell(int x, int y, int z) {
        return x | ((long) y << BITS) | ((long) z << (2 * BITS));
    }

    static int x(long cell) {
        return (int) (cell & MASK);
    }

    static int y(long cell) {
        return (int) ((cell >>> BITS) & MASK);
    }

    static int z(long cell) {
        return (int) ((cell >>> (2 * BITS)) & MASK);
    }

    /** Sets the volume of every position in the box, inclusive. */
    Grid box(int x0, int y0, int z0, int x1, int y1, int z1, double volume) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    set(cell(x, y, z), volume);
                }
            }
        }
        return this;
    }

    /** The cells of a box, in x, y, z order. */
    static long[] cells(int x0, int y0, int z0, int x1, int y1, int z1) {
        long[] out = new long[(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1)];
        int i = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    out[i++] = cell(x, y, z);
                }
            }
        }
        return out;
    }

    void set(long cell, double volume) {
        if (volume > 0) {
            volumes.put(cell, volume);
        } else {
            volumes.remove(cell);
        }
    }

    void close(long a, long b) {
        doors.remove(Face.of(a, b));
        closed.add(Face.of(a, b));
    }

    void open(long a, long b) {
        closed.remove(Face.of(a, b));
        doors.remove(Face.of(a, b));
    }

    void door(long a, long b) {
        closed.remove(Face.of(a, b));
        doors.add(Face.of(a, b));
    }

    /** The cell and its six face neighbours, the cells to refresh after a block change. */
    static long[] around(long cell) {
        long[] out = new long[7];
        out[0] = cell;
        int n = 1;
        for (int[] d : DIRECTIONS) {
            long neighbour = offset(cell, d);
            if (neighbour >= 0) {
                out[n++] = neighbour;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static long offset(long cell, int[] d) {
        int x = x(cell) + d[0];
        int y = y(cell) + d[1];
        int z = z(cell) + d[2];
        if (x < 0 || y < 0 || z < 0 || x > MASK || y > MASK || z > MASK) {
            return -1;
        }
        return cell(x, y, z);
    }

    @Override
    public double volume(long cell) {
        Double v = volumes.get(cell);
        return v == null ? 0 : v;
    }

    @Override
    public void forEachOpenNeighbour(long cell, NeighbourVisitor visitor) {
        if (volume(cell) <= 0) {
            return;
        }
        for (int[] d : DIRECTIONS) {
            long n = offset(cell, d);
            if (n < 0 || volume(n) <= 0) {
                continue;
            }
            Face face = Face.of(cell, n);
            if (closed.contains(face)) {
                continue;
            }
            visitor.accept(n, !doors.contains(face));
        }
    }
}
