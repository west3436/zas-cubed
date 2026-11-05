package io.github.west3436.zas.airflow;

/**
 * One opening of an active edge, as a client needs it to show the flow (CLIENT-003, DATA-015): the
 * block on the higher-pressure side and the face the flow leaves it by. The low side is the block
 * across that face. Platform-free.
 *
 * @param x the high side's block x
 * @param y the high side's block y
 * @param z the high side's block z
 * @param face the face the flow leaves by, in the order down, up, north, south, west, east; or
 *     {@link #NO_FACE} for two cells in one block, whose flow has no single direction
 */
public record VisibleOpening(int x, int y, int z, int face) {
    /** The {@link #face} of an opening between two cells of one block. */
    public static final int NO_FACE = 6;

    private static final int[][] NORMALS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 0, 0}
    };

    public VisibleOpening {
        if (face < 0 || face > NO_FACE) {
            throw new IllegalArgumentException("bad face: " + face);
        }
    }

    /** The face from a block to the one at this offset, or {@link #NO_FACE} if it isn't adjacent. */
    public static int faceOf(int dx, int dy, int dz) {
        for (int face = 0; face < NO_FACE; face++) {
            int[] n = NORMALS[face];
            if (n[0] == dx && n[1] == dy && n[2] == dz) {
                return face;
            }
        }
        return NO_FACE;
    }

    /** The x of the flow's unit direction, towards the low side; all 0 for {@link #NO_FACE}. */
    public int nx() {
        return NORMALS[face][0];
    }

    public int ny() {
        return NORMALS[face][1];
    }

    public int nz() {
        return NORMALS[face][2];
    }

    /** The x of the middle of the face between the two sides. */
    public double mx() {
        return x + 0.5 + nx() * 0.5;
    }

    public double my() {
        return y + 0.5 + ny() * 0.5;
    }

    public double mz() {
        return z + 0.5 + nz() * 0.5;
    }

    /** The distance from a point to the middle of the face. */
    public double distance(double px, double py, double pz) {
        double dx = px - mx();
        double dy = py - my();
        double dz = pz - mz();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
