package io.github.west3436.zas.geometry;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which sub-cell of a block a point inside it belongs to (API-015, D-44, architecture §4.1).
 *
 * <p>A block's cells come from its collision shape rasterized to 16³ voxels (GEO-004); this maps
 * each voxel back to its cell with {@link GeometryDeriver#cellOfVoxels}, so a free voxel gives the
 * cell holding it and a voxel inside the shape gives the nearest cell. The map is built the first
 * time a block state with more than one cell is asked about and kept for the session: 4 KiB each,
 * for the few states that divide their block (panes, bars, walls, fences). Safe to call from any
 * thread.
 */
public final class SubCells {
    private static final VoxelMap NONE = new VoxelMap(null, 0);
    private static final ConcurrentHashMap<BlockState, VoxelMap> MAPS = new ConcurrentHashMap<>();

    /** A state's voxel to cell map and the number of cells in it. */
    private record VoxelMap(byte[] cellOf, int cells) {}

    private SubCells() {}

    /** The voxel, 0 to 15, holding a coordinate within a block, 0 to 1. */
    public static int voxel(double withinBlock) {
        int v = (int) Math.floor(withinBlock * GeometryDeriver.N);
        return Math.max(0, Math.min(GeometryDeriver.N - 1, v));
    }

    /**
     * The sub-cell of a block that holds voxel {@code (x, y, z)}, or the nearest one if that voxel
     * is inside the block's shape.
     *
     * @param state the block
     * @param geometry its geometry in the current table
     * @return the cell's index in {@code geometry}; -1 if the block has no cells, or if its cells
     *     don't come from its collision shape as derived at the origin (a position-dependent
     *     shape whose cells differ), so a caller should fall back to the largest cell
     */
    public static int at(BlockState state, Geometry geometry, int x, int y, int z) {
        int count = geometry.cellCount();
        if (count <= 1) {
            return count - 1;
        }
        VoxelMap map = MAPS.computeIfAbsent(state, SubCells::map);
        if (map.cells() != count) {
            return -1;
        }
        return map.cellOf()[GeometryDeriver.index(x, y, z)];
    }

    /**
     * A voxel that resolves to one cell of a block: of the voxels {@link #at} maps to it, the one
     * nearest the block's centre, the first of equals.
     *
     * @return the voxel's index ({@link GeometryDeriver#index}), or -1 under the same conditions as
     *     {@link #at}, or for a block with one cell, where any voxel will do
     */
    public static int voxelOf(BlockState state, Geometry geometry, int cell) {
        if (geometry.cellCount() <= 1 || cell < 0 || cell >= geometry.cellCount()) {
            return -1;
        }
        VoxelMap map = MAPS.computeIfAbsent(state, SubCells::map);
        if (map.cells() != geometry.cellCount()) {
            return -1;
        }
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < GeometryDeriver.VOXELS; i++) {
            if (map.cellOf()[i] != cell) {
                continue;
            }
            int dx = 2 * (i & 15) - 15;
            int dz = 2 * ((i >>> 4) & 15) - 15;
            int dy = 2 * (i >>> 8) - 15;
            int d = dx * dx + dy * dy + dz * dz;
            if (d < bestDistance) {
                best = i;
                bestDistance = d;
            }
        }
        return best;
    }

    /** The index of a geometry's largest cell, the first of equals; -1 if it has none. */
    public static int largest(Geometry geometry) {
        int best = -1;
        double bestVolume = 0;
        for (int i = 0; i < geometry.cellCount(); i++) {
            double v = geometry.cell(i).volume();
            if (v > bestVolume) {
                best = i;
                bestVolume = v;
            }
        }
        return best;
    }

    private static VoxelMap map(BlockState state) {
        try {
            byte[] cellOf = GeometryDeriver.cellOfVoxels(
                    GeometryDerivation.rasterize(GeometryDerivation.collisionShape(state)));
            if (cellOf == null) {
                return NONE;
            }
            int cells = 0;
            for (byte c : cellOf) {
                cells = Math.max(cells, c + 1);
            }
            return new VoxelMap(cellOf, cells);
        } catch (RuntimeException e) {
            // A broken modded shape: its geometry fell back to solid, or will be resolved by size.
            return NONE;
        }
    }
}
