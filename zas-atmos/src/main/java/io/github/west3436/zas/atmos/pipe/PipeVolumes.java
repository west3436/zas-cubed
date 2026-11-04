package io.github.west3436.zas.atmos.pipe;

/** The gas volumes of network parts, in L (CONT-007, CONT-017). */
public final class PipeVolumes {
    /** A pipe segment, as in vg; a pipe cap is a segment too. */
    public static final double PIPE = 70;

    /** A device port's buffer, as in vg. */
    public static final double PORT = 200;

    /** A valve's body, one port's buffer, as in vg. */
    public static final double VALVE = PORT;

    /** A vent's buffer: twice a port's, so it can keep up its rate (CONT-078). */
    public static final double VENT = 400;

    /** A canister. */
    public static final double CANISTER = 1000;

    private PipeVolumes() {}
}
