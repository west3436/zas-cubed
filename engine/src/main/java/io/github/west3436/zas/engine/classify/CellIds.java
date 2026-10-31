package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.SectionKeys;

/**
 * The cell ids {@link SectionTopology} uses: a block position plus a sub-cell index, packed into
 * a {@code long}.
 *
 * <p>From the high bits down: 24 bits of x, 24 bits of z, 12 bits of y, each signed two's
 * complement, then 4 bits of sub-cell index (the cell's index in its block's {@link
 * io.github.west3436.zas.engine.Geometry#cells()} list). So:
 *
 * <ul>
 *   <li>x and z run from {@value #MIN_XZ} to {@value #MAX_XZ}, about a quarter of the way to the
 *       vanilla world border;
 *   <li>y runs from {@value #MIN_Y} to {@value #MAX_Y}, the whole range a dimension type allows;
 *   <li>a block can hold up to {@value #MAX_SUB_CELLS} cells; any further cells of an unusually
 *       complex shape are not cells to the topology.
 * </ul>
 *
 * <p>Positions outside that range can't be named, so {@link SectionTopology} treats faces that
 * lead to them as leading outside the world. Sorting ids as signed {@code long}s orders cells by
 * x, then z, then y, then sub-cell index (with each coordinate's negative values after its
 * positive ones), a fixed order that the fill's results use (architecture §3.5).
 */
public final class CellIds {
    /** The lowest x or z a cell id can hold. */
    public static final int MIN_XZ = -(1 << 23);

    /** The highest x or z a cell id can hold. */
    public static final int MAX_XZ = (1 << 23) - 1;

    /** The lowest y a cell id can hold. */
    public static final int MIN_Y = -(1 << 11);

    /** The highest y a cell id can hold. */
    public static final int MAX_Y = (1 << 11) - 1;

    /** How many cells of one block position an id can name. */
    public static final int MAX_SUB_CELLS = 16;

    private CellIds() {}

    /**
     * The id of sub-cell {@code sub} of block {@code (x, y, z)}.
     *
     * @throws IllegalArgumentException if the position or index is out of range
     */
    public static long of(int x, int y, int z, int sub) {
        if (!inRange(x, y, z) || sub < 0 || sub >= MAX_SUB_CELLS) {
            throw new IllegalArgumentException("no cell id for (" + x + ", " + y + ", " + z + ") sub-cell " + sub);
        }
        return pack(x, y, z, sub);
    }

    /** The id of the first (often only) cell of block {@code (x, y, z)}. */
    public static long of(int x, int y, int z) {
        return of(x, y, z, 0);
    }

    /** Whether block {@code (x, y, z)} can be named by a cell id. */
    public static boolean inRange(int x, int y, int z) {
        return x >= MIN_XZ && x <= MAX_XZ && z >= MIN_XZ && z <= MAX_XZ && y >= MIN_Y && y <= MAX_Y;
    }

    /** {@link #of} without the range checks; the caller has checked them. */
    static long pack(int x, int y, int z, int sub) {
        return ((long) (x & 0xFFFFFF) << 40) | ((long) (z & 0xFFFFFF) << 16) | ((long) (y & 0xFFF) << 4) | sub;
    }

    public static int x(long cell) {
        return (int) (cell >> 40);
    }

    public static int z(long cell) {
        return (int) (cell << 24 >> 40);
    }

    public static int y(long cell) {
        return (int) (cell << 48 >> 52);
    }

    /** The cell's index among its block's cells. */
    public static int sub(long cell) {
        return (int) (cell & 0xF);
    }

    /** The id of the block's first cell: the cell with its sub-cell index cleared. */
    public static long block(long cell) {
        return cell & ~0xFL;
    }

    /** The key of the section holding the cell ({@link SectionKeys}). */
    public static long section(long cell) {
        return SectionKeys.sectionOfBlock(x(cell), y(cell), z(cell));
    }

    /** The cell's position within its section, as {@link SectionKeys#index(int, int, int)}. */
    public static int indexInSection(long cell) {
        return SectionKeys.index(x(cell) & 15, y(cell) & 15, z(cell) & 15);
    }

    /** A readable form, for logs and test messages: {@code (x, y, z)#sub}. */
    public static String toString(long cell) {
        return "(" + x(cell) + ", " + y(cell) + ", " + z(cell) + ")#" + sub(cell);
    }
}
