package io.github.west3436.zas.engine.reaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * When a reaction can happen (FIRE-001, FIRE-006). Every condition is checked against the whole
 * mixture at the start of a step; a reaction happens only if all of them hold.
 *
 * @param minTemperature the least temperature, K; 0 for none
 * @param maxTemperature the greatest temperature, K; {@link Double#POSITIVE_INFINITY} for none
 * @param concentrations mole fraction ranges gases must be in
 * @param catalysts gases that must be present, and at least their {@code min} fraction, but aren't
 *     consumed
 * @param requiresIgnition whether it needs an ignition source in or next to the cell (FIRE-006,
 *     FIRE-014): such a reaction burns only in a hotspot's parcel or a container's (FIRE-011,
 *     FIRE-031), never zone-wide. Its flammability limits' autoignition temperature stands in for a
 *     source
 * @param flammability the fuel's flammability limits and autoignition temperature; a reaction with
 *     them is a fire, so it also needs the fire oxygen threshold (FIRE-030)
 */
public record ReactionConditions(
        double minTemperature,
        double maxTemperature,
        List<Concentration> concentrations,
        List<Concentration> catalysts,
        boolean requiresIgnition,
        Optional<FlammabilityLimits> flammability) {

    /** No conditions: always. */
    public static final ReactionConditions NONE =
            new ReactionConditions(0, Double.POSITIVE_INFINITY, List.of(), List.of(), false, Optional.empty());

    public ReactionConditions {
        concentrations = List.copyOf(Objects.requireNonNull(concentrations, "concentrations"));
        catalysts = List.copyOf(Objects.requireNonNull(catalysts, "catalysts"));
        Objects.requireNonNull(flammability, "flammability");
        if (!(minTemperature >= 0) || minTemperature == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException(
                    "min_temperature: must be finite and not negative, got " + minTemperature);
        }
        if (!(maxTemperature > minTemperature)) {
            throw new IllegalArgumentException("max_temperature: must be above min_temperature, got " + maxTemperature);
        }
    }

    /** A combustion's conditions: it needs ignition and its fuel within the limits. */
    public static ReactionConditions combustion(FlammabilityLimits limits) {
        return new ReactionConditions(0, Double.POSITIVE_INFINITY, List.of(), List.of(), true, Optional.of(limits));
    }

    /**
     * A mole fraction range for one gas.
     *
     * @param gas the gas id
     * @param min the least fraction, from 0 to 1; a catalyst must also be present
     * @param max the greatest fraction, from {@code min} to 1
     */
    public record Concentration(String gas, double min, double max) {
        public Concentration {
            Objects.requireNonNull(gas, "gas");
            if (!(min >= 0 && min <= 1)) {
                throw new IllegalArgumentException(gas + ": min must be from 0 to 1, got " + min);
            }
            if (!(max >= min && max <= 1)) {
                throw new IllegalArgumentException(gas + ": max must be from min to 1, got " + max);
            }
        }
    }

    /**
     * A fuel's flammability limits in air and its autoignition temperature (FIRE-003, FIRE-006). The
     * limits are declared for {@link #REFERENCE_TEMPERATURE} and hold at and below it; above it they
     * widen with the gas's temperature (FIRE-032, {@link #lowerAt}, {@link #upperAt}).
     *
     * @param fuel the fuel gas id
     * @param lower the lower flammability limit, as the fuel's mole fraction of the mixture
     * @param upper the upper flammability limit, likewise
     * @param autoignition the autoignition temperature, K: at or above it the mixture needs no
     *     ignition source; {@link Double#POSITIVE_INFINITY} if it always needs one
     */
    public record FlammabilityLimits(String fuel, double lower, double upper, double autoignition) {
        /** The temperature the declared limits are for, K (FIRE-032). */
        public static final double REFERENCE_TEMPERATURE = 298;

        /**
         * The lowest a lower limit falls to with temperature, as a mole fraction (FIRE-032): 0.1 %, so
         * a hot room's fire burns its fuel out and stops instead of chasing the last traces. A limit
         * declared below it stays where it is.
         */
        public static final double LOWER_LIMIT_FLOOR = 0.001;

        /**
         * The modified Burgess–Wheeler constant, 0.75 percentage points · (kcal/mol) / K, as mole
         * fraction · (J/mol) / K.
         */
        private static final double BURGESS_WHEELER = 0.0075 * 4184;

        public FlammabilityLimits {
            Objects.requireNonNull(fuel, "fuel");
            if (!(lower > 0 && lower < 1)) {
                throw new IllegalArgumentException("flammability: lower must be between 0 and 1, got " + lower);
            }
            if (!(upper > lower && upper <= 1)) {
                throw new IllegalArgumentException("flammability: upper must be from lower to 1, got " + upper);
            }
            if (!(autoignition > 0)) {
                throw new IllegalArgumentException("flammability: autoignition must be positive, got " + autoignition);
            }
        }

        /**
         * The lower limit at a temperature (FIRE-032).
         *
         * @param temperature the gas's temperature, K
         * @param heatOfCombustion the heat released per mole of fuel, J/mol; without any, the limit
         *     stays as declared
         * @return the limit, as a mole fraction: the declared one less the widening, but not below
         *     {@link #LOWER_LIMIT_FLOOR}
         */
        public double lowerAt(double temperature, double heatOfCombustion) {
            return Math.max(Math.min(lower, LOWER_LIMIT_FLOOR), lower - widening(temperature, heatOfCombustion));
        }

        /**
         * The upper limit at a temperature (FIRE-032).
         *
         * @param temperature the gas's temperature, K
         * @param heatOfCombustion the heat released per mole of fuel, J/mol; without any, the limit
         *     stays as declared
         * @return the limit, as a mole fraction: the declared one plus the widening, at most 1
         */
        public double upperAt(double temperature, double heatOfCombustion) {
            return Math.min(1, upper + widening(temperature, heatOfCombustion));
        }

        private static double widening(double temperature, double heatOfCombustion) {
            if (!(temperature > REFERENCE_TEMPERATURE) || !(heatOfCombustion > 0)) {
                return 0;
            }
            return BURGESS_WHEELER * (temperature - REFERENCE_TEMPERATURE) / heatOfCombustion;
        }
    }
}
