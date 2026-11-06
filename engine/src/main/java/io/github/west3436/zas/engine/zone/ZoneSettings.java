package io.github.west3436.zas.engine.zone;

/**
 * What happens to zones apart from how they exchange gas ({@link ExchangeSettings}).
 *
 * @param deleteDisplacedGas whether the gas of a cell a block fills is deleted, its share of the zone's
 *     by volume, as ZAS does (CORE-029), rather than staying in the zone (CORE-022, CORE-075)
 * @param sealedDissolveSeconds how long a sealed zone must match the ambient before it dissolves into
 *     it, in s (CORE-052)
 * @throws IllegalArgumentException if the delay is negative or not a number
 */
public record ZoneSettings(boolean deleteDisplacedGas, double sealedDissolveSeconds) {
    /** Displaced gas is conserved (D-41), and a sealed zone dissolves after a minute at the ambient. */
    public static final ZoneSettings DEFAULT = new ZoneSettings(false, 60);

    public ZoneSettings {
        if (!(sealedDissolveSeconds >= 0)) {
            throw new IllegalArgumentException("sealedDissolveSeconds must be at least 0: " + sealedDissolveSeconds);
        }
    }

    /** These settings with displaced gas deleted or kept (CORE-029). */
    public ZoneSettings withDeleteDisplacedGas(boolean delete) {
        return new ZoneSettings(delete, sealedDissolveSeconds);
    }

    /** These settings with another delay before a sealed zone dissolves (CORE-052). */
    public ZoneSettings withSealedDissolveSeconds(double seconds) {
        return new ZoneSettings(deleteDisplacedGas, seconds);
    }
}
