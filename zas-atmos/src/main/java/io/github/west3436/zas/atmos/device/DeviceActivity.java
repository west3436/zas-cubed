package io.github.west3436.zas.atmos.device;

/**
 * How hard a device is working (DATA-017): the moles of gas it moved in its last atmos tick, read
 * against a scale of four levels, with hysteresis so the level doesn't flicker.
 *
 * <table>
 *   <caption>The scale, in moles per atmos tick (a second), at 1 atm and 293 K</caption>
 *   <tr><th>Level</th><th>From</th><th>Falls below</th><th>For example</th></tr>
 *   <tr><td>{@link #IDLE}</td><td>0</td><td></td><td>A vent holding a room against a small leak; a
 *       scrubber on trace CO₂</td></tr>
 *   <tr><td>{@link #TRICKLE}</td><td>0.05</td><td>0.025</td><td>A scrubber on 1 kPa of CO₂: 0.2</td></tr>
 *   <tr><td>{@link #WORKING}</td><td>1</td><td>0.5</td><td>A scrubber on 10 kPa of CO₂: 2. A vent on a
 *       half-empty line: 4</td></tr>
 *   <tr><td>{@link #HARD}</td><td>5</td><td>2.5</td><td>A vent on a full line filling a vacuum: 8. A
 *       scrubber siphoning: 21, or in panic: 42</td></tr>
 * </table>
 *
 * <p>A level rises at once. It falls only once {@value #HOLD_STEPS} steps running have read below its
 * falling bound, so a device that moves gas every other step holds steady. A device that was switched
 * off says so ({@link #stopNow}) and drops at once.
 *
 * <p>Platform-free. Server thread only.
 */
public final class DeviceActivity {
    /** Not moving gas, or next to none. No loop and no particles. */
    public static final int IDLE = 0;

    /** Moving a little: a quiet loop, and no particles. */
    public static final int TRICKLE = 1;

    /** Moving a fair amount. */
    public static final int WORKING = 2;

    /** Moving a lot, as when filling a vacuum or scrubbing hard. */
    public static final int HARD = 3;

    /** Moles per atmos tick at which each level starts. */
    private static final double[] RISES = {0, 0.05, 1, 5};

    /** A level falls below this fraction of where it starts. */
    private static final double FALL_RATIO = 0.5;

    /** How many steps running must read low before a level falls. */
    static final int HOLD_STEPS = 2;

    private double moved;
    private int level = IDLE;
    private int lowSteps;
    private boolean stopped;

    /** Adds to what the device moved in this step. Anything not above 0 counts for nothing. */
    public void moved(double moles) {
        if (moles > 0) {
            moved += moles;
        }
    }

    /** The device has stopped, as when switched off: this step's level is {@link #IDLE}, at once. */
    public void stopNow() {
        stopped = true;
    }

    /**
     * Ends a step.
     *
     * @return the level after it
     */
    public int finishStep() {
        double moles = moved;
        moved = 0;
        if (stopped) {
            stopped = false;
            level = IDLE;
            lowSteps = 0;
            return level;
        }
        int rising = levelOf(moles);
        if (rising >= level || moles >= falls(level)) {
            level = Math.max(level, rising);
            lowSteps = 0;
        } else if (++lowSteps >= HOLD_STEPS) {
            lowSteps = 0;
            while (level > IDLE && moles < falls(level)) {
                level--;
            }
        }
        return level;
    }

    /** The level now. */
    public int level() {
        return level;
    }

    /**
     * How loud a device's loop plays at a level, before its distance and thin air.
     *
     * @return 0 to 1, and 0 only when idle
     */
    public static float volume(int level) {
        return switch (level) {
            case TRICKLE -> 0.35f;
            case WORKING -> 0.6f;
            case HARD -> 1f;
            default -> 0f;
        };
    }

    /** The level that {@code moles} in a step rises to. */
    private static int levelOf(double moles) {
        int k = IDLE;
        while (k < HARD && moles >= RISES[k + 1]) {
            k++;
        }
        return k;
    }

    private static double falls(int level) {
        return RISES[level] * FALL_RATIO;
    }
}
