package io.github.west3436.zas.api.life;

/**
 * A way the environment harms an entity where it is, other than running out of breath (API-014).
 * A verdict lists the hazards its protection doesn't cover ({@link Breathability#hazards()}).
 *
 * <p>Later minor versions may add hazards.
 */
public enum Hazard {
    /** A gas in its breath is at a partial pressure its hazard profile makes harmful (LIFE-004). */
    TOXIC_GAS,
    /** The effective pressure is below the low-pressure threshold (LIFE-010, LIFE-020). */
    LOW_PRESSURE,
    /** The effective pressure is at or above the high-pressure threshold (LIFE-014, LIFE-020). */
    HIGH_PRESSURE,
    /** The exposure temperature is below the comfort band (LIFE-011, LIFE-025). */
    COLD,
    /** The exposure temperature is above the comfort band (LIFE-011, LIFE-025). */
    HEAT
}
