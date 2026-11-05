package io.github.west3436.zas.sound;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * When gas catching fire plays its whoomp, and how loud (FIRE-036), with no game classes.
 *
 * <p>A fire that keeps relighting would otherwise whoomp each time it does, so a whoomp silences
 * others within {@value #RADIUS} blocks for {@value #QUIET_TICKS} game ticks. A much louder one plays
 * anyway: it carries further, so a listener may not have heard the one that would silence it. One
 * instance covers one dimension. Not thread-safe: the server thread only.
 */
public final class IgnitionWhoomps {
    /** How far a whoomp silences others, in blocks: the range of one at volume 1. */
    public static final int RADIUS = 16;

    /** How long a whoomp silences others, in game ticks: 5 s. */
    public static final int QUIET_TICKS = 100;

    /**
     * How much louder than a recent whoomp another must be to play through its quiet: about three times
     * the fuel. A room relighting on slowly rising fuel stays quiet.
     */
    static final double LOUDER_BY = 0.3;

    /** The volume of the smallest whoomp. */
    static final double SMALLEST_VOLUME = 0.4;

    /** The volume of the largest whoomp, whose range is then 48 blocks. */
    static final double LARGEST_VOLUME = 3;

    /** What ten times the fuel adds to the volume. */
    static final double VOLUME_PER_DECADE = 0.6;

    private record Whoomp(int x, int y, int z, long tick, float volume) {}

    private final Deque<Whoomp> recent = new ArrayDeque<>();

    /**
     * Whether gas catching fire here plays its whoomp now. If so, it counts from now.
     *
     * @param x where
     * @param y where
     * @param z where
     * @param tick the game tick
     * @param volume its {@linkplain #volume volume}
     * @return false if another played nearby too recently, and this one isn't much louder
     */
    public boolean allow(int x, int y, int z, long tick, float volume) {
        while (!recent.isEmpty() && tick - recent.peekFirst().tick() >= QUIET_TICKS) {
            recent.removeFirst();
        }
        for (Whoomp w : recent) {
            long dx = w.x() - x;
            long dy = w.y() - y;
            long dz = w.z() - z;
            if (dx * dx + dy * dy + dz * dz <= (long) RADIUS * RADIUS && volume < w.volume() + LOUDER_BY) {
                return false;
            }
        }
        recent.addLast(new Whoomp(x, y, z, tick, volume));
        return true;
    }

    /**
     * How loud a whoomp is for what is burning: {@value #SMALLEST_VOLUME} up to 1 mol of fuel, then
     * {@value #VOLUME_PER_DECADE} more for each ten times the fuel, up to {@value #LARGEST_VOLUME}. A
     * volume above 1 doesn't get louder at the source, because the game clamps it, but it carries
     * further: 16 blocks times the volume.
     *
     * @param fuelMoles the moles of fuel in the gas that caught
     * @return the volume to play it at
     */
    public static float volume(double fuelMoles) {
        double decades = fuelMoles > 1 ? Math.log10(fuelMoles) : 0;
        return (float) Math.min(LARGEST_VOLUME, SMALLEST_VOLUME + VOLUME_PER_DECADE * decades);
    }
}
