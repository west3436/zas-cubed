package io.github.west3436.zas.airflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Where a client's airflow particles go each game tick (CLIENT-003): the density and speed curves
 * and the caps, with nothing of Minecraft's. The client hands each result to Minecraft's particle
 * engine.
 *
 * <ul>
 *   <li><b>Density</b> follows the pressure difference, per opening: {@link #perOpening}. An edge's
 *       openings add up to at most {@value #MAX_PER_FLOW} a tick, so a wide breach is dense rather
 *       than overwhelming, and all edges to at most {@value #MAX_PER_TICK}, spent on the nearest
 *       edge first.
 *   <li><b>Speed</b> also follows the difference: {@link #speed}.
 *   <li><b>Minecraft's Particles setting</b> scales the density: {@link #ALL}, {@link #DECREASED}
 *       and {@link #MINIMAL}.
 * </ul>
 *
 * <p>A particle starts a little on the high side of the opening's face, so it is seen passing
 * through, and spreads out as it goes towards the low side.
 */
public final class AirflowParticlePlan {
    /** The most particles one edge starts in a game tick. */
    public static final int MAX_PER_FLOW = 8;

    /** The most particles a client starts in a game tick, over all edges. */
    public static final int MAX_PER_TICK = 32;

    /** The density at Minecraft's Particles setting All. */
    public static final double ALL = 1;

    /** The density at Decreased: vanilla's own share, as it drops one particle in three. */
    public static final double DECREASED = 2.0 / 3.0;

    /** The density at Minimal: none. */
    public static final double MINIMAL = 0;

    /** The difference at which one opening starts a particle every game tick, kPa. */
    private static final double FULL_DENSITY_KPA = 200;

    /** The slowest particle, blocks per game tick, as the difference nears 0. */
    private static final double MIN_SPEED = 0.1;

    /** What one atmosphere of difference adds to the speed, blocks per game tick. */
    private static final double SPEED_PER_ATM = 0.35;

    /** The fastest particle, blocks per game tick. */
    private static final double MAX_SPEED = 0.6;

    private static final double ONE_ATM = 101.325;

    /** How far a particle may start across the opening from its middle, blocks. */
    private static final double START_SPREAD = 0.45;

    /** Where along the flow a particle may start, from the face: on the high side up to just past it. */
    private static final double START_BEHIND = 0.6;

    private static final double START_AHEAD = 0.2;

    /** How much of its speed a particle may have across the flow, in either direction. */
    private static final double SIDEWAYS = 0.12;

    /** How much a particle's speed along the flow may differ from the curve's, in either direction. */
    private static final double SPEED_SPREAD = 0.2;

    private AirflowParticlePlan() {}

    /** Takes each particle to start. */
    @FunctionalInterface
    public interface Sink {
        void particle(double x, double y, double z, double vx, double vy, double vz);
    }

    /** The particles one opening starts per game tick at this difference, before the caps. */
    public static double perOpening(int kpa) {
        return Math.min(1, Math.max(0, kpa) / FULL_DENSITY_KPA);
    }

    /** A particle's speed along the flow at this difference, blocks per game tick. */
    public static double speed(int kpa) {
        return Math.min(MAX_SPEED, MIN_SPEED + SPEED_PER_ATM * Math.max(0, kpa) / ONE_ATM);
    }

    /**
     * Plans one game tick's particles.
     *
     * @param flows what the client holds
     * @param px the player's centre
     * @param density the Particles setting's density
     * @return the number of particles started
     */
    public static int plan(
            List<VisibleFlow> flows,
            double px,
            double py,
            double pz,
            double density,
            RandomGenerator random,
            Sink sink) {
        if (density <= 0 || flows.isEmpty()) {
            return 0;
        }
        record InRange(VisibleFlow flow, List<VisibleOpening> openings, double nearest) {}
        List<InRange> near = new ArrayList<>(flows.size());
        for (VisibleFlow flow : flows) {
            List<VisibleOpening> openings = new ArrayList<>(flow.openings().size());
            double nearest = Double.MAX_VALUE;
            for (VisibleOpening o : flow.openings()) {
                double d = o.distance(px, py, pz);
                if (d <= VisibleFlows.RANGE) {
                    openings.add(o);
                    nearest = Math.min(nearest, d);
                }
            }
            if (!openings.isEmpty()) {
                near.add(new InRange(flow, openings, nearest));
            }
        }
        near.sort(Comparator.comparingDouble(InRange::nearest));
        int left = MAX_PER_TICK;
        for (InRange in : near) {
            if (left <= 0) {
                break;
            }
            int kpa = in.flow().kpa();
            double wanted = Math.min(MAX_PER_FLOW, in.openings().size() * perOpening(kpa)) * density;
            int count = Math.min(left, whole(wanted, random));
            for (int i = 0; i < count; i++) {
                start(in.openings().get(random.nextInt(in.openings().size())), speed(kpa), random, sink);
            }
            left -= count;
        }
        return MAX_PER_TICK - left;
    }

    /** {@code wanted} as a whole number, rounded up with the chance of its fraction. */
    private static int whole(double wanted, RandomGenerator random) {
        int floor = (int) wanted;
        return random.nextDouble() < wanted - floor ? floor + 1 : floor;
    }

    private static void start(VisibleOpening o, double speed, RandomGenerator random, Sink sink) {
        double along = random.nextDouble() * (START_BEHIND + START_AHEAD) - START_BEHIND;
        double[] at = {o.mx(), o.my(), o.mz()};
        int[] n = {o.nx(), o.ny(), o.nz()};
        double[] v = new double[3];
        boolean directed = o.face() != VisibleOpening.NO_FACE;
        double forward = speed * (1 + spread(random, SPEED_SPREAD));
        for (int axis = 0; axis < 3; axis++) {
            if (n[axis] != 0) {
                at[axis] += n[axis] * along;
                v[axis] = n[axis] * forward;
            } else {
                at[axis] += spread(random, START_SPREAD);
                v[axis] = speed * spread(random, directed ? SIDEWAYS : SIDEWAYS * 2);
            }
        }
        sink.particle(at[0], at[1], at[2], v[0], v[1], v[2]);
    }

    /** A random value between {@code -size} and {@code size}. */
    private static double spread(RandomGenerator random, double size) {
        return (random.nextDouble() * 2 - 1) * size;
    }
}
