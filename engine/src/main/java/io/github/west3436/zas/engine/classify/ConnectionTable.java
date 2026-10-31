package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;

/**
 * A lazily filled cache of {@link GeometryPalette#connects}, which builds face masks on every
 * call. For each (geometry, face, neighbour geometry) it stores which pairs of their cells connect,
 * as one bit per pair. Single-threaded, like the worker that owns it.
 */
final class ConnectionTable {
    private static final long[] NONE = new long[4];
    private static final long[] AIR_AIR = {1L, 0, 0, 0};

    private final GeometryPalette palette;
    private final int[] cellCounts;
    private final double[][] volumes;
    private final boolean[] boundary;

    private long[] keys = new long[256];
    private long[][] values = new long[256][];
    private int size;

    ConnectionTable(GeometryPalette palette) {
        this.palette = palette;
        int n = palette.size();
        cellCounts = new int[n];
        volumes = new double[n][];
        boundary = new boolean[n];
        for (int id = 0; id < n; id++) {
            Geometry g = palette.geometry(id);
            int count = Math.min(g.cellCount(), CellIds.MAX_SUB_CELLS);
            cellCounts[id] = count;
            volumes[id] = new double[count];
            for (int c = 0; c < count; c++) {
                volumes[id][c] = g.cell(c).volume();
            }
            boundary[id] = g.hasFlag(Geometry.FLAG_ZONE_BOUNDARY);
        }
    }

    GeometryPalette palette() {
        return palette;
    }

    /** The number of cells of a geometry the topology can name. */
    int cellCount(int id) {
        return cellCounts[id];
    }

    /** The volume of a cell, or 0 if the geometry has no such cell. */
    double volume(int id, int sub) {
        double[] v = volumes[id];
        return sub < v.length ? v[sub] : 0;
    }

    /** Whether openings between these two geometries are zone boundaries (GEO-007). */
    boolean boundary(int a, int b) {
        return boundary[a] || boundary[b];
    }

    /**
     * The connection bits from {@code a} across {@code face} to {@code b}: bit {@code cellA * 16 +
     * cellB} of the 256-bit set is set when the two cells connect. Never modify the array.
     */
    long[] connections(int a, Face face, int b) {
        if (a == GeometryPalette.AIR_ID && b == GeometryPalette.AIR_ID) {
            return AIR_AIR;
        }
        if (cellCounts[a] == 0 || cellCounts[b] == 0) {
            return NONE;
        }
        long key = ((long) a << 35) | ((long) b << 3) | face.ordinal();
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        while (values[slot] != null) {
            if (keys[slot] == key) {
                return values[slot];
            }
            slot = (slot + 1) & mask;
        }
        long[] bits = compute(a, face, b);
        keys[slot] = key;
        values[slot] = bits;
        if (++size * 2 > keys.length) {
            grow();
        }
        return bits;
    }

    static boolean connected(long[] bits, int cellA, int cellB) {
        int bit = cellA * 16 + cellB;
        return (bits[bit >>> 6] & (1L << bit)) != 0;
    }

    private long[] compute(int a, Face face, int b) {
        Geometry ga = palette.geometry(a);
        Geometry gb = palette.geometry(b);
        long[] bits = new long[4];
        boolean any = false;
        for (int ca = 0; ca < cellCounts[a]; ca++) {
            for (int cb = 0; cb < cellCounts[b]; cb++) {
                if (GeometryPalette.connects(ga, ca, face, gb, cb)) {
                    int bit = ca * 16 + cb;
                    bits[bit >>> 6] |= 1L << bit;
                    any = true;
                }
            }
        }
        return any ? bits : NONE;
    }

    private void grow() {
        long[] oldKeys = keys;
        long[][] oldValues = values;
        keys = new long[oldKeys.length * 2];
        values = new long[oldKeys.length * 2][];
        int mask = keys.length - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldValues[i] != null) {
                int slot = mix(oldKeys[i]) & mask;
                while (values[slot] != null) {
                    slot = (slot + 1) & mask;
                }
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
            }
        }
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }
}
