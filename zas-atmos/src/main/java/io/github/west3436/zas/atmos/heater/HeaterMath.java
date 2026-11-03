package io.github.west3436.zas.atmos.heater;

/**
 * A heater's step (CONT-019, CONT-087): the heat it adds to the zone it stands in, and the FE that
 * costs its battery. Pure, so it is unit-tested.
 */
public final class HeaterMath {
    /** The default most a heater adds, in W: {@code [power] heater_watts} (CONT-087). */
    public static final double DEFAULT_MAX_WATTS = 5000;

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
     * The heat to add to a zone this step: what brings it to the setpoint, but at most {@code
     * maxWatts} over the step, and nothing at or above the setpoint.
     *
     * @param heatCapacity the zone's gas's heat capacity, J/K
     * @param temperature its temperature, K
     * @param setpoint the setpoint, K
     * @param seconds the step's length, s
     * @param maxWatts the most the heater adds, W
     * @return the energy in J, from 0 to {@code maxWatts} × {@code seconds}; it never takes the zone
     *     past the setpoint
     */
    public static double heat(
            double heatCapacity, double temperature, double setpoint, double seconds, double maxWatts) {
        if (!(heatCapacity > 0) || !(temperature < setpoint) || !(seconds > 0) || !(maxWatts > 0)) {
            return 0;
        }
        return Math.min(maxWatts * seconds, heatCapacity * (setpoint - temperature));
    }

    /**
     * The FE a step of heat costs: the full draw over the step at full power, and in proportion
     * below it, rounded up.
     *
     * @param joules the heat, J
     * @param seconds the step's length, s
     * @param maxWatts the most the heater adds, W
     * @param drawPerTick the draw at full power, FE/t
     * @param ticks the step's length, game ticks
     * @return the FE, from 0 to {@code drawPerTick} × {@code ticks}
     */
    public static int energy(double joules, double seconds, double maxWatts, int drawPerTick, int ticks) {
        if (!(joules > 0) || !(maxWatts > 0) || !(seconds > 0) || drawPerTick <= 0 || ticks <= 0) {
            return 0;
        }
        double full = (double) drawPerTick * ticks;
        double fraction = Math.min(1, joules / (maxWatts * seconds));
        return (int) Math.min(full, Math.ceil(full * fraction - 1e-9));
    }
}
