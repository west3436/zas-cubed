package io.github.west3436.zas.compat.pneumaticcraft;

/**
 * The arithmetic of heat between one of PneumaticCraft's heat exchangers and a zone's gas
 * (COMPAT-013, Q-25). Pure: no PneumaticCraft and no game, so the unit tests reach every case.
 * {@link PncHeat} finds the blocks and moves the heat.
 *
 * <p><b>Units.</b> PneumaticCraft counts heat as temperature times its own thermal capacity: adding
 * one unit of heat to a block of capacity C warms it by 1/C K. A server setting says how many
 * joules one unit is ({@link #DEFAULT_JOULES_PER_HEAT_UNIT}); every value here is already in
 * joules and J/K.
 *
 * <p><b>Conductance.</b> A face of the block that touches a zone's gas conducts as a wall does
 * (HEAT-012), so many W/K for the face ({@link #DEFAULT_FACE_CONDUCTANCE}, a server setting), in
 * series with the block's own thermal resistance. PneumaticCraft moves ΔT / (R₁ + R₂) units of heat
 * a tick between two exchangers whose resistances are R₁ and R₂; one exchanger's own part is then a
 * conductance of 20 × J / R W/K, where J is the joules in a unit. A block PneumaticCraft makes slow
 * to pass heat, such as a Vortex Tube's middle, stays slow.
 *
 * <p><b>The exchange</b> is the exact course of two bodies joined by a conductance over the time
 * given, not one explicit step, so it never passes their common temperature whatever the time,
 * capacities or conductance (HEAT-006), and what one side loses the other gains.
 */
public final class HeatExchange {
    /**
     * How many joules one unit of PneumaticCraft's heat is, unless set. PneumaticCraft's own
     * Mekanism integration makes 100 units of Mekanism's heat one of its own (its {@code
     * mek_thermal_conversion_efficiency} of 0.01), and ZAS³'s Mekanism adapter counts a unit of
     * Mekanism's heat as 1 J. So heat that goes from a Mekanism block through a PneumaticCraft one
     * into a room is the same number of joules either way.
     */
    public static final double DEFAULT_JOULES_PER_HEAT_UNIT = 100.0;

    /** The least the unit factor can be set to. */
    public static final double MIN_JOULES_PER_HEAT_UNIT = 0.001;

    /** The most the unit factor can be set to. */
    public static final double MAX_JOULES_PER_HEAT_UNIT = 1.0e7;

    /**
     * What one face conducts to the gas it touches, in W/K, unless set: about what a square metre of
     * warm metal gives still air, as for Mekanism's heat blocks.
     */
    public static final double DEFAULT_FACE_CONDUCTANCE = 10.0;

    /** The most a face can be set to conduct, in W/K. 0, the least, turns the exchange off. */
    public static final double MAX_FACE_CONDUCTANCE = 10_000.0;

    /** Game ticks in a second. */
    private static final double TICKS_PER_SECOND = 20.0;

    private HeatExchange() {}

    /**
     * What one face of a block conducts between the block and the gas it touches: the face's
     * setting in series with the block's own resistance.
     *
     * @param face the face's conductance, in W/K, at least 0
     * @param joulesPerUnit the joules in one unit of PneumaticCraft's heat, above 0
     * @param resistance the block's thermal resistance as PneumaticCraft counts it, at least 0
     * @return the conductance in W/K; 0 if either part passes nothing
     */
    public static double conductance(double face, double joulesPerUnit, double resistance) {
        if (!(face > 0) || !(joulesPerUnit > 0) || !Double.isFinite(face)) {
            return 0;
        }
        if (!(resistance > 0)) {
            return face;
        }
        if (!Double.isFinite(resistance)) {
            return 0;
        }
        double own = TICKS_PER_SECOND * joulesPerUnit / resistance;
        return 1 / (1 / face + 1 / own);
    }

    /**
     * The heat that flows from a block into a gas over a time: the exact course of two bodies joined
     * by a conductance, so it never passes their common temperature.
     *
     * @param blockCapacity the block's heat capacity, in J/K, above 0
     * @param blockTemperature its temperature, in K
     * @param gasCapacity the gas's heat capacity, in J/K, above 0
     * @param gasTemperature its temperature, in K
     * @param conductance what joins them, in W/K, at least 0
     * @param seconds the time, at least 0
     * @return the joules that flow: above 0 from the block to the gas, below 0 the other way
     */
    public static double exchange(
            double blockCapacity,
            double blockTemperature,
            double gasCapacity,
            double gasTemperature,
            double conductance,
            double seconds) {
        if (!(blockCapacity > 0)
                || !(gasCapacity > 0)
                || !(conductance > 0)
                || !(seconds > 0)
                || !Double.isFinite(blockTemperature)
                || !Double.isFinite(gasTemperature)) {
            return 0;
        }
        double difference = blockTemperature - gasTemperature;
        if (difference == 0) {
            return 0;
        }
        // The two close as exp(-G t / μ), where μ is their reduced heat capacity; all the way, they
        // would pass μ ΔT. A body of infinite capacity is the other body alone.
        double reduced = Double.isInfinite(blockCapacity)
                ? gasCapacity
                : Double.isInfinite(gasCapacity)
                        ? blockCapacity
                        : blockCapacity * gasCapacity / (blockCapacity + gasCapacity);
        double joules = difference * reduced * -Math.expm1(-conductance * seconds / reduced);
        return Double.isFinite(joules) ? joules : 0;
    }
}
