package io.github.west3436.zas.atmos.heater;

/**
 * A heater's control (CONT-019, CONT-087). In a room, it heats its network's gas toward {@link
 * #roomTarget}: as hot as the room needs, up to {@link #MAX_GAS}, and not at all once the room is at
 * its setpoint. With no room, it heats the gas toward the setpoint itself. Either way {@link #heat}
 * gives what one step adds, at most the configured power. Pure, so it is unit-tested.
 */
public final class HeaterMath {
    /** The default most a heater adds, in W: {@code [power] heater_watts} (CONT-087). */
    public static final double DEFAULT_MAX_WATTS = 5000;

    /** The lowest setpoint, K (CONT-019). */
    public static final double MIN_SETPOINT = 293;

    /** The highest setpoint, K (CONT-019). */
    public static final double MAX_SETPOINT = 573;

    /** The hottest a heater makes its network's gas, K: the top of its range (CONT-019). */
    public static final double MAX_GAS = MAX_SETPOINT;

    /** A new heater's setpoint, K: room temperature, 20 °C. */
    public static final double DEFAULT_SETPOINT = 293.15;

    private HeaterMath() {}

    /** A setpoint clamped to 293–573 K; NaN is the lowest. */
    public static double clampSetpoint(double kelvin) {
        return Double.isNaN(kelvin) ? MIN_SETPOINT : Math.max(MIN_SETPOINT, Math.min(MAX_SETPOINT, kelvin));
    }

    /**
     * The temperature to heat the network's gas to when the heater regulates a room: the one at
     * which the room's gas and the network's, fully mixed, would be at the setpoint. So the heat
     * the network holds above the setpoint is never more than the room still needs, and the vents
     * can't carry the room past it. The further the room is below the setpoint, and the larger it
     * is beside the network, the hotter the gas, up to {@link #MAX_GAS}.
     *
     * @param roomCapacity the room's gas's heat capacity, J/K
     * @param roomTemperature its temperature, K
     * @param networkCapacity the network gas's heat capacity, J/K
     * @param setpoint the setpoint, K
     * @return the target in K; 0, so nothing to heat toward, when the room is at or above the setpoint
     *     or the network is empty
     */
    public static double roomTarget(
            double roomCapacity, double roomTemperature, double networkCapacity, double setpoint) {
        if (!(roomTemperature < setpoint) || !(networkCapacity > 0)) {
            return 0;
        }
        double shortfall = Math.max(0, roomCapacity) * (setpoint - roomTemperature);
        return Math.min(MAX_GAS, setpoint + shortfall / networkCapacity);
    }

    /**
     * The heat to add this step.
     *
     * @param heatCapacity the network gas's heat capacity, J/K
     * @param temperature its temperature, K
     * @param target the temperature to heat it toward, K
     * @param seconds the step's length, s
     * @param maxWatts the most the heater adds, W
     * @return the energy in J, from 0 to {@code maxWatts} × {@code seconds}; it never takes the gas
     *     past the target
     */
    public static double heat(double heatCapacity, double temperature, double target, double seconds, double maxWatts) {
        if (!(heatCapacity > 0) || !(temperature < target) || !(seconds > 0) || !(maxWatts > 0)) {
            return 0;
        }
        return Math.min(maxWatts * seconds, heatCapacity * (target - temperature));
    }
}
