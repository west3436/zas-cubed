package io.github.west3436.zas.atmos.portable;

import java.util.Locale;

/** What a portable pump or scrubber did at its last step, for its GUI. */
public enum PortableStatus {
    /** Switched off. */
    OFF,
    /** Switched on, with no battery. */
    NO_BATTERY,
    /** Switched on, with work to do and an empty battery. */
    BATTERY_EMPTY,
    /** It holds no tank and its cell isn't in a zone: open air, or unresolved. */
    NO_ROOM,
    /** It is waiting for the air around it to settle (CONT-018, as a vent does). */
    WAITING,
    /** It moved gas. */
    WORKING,
    /** The air, or its store, holds nothing for it to move. */
    NOTHING_TO_MOVE,
    /** Its store has reached the pressure it stops at. */
    STORE_FULL,
    /** What it pumps has reached its target pressure. */
    AT_TARGET;

    /** The status with this ordinal, or {@link #OFF}. */
    public static PortableStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Its GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.portable.status." + name().toLowerCase(Locale.ROOT);
    }
}
