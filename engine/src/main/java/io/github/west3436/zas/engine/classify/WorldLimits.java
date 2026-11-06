package io.github.west3436.zas.engine.classify;

/**
 * Where a dimension's world ends sideways, and what its edges do (GEO-031). The build height is fixed
 * for a dimension, so a topology has that already; the world border can move, and the dimension's
 * definition can change with a reload, so these come with each batch.
 *
 * @param minX the lowest block x inside the world border
 * @param minZ the lowest block z inside it
 * @param maxX the highest block x inside it
 * @param maxZ the highest block z inside it
 * @param sealed whether a face leading past the build height or the world border is gas-tight; by
 *     default it is open to the ambient
 */
public record WorldLimits(int minX, int minZ, int maxX, int maxZ, boolean sealed) {
    /** No border short of what a cell id can name, and open edges: the default. */
    public static final WorldLimits OPEN =
            new WorldLimits(CellIds.MIN_XZ, CellIds.MIN_XZ, CellIds.MAX_XZ, CellIds.MAX_XZ, false);

    /** Whether block column {@code (x, z)} is inside the world border. */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
