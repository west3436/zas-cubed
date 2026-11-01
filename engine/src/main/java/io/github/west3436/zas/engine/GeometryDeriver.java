package io.github.west3436.zas.engine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Derives a {@link Geometry} from a block's solid voxels (architecture §4.1, step 2).
 *
 * <p>The input is a 16³ grid where a set bit is solid (inside the collision shape); use {@link
 * #index(int, int, int)} to address it. Each face's coverage is the solid part of its boundary
 * layer (GEO-003). The free voxels are split into 6-connected components; each component that
 * touches a boundary layer is a cell with its volume and per-face footprint (GEO-001, GEO-002,
 * GEO-004). Components that touch no boundary are ignored.
 *
 * <p>Cells come out in the order of their lowest voxel index, so the result is deterministic.
 */
public final class GeometryDeriver {
    /** Voxels on a side. */
    public static final int N = 16;

    /** Voxels in a grid. */
    public static final int VOXELS = N * N * N;

    private GeometryDeriver() {}

    /** The bit index of voxel {@code (x, y, z)}, each 0 to 15. */
    public static int index(int x, int y, int z) {
        return (y * N + z) * N + x;
    }

    /** Sets the voxels with {@code x0 <= x < x1}, and likewise for y and z, in {@code solid}. */
    public static void fill(BitSet solid, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (x0 < 0 || y0 < 0 || z0 < 0 || x1 > N || y1 > N || z1 > N) {
            throw new IndexOutOfBoundsException("box outside the 16³ grid");
        }
        if (x1 <= x0) {
            return;
        }
        for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
                int base = index(0, y, z);
                solid.set(base + x0, base + x1);
            }
        }
    }

    /** The geometry of a block whose solid voxels are set in {@code solid}. */
    public static Geometry derive(BitSet solid) {
        if (solid.isEmpty()) {
            return Geometry.AIR;
        }
        if (solid.cardinality() >= VOXELS) {
            return Geometry.SOLID;
        }
        long[][] cover = new long[6][4];
        for (int i = solid.nextSetBit(0); i >= 0 && i < VOXELS; i = solid.nextSetBit(i + 1)) {
            markFaces(cover, i & 15, i >>> 8, (i >>> 4) & 15);
        }
        List<FaceMask> coverage = new ArrayList<>(6);
        for (long[] w : cover) {
            coverage.add(FaceMask.canonical(w));
        }

        List<Geometry.Cell> cells = new ArrayList<>();
        BitSet visited = (BitSet) solid.clone();
        int[] stack = new int[VOXELS];
        for (int start = visited.nextClearBit(0); start < VOXELS; start = visited.nextClearBit(start + 1)) {
            long[][] foot = new long[6][4];
            boolean touches = false;
            int count = 0;
            int top = 0;
            stack[top++] = start;
            visited.set(start);
            while (top > 0) {
                int i = stack[--top];
                count++;
                int x = i & 15;
                int z = (i >>> 4) & 15;
                int y = i >>> 8;
                touches |= markFaces(foot, x, y, z);
                if (x > 0) top = push(visited, stack, top, i - 1);
                if (x < N - 1) top = push(visited, stack, top, i + 1);
                if (z > 0) top = push(visited, stack, top, i - N);
                if (z < N - 1) top = push(visited, stack, top, i + N);
                if (y > 0) top = push(visited, stack, top, i - N * N);
                if (y < N - 1) top = push(visited, stack, top, i + N * N);
            }
            if (touches) {
                List<FaceMask> footprints = new ArrayList<>(6);
                for (long[] w : foot) {
                    footprints.add(FaceMask.canonical(w));
                }
                cells.add(new Geometry.Cell(count * Geometry.VOXEL_VOLUME, footprints));
            }
        }
        return new Geometry(coverage, cells, 0);
    }

    /**
     * Which cell of {@link #derive}'s result each voxel belongs to, for resolving a point to a
     * sub-cell (API-015, D-44). A free voxel of a cell gets that cell's index. Every other voxel,
     * solid or in an enclosed pocket, gets the index of the nearest cell: the first reached by a
     * breadth-first search over the 6-connected grid from every cell voxel at once, in voxel index
     * order, so the result is deterministic.
     *
     * @param solid the solid voxels, as for {@link #derive}
     * @return one cell index per voxel, by {@link #index}; null if the block has no cells
     */
    public static byte[] cellOfVoxels(BitSet solid) {
        byte[] cellOf = new byte[VOXELS];
        java.util.Arrays.fill(cellOf, (byte) -1);
        BitSet visited = (BitSet) solid.clone();
        int[] stack = new int[VOXELS];
        int[] component = new int[VOXELS];
        int[] queue = new int[VOXELS];
        int queued = 0;
        int cells = 0;
        for (int start = visited.nextClearBit(0); start < VOXELS; start = visited.nextClearBit(start + 1)) {
            boolean touches = false;
            int count = 0;
            int top = 0;
            stack[top++] = start;
            visited.set(start);
            while (top > 0) {
                int i = stack[--top];
                component[count++] = i;
                int x = i & 15;
                int z = (i >>> 4) & 15;
                int y = i >>> 8;
                touches |= x == 0 || x == N - 1 || y == 0 || y == N - 1 || z == 0 || z == N - 1;
                if (x > 0) top = push(visited, stack, top, i - 1);
                if (x < N - 1) top = push(visited, stack, top, i + 1);
                if (z > 0) top = push(visited, stack, top, i - N);
                if (z < N - 1) top = push(visited, stack, top, i + N);
                if (y > 0) top = push(visited, stack, top, i - N * N);
                if (y < N - 1) top = push(visited, stack, top, i + N * N);
            }
            if (touches) {
                for (int k = 0; k < count; k++) {
                    cellOf[component[k]] = (byte) cells;
                }
                cells++;
            }
        }
        if (cells == 0) {
            return null;
        }
        for (int i = 0; i < VOXELS; i++) {
            if (cellOf[i] >= 0) {
                queue[queued++] = i;
            }
        }
        for (int head = 0; head < queued; head++) {
            int i = queue[head];
            int x = i & 15;
            int z = (i >>> 4) & 15;
            int y = i >>> 8;
            byte c = cellOf[i];
            if (x > 0) queued = spread(cellOf, queue, queued, i - 1, c);
            if (x < N - 1) queued = spread(cellOf, queue, queued, i + 1, c);
            if (z > 0) queued = spread(cellOf, queue, queued, i - N, c);
            if (z < N - 1) queued = spread(cellOf, queue, queued, i + N, c);
            if (y > 0) queued = spread(cellOf, queue, queued, i - N * N, c);
            if (y < N - 1) queued = spread(cellOf, queue, queued, i + N * N, c);
        }
        return cellOf;
    }

    private static int spread(byte[] cellOf, int[] queue, int queued, int i, byte cell) {
        if (cellOf[i] < 0) {
            cellOf[i] = cell;
            queue[queued++] = i;
        }
        return queued;
    }

    private static int push(BitSet visited, int[] stack, int top, int i) {
        if (!visited.get(i)) {
            visited.set(i);
            stack[top++] = i;
        }
        return top;
    }

    /** Sets the face bits of voxel {@code (x, y, z)} for every boundary layer it lies on. */
    private static boolean markFaces(long[][] masks, int x, int y, int z) {
        boolean any = false;
        if (y == 0) any = set(masks[Face.DOWN.ordinal()], x, z);
        if (y == N - 1) any = set(masks[Face.UP.ordinal()], x, z);
        if (z == 0) any = set(masks[Face.NORTH.ordinal()], x, y);
        if (z == N - 1) any = set(masks[Face.SOUTH.ordinal()], x, y);
        if (x == 0) any = set(masks[Face.WEST.ordinal()], z, y);
        if (x == N - 1) any = set(masks[Face.EAST.ordinal()], z, y);
        return any;
    }

    private static boolean set(long[] words, int u, int v) {
        int bit = v * N + u;
        words[bit >>> 6] |= 1L << bit;
        return true;
    }
}
