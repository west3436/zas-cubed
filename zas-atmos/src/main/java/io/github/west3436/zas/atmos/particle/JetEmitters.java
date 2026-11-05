package io.github.west3436.zas.atmos.particle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The jets a client is showing, and the limits on them (CLIENT-017). Each jet is a source that makes
 * particles for its plan's few ticks and then ends by itself, so nothing can get stuck on if the
 * server stops sending. It holds no game classes, so the bookkeeping is tested without the game;
 * the client gives it a {@link Sink} that makes the particles.
 *
 * <p>Each game tick a source can make at most {@link #PER_SOURCE} particles, and all of them
 * together at most {@link #TOTAL}, scaled by the Particles setting. When there are more wanted than
 * that, they are shared out evenly, nearest source first, and what doesn't fit is dropped rather
 * than saved for later.
 */
public final class JetEmitters {
    /** The most particles one source makes in a game tick. */
    public static final int PER_SOURCE = 40;

    /** The most particles a client makes for jets in a game tick, at the All setting. */
    public static final int TOTAL = 120;

    /**
     * The most sources it keeps at once. It is well above what the sends and a station's working devices
     * come to, and only a safety limit.
     */
    public static final int MAX_SOURCES = 512;

    /** A source further than this from the camera makes nothing, in blocks: vanilla's particle limit. */
    public static final double RANGE = 32;

    private static final double[] NO_OFFSET = {0, 0, 0};

    /** Makes one particle. */
    @FunctionalInterface
    public interface Sink {
        /**
         * @param x where it starts
         * @param y as {@code x}
         * @param z as {@code x}
         * @param vx its speed, in blocks per game tick
         * @param vy as {@code vx}
         * @param vz as {@code vx}
         * @param lifetime its life, in game ticks
         * @param shade its grey, 0 to 1
         * @param size its size, as a multiple of the sprite's own
         */
        void spawn(
                double x, double y, double z, double vx, double vy, double vz, int lifetime, float shade, float size);
    }

    /** What makes two jets one source: a new one at the same place takes over from the old. */
    private record Key(GasJetKind kind, double x, double y, double z) {}

    private static final class Source {
        final double x;
        final double y;
        final double z;
        double ax;
        double ay;
        double az;
        JetPlan plan;
        int ticksLeft;
        double carry;
        double distanceSq;

        Source(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Map<Key, Source> sources = new LinkedHashMap<>();
    private final RandomGenerator random;

    /** @param random the source of each particle's direction */
    public JetEmitters(RandomGenerator random) {
        this.random = random;
    }

    /**
     * Starts a jet, or takes over from the same kind of jet at the same place.
     *
     * @param kind what it is
     * @param x where it comes from
     * @param y as {@code x}
     * @param z as {@code x}
     * @param dx its direction; all zero means every direction
     * @param dy as {@code dx}
     * @param dz as {@code dx}
     * @param amount its amount, as {@code kind} says
     */
    public void add(GasJetKind kind, double x, double y, double z, double dx, double dy, double dz, double amount) {
        Key key = new Key(kind, x, y, z);
        Source source = sources.get(key);
        if (source == null) {
            if (sources.size() >= MAX_SOURCES) {
                return;
            }
            source = new Source(x, y, z);
            sources.put(key, source);
        }
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean aimed = len > 1e-6 && len < Double.POSITIVE_INFINITY;
        source.ax = aimed ? dx / len : 0;
        source.ay = aimed ? dy / len : 0;
        source.az = aimed ? dz / len : 0;
        source.plan = JetPlan.of(kind, amount);
        source.ticksLeft = source.plan.ticks();
    }

    /**
     * One game tick: every source makes its share of particles, and those that have finished end.
     *
     * @param camX the camera, for the sources' distances
     * @param camY as {@code camX}
     * @param camZ as {@code camX}
     * @param density the Particles setting
     * @param sink makes the particles
     */
    public void tick(double camX, double camY, double camZ, ParticleDensity density, Sink sink) {
        if (sources.isEmpty()) {
            return;
        }
        List<Source> showing = new ArrayList<>(sources.size());
        for (Iterator<Source> it = sources.values().iterator(); it.hasNext(); ) {
            Source s = it.next();
            if (--s.ticksLeft < 0) {
                it.remove();
                continue;
            }
            double dx = s.x - camX;
            double dy = s.y - camY;
            double dz = s.z - camZ;
            s.distanceSq = dx * dx + dy * dy + dz * dz;
            if (s.distanceSq <= RANGE * RANGE) {
                s.carry += s.plan.perTick() * density.keep();
                showing.add(s);
            }
        }
        showing.sort(Comparator.comparingDouble(s -> s.distanceSq));
        int[] wanted = new int[showing.size()];
        for (int i = 0; i < wanted.length; i++) {
            Source s = showing.get(i);
            wanted[i] = Math.min(PER_SOURCE, (int) s.carry);
            s.carry -= (int) s.carry;
        }
        int[] granted = share(wanted, (int) Math.round(TOTAL * density.keep()));
        for (int i = 0; i < granted.length; i++) {
            Source s = showing.get(i);
            for (int n = 0; n < granted[i]; n++) {
                emit(s, sink);
            }
        }
    }

    /**
     * Makes one particle of a source, part of the way along its first tick so they don't bunch. One
     * going inward starts as far out as it would have ended, and flies back to the source.
     */
    private void emit(Source s, Sink sink) {
        JetPlan plan = s.plan;
        double[] d = JetPlan.direction(s.ax, s.ay, s.az, plan.spread(), random);
        double speed = plan.speed();
        double along = speed * random.nextDouble();
        double out = plan.inward() ? plan.distance() - along : along;
        double way = plan.inward() ? -speed : speed;
        double[] at = plan.patch() > 0 ? JetPlan.across(s.ax, s.ay, s.az, plan.patch(), random) : NO_OFFSET;
        sink.spawn(
                s.x + at[0] + d[0] * out,
                s.y + at[1] + d[1] * out,
                s.z + at[2] + d[2] * out,
                d[0] * way,
                d[1] * way,
                d[2] * way,
                plan.lifetime(),
                plan.shade(),
                plan.size());
    }

    /**
     * Shares out a number of particles among sources one at a time, round and round, so a source that
     * wants few gets them all before one that wants many gets more.
     *
     * @param wanted how many each source wants, in order of who goes first
     * @param total how many there are to share
     * @return how many each gets
     */
    static int[] share(int[] wanted, int total) {
        int[] granted = new int[wanted.length];
        int left = total;
        boolean more = true;
        while (left > 0 && more) {
            more = false;
            for (int i = 0; i < wanted.length && left > 0; i++) {
                if (granted[i] < wanted[i]) {
                    granted[i]++;
                    left--;
                    more = true;
                }
            }
        }
        return granted;
    }

    /** Ends every jet, as when the client leaves a world. */
    public void clear() {
        sources.clear();
    }

    /** The jets going on now. */
    public int sources() {
        return sources.size();
    }
}
