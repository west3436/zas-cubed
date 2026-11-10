package io.github.west3436.zas.compat.mekanism;

/**
 * The arithmetic of heat between a Mekanism heat capacitor and a zone's gas (MEK-040). Pure: no
 * Mekanism, no game, so the unit tests reach every case. {@link MekanismHeat} finds the blocks and
 * moves the heat.
 *
 * <p><b>Units.</b> Mekanism counts heat in the unit it counts energy in, and a capacitor's heat
 * capacity in that unit per kelvin. One server setting says how many joules of ZAS³'s that unit is
 * ({@link #DEFAULT_JOULES_PER_HEAT_UNIT}); every value here is already in joules and J/K.
 *
 * <p><b>Conductance.</b> A face of the block that touches a zone's gas conducts as a wall does
 * (HEAT-012): so many W/K for the face ({@link #DEFAULT_FACE_CONDUCTANCE}, a server setting), in
 * series with the block's own resistance as Mekanism counts it. Mekanism moves C × ΔT / R of heat
 * a tick through a capacitor whose heat capacity is C and whose inverse conduction and inverse
 * insulation add up to R, which is a conductance of 20 × C / R W/K; so a well insulated
 * thermodynamic conductor passes little, as in Mekanism.
 *
 * <p><b>The exchange</b> is the exact course of two bodies joined by a conductance over the time
 * given, not one explicit step, so it never passes their common temperature whatever the time,
 * capacities or conductance (HEAT-006), and what one side loses the other gains.
 */
public final class HeatFlow {
    /** How many joules one unit of Mekanism's heat is, unless set. */
    public static final double DEFAULT_JOULES_PER_HEAT_UNIT = 1.0;

    /** The least the unit factor can be set to. */
    public static final double MIN_JOULES_PER_HEAT_UNIT = 0.001;

    /** The most the unit factor can be set to. */
    public static final double MAX_JOULES_PER_HEAT_UNIT = 1000.0;

    /**
     * What one face conducts to the gas it touches, in W/K, unless set: about what a square metre of
     * warm metal gives still air.
     */
    public static final double DEFAULT_FACE_CONDUCTANCE = 10.0;

    /** The most a face can be set to conduct, in W/K. 0, the least, turns the exchange off. */
    public static final double MAX_FACE_CONDUCTANCE = 10_000.0;

    /** Game ticks in a second: Mekanism's coefficients are per tick. */
    private static final double TICKS_PER_SECOND = 20.0;

    private HeatFlow() {}

    /**
     * What one face of a block conducts between its capacitor and the gas the face touches.
     *
     * @param face the face's conductance to the gas, in W/K
     * @param heatCapacity the capacitor's heat capacity, in J/K
     * @param inverse the capacitor's inverse conduction and inverse insulation, added, as Mekanism
     *     counts them; 0 or less for none
     * @return the conductance of the two in series, in W/K; 0 if either is 0 or less
     */
    public static double conductance(double face, double heatCapacity, double inverse) {
        if (!(face > 0) || !(heatCapacity > 0) || !Double.isFinite(face) || !Double.isFinite(heatCapacity)) {
            return 0;
        }
        if (!(inverse > 0)) {
            return face;
        }
        double own = TICKS_PER_SECOND * heatCapacity / inverse;
        return face * own / (face + own);
    }

    /**
     * The heat that flows from a body to a gas joined to it by a conductance, over a time.
     *
     * @param bodyCapacity the body's heat capacity, in J/K
     * @param bodyTemperature the body's temperature, in K
     * @param gasCapacity the gas's heat capacity, in J/K
     * @param gasTemperature the gas's temperature, in K
     * @param conductance the conductance between them, in W/K
     * @param seconds the time
     * @return the heat that leaves the body and enters the gas, in J; negative when the gas is the
     *     hotter. Never more than brings the two to one temperature; 0 if a capacity, the
     *     conductance or the time is 0 or less, or a value isn't finite
     */
    public static double exchange(
            double bodyCapacity,
            double bodyTemperature,
            double gasCapacity,
            double gasTemperature,
            double conductance,
            double seconds) {
        if (!(bodyCapacity > 0) || !(gasCapacity > 0) || !(conductance > 0) || !(seconds > 0)) {
            return 0;
        }
        double difference = bodyTemperature - gasTemperature;
        if (!Double.isFinite(difference) || !Double.isFinite(bodyCapacity) || !Double.isFinite(gasCapacity)) {
            return 0;
        }
        // The difference decays as exp(-G t / C), where C is the two capacities in series.
        double reduced = bodyCapacity / (1 + bodyCapacity / gasCapacity);
        double fraction = -Math.expm1(-conductance * seconds / reduced);
        return difference * reduced * fraction;
    }

    /**
     * Whether gas is too thin to carry heat (HEAT-008).
     *
     * @param moles the gas's moles
     * @param litres its volume
     * @param nearVacuum the density below which gas carries no heat, in mol/L
     * @return true if it is below that density, or holds nothing
     */
    public static boolean thin(double moles, double litres, double nearVacuum) {
        return !(moles > 0) || !(litres > 0) || moles < nearVacuum * litres;
    }
}
