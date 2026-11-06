package io.github.west3436.zas.engine.zone;

/**
 * What happens to zones apart from how they exchange gas ({@link ExchangeSettings}).
 *
 * @param deleteDisplacedGas whether the gas of a cell a block fills is deleted, its share of the zone's
 *     by volume, as ZAS does (CORE-029), rather than staying in the zone (CORE-022, CORE-075)
 */
public record ZoneSettings(boolean deleteDisplacedGas) {
    /** Displaced gas is conserved (D-41). */
    public static final ZoneSettings DEFAULT = new ZoneSettings(false);
}
