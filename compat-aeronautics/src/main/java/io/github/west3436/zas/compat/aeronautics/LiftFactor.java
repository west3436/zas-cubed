package io.github.west3436.zas.compat.aeronautics;

import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * The arithmetic of the lift factor (COMPAT-080), pure: the ambient's density over standard air's.
 *
 * <p>A gas's density is Σ(pᵢMᵢ) / (RT): partial pressures in kPa, molar masses in kg/mol. The
 * factor is a ratio of two such densities, so the units cancel. ρ₀ is standard air's, 101.325 kPa
 * of 79 % N₂ and 21 % O₂ at 293.15 K, worked out the same way from the same molar masses, so an
 * ambient that is standard air gives exactly 1.
 */
public final class LiftFactor {
    /** What the factor is where the adapter leaves Sable's pressure as it is. */
    public static final double NONE = 1.0;

    private LiftFactor() {}

    /**
     * A gas's density, in kg per m³ when the pressures are in kPa.
     *
     * @param partialPressures partial pressure in kPa by gas id
     * @param temperature the temperature in kelvin
     * @param molarMass a gas's molar mass in kg/mol, or NaN if the gas isn't known
     * @return the density, 0 for a vacuum, or NaN if a gas is unknown
     */
    public static double density(
            Map<String, Double> partialPressures, double temperature, ToDoubleFunction<String> molarMass) {
        if (partialPressures.isEmpty()) {
            return 0;
        }
        if (!(temperature > 0)) {
            return Double.NaN;
        }
        double sum = 0;
        for (Map.Entry<String, Double> gas : partialPressures.entrySet()) {
            double m = molarMass.applyAsDouble(gas.getKey());
            if (!(m > 0) || !Double.isFinite(m)) {
                return Double.NaN;
            }
            sum += gas.getValue() * m;
        }
        return sum / (GasMixture.GAS_CONSTANT * temperature);
    }

    /**
     * The factor Sable's pressure is multiplied by: ρ / ρ₀.
     *
     * @param ambient the ambient's partial pressures in kPa by gas id
     * @param temperature the ambient's temperature in kelvin
     * @param standard standard air's partial pressures in kPa by gas id
     * @param standardTemperature standard air's temperature, 293.15 K
     * @param molarMass a gas's molar mass in kg/mol, or NaN if the gas isn't known
     * @return the factor, 0 or more; {@link #NONE} if either density can't be worked out
     */
    public static double factor(
            Map<String, Double> ambient,
            double temperature,
            Map<String, Double> standard,
            double standardTemperature,
            ToDoubleFunction<String> molarMass) {
        double rho = density(ambient, temperature, molarMass);
        double rho0 = density(standard, standardTemperature, molarMass);
        if (!Double.isFinite(rho) || !(rho0 > 0) || !Double.isFinite(rho0)) {
            return NONE;
        }
        return rho / rho0;
    }
}
