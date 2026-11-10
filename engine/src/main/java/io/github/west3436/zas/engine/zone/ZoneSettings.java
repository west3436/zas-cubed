package io.github.west3436.zas.engine.zone;

import java.util.Objects;

/**
 * What happens to zones apart from how they exchange gas ({@link ExchangeSettings}).
 *
 * @param deleteDisplacedGas whether the gas of a cell a block fills is deleted, its share of the zone's
 *     by volume, as ZAS does (CORE-029), rather than staying in the zone (CORE-022, CORE-075)
 * @param sealedDissolveSeconds how long a sealed zone must match the ambient before it dissolves into
 *     it, in s (CORE-052)
 * @param guard the gas levels that keep a sealed zone though it matches the ambient (CORE-052)
 * @throws IllegalArgumentException if the delay is negative or not a number
 */
public record ZoneSettings(boolean deleteDisplacedGas, double sealedDissolveSeconds, DissolveGuard guard) {
    /**
     * Displaced gas is conserved (D-41), and a sealed zone dissolves after a minute at the ambient,
     * whatever trace it holds.
     */
    public static final ZoneSettings DEFAULT = new ZoneSettings(false, 60);

    public ZoneSettings {
        if (!(sealedDissolveSeconds >= 0)) {
            throw new IllegalArgumentException("sealedDissolveSeconds must be at least 0: " + sealedDissolveSeconds);
        }
        Objects.requireNonNull(guard, "guard");
    }

    /** Settings with no gas levels: a sealed zone that matches the ambient always dissolves. */
    public ZoneSettings(boolean deleteDisplacedGas, double sealedDissolveSeconds) {
        this(deleteDisplacedGas, sealedDissolveSeconds, DissolveGuard.NONE);
    }

    /** These settings with displaced gas deleted or kept (CORE-029). */
    public ZoneSettings withDeleteDisplacedGas(boolean delete) {
        return new ZoneSettings(delete, sealedDissolveSeconds, guard);
    }

    /** These settings with another delay before a sealed zone dissolves (CORE-052). */
    public ZoneSettings withSealedDissolveSeconds(double seconds) {
        return new ZoneSettings(deleteDisplacedGas, seconds, guard);
    }

    /** These settings with other gas levels that keep a sealed zone (CORE-052). */
    public ZoneSettings withGuard(DissolveGuard levels) {
        return new ZoneSettings(deleteDisplacedGas, sealedDissolveSeconds, levels);
    }
}
