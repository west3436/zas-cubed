package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;

/**
 * What an entity breathes from (API-014, LIFE-002).
 *
 * <p>Later minor versions may add sources.
 */
public enum BreathSource {
    /** The atmosphere at its eye position (LIFE-002, API-015). */
    ATMOSPHERE,
    /**
     * Its internals: a tank through a sealed mask or helmet (LIFE-002, LIFE-021), or another
     * life-support supply of breathable gas (API-022).
     */
    INTERNALS,
    /** The fluid its eyes are in, such as water, where vanilla's rules apply (LIFE-022). */
    FLUID,
    /** Nothing: it doesn't breathe (LIFE-001). */
    NONE;

    /**
     * Whether this source supplies gas, so that a verdict can carry the gas breathed
     * ({@link Breathability#breath()}).
     *
     * <p>Thread: any.
     *
     * @return true for {@link #ATMOSPHERE} and {@link #INTERNALS}
     */
    @AnyThread
    public boolean isGas() {
        return this == ATMOSPHERE || this == INTERNALS;
    }
}
