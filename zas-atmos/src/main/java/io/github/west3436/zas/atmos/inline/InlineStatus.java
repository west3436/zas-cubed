package io.github.west3436.zas.atmos.inline;

import java.util.Locale;

/**
 * What an inline device did at its last atmos tick, for its GUI (CONT-074): off, moving gas, or idle
 * and why. Decided on the server each atmos tick and synced to the GUI by its ordinal.
 */
public enum InlineStatus {
    /** Off or unpowered: the GUI's switch and power lines say which. */
    OFF,
    /** Moved gas. */
    MOVING,
    /** There is no gas at its input. */
    INPUT_EMPTY,
    /** Its output has reached its target. */
    TARGET_REACHED,
    /** Pipes join its ports into one network, so it has nothing to move gas between. */
    SAME_NETWORK,
    /** Its rate is set to 0. */
    RATE_ZERO;

    /** The status with an ordinal, or {@link #OFF} for one out of range. */
    public static InlineStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** The GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.inline.status." + name().toLowerCase(Locale.ROOT);
    }
}
