package io.github.west3436.zas.engine;

/**
 * One of the six faces of a block position.
 *
 * <p>The order matches Minecraft's 3D direction index (down, up, north, south, west, east), so
 * the platform converts with {@code Face.of(direction.get3DDataValue())}.
 *
 * <p>Each face has a 16×16 grid of {@linkplain FaceMask mask} bits addressed by {@code (u, v)}.
 * The axes are world-aligned, so the two sides of a shared face use the same coordinates and
 * their masks can be combined bit for bit:
 *
 * <ul>
 *   <li>{@link #DOWN}, {@link #UP}: {@code u = x}, {@code v = z}
 *   <li>{@link #NORTH}, {@link #SOUTH}: {@code u = x}, {@code v = y}
 *   <li>{@link #WEST}, {@link #EAST}: {@code u = z}, {@code v = y}
 * </ul>
 */
public enum Face {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private static final Face[] VALUES = values();

    private final int dx;
    private final int dy;
    private final int dz;

    Face(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** The face with the given index, as returned by {@link #ordinal()}. */
    public static Face of(int index) {
        return VALUES[index];
    }

    /** The face of the neighbouring position that touches this one. */
    public Face opposite() {
        return VALUES[ordinal() ^ 1];
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }
}
