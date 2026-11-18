package io.github.west3436.zas.atmos.teg;

import io.github.west3436.zas.api.gas.GasMixture;

/**
 * The thermoelectric generator's arithmetic (CONT-157, CONT-158). Pure: no world access, so it is
 * unit-tested.
 *
 * <p><b>The exchange.</b> Two streams, each the gas one circulator passed in an atmos tick, meet in
 * the core. Plain contact would bring them to one temperature, moving Q = ΔT·C<sub>h</sub>C<sub>c
 * </sub>/(C<sub>h</sub> + C<sub>c</sub>) from the hot stream to the cold one. The core takes that Q
 * from the hot stream and turns a share of it into work, η = f·(1 − T<sub>c</sub>/T<sub>h</sub>),
 * with f below 1 ({@code teg_carnot_fraction}). The rest goes to the cold stream, so energy is
 * conserved: Q = W + Q<sub>c</sub>, which vg's TEG isn't (its quirk 9). The work is also capped so
 * that the two streams' entropy never falls, whatever f is set to: Carnot's efficiency at the inlet
 * temperatures overstates what two finite streams can give.
 */
public final class TegMath {
    private TegMath() {}

    /**
     * What one exchange does.
     *
     * @param fromHot the heat taken from the hot stream, J
     * @param toCold the heat given to the cold stream, J
     * @param work the work made, J: {@code fromHot - toCold}
     */
    public record Exchange(double fromHot, double toCold, double work) {
        /** No exchange. */
        public static final Exchange NONE = new Exchange(0, 0, 0);
    }

    /**
     * One exchange between a hot and a cold stream.
     *
     * @param hotC the hot stream's heat capacity, J/K
     * @param hotT its temperature, K
     * @param coldC the cold stream's heat capacity, J/K
     * @param coldT its temperature, K
     * @param carnotFraction the share of Carnot's efficiency it reaches, 0 to 1
     * @param maxWork the most work it may make, J, as what its FE store has room for
     * @return the heats and the work; {@link Exchange#NONE} unless the hot stream is the hotter and
     *     both have gas
     */
    public static Exchange exchange(
            double hotC, double hotT, double coldC, double coldT, double carnotFraction, double maxWork) {
        if (!(hotC > 0) || !(coldC > 0) || !(coldT > 0) || !(hotT > coldT)) {
            return Exchange.NONE;
        }
        double q = (hotT - coldT) * hotC * coldC / (hotC + coldC);
        double eta = Math.max(0, Math.min(1, carnotFraction)) * (1 - coldT / hotT);
        double work = eta * q;
        // The second law: the cold stream must take at least what keeps the total entropy from falling.
        double hotOut = hotT - q / hotC;
        double leastToCold = coldC * coldT * (Math.pow(hotT / hotOut, hotC / coldC) - 1);
        work = Math.min(work, q - leastToCold);
        work = Math.max(0, Math.min(work, maxWork));
        return new Exchange(q, q - work, work);
    }

    /**
     * The moles a circulator passes in an atmos tick (CONT-157): what brings its inlet's network down
     * to its outlet's plus the friction, at most {@code maxLitres} of the inlet's gas. The gas is
     * reckoned at the inlet's temperature on both sides, which is close enough for a passive flow.
     *
     * @param inMoles the inlet network's moles
     * @param inT its temperature, K
     * @param inV its volume, L
     * @param inP its pressure, kPa
     * @param outV the outlet network's volume, L
     * @param outP its pressure, kPa
     * @param frictionKpa the difference it keeps, kPa
     * @param maxLitres the most it passes, L at the inlet's conditions
     * @return the moles, at least 0 and at most the inlet's
     */
    public static double circulatorMoles(
            double inMoles,
            double inT,
            double inV,
            double inP,
            double outV,
            double outP,
            double frictionKpa,
            double maxLitres) {
        double drive = inP - outP - frictionKpa;
        if (!(drive > 0) || !(inMoles > 0) || !(inT > 0) || !(inV > 0) || !(outV > 0)) {
            return 0;
        }
        double even = drive / (GasMixture.GAS_CONSTANT * inT * (1 / inV + 1 / outV));
        double cap = inMoles * Math.min(1, Math.max(0, maxLitres) / inV);
        return Math.max(0, Math.min(Math.min(even, cap), inMoles));
    }

    /**
     * How many lit segments the core's front shows for what it makes (CONT-159): none for nothing, one
     * for any, then one more at each of 25, 100, 400 and 1,600 FE/t.
     *
     * @param fePerTick what it made over its last atmos tick, FE/t
     * @return 0 to {@link TegBlock#MAX_OUTPUT}
     */
    public static int segments(double fePerTick) {
        if (!(fePerTick > 0)) {
            return 0;
        }
        int lit = 1;
        for (double step = 25; step <= 1600 && fePerTick >= step; step *= 4) {
            lit++;
        }
        return lit;
    }
}
