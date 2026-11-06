package io.github.west3436.zas.engine.flow;

/**
 * When a pressure difference breaks fragile blocks (FLOW-007, FLOW-015, FLOW-016).
 *
 * @param thresholdKpa the difference a fragile block holds, kPa: it breaks at more than this. {@link
 *     Double#POSITIVE_INFINITY} is off, as under the Normal preset
 * @param maxBreaks the most fragile blocks one atmos tick breaks in a dimension; the rest wait for the
 *     next, so a wall of windows can't stall a game tick
 */
public record FragileSettings(double thresholdKpa, int maxBreaks) {
    /** FLOW-015: windows hold 1 atm against a vacuum and blow out on overpressure (D-28). */
    public static final double DEFAULT_THRESHOLD_KPA = 150;

    /** The default cap on breaks per atmos tick in a dimension. */
    public static final int DEFAULT_MAX_BREAKS = 32;

    /** Nothing breaks. */
    public static final FragileSettings OFF = new FragileSettings(Double.POSITIVE_INFINITY, DEFAULT_MAX_BREAKS);

    public FragileSettings {
        if (Double.isNaN(thresholdKpa) || thresholdKpa < 0) {
            throw new IllegalArgumentException("thresholdKpa must be >= 0: " + thresholdKpa);
        }
        if (maxBreaks < 1) {
            throw new IllegalArgumentException("maxBreaks must be at least 1: " + maxBreaks);
        }
    }

    /** Whether any difference can break a block. */
    public boolean enabled() {
        return thresholdKpa < Double.POSITIVE_INFINITY;
    }
}
