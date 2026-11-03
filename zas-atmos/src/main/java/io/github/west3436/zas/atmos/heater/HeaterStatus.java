package io.github.west3436.zas.atmos.heater;

/** What a heater did at its last step, for its GUI. */
public enum HeaterStatus {
    /** Switched off. */
    OFF,
    /** Switched on, with no battery. */
    NO_BATTERY,
    /** Switched on, with an empty battery. */
    BATTERY_EMPTY,
    /** Its cell isn't in a zone: open air, unresolved or paused. */
    NO_ROOM,
    /** Heating its room. */
    HEATING,
    /** Its room is at or above the setpoint. */
    AT_SETPOINT;

    /** The status with this ordinal, or {@link #OFF}. */
    public static HeaterStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Its GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.heater.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
