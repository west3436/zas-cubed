package io.github.west3436.zas.atmos.heater;

/** What a heater did at its last step, for its GUI. */
public enum HeaterStatus {
    /** Switched off. */
    OFF,
    /** Switched on, with no power (CONT-060). */
    UNPOWERED,
    /** Heating its network's gas. */
    HEATING,
    /** Its network's gas is at or above the setpoint. */
    AT_SETPOINT,
    /** Its network holds no gas to heat. */
    NETWORK_EMPTY;

    /** The status with this ordinal, or {@link #OFF}. */
    public static HeaterStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Its GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.heater.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
