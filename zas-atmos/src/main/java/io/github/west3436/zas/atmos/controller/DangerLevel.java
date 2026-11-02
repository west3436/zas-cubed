package io.github.west3436.zas.atmos.controller;

/**
 * A controller's danger level with its hysteresis (CONT-046): it rises to a reading's level at
 * once, and falls only after {@link #FALL_READINGS} readings in a row below it, 10 s at one reading
 * per atmos tick. It then falls to the highest of those readings.
 */
public final class DangerLevel {
    /** Readings in a row below the level before it falls: 10, one per second. */
    public static final int FALL_READINGS = 10;

    private int level;
    private int lower;
    private int highestLower;

    /** The level, 0 to 2. */
    public int level() {
        return level;
    }

    /**
     * Takes one reading.
     *
     * @param reading the worst danger of this atmos tick's readings, 0 to 2
     * @return the level after it
     */
    public int update(int reading) {
        if (reading >= level) {
            level = reading;
            lower = 0;
            highestLower = 0;
            return level;
        }
        lower++;
        highestLower = Math.max(highestLower, reading);
        if (lower >= FALL_READINGS) {
            level = highestLower;
            lower = 0;
            highestLower = 0;
        }
        return level;
    }

    /** Drops straight to 0, as when the controller loses power. */
    public void reset() {
        level = 0;
        lower = 0;
        highestLower = 0;
    }

    /** The readings in a row below the level so far. */
    public int lowerReadings() {
        return lower;
    }

    /** Restores a saved state; out-of-range values are clamped. */
    public void restore(int level, int lower, int highestLower) {
        this.level = Math.max(0, Math.min(2, level));
        this.lower = Math.max(0, Math.min(FALL_READINGS - 1, lower));
        this.highestLower = Math.max(0, Math.min(this.level, highestLower));
    }

    /** The highest of the lower readings so far, for saving. */
    public int highestLower() {
        return highestLower;
    }
}
