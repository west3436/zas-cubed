package zas.spike;

/**
 * Per-state geometry table, the shape GEO-040 caches: six 8x8 face coverage
 * masks and a gas volume. A face between two positions is gas-tight when the
 * union of both sides' coverage is full (GEO-003).
 */
public final class Shapes {
    public static final short AIR = 0;
    public static final short STONE = 1;
    public static final short BOTTOM_SLAB = 2;
    public static final short GLASS = 3;
    public static final short TOP_SLAB = 4;
    public static final int COUNT = 5;

    static final long FULL = -1L;
    static final long LOWER_HALF = 0x00000000FFFFFFFFL; // rows 0-3 of 8
    static final long UPPER_HALF = 0xFFFFFFFF00000000L;

    /** coverage[state * 6 + dir]. */
    public static final long[] COVERAGE = new long[COUNT * 6];
    public static final float[] VOLUME = new float[COUNT];

    static {
        for (int d = 0; d < 6; d++) {
            COVERAGE[STONE * 6 + d] = FULL;
            COVERAGE[GLASS * 6 + d] = FULL;
        }
        COVERAGE[BOTTOM_SLAB * 6] = FULL;
        COVERAGE[TOP_SLAB * 6 + 1] = FULL;
        for (int d = 2; d < 6; d++) {
            COVERAGE[BOTTOM_SLAB * 6 + d] = LOWER_HALF;
            COVERAGE[TOP_SLAB * 6 + d] = UPPER_HALF;
        }
        VOLUME[AIR] = 1000f;
        VOLUME[BOTTOM_SLAB] = 500f;
        VOLUME[TOP_SLAB] = 500f;
    }

    private Shapes() {}

    public static boolean open(short a, int d, short b) {
        return (COVERAGE[a * 6 + d] | COVERAGE[b * 6 + (d ^ 1)]) != FULL;
    }
}
