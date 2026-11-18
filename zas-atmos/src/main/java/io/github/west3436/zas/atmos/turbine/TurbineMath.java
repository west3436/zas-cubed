package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.api.gas.GasMixture;

/**
 * The gas turbine's arithmetic (CONT-160 to CONT-162). Pure: no world access, so it is unit-tested.
 *
 * <p>Gas holds C·T of thermal energy, as everywhere in ZAS³. The compressor's work raises the
 * temperature of the gas it draws by what the shaft paid for it, W/C; the turbine's work lowers the
 * temperature of the gas it lets out by what the shaft gained, W/C. So the gas's thermal energy, the
 * shaft's energy, the FE made (at {@code joules_per_fe}) and the friction's heat always add up to the
 * same. Ideal compression and expansion follow T₂/T₁ = (P₂/P₁)^k with k = R/(c + R), c the gas's
 * molar heat capacity; the compressor spends more than ideal and the turbine takes less, so with no
 * heat added in the chamber, the shaft only slows.
 */
public final class TurbineMath {
    /** The most a turbine expands its gas, as a pressure ratio: one stage. */
    public static final double MAX_EXPANSION = 10;

    /** The exponent of vg's output curve: the generator makes in proportion to rpm^0.8 (CONT-162). */
    public static final double OUTPUT_EXPONENT = 0.8;

    private TurbineMath() {}

    /**
     * The exponent k = R/(c + R) of a gas whose heat capacity is {@code heatCapacity} over {@code
     * moles}.
     */
    static double exponent(double heatCapacity, double moles) {
        double c = heatCapacity / moles;
        return GasMixture.GAS_CONSTANT / (c + GasMixture.GAS_CONSTANT);
    }

    /**
     * The work to compress gas from an intake's pressure to a chamber's (CONT-160): the ideal work
     * over the compressor's efficiency. None if the chamber isn't the higher.
     *
     * @param heatCapacity the gas's heat capacity, J/K
     * @param moles its moles
     * @param temperature its temperature, K
     * @param from the intake's pressure, kPa
     * @param to the chamber's pressure, kPa
     * @param efficiency the compressor's, above 0
     * @return J, at least 0
     */
    public static double compressionWork(
            double heatCapacity, double moles, double temperature, double from, double to, double efficiency) {
        if (!(moles > 0) || !(heatCapacity > 0) || !(temperature > 0) || !(from > 0) || !(to > from)) {
            return 0;
        }
        double ideal = heatCapacity * temperature * (Math.pow(to / from, exponent(heatCapacity, moles)) - 1);
        return ideal / Math.max(1e-6, efficiency);
    }

    /**
     * The work the turbine takes from gas it lets out of the chamber (CONT-161): the share {@code
     * efficiency} of an ideal expansion to the exhaust's pressure, or to a tenth of the chamber's if
     * that is higher. None if the chamber isn't the higher.
     *
     * @param heatCapacity the gas's heat capacity, J/K
     * @param moles its moles
     * @param temperature its temperature, K
     * @param from the chamber's pressure, kPa
     * @param to the exhaust's pressure, kPa
     * @param efficiency the turbine's, 0 to 1
     * @return J, at least 0 and less than the gas's thermal energy
     */
    public static double expansionWork(
            double heatCapacity, double moles, double temperature, double from, double to, double efficiency) {
        if (!(moles > 0) || !(heatCapacity > 0) || !(temperature > 0) || !(from > 0) || !(from > to)) {
            return 0;
        }
        double ratio = Math.max(Math.max(0, to) / from, 1 / MAX_EXPANSION);
        double ideal = heatCapacity * temperature * (1 - Math.pow(ratio, exponent(heatCapacity, moles)));
        return Math.max(0, Math.min(1, efficiency)) * ideal;
    }

    /**
     * The moles the turbine lets out of the chamber in an atmos tick (CONT-161): {@code nozzle} times
     * what would even out the chamber and the exhaust, at most {@code maxLitres} of the chamber's gas
     * and half what it holds.
     *
     * @param chamberMoles the chamber's moles
     * @param chamberT its temperature, K
     * @param chamberV its volume, L
     * @param chamberP its pressure, kPa
     * @param exhaustV the exhaust's volume, L; infinite for the open air
     * @param exhaustP its pressure, kPa
     * @param nozzle the share of the evening moles
     * @param maxLitres the most it lets out, L at the chamber's conditions
     * @return the moles, at least 0
     */
    public static double turbineMoles(
            double chamberMoles,
            double chamberT,
            double chamberV,
            double chamberP,
            double exhaustV,
            double exhaustP,
            double nozzle,
            double maxLitres) {
        double drive = chamberP - exhaustP;
        if (!(drive > 0) || !(chamberMoles > 0) || !(chamberT > 0) || !(chamberV > 0) || !(exhaustV > 0)) {
            return 0;
        }
        double even = drive / (GasMixture.GAS_CONSTANT * chamberT * (1 / chamberV + 1 / exhaustV));
        double cap = Math.max(0, maxLitres) * chamberP / (GasMixture.GAS_CONSTANT * chamberT);
        return Math.max(0, Math.min(Math.min(Math.max(0, nozzle) * even, cap), chamberMoles / 2));
    }

    /**
     * The moles the compressor draws in an atmos tick (CONT-160): its draw at full speed times the
     * shaft's share of its top speed, at the intake's conditions, and at most a quarter of what a
     * zoned intake holds.
     *
     * @param speed the shaft's share of its top speed, 0 to 1
     * @param litres what it draws at top speed, L
     * @param intakeP the intake's pressure, kPa
     * @param intakeT its temperature, K
     * @param intakeMoles its moles, or infinite for the open air
     * @return the moles, at least 0
     */
    public static double compressorMoles(
            double speed, double litres, double intakeP, double intakeT, double intakeMoles) {
        if (!(speed > 0) || !(litres > 0) || !(intakeP > 0) || !(intakeT > 0)) {
            return 0;
        }
        double n = Math.min(1, speed) * litres * intakeP / (GasMixture.GAS_CONSTANT * intakeT);
        return Math.max(0, Math.min(n, intakeMoles / 4));
    }

    /**
     * The shaft's speed for its energy: E = E<sub>max</sub>·(rpm/rpm<sub>max</sub>)².
     *
     * @param energy the shaft's energy, J
     * @param topEnergy its energy at top speed, J
     * @param topRpm its top speed, rpm
     * @return rpm, 0 to {@code topRpm}
     */
    public static double rpm(double energy, double topEnergy, double topRpm) {
        if (!(energy > 0) || !(topEnergy > 0)) {
            return 0;
        }
        return topRpm * Math.sqrt(Math.min(1, energy / topEnergy));
    }

    /**
     * What the generator takes from the shaft in an atmos tick (CONT-162): its top output times the
     * share of top speed to the power 0.8, and never more than the shaft holds or the store has room
     * for.
     *
     * @param energy the shaft's energy, J
     * @param speed its share of top speed, 0 to 1
     * @param topJoules what the generator takes at top speed in the atmos tick, J
     * @param room what the store has room for, J
     * @return J, at least 0
     */
    public static double generatorJoules(double energy, double speed, double topJoules, double room) {
        if (!(energy > 0) || !(speed > 0) || !(topJoules > 0) || !(room > 0)) {
            return 0;
        }
        double want = topJoules * Math.pow(Math.min(1, speed), OUTPUT_EXPONENT);
        return Math.min(Math.min(want, energy), room);
    }

    /**
     * How fast its blades show turning: 0 still, then 1 to 3.
     *
     * @param speed the shaft's share of top speed
     * @return 0 to {@link TurbinePartBlock#MAX_SPIN}
     */
    public static int spin(double speed) {
        if (!(speed >= 0.02)) {
            return 0;
        }
        return speed < 0.35 ? 1 : speed < 0.7 ? 2 : 3;
    }
}
