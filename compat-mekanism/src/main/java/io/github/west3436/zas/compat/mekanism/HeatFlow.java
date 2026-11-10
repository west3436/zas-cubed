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
 *
 * <p><b>The ceiling.</b> One body, which is one capacitor and so one block or one whole multiblock,
 * passes no more than so many watts to and from the zones it touches, taken together ({@link
 * #DEFAULT_MAX_BODY_WATTS}, a server setting). Mekanism's hottest and largest blocks were never
 * meant as room heat: a running Fusion Reactor's case is at 5 × 10⁷ K and more, and through even
 * its insulation one port face would pass up to two megawatts; a large boiler has over a thousand
 * faces. Held to the ceiling, such a body makes a sealed room deadly in minutes, which a player can
 * deal with, and not in seconds. While the conductance would carry more than the ceiling, the heat
 * flows at the ceiling; once the two are close enough that it carries less, they close as before.
 * That is still the exact course, so the exchange is still never past the common temperature. A
 * body that touches several zones has its ceiling shared out among them ({@link #share}).
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

    /**
     * The most watts one body passes, unless set. 0 is no ceiling. It is chosen so that a sealed room
     * with a Fusion Reactor in it becomes deadly in 10 to 15 minutes if nothing is done about it.
     * With a formed reactor held at its highest injection rate, the air of the smallest stone room
     * that holds one, 7 by 7 by 7 with open air outside, passed 360 K, where an unprotected player
     * starts to burn, after 11 to 15 minutes, and that of a 9 by 5 by 9 room after 14 to 25; cut out
     * of solid stone the two took 6 to 13. Two or three of the content mod's air conditioners hold
     * such a room under 360 K with open air outside, six to eight underground, and ten carry all of
     * it off. A Resistive Heater passes 1.2 kW and four Fission Reactor ports at 1,200 K pass 35 kW,
     * so only the hottest and the largest bodies are held back.
     */
    public static final double DEFAULT_MAX_BODY_WATTS = 50_000.0;

    /** The most the ceiling can be set to, in W. */
    public static final double HIGHEST_MAX_BODY_WATTS = 1.0e12;

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
     * The heat that flows from a body to a gas joined to it by a conductance, over a time, with no
     * ceiling.
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
        return exchange(
                bodyCapacity,
                bodyTemperature,
                gasCapacity,
                gasTemperature,
                conductance,
                seconds,
                Double.POSITIVE_INFINITY);
    }

    /**
     * The heat that flows from a body to a gas joined to it by a conductance, over a time, at no more
     * than so many watts.
     *
     * <p>While the conductance would carry more than the ceiling, the heat flows at the ceiling, which
     * closes the difference at a steady rate. From the moment the difference is small enough that the
     * conductance carries no more than the ceiling, it decays as it does with no ceiling. Both parts
     * are worked out exactly, so the answer doesn't depend on how the time is cut into steps.
     *
     * @param bodyCapacity the body's heat capacity, in J/K
     * @param bodyTemperature the body's temperature, in K
     * @param gasCapacity the gas's heat capacity, in J/K
     * @param gasTemperature the gas's temperature, in K
     * @param conductance the conductance between them, in W/K
     * @param seconds the time
     * @param mostWatts the most heat that flows in a second, either way, in W; {@link
     *     Double#POSITIVE_INFINITY} for no ceiling
     * @return the heat that leaves the body and enters the gas, in J; negative when the gas is the
     *     hotter. Never more than brings the two to one temperature, nor than the ceiling allows in
     *     the time; 0 if a capacity, the conductance, the time or the ceiling is 0 or less, or a
     *     value isn't a number
     */
    public static double exchange(
            double bodyCapacity,
            double bodyTemperature,
            double gasCapacity,
            double gasTemperature,
            double conductance,
            double seconds,
            double mostWatts) {
        if (!(bodyCapacity > 0) || !(gasCapacity > 0) || !(conductance > 0) || !(seconds > 0) || !(mostWatts > 0)) {
            return 0;
        }
        double difference = bodyTemperature - gasTemperature;
        if (!Double.isFinite(difference) || !Double.isFinite(bodyCapacity) || !Double.isFinite(gasCapacity)) {
            return 0;
        }
        // The difference decays as exp(-G t / C), where C is the two capacities in series.
        double reduced = bodyCapacity / (1 + bodyCapacity / gasCapacity);
        // The difference at which the conductance carries just the ceiling.
        double level = mostWatts / conductance;
        double size = Math.abs(difference);
        if (!(size > level)) {
            return difference * reduced * -Math.expm1(-conductance * seconds / reduced);
        }
        // At the ceiling the difference closes by mostWatts / C a second, until it is down to the level.
        double atCeiling = (size - level) * reduced / mostWatts;
        if (atCeiling >= seconds) {
            return Math.copySign(mostWatts * seconds, difference);
        }
        double after = level * reduced * -Math.expm1(-conductance * (seconds - atCeiling) / reduced);
        return Math.copySign((size - level) * reduced + after, difference);
    }

    /**
     * What of a body's ceiling one of the zones it touches has: the ceiling shared out in proportion
     * to what each zone would be passed with no ceiling, so that all of them together stay under it.
     * A body with one zone gives it the whole ceiling.
     *
     * @param most the body's ceiling, in W; 0 or less for none
     * @param wouldPass what this zone would be passed now, in W, either way
     * @param wouldPassInAll the sizes of what each of the body's zones would be passed, added up, in W
     * @return this zone's ceiling, in W, for {@link #exchange}: {@link Double#POSITIVE_INFINITY} if
     *     the body has no ceiling, and 0 for a zone that would be passed nothing
     */
    public static double share(double most, double wouldPass, double wouldPassInAll) {
        if (!(most > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        double size = Math.abs(wouldPass);
        if (!(size > 0) || !(wouldPassInAll > 0)) {
            return 0;
        }
        return most * Math.min(1, size / wouldPassInAll);
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
