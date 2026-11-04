package io.github.west3436.zas.atmos.mixer;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.gas.GasStore;

/**
 * A mixer's step (CONT-077): it adds gas from two inputs to its output, up to a target, each input
 * making up its set fraction of the pressure the output gains. Nothing here touches the world, so
 * it is unit-tested.
 *
 * <p><b>Moles.</b> An input with fraction c is asked for c·ΔP·V<sub>out</sub>/(R·T<sub>in</sub>)
 * moles, which is that fraction of the rise from the output's pressure to the target, at that
 * input's own temperature. Inputs at the same temperature therefore add gas in the set fractions by
 * moles too. Each input gives at most 100 L/s at its own conditions, the
 * pressure pump's limit (CONT-076). When an input can't give what it is asked for, because it is
 * limited or empty, both inputs scale by the same factor, so the fractions hold.
 *
 * <p>An input with a fraction of 0 is never asked for gas, so it can be empty.
 *
 * <p>Gas moves at each input's temperature and mixes into the output conserving thermal energy, as
 * everything added to a {@link GasStore} does (CONT-008). The target is exact when the gases' heat
 * capacities match; otherwise the output can land a little off it, and the next step corrects a
 * shortfall.
 */
public final class MixerMath {
    /** The highest target, kPa (CONT-077). */
    public static final double MAX_TARGET = 4_500;

    /** A new mixer's target, kPa: one atmosphere. */
    public static final double DEFAULT_TARGET = 101.325;

    /** A new mixer's first input's fraction: half and half. */
    public static final double DEFAULT_FRACTION = 0.5;

    /** The most a mixer takes from each input per second, L, at that input's conditions. */
    public static final double LITRES_PER_SECOND = 100;

    private MixerMath() {}

    /**
     * The moles a mixer takes from each input in one step.
     *
     * @param first the moles from the first input
     * @param second the moles from the second input
     */
    public record Flow(double first, double second) {
        /** Nothing moves. */
        public static final Flow NONE = new Flow(0, 0);

        /** The moles the output gains. */
        public double total() {
            return first + second;
        }
    }

    /** A target clamped to 0–4,500 kPa; NaN is 0. */
    public static double clampTarget(double kpa) {
        return Double.isNaN(kpa) ? 0 : Math.max(0, Math.min(MAX_TARGET, kpa));
    }

    /** The first input's fraction clamped to 0–1; NaN is half. The second's is the rest. */
    public static double clampFraction(double fraction) {
        return Double.isNaN(fraction) ? DEFAULT_FRACTION : Math.max(0, Math.min(1, fraction));
    }

    /**
     * The moles a mixer takes from each input this step.
     *
     * @param first the first input's gas
     * @param second the second input's gas
     * @param out the output's gas
     * @param fraction the first input's fraction, clamped to 0–1; the second's is the rest
     * @param target the target, kPa, clamped to 0–4,500
     * @param seconds the step's length, s
     * @return the moles from each input; {@link Flow#NONE} once the output is at the target, or while
     *     an input with a fraction above 0 is empty
     */
    public static Flow moles(
            GasStore first, GasStore second, GasStore out, double fraction, double target, double seconds) {
        double rise = clampTarget(target) - out.pressure();
        if (!(rise > 0) || !(seconds > 0)) {
            return Flow.NONE;
        }
        double c1 = clampFraction(fraction);
        double c2 = 1 - c1;
        double want1 = asked(first, c1, rise, out.volume());
        double want2 = asked(second, c2, rise, out.volume());
        if (want1 < 0 || want2 < 0) {
            return Flow.NONE;
        }
        double scale = Math.min(1, Math.min(scale(first, want1, seconds), scale(second, want2, seconds)));
        return new Flow(want1 * scale, want2 * scale);
    }

    /**
     * What an output asks of an input at its fraction.
     *
     * @return the moles; 0 for a fraction of 0; below 0 if the input can't give any, being empty
     */
    private static double asked(GasStore in, double fraction, double rise, double outVolume) {
        if (!(fraction > 0)) {
            return 0;
        }
        if (!(in.totalMoles() > 0) || !(in.temperature() > 0)) {
            return -1;
        }
        return fraction * rise * outVolume / (GasMixture.GAS_CONSTANT * in.temperature());
    }

    /** How much of what it's asked for an input can give this step; infinite if it's asked for nothing. */
    private static double scale(GasStore in, double want, double seconds) {
        if (!(want > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        return in.totalMoles() * Math.min(1, LITRES_PER_SECOND * seconds / in.volume()) / want;
    }

    /**
     * Moves a flow into the output, each input's gas at its temperature. Any of the stores may be one
     * network: gas leaves before it arrives, so none is lost or counted twice.
     *
     * @param flow the moles, as {@link #moles} gives
     * @param first the first input's gas
     * @param second the second input's gas
     * @param out the output's gas
     */
    public static void move(Flow flow, GasStore first, GasStore second, GasStore out) {
        take(flow.first(), first, out);
        take(flow.second(), second, out);
    }

    private static void take(double moles, GasStore in, GasStore out) {
        double total = in.totalMoles();
        if (moles > 0 && total > 0) {
            out.add(in.takeFraction(moles / total));
        }
    }
}
