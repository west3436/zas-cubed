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
 * @param inward whether its particles fly toward the source, as gas drawn in, rather than away
 */
public record JetPlan(
        double perTick,
        int ticks,
        int lifetime,
        double distance,
        double spread,
        float shade,
        float size,
        boolean inward) {
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

    /** The moles in an atmos tick a leak's size scales over: a small tank's trickle to a 70 L tank's most. */
    static final double LEAK_LOW = 0.1;

    static final double LEAK_HIGH = 10;

    /** A leak's game ticks: most of the second until its next atmos tick, so a long leak pulses. */
    static final int LEAK_TICKS = 16;

    private static final int LEAK_LIFETIME = 10;
    private static final double LEAK_SPREAD = Math.toRadians(10);

    /** The moles a burst's size scales over: a small tank to a full canister's 3,700 and more. */
    static final double BURST_LOW = 1;

    static final double BURST_HIGH = 5000;

    /** A burst's game ticks: nearly at once, and short enough that no particles are lost to the cap. */
    static final int BURST_TICKS = 3;

    /** The particles in the smallest burst and the biggest. */
    static final int BURST_FEWEST = 16;

    static final int BURST_MOST = 120;

    private static final int BURST_LIFETIME = 16;

    /** The grey of escaping gas. */
    private static final float GAS_SHADE = 0.82f;

    /**
     * The least flow an open end shows, in moles per second: below it, a trickle shows nothing
     * (CLIENT-019). A lone 70 L pipe reaches it about 0.5 atm above a large room.
     */
    static final double OPEN_END_MIN_FLOW = 0.25;

    /** The flow at and above which an open end's jet is at its biggest, in moles per second. */
    static final double OPEN_END_MAX_FLOW = 64;

    /**
     * An open end's game ticks: its 20-tick atmos tick and a little, so a flowing end's jets run into
     * each other, and one that stops goes quiet within about a second.
     */
    static final int OPEN_END_TICKS = 22;

    private static final int OPEN_END_LIFETIME = 8;
    private static final double OPEN_END_SPREAD = Math.toRadians(12);

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
                        1.0f,
                        false);
            case LEAK -> {
                double t = scale(amount, LEAK_LOW, LEAK_HIGH);
                yield new JetPlan(
                        lerp(0.75, 3, t),
                        LEAK_TICKS,
                        LEAK_LIFETIME,
                        lerp(1, 3, t),
                        LEAK_SPREAD,
                        GAS_SHADE,
                        (float) lerp(0.7, 1, t),
                        false);
            }
            case BURST -> {
                double t = scale(amount, BURST_LOW, BURST_HIGH);
                yield new JetPlan(
                        lerp(BURST_FEWEST, BURST_MOST, t) / BURST_TICKS,
                        BURST_TICKS,
                        BURST_LIFETIME,
                        lerp(1.5, 6, t),
                        Math.PI,
                        GAS_SHADE,
                        (float) lerp(1, 1.6, t),
                        false);
            }
            case OPEN_END -> {
                double t = scale(Math.abs(amount), OPEN_END_MIN_FLOW, OPEN_END_MAX_FLOW);
                yield new JetPlan(
                        lerp(0.2, 2.5, t),
                        OPEN_END_TICKS,
                        OPEN_END_LIFETIME,
                        lerp(0.6, 2.5, t),
                        OPEN_END_SPREAD,
                        GAS_SHADE,
                        (float) lerp(0.5, 0.8, t),
                        amount < 0);
            }
        };
    }

    /**
     * Whether an open end's flow is enough to show (CLIENT-019).
     *
     * @param molesPerSecond its net flow, either way
     * @return whether it is at or above {@link #OPEN_END_MIN_FLOW}; anything that isn't a number isn't
     */
    public static boolean showsFlow(double molesPerSecond) {
        return Math.abs(molesPerSecond) >= OPEN_END_MIN_FLOW;
    }

    /**
     * How big a jet is, 0 to 1, for an amount between two it scales over, by ratio: gas comes in
     * amounts many powers of ten apart, so each doubling adds the same.
     *
     * @param amount the amount
     * @param low the amount at and below which it is 0; anything that isn't a number is 0 too
     * @param high the amount at and above which it is 1
     * @return its size
     */
    static double scale(double amount, double low, double high) {
        if (!(amount > low)) {
            return 0;
        }
        return Math.min(1, Math.log(amount / low) / Math.log(high / low));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
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
