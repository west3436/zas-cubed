package io.github.west3436.zas.atmos.heater;

/**
 * An air conditioner's step (CONT-019, CONT-087): the heat it takes from the zone it stands in. Its
 * cost is the heater's, {@link HeaterMath#energy}. Pure, so it is unit-tested.
 */
public final class AirConditionerMath {
    /** The default most an air conditioner takes, in W: {@code [power] air_conditioner_watts} (CONT-087). */
    public static final double DEFAULT_MAX_WATTS = 5000;

    /** The lowest setpoint, K (CONT-019). */
    public static final double MIN_SETPOINT = 73;

    /** The highest setpoint, K (CONT-019). */
    public static final double MAX_SETPOINT = 293;

    /** A new air conditioner's setpoint, K: the highest, about room temperature. */
    public static final double DEFAULT_SETPOINT = MAX_SETPOINT;

    private AirConditionerMath() {}

    /** A setpoint clamped to 73–293 K; NaN is the highest, which cools least. */
    public static double clampSetpoint(double kelvin) {
        return Double.isNaN(kelvin) ? MAX_SETPOINT : Math.max(MIN_SETPOINT, Math.min(MAX_SETPOINT, kelvin));
    }

    /**
     * The heat to take from a zone this step: what brings it down to the setpoint, but at most {@code
     * maxWatts} over the step, and nothing at or below the setpoint. It is the heat that would warm
     * the zone from the setpoint to its temperature.
     *
     * @param heatCapacity the zone's gas's heat capacity, J/K
     * @param temperature its temperature, K
     * @param setpoint the setpoint, K
     * @param seconds the step's length, s
     * @param maxWatts the most it takes, W
     * @return the energy in J, from 0 to {@code maxWatts} × {@code seconds}; it never takes the zone
     *     below the setpoint
     */
    public static double cool(
            double heatCapacity, double temperature, double setpoint, double seconds, double maxWatts) {
        return HeaterMath.heat(heatCapacity, setpoint, temperature, seconds, maxWatts);
    }
}
