package io.github.west3436.zas.atmos.particle;

/**
 * How much of a jet's particles a client shows under Minecraft's Particles setting (CLIENT-017). The
 * shares are vanilla's own: Decreased drops one particle in three, and Minimal drops them all.
 */
public enum ParticleDensity {
    ALL(1.0),
    DECREASED(2.0 / 3.0),
    MINIMAL(0.0);

    private final double keep;

    ParticleDensity(double keep) {
        this.keep = keep;
    }

    /** The share of particles shown, 0 to 1. */
    public double keep() {
        return keep;
    }
}
