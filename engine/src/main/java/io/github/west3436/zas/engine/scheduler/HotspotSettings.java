package io.github.west3436.zas.engine.scheduler;

import java.util.Objects;

/**
 * Hotspot settings a {@link Batch} carries (FIRE-011, FIRE-016), so a change applies from a batch
 * boundary.
 *
 * @param spreadChance the probability per second that a burning hotspot spreads to each adjacent
 *     combustible cell (FIRE-016), from 0 to 1
 * @param combustion the chemistry hotspots burn with
 */
public record HotspotSettings(double spreadChance, Combustion combustion) {
    /** The default spread chance per second (FIRE-016). */
    public static final double DEFAULT_SPREAD_CHANCE = 0.3;

    /** The parcel a hotspot burns each second, in litres: 0.375 cell volumes (FIRE-011). */
    public static final double PARCEL_LITRES_PER_SECOND = 375;

    /** The default spread chance, with nothing to burn. */
    public static final HotspotSettings DEFAULT = new HotspotSettings(DEFAULT_SPREAD_CHANCE, Combustion.NONE);

    public HotspotSettings {
        if (!(spreadChance >= 0 && spreadChance <= 1)) {
            throw new IllegalArgumentException("spreadChance must be in [0, 1]: " + spreadChance);
        }
        Objects.requireNonNull(combustion, "combustion");
    }

    /** These settings with another spread chance. */
    public HotspotSettings withSpreadChance(double chance) {
        return new HotspotSettings(chance, combustion);
    }

    /** These settings burning with {@code chemistry}. */
    public HotspotSettings withCombustion(Combustion chemistry) {
        return new HotspotSettings(spreadChance, chemistry);
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
