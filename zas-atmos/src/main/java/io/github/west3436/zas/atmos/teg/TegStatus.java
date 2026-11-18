package io.github.west3436.zas.atmos.teg;

import java.util.Locale;

/** What a TEG core did at its last atmos tick, for its window and a computer (CONT-159). */
public enum TegStatus {
    /** Switched off: its circulators pass gas on their own. */
    OFF,
    /** It hasn't a circulator on each side. */
    NO_CIRCULATORS,
    /** No gas passed one side or the other. */
    NO_FLOW,
    /** Both streams are at one temperature. */
    NO_DIFFERENCE,
    /** Its store is full, so it made nothing. */
    FULL,
    /** It made FE. */
    RUNNING;

    /** The status with an ordinal, or {@link #OFF} for one out of range. */
    public static TegStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Its window's word's translation key. */
    public String key() {
        return "gui.zas_atmos.teg.status." + name().toLowerCase(Locale.ROOT);
    }
}
