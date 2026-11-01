package io.github.west3436.zas.api.life;

/**
 * What protects an entity from the environment where it is (API-014): the life support that
 * applies to it, from the items it wears or from itself (API-022, LIFE-026), and the rules that
 * spare it. A verdict lists them in {@link Breathability#protection()}.
 *
 * <p>Later minor versions may add kinds.
 */
public enum Protection {
    /**
     * Worn pieces rated for the pressure here, on its side of 1 atm (LIFE-020). They may cover
     * only part of the body.
     */
    PRESSURE,
    /**
     * Worn pieces whose thermal range includes the temperature here (LIFE-025). They may cover
     * only part of the body.
     */
    THERMAL,
    /** A worn head item that seals its breathing, which internals need (LIFE-021). */
    SEALED_BREATHING,
    /** A filter that keeps some gases out of its breath (LIFE-026). */
    GAS_FILTERING,
    /** It is in {@code zas:atmosphere_immune}: pressure and temperature don't harm it (LIFE-015). */
    IMMUNE,
    /** It is a creative or spectator player, exempt from every hazard (LIFE-037). */
    EXEMPT
}
