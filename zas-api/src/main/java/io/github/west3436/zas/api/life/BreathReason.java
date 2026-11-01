package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;

/**
 * Why an entity can or can't breathe where it is (API-014). Each reason decides which:
 * {@link #canBreathe()}.
 *
 * <p>Later minor versions may add reasons. Treat one you don't know by its {@link #canBreathe()}.
 */
public enum BreathReason {
    /**
     * It breathes gas that keeps it supplied. By ZAS³'s breathing rules, the O₂ partial pressure
     * of its breath is at or above the minimum (LIFE-003). By vanilla's rules, its eyes are in
     * air or in a bubble column.
     */
    BREATHABLE(true),
    /**
     * Its eyes are in a fluid it doesn't drown in. It has Water Breathing or Conduit Power, its
     * type can breathe under water ({@code #minecraft:can_breathe_under_water}), or nothing drowns
     * in that fluid, as in lava. As in vanilla, its air holds but doesn't refill (LIFE-022).
     */
    FLUID_BREATHING(true),
    /**
     * It is a player whose abilities make it invulnerable, as in creative or spectator mode, so it
     * never runs out of air (LIFE-037).
     */
    EXEMPT(true),
    /** It doesn't breathe: it is neither a player nor in {@code zas:breathers} (LIFE-001). */
    DOES_NOT_BREATHE(true),
    /** Its eyes are in a fluid it drowns in, and nothing lets it breathe there (LIFE-022). */
    DROWNING(false),
    /**
     * The O₂ partial pressure of its breath is below the minimum (LIFE-003), as in a vacuum. Only
     * ZAS³'s breathing rules give this reason.
     */
    LOW_OXYGEN(false);

    private final boolean canBreathe;

    BreathReason(boolean canBreathe) {
        this.canBreathe = canBreathe;
    }

    /**
     * Whether an entity with this reason can breathe: whether its air holds or refills, rather
     * than running out.
     *
     * <p>Thread: any.
     *
     * @return true if it can breathe
     */
    @AnyThread
    public boolean canBreathe() {
        return canBreathe;
    }
}
