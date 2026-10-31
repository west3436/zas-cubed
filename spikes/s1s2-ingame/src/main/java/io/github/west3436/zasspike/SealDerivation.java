package io.github.west3436.zasspike;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spike S2: derives a state's gas geometry from its collision shape in an
 * empty collision context (GEO-041): six 16x16 face coverage masks (GEO-003),
 * the free volume (GEO-002) and the sub-cells (GEO-004), from a 16^3 voxel
 * rasterization.
 */
public final class SealDerivation {
    static final int N = 16;

    /** Face order: down, up, north, south, west, east (Direction ordinal order). */
    public record Geometry(long[] faces, int freeVoxels, int subCells, int[] subCellVoxels, int[] subCellFaces) {
        public boolean faceFull(int d) {
            for (int i = 0; i < 4; i++) {
                if (faces[d * 4 + i] != -1L) {
                    return false;
                }
            }
            return true;
        }

        public boolean faceEmpty(int d) {
            for (int i = 0; i < 4; i++) {
                if (faces[d * 4 + i] != 0L) {
                    return false;
                }
            }
            return true;
        }

        public String faceSummary() {
            StringBuilder sb = new StringBuilder();
            String names = "DUNSWE";
            for (int d = 0; d < 6; d++) {
                sb.append(names.charAt(d)).append(faceFull(d) ? "#" : faceEmpty(d) ? "." : "~");
            }
            return sb.toString();
        }

        long key() {
            long h = Arrays.hashCode(faces);
            h = h * 31 + freeVoxels;
            h = h * 31 + Arrays.hashCode(subCellVoxels);
            h = h * 31 + Arrays.hashCode(subCellFaces);
            return h;
        }
    }

    private SealDerivation() {}

    public static VoxelShape shape(BlockState state, BlockPos pos) {
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos, CollisionContext.empty());
    }

    public static Geometry derive(VoxelShape shape) {
        boolean[] solid = new boolean[N * N * N];
        if (!shape.isEmpty()) {
            List<AABB> boxes = shape.toAabbs();
            for (AABB b : boxes) {
                int x0 = clamp(b.minX);
                int x1 = clampHi(b.maxX);
                int y0 = clamp(b.minY);
                int y1 = clampHi(b.maxY);
                int z0 = clamp(b.minZ);
                int z1 = clampHi(b.maxZ);
                for (int x = x0; x < x1; x++) {
                    for (int y = y0; y < y1; y++) {
                        for (int z = z0; z < z1; z++) {
                            solid[idx(x, y, z)] = true;
                        }
                    }
                }
            }
        }
        long[] faces = new long[24];
        for (int a = 0; a < N; a++) {
            for (int b = 0; b < N; b++) {
                int bit = a * N + b;
                setIf(faces, 0, bit, solid[idx(a, 0, b)]);
                setIf(faces, 1, bit, solid[idx(a, N - 1, b)]);
                setIf(faces, 2, bit, solid[idx(a, b, 0)]);
                setIf(faces, 3, bit, solid[idx(a, b, N - 1)]);
                setIf(faces, 4, bit, solid[idx(0, b, a)]);
                setIf(faces, 5, bit, solid[idx(N - 1, b, a)]);
            }
        }
        // Free-space components that reach a face are the sub-cells.
        int[] comp = new int[N * N * N];
        Arrays.fill(comp, -1);
        int free = 0;
        int count = 0;
        int[] vox = new int[8];
        int[] fmask = new int[8];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < solid.length; i++) {
            if (solid[i]) {
                continue;
            }
            free++;
            if (comp[i] >= 0) {
                continue;
            }
            int c = count;
            int size = 0;
            int touches = 0;
            comp[i] = c;
            q.add(i);
            while (!q.isEmpty()) {
                int v = q.poll();
                size++;
                int x = v / (N * N);
                int y = (v / N) % N;
                int z = v % N;
                if (y == 0) touches |= 1;
                if (y == N - 1) touches |= 2;
                if (z == 0) touches |= 4;
                if (z == N - 1) touches |= 8;
                if (x == 0) touches |= 16;
                if (x == N - 1) touches |= 32;
                if (x > 0) visit(solid, comp, q, idx(x - 1, y, z), c);
                if (x < N - 1) visit(solid, comp, q, idx(x + 1, y, z), c);
                if (y > 0) visit(solid, comp, q, idx(x, y - 1, z), c);
                if (y < N - 1) visit(solid, comp, q, idx(x, y + 1, z), c);
                if (z > 0) visit(solid, comp, q, idx(x, y, z - 1), c);
                if (z < N - 1) visit(solid, comp, q, idx(x, y, z + 1), c);
            }
            if (touches != 0) {
                if (count == vox.length) {
                    vox = Arrays.copyOf(vox, count * 2);
                    fmask = Arrays.copyOf(fmask, count * 2);
                }
                vox[count] = size;
                fmask[count] = touches;
                count++;
            } else {
                // Enclosed pocket inside the shape: ignored (GEO-001). Relabel so
                // the next component reuses this index.
                for (int j = 0; j < comp.length; j++) {
                    if (comp[j] == c) {
                        comp[j] = Integer.MAX_VALUE;
                    }
                }
            }
        }
        return new Geometry(faces, free, count, Arrays.copyOf(vox, count), Arrays.copyOf(fmask, count));
    }

    private static void visit(boolean[] solid, int[] comp, ArrayDeque<Integer> q, int j, int c) {
        if (!solid[j] && comp[j] == -1) {
            comp[j] = c;
            q.add(j);
        }
    }

    private static void setIf(long[] faces, int d, int bit, boolean v) {
        if (v) {
            faces[d * 4 + (bit >> 6)] |= 1L << (bit & 63);
        }
    }

    private static int idx(int x, int y, int z) {
        return (x * N + y) * N + z;
    }

    private static int clamp(double v) {
        return Math.max(0, Math.min(N, (int) Math.round(v * N)));
    }

    private static int clampHi(double v) {
        return Math.max(0, Math.min(N, (int) Math.round(v * N)));
    }
}
