package io.github.west3436.zas.atmos.mixer;

import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pump.PumpFlow;

/**
 * A mixer's step (CONT-077): it adds gas from two inputs to its output, up to a target, each input
 * making up its set fraction of the pressure the output gains. Nothing here touches the world, so
 * it is unit-tested.
 *
 * <p><b>Moles.</b> An input with fraction c gives moles in proportion to c/T<sub>in</sub>, so each
 * makes up its fraction of the pressure rise at its own temperature. Inputs at the same temperature
 * therefore add gas in the set fractions by moles too. Together they are asked for what brings the
 * output to the target, as a pressure pump works that out ({@link PumpFlow#molesToTarget}), allowing
 * for the temperature the output settles at. Each input gives at most 100 L/s at its own conditions,
 * the pressure pump's limit (CONT-076). When an input can't give what it is asked for, because it is
 * limited or empty, both inputs scale by the same factor, so the fractions hold.
 *
 * <p>An input with a fraction of 0 is never asked for gas, so it can be empty.
 *
 * <p>Gas moves at each input's temperature and mixes into the output conserving thermal energy, as
 * everything added to a {@link GasStore} does (CONT-008).
 */
public final class MixerMath {
    /** A new mixer's first input's fraction: half and half. */
    public static final double DEFAULT_FRACTION = 0.5;

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
        double kpa = PumpFlow.clampTarget(target);
        if (out.pressure() >= kpa || !(seconds > 0)) {
            return Flow.NONE;
        }
        double c1 = clampFraction(fraction);
        double c2 = 1 - c1;
        if (empty(first, c1) || empty(second, c2)) {
            return Flow.NONE;
        }
        double part1 = c1 > 0 ? c1 / first.temperature() : 0;
        double part2 = c2 > 0 ? c2 / second.temperature() : 0;
        double all = toTarget(first, part1, second, part2, out, kpa);
        double want1 = part1 / (part1 + part2) * all;
        double want2 = part2 / (part1 + part2) * all;
        double scale = Math.min(1, Math.min(available(first, want1, seconds), available(second, want2, seconds)));
        return new Flow(want1 * scale, want2 * scale);
    }

    /** Whether an input is asked for gas it can't give at all. */
    private static boolean empty(GasStore in, double fraction) {
        return fraction > 0 && (!(in.totalMoles() > 0) || !(in.temperature() > 0));
    }

    /**
     * The moles of the two inputs' gas, mixed in the proportion of their parts, that bring the output
     * to the target.
     */
    private static double toTarget(
            GasStore first, double part1, GasStore second, double part2, GasStore out, double target) {
        double heat1 = part1 > 0 ? part1 * first.heatCapacity() / first.totalMoles() : 0;
        double heat2 = part2 > 0 ? part2 * second.heatCapacity() / second.totalMoles() : 0;
        double temperature = GasProperties.mixedTemperature(heat1, first.temperature(), heat2, second.temperature());
        PumpFlow.Side gas = new PumpFlow.Side(part1 + part2, heat1 + heat2, temperature, first.volume());
        return PumpFlow.molesToTarget(target, gas, PumpFlow.Side.of(out));
    }

    /** How much of what it's asked for an input can give this step; infinite if it's asked for nothing. */
    private static double available(GasStore in, double want, double seconds) {
        if (!(want > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        return PumpFlow.molesInVolume(PumpFlow.MAX_RATE * seconds, PumpFlow.Side.of(in)) / want;
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
