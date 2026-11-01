package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.SectionKeys;

/**
 * The cell ids {@link SectionTopology} uses: a block position plus a sub-cell index, packed into
 * a {@code long}.
 *
 * <p>From the high bits down: 26 bits of x, 26 bits of z and 10 bits of y, each signed two's
 * complement, then 2 bits of sub-cell index (the cell's index in its block's {@link
 * io.github.west3436.zas.engine.Geometry#cells()} list). So:
 *
 * <ul>
 *   <li>x and z run from {@value #MIN_XZ} to {@value #MAX_XZ}, Minecraft's whole world, past the
 *       vanilla world border at ±29,999,984;
 *   <li>y runs from {@value #MIN_Y} to {@value #MAX_Y}, which holds every vanilla dimension
 *       (−64 to 319 and 0 to 255) but not the full −2,032 to 2,031 a datapack dimension type may
 *       declare;
 *   <li>a block can hold up to {@value #MAX_SUB_CELLS} cells, the most any vanilla shape divides
 *       into (crossed panes, fences and walls); any further cells of an unusually complex shape are
 *       not cells to the topology.
 * </ul>
 *
 * <p>The world border, every vanilla height and a sub-cell index can't all fit one {@code long}:
 * 60,000,000² columns × 4,064 heights × 2 is about 2.9 × 10¹⁹, more than 2⁶⁴. The y range is
 * what gives (architecture §4.3).
 *
 * <p>Positions outside that range can't be named, so {@link SectionTopology} treats faces that
 * lead to them as leading outside the world. Since x and z stop short of what 26 bits hold, {@link
 * #NONE} is never the id of a cell. Sorting ids as signed {@code long}s orders cells by x, then z,
 * then y, then sub-cell index (with z's and y's negative values after their positive ones), a
 * fixed order that the fill's results use (architecture §3.5).
 */
public final class CellIds {
    /** The lowest x or z a cell id can hold: Minecraft's world limit. */
    public static final int MIN_XZ = -30_000_000;

    /** The highest x or z a cell id can hold: Minecraft's world limit. */
    public static final int MAX_XZ = 29_999_999;

    /** The lowest y a cell id can hold. */
    public static final int MIN_Y = -(1 << 9);

    /** The highest y a cell id can hold. */
    public static final int MAX_Y = (1 << 9) - 1;

    /** How many cells of one block position an id can name. */
    public static final int MAX_SUB_CELLS = 4;

    /** A value that is never a cell id: its x is beyond {@link #MIN_XZ}. */
    public static final long NONE = Long.MIN_VALUE;

    private static final int SUB_BITS = 2;
    private static final long SUB_MASK = (1L << SUB_BITS) - 1;

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
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((long) (y & 0x3FF) << 2) | sub;
    }

    public static int x(long cell) {
        return (int) (cell >> 38);
    }

    public static int z(long cell) {
        return (int) (cell << 26 >> 38);
    }

    public static int y(long cell) {
        return (int) (cell << 52 >> 54);
    }

    /** The cell's index among its block's cells. */
    public static int sub(long cell) {
        return (int) (cell & SUB_MASK);
    }

    /** The id of the block's first cell: the cell with its sub-cell index cleared. */
    public static long block(long cell) {
        return cell & ~SUB_MASK;
    }

    /** The key of the section holding the cell ({@link SectionKeys}). */
    public static long section(long cell) {
        return SectionKeys.sectionOfBlock(x(cell), y(cell), z(cell));
    }

    /** The cell's position within its section, as {@link SectionKeys#index(int, int, int)}. */
    public static int indexInSection(long cell) {
        return SectionKeys.index(x(cell) & 15, y(cell) & 15, z(cell) & 15);
    }

    /**
     * The id, in this layout, of a cell id saved in the layout of chunk record formats 1 and 2: 24
     * bits of x, 24 of z and 12 of y, then 4 bits of sub-cell index.
     *
     * @return the id, or {@link #NONE} if this layout can't name that cell
     */
    public static long fromFormat2(long old) {
        int x = (int) (old >> 40);
        int z = (int) (old << 24 >> 40);
        int y = (int) (old << 48 >> 52);
        int sub = (int) (old & 0xF);
        return inRange(x, y, z) && sub < MAX_SUB_CELLS ? pack(x, y, z, sub) : NONE;
    }

    /** A readable form, for logs and test messages: {@code (x, y, z)#sub}. */
    public static String toString(long cell) {
        return "(" + x(cell) + ", " + y(cell) + ", " + z(cell) + ")#" + sub(cell);
    }
}
