package io.github.west3436.zas.engine.scheduler;

/**
 * The atmos tick's timing in game ticks of 50 ms (architecture §3.2).
 *
 * <p>Batch N closes at game tick {@code N · periodTicks}, and its result is applied {@code
 * applyDelayTicks} later, at a fixed game tick whenever the worker finishes, so thread
 * scheduling can't change any outcome (CORE-044, CORE-071). The delay must be shorter than the
 * period (architecture O-7), so each result is applied before the next batch closes.
 *
 * @param periodTicks the atmos period T in game ticks; the default, 20, is 1 s (CORE-041, D-20)
 * @param applyDelayTicks the apply delay D in game ticks, at least 1 and less than T; default 10
 */
public record SchedulerSettings(int periodTicks, int applyDelayTicks) {
    /** The game ticks per second. */
    public static final int TICKS_PER_SECOND = 20;

    /** A 1 s period (CORE-041) with results applied half a period after their batch closes. */
    public static final SchedulerSettings DEFAULT = new SchedulerSettings(20, 10);

    /**
     * Checks the timing.
     *
     * @throws IllegalArgumentException if the delay isn't at least 1 and less than the period
     */
    public SchedulerSettings {
        if (periodTicks < 2) {
            throw new IllegalArgumentException("the atmos period must be at least 2 game ticks, got " + periodTicks);
        }
        if (applyDelayTicks < 1 || applyDelayTicks >= periodTicks) {
            throw new IllegalArgumentException("the apply delay must be at least 1 and less than the period ("
                    + periodTicks + " ticks), got " + applyDelayTicks);
        }
    }

    /** The period in seconds. */
    public double periodSeconds() {
        return seconds(periodTicks);
    }

    /** A number of game ticks in seconds. */
    public static double seconds(int gameTicks) {
        return gameTicks / (double) TICKS_PER_SECOND;
    }

    /** Whether a batch closes at this game tick. */
    public boolean closesAt(long gameTick) {
        return gameTick % periodTicks == 0;
    }

    /** The atmos tick whose batch closes at or most recently before this game tick. */
    public long atmosTick(long gameTick) {
        return Math.floorDiv(gameTick, periodTicks);
    }
}
