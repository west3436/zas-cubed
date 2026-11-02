package io.github.west3436.zas.atmos.heater;

/** What a heater did at its last step, for its GUI. */
public enum HeaterStatus {
    /** Switched off. */
    OFF,
    /** Switched on, with no power (CONT-060). */
    UNPOWERED,
    /** Heating its network's gas. */
    HEATING,
    /** Its room, or with no room its network's gas, is at or above the setpoint. */
    AT_SETPOINT,
    /** Its network holds no gas to heat. */
    NETWORK_EMPTY,
    /** Its room is below the setpoint, but the network's gas already holds the heat the room needs. */
    CARRYING,
    /** Its room is below the setpoint, and the network's gas is at {@link HeaterMath#MAX_GAS}. */
    AT_LIMIT;

    /** The status with this ordinal, or {@link #OFF}. */
    public static HeaterStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Its GUI line's translation key. */
    public String key() {
        return "gui.zas_atmos.heater.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
