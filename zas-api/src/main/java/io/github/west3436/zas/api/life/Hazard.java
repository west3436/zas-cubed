package io.github.west3436.zas.api.life;

/**
 * A way the environment harms an entity. A verdict lists those of where it is that its protection
 * doesn't cover ({@link Breathability#hazards()}, API-014), and a {@link
 * io.github.west3436.zas.api.event.HazardTickEvent} names the one about to harm it (API-033).
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
    HEAT,
    /**
     * Its air meter is empty, in a breath short of O₂ (LIFE-008). A verdict doesn't list it: {@link
     * Breathability#canBreathe()} says so instead.
     */
    HYPOXIA,
    /**
     * Airflow threw it against a block (FLOW-005). A verdict doesn't list it, as it isn't a property of
     * where the entity is.
     */
    AIRFLOW_IMPACT
}
