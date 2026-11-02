package io.github.west3436.zas.atmos.heater;

/**
 * How much heat a heater puts into its network's gas in one step (CONT-019, CONT-087): what
 * brings the gas to the setpoint, but at most {@link #MAX_WATTS} over the step, and nothing at or
 * above the setpoint. Pure, so it is unit-tested.
 */
public final class HeaterMath {
    /** The most a heater moves, in W (CONT-087). */
    public static final double MAX_WATTS = 5000;

    /** The lowest setpoint, K (CONT-019). */
    public static final double MIN_SETPOINT = 293;

    /** The highest setpoint, K (CONT-019). */
    public static final double MAX_SETPOINT = 573;

    /** A new heater's setpoint, K: room temperature, 20 °C. */
    public static final double DEFAULT_SETPOINT = 293.15;

    private HeaterMath() {}

    /** A setpoint clamped to 293–573 K; NaN is the lowest. */
    public static double clampSetpoint(double kelvin) {
        return Double.isNaN(kelvin) ? MIN_SETPOINT : Math.max(MIN_SETPOINT, Math.min(MAX_SETPOINT, kelvin));
    }

    /**
     * The heat to add this step.
     *
     * @param heatCapacity the network gas's heat capacity, J/K
     * @param temperature its temperature, K
     * @param setpoint the setpoint, K
     * @param seconds the step's length, s
     * @return the energy in J, from 0 to {@link #MAX_WATTS} × {@code seconds}; it never takes the gas
     *     past the setpoint
     */
    public static double heat(double heatCapacity, double temperature, double setpoint, double seconds) {
        if (!(heatCapacity > 0) || !(temperature < setpoint) || !(seconds > 0)) {
            return 0;
        }
        return Math.min(MAX_WATTS * seconds, heatCapacity * (setpoint - temperature));
    }
}
