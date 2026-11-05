package io.github.west3436.zas.atmos.pump;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.gas.GasStore;

/**
 * How many moles a pump moves in one second (CONT-011, CONT-076). Gas moves conserving thermal
 * energy, so this reckons with the temperature the output settles at.
 */
public final class PumpFlow {
    /** The highest target a pump can be set to, kPa (CONT-011). */
    public static final double MAX_TARGET = 4_500;

    /** The most a pump moves per second, in L at the input's conditions (CONT-076). */
    public static final double MAX_RATE = 100;

    private PumpFlow() {}

    /**
     * One side of a pump: the mixture it takes gas from or adds gas to.
     *
     * @param moles its moles
     * @param heatCapacity its heat capacity, J/K
     * @param temperature its temperature, K
     * @param volume its volume, L
     */
    public record Side(double moles, double heatCapacity, double temperature, double volume) {
        /** A store's side, read now. */
        public static Side of(GasStore gas) {
            return new Side(gas.totalMoles(), gas.heatCapacity(), gas.temperature(), gas.volume());
        }

        /** A mixture's side, as for a zone. */
        public static Side of(GasMixture gas) {
            return new Side(gas.totalMoles(), GasProperties.heatCapacity(gas.moles()), gas.temperature(), gas.volume());
        }
    }

    /**
     * What a pressure pump moves in a second: what brings the output to the target, at most {@link
     * #MAX_RATE} L at the input's conditions.
     *
     * @param target the target, kPa
     * @param in the input
     * @param out the output, its network's whole volume, which counts its buffer once
     * @return the moles, at least 0 and at most the input's
     */
    public static double pressurePump(double target, Side in, Side out) {
        return Math.min(molesToTarget(target, in, out), molesInVolume(MAX_RATE, in));
    }

    /**
     * What a volume pump moves in a second: its set volume at the input's conditions, and never more
     * than {@link #MAX_RATE} L, whatever it is set to (CONT-012).
     *
     * @param rate the set volume, L/s
     * @param in the input
     * @return the moles, at least 0 and at most the input's
     */
    public static double volumePump(double rate, Side in) {
        return molesInVolume(Math.min(rate, MAX_RATE), in);
    }

    /**
     * The moles that bring the output to a target: once the input's gas has mixed in, conserving
     * thermal energy, the output's pressure is the target. At or above the target, none.
     *
     * <p>With T the output's temperature once x moles at T<sub>i</sub> with molar heat capacity c
     * have mixed in, the target is reached when (n + x)·T = P·V/R, a quadratic in x with one positive
     * root. If the two mixtures have the same molar heat capacity, it is x = (P − P<sub>out</sub>)·V
     * /(R·T<sub>i</sub>).
     *
     * @param target the target, kPa
     * @param in the input: its temperature and molar heat capacity
     * @param out the output
     * @return the moles, at least 0; not limited by what the input holds
     */
    public static double molesToTarget(double target, Side in, Side out) {
        if (in.moles() <= 0 || in.temperature() <= 0 || in.heatCapacity() <= 0 || out.volume() <= 0) {
            return 0;
        }
        double goal = target * out.volume() / GasMixture.GAS_CONSTANT;
        double have = out.moles() * out.temperature();
        if (have >= goal) {
            return 0;
        }
        double molar = in.heatCapacity() / in.moles();
        double a = molar * in.temperature();
        double b = out.moles() * a + out.heatCapacity() * out.temperature() - goal * molar;
        double c = out.heatCapacity() * (have - goal);
        double root = Math.sqrt(b * b - 4 * a * c);
        return Math.max(0, b > 0 ? -2 * c / (b + root) : (root - b) / (2 * a));
    }

    /**
     * The moles in a volume of the input, at its conditions.
     *
     * @param litres the volume, L
     * @param in the input
     * @return the moles, at most the input's
     */
    public static double molesInVolume(double litres, Side in) {
        if (litres <= 0 || in.volume() <= 0) {
            return 0;
        }
        return in.moles() * Math.min(1, litres / in.volume());
    }
}
