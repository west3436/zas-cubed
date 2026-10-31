package zas.spike;

/** Packed block positions and section keys, like BlockPos#asLong. */
public final class Pos {
    private Pos() {}

    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long p) {
        return (int) (p >> 38);
    }

    public static int y(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int z(long p) {
        return (int) (p << 26 >> 38);
    }

    public static long offset(long p, int d) {
        return pack(x(p) + DX[d], y(p) + DY[d], z(p) + DZ[d]);
    }

    public static long section(int x, int y, int z) {
        return pack(x >> 4, y >> 4, z >> 4);
    }

    public static long chunk(int x, int z) {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }

    public static int index(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }
}
