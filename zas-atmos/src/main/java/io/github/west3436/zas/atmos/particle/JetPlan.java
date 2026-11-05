package io.github.west3436.zas.atmos.particle;

import java.util.random.RandomGenerator;

/**
 * What a jet looks like: how many particles it makes, over how long, how far they travel and how
 * they are tinted. It is plain arithmetic, so its numbers are tested without the game; the client
 * only turns the result into particles.
 *
 * @param perTick particles per game tick, at the All setting; a fraction builds up over ticks
 * @param ticks the game ticks it goes on for
 * @param lifetime each particle's life, in game ticks
 * @param distance how far each particle travels in its life, in blocks
 * @param spread the half-angle of its cone, in radians; {@link Math#PI} is every direction
 * @param shade its grey, 0 black to 1 white: neutral, never a gas's colour (CLIENT-017)
 * @param size its size, as a multiple of the sprite's own
 */
public record JetPlan(
        double perTick, int ticks, int lifetime, double distance, double spread, float shade, float size) {
    /**
     * How much of its speed the sprite keeps each game tick: vanilla's poof, which is what the client
     * spawns. A particle's speed comes from this and {@link #lifetime}, so it ends {@link #distance} away.
     */
    public static final double FRICTION = 0.9;

    /** A spray's particles. Sprays come {@link #SPRAY_TICKS} apart, so one runs into the next. */
    static final int SPRAY_PARTICLES = 10;

    /** The game ticks between an extinguisher's sprays. */
    public static final int SPRAY_TICKS = 4;

    /** A spray's half-angle: at its 5-block reach it is about as wide as the 3 blocks it puts out. */
    static final double SPRAY_SPREAD = Math.toRadians(14);

    /** A spray too short to fly further than this still shows, against the wall that stopped it. */
    static final double SPRAY_MIN_DISTANCE = 0.5;

    private static final int SPRAY_LIFETIME = 14;
    private static final float SPRAY_SHADE = 0.94f;

    /**
     * The plan for a jet.
     *
     * @param kind what it is
     * @param amount its amount, as {@code kind} says
     * @return its plan
     */
    public static JetPlan of(GasJetKind kind, double amount) {
        return switch (kind) {
            case SPRAY ->
                new JetPlan(
                        (double) SPRAY_PARTICLES / SPRAY_TICKS,
                        SPRAY_TICKS,
                        SPRAY_LIFETIME,
                        Math.max(SPRAY_MIN_DISTANCE, positive(amount)),
                        SPRAY_SPREAD,
                        SPRAY_SHADE,
                        1.0f);
        };
    }

    /** An amount, with anything that isn't a positive number as 0. */
    private static double positive(double amount) {
        return amount > 0 && amount < Double.POSITIVE_INFINITY ? amount : 0;
    }

    /** All the particles it makes, at the All setting. */
    public double particles() {
        return perTick * ticks;
    }

    /**
     * The speed a particle starts at, in blocks per game tick, to end {@link #distance} away given
     * {@link #FRICTION}: the sum of its speeds over its life is a geometric series.
     */
    public double speed() {
        return distance * (1 - FRICTION) / (1 - Math.pow(FRICTION, lifetime));
    }

    /**
     * A random direction within a cone, evenly over the part of the sphere it covers.
     *
     * @param ax the cone's axis, a unit vector; all zero means every direction
     * @param ay as {@code ax}
     * @param az as {@code ax}
     * @param spread the cone's half-angle, in radians
     * @param random the source of the direction
     * @return a unit vector, as {x, y, z}
     */
    public static double[] direction(double ax, double ay, double az, double spread, RandomGenerator random) {
        boolean noAxis = ax == 0 && ay == 0 && az == 0;
        double cos = 1 - random.nextDouble() * (1 - Math.cos(noAxis ? Math.PI : spread));
        double sin = Math.sqrt(Math.max(0, 1 - cos * cos));
        double around = random.nextDouble() * 2 * Math.PI;
        double a = sin * Math.cos(around);
        double b = sin * Math.sin(around);
        if (noAxis) {
            return new double[] {a, b, cos};
        }
        // Two unit vectors at right angles to the axis, from whichever world axis it is least along.
        double ux;
        double uy;
        double uz;
        if (Math.abs(ax) < 0.9) {
            double len = Math.sqrt(ay * ay + az * az);
            ux = 0;
            uy = az / len;
            uz = -ay / len;
        } else {
            double len = Math.sqrt(ax * ax + az * az);
            ux = -az / len;
            uy = 0;
            uz = ax / len;
        }
        double vx = ay * uz - az * uy;
        double vy = az * ux - ax * uz;
        double vz = ax * uy - ay * ux;
        return new double[] {
            ax * cos + ux * a + vx * b, ay * cos + uy * a + vy * b, az * cos + uz * a + vz * b,
        };
    }
}
