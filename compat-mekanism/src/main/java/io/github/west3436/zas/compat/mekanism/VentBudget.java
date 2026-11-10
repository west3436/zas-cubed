package io.github.west3436.zas.compat.mekanism;

import java.util.HashMap;
import java.util.Map;

/**
 * How much each source may still release (MEK-021): at most a cap in each second, counted for each
 * source on its own.
 *
 * <p>A second is {@link #WINDOW_TICKS} game ticks, and every source's count starts again when the
 * next one begins, so the record holds only the sources that released something in the current
 * second and never grows beyond them. A source is any key with value equality; the venting uses a
 * dimension and a block position.
 *
 * <p>Pure arithmetic: this class names nothing of the game's, Mekanism's or ZAS³'s. Not
 * thread-safe; the venting uses one instance on the server thread.
 */
public final class VentBudget {
    /** The ticks in one second, the span a cap is counted over. */
    public static final int WINDOW_TICKS = 20;

    /** The default of {@code [venting] enabled}. */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default of {@code [venting] max_millibuckets_per_second}: 20,000 mB, 1,000 mB a tick. At
     * 1 L per mB that is 20 m³ of gas a second, twenty blocks of air.
     */
    public static final long DEFAULT_MAX_PER_SECOND = 20_000L;

    /** The default of {@code [venting] release_above_cap}. */
    public static final boolean DEFAULT_RELEASE_ABOVE_CAP = false;

    /** What each source has been granted in the current second. */
    private final Map<Object, long[]> granted = new HashMap<>();

    /** The second the counts are for, as a tick count divided by {@link #WINDOW_TICKS}. */
    private long window = Long.MIN_VALUE;

    /**
     * Grants a source as much of an amount as its cap still allows this second, and counts it.
     *
     * @param source the source, a key with value equality
     * @param tick the game tick now; a tick in another second than the last call's starts every
     *     count again
     * @param wanted the amount asked for, in mB
     * @param cap the most a source gets in one second, in mB
     * @return the amount granted, from 0 to {@code wanted}
     */
    public long grant(Object source, long tick, long wanted, long cap) {
        long now = Math.floorDiv(tick, WINDOW_TICKS);
        if (now != window) {
            granted.clear();
            window = now;
        }
        if (wanted <= 0 || cap <= 0) {
            return 0;
        }
        long[] used = granted.computeIfAbsent(source, key -> new long[1]);
        long amount = Math.min(wanted, Math.max(0, cap - used[0]));
        used[0] += amount;
        return amount;
    }

    /**
     * How many sources have a count in the current second.
     *
     * @return the number of sources recorded
     */
    public int sources() {
        return granted.size();
    }

    /** Forgets every count, as when a server stops. */
    public void clear() {
        granted.clear();
        window = Long.MIN_VALUE;
    }
}
