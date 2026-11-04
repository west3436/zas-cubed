package io.github.west3436.zas.atmos.heater;

/** What a heater or air conditioner did at its last step, for its GUI. */
public enum ThermoStatus {
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
    /** Cooling its room. */
    COOLING,
    /** Its room is at or past the setpoint. */
    AT_SETPOINT;

    /** The status with this ordinal, or {@link #OFF}. */
    public static ThermoStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Whether it moved heat at its last step. */
    public boolean working() {
        return this == HEATING || this == COOLING;
    }

    /** Its GUI line's translation key. While working, the line takes the power in W. */
    public String key() {
        return "gui.zas_atmos.thermo.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
