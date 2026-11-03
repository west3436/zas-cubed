package io.github.west3436.zas.engine.scheduler;

/**
 * Hotspot settings a {@link Batch} carries (FIRE-016), so a change applies from a batch boundary.
 * Hotspots burn with the batch's {@link EngineSettings#reactions()}.
 *
 * @param spreadChance the probability per second that a burning hotspot spreads to each adjacent
 *     combustible cell (FIRE-016), from 0 to 1
 */
public record HotspotSettings(double spreadChance) {
    /** The default spread chance per second (FIRE-016). */
    public static final double DEFAULT_SPREAD_CHANCE = 0.3;

    /** The default spread chance. */
    public static final HotspotSettings DEFAULT = new HotspotSettings(DEFAULT_SPREAD_CHANCE);

    public HotspotSettings {
        if (!(spreadChance >= 0 && spreadChance <= 1)) {
            throw new IllegalArgumentException("spreadChance must be in [0, 1]: " + spreadChance);
        }
    }

    /**
     * The chance a hotspot spreads to one neighbour in an atmos tick of {@code seconds}: 1 − (1 −
     * p)^t, so the per-second chance holds whatever the period.
     */
    public double chanceIn(double seconds) {
        if (spreadChance >= 1) {
            return 1;
        }
        return 1 - Math.pow(1 - spreadChance, seconds);
    }
}
