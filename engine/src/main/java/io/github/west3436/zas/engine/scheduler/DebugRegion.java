package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;

/**
 * A box of block positions, corners included, that the operator debug overlay wants exported
 * after an atmos tick (CLIENT-005, CLIENT-011): one per operator with the overlay on, around that
 * operator.
 *
 * @param minX the lowest x
 * @param minY the lowest y
 * @param minZ the lowest z
 * @param maxX the highest x
 * @param maxY the highest y
 * @param maxZ the highest z
 */
public record DebugRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public DebugRegion {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("empty debug region");
        }
    }

    /** The cube of positions within {@code radius} blocks of {@code (x, y, z)} on each axis. */
    public static DebugRegion around(int x, int y, int z, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("negative radius " + radius);
        }
        return new DebugRegion(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    /** Whether block {@code (x, y, z)} is inside. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Whether the block with key {@code block} ({@link SectionKeys#block}) is inside. */
    public boolean contains(long block) {
        return contains(SectionKeys.blockX(block), SectionKeys.blockY(block), SectionKeys.blockZ(block));
    }
}
