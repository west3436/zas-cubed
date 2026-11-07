package io.github.west3436.zas.atmos.pipe;

/**
 * The faces of a pipe that show an open mouth: the faces it leaks from (CONT-004). A pipe with one
 * connection leaks from the face opposite it, and a pipe with none from every face. A pipe with two or
 * more has no open end, so straight runs, bends and tees have no mouth. The pipe's blockstate puts its
 * flared mouth model on these faces, and {@link PipeBlock} adds them to its shape.
 *
 * <p>Faces are bits by the ordinal of Minecraft's {@code Direction}: down, up, north, south, west, east.
 * Each pair is a face and its opposite, so flipping an ordinal's lowest bit gives the opposite face.
 */
public final class PipeMouths {
    /** Every face. */
    public static final int ALL = 0b111111;

    private PipeMouths() {}

    /**
     * The faces with a mouth.
     *
     * @param arms a bit for each face the pipe connects on
     * @return a bit for each face with a mouth
     */
    public static int of(int arms) {
        if (arms == 0) {
            return ALL;
        }
        if (Integer.bitCount(arms) == 1) {
            return 1 << (Integer.numberOfTrailingZeros(arms) ^ 1);
        }
        return 0;
    }
}
