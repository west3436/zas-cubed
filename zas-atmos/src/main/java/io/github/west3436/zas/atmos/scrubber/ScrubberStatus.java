package io.github.west3436.zas.atmos.scrubber;

import java.util.Locale;

/**
 * What a scrubber did at its last atmos tick, for its GUI (CONT-074): off, moving gas, or idle and
 * why. Decided on the server each atmos tick and synced to the GUI by its ordinal.
 */
public enum ScrubberStatus {
    /** Off or unpowered: the GUI's mode and power lines say which. */
    OFF,
    /** Took gas. */
    MOVING,
    /** Waiting for the air in front to read as a zone or the ambient, as after a change there. */
    WAITING,
    /** The air in front holds nothing it keeps: no selected gas when scrubbing, or no gas at all. */
    NOTHING,
    /** Its buffer has reached 50 atm (CONT-081). */
    STALLED;

    /** The status with an ordinal, or {@link #OFF} for one out of range. */
    public static ScrubberStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** The GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.scrubber.status." + name().toLowerCase(Locale.ROOT);
    }
}
