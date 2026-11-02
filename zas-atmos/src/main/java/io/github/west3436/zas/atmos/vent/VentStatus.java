package io.github.west3436.zas.atmos.vent;

import java.util.Locale;

/**
 * What a vent did at its last atmos tick, for its GUI (CONT-014, CONT-074): off, moving gas, or
 * idle and why. Decided on the server each atmos tick and synced to the GUI by its ordinal.
 */
public enum VentStatus {
    /** Off or unpowered: the GUI's mode and power lines say which. */
    OFF,
    /** Moved gas. */
    MOVING,
    /** Waiting for the air in front to read as a zone or the ambient, as after a change there. */
    WAITING,
    /** Blowing, with no gas in the network. */
    NETWORK_EMPTY,
    /** Siphoning, with no gas in front. */
    ROOM_EMPTY,
    /**
     * The network has reached the internal bound: blowing, it is at or below it; siphoning, at or
     * above it.
     */
    NETWORK_BOUND,
    /** The room has reached the external bound: blowing, it is at or above it; siphoning, at or below it. */
    ROOM_BOUND;

    /** The status with an ordinal, or {@link #OFF} for one out of range. */
    public static VentStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** The GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.vent.status." + name().toLowerCase(Locale.ROOT);
    }
}
