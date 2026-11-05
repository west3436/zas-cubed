package io.github.west3436.zas.airflow;

import java.util.List;

/**
 * One active edge, as a client needs it to show the flow (CLIENT-003, CLIENT-010, DATA-015). Its
 * zones aren't included (D-53). Platform-free.
 *
 * @param kpa the edge's pressure difference, in whole kPa, so a change smaller than that isn't a
 *     change worth a packet
 * @param flowStep the moles per second through its openings in range, as a step of {@link
 *     #step}, for the same reason; 0 is none
 * @param openings its openings within the player's range, at most {@link
 *     VisibleFlows#MAX_OPENINGS_PER_FLOW}
 */
public record VisibleFlow(int kpa, int flowStep, List<VisibleOpening> openings) {
    /** The steps for each doubling of 1 + moles per second, about 9 % apart at a strong flow. */
    private static final double STEPS_PER_DOUBLING = 8;

    public VisibleFlow {
        openings = List.copyOf(openings);
    }

    /** The step nearest these moles per second. */
    public static int step(double molesPerSecond) {
        return (int) Math.round(STEPS_PER_DOUBLING * log2(1 + Math.max(0, molesPerSecond)));
    }

    /** The moles per second through the openings, from {@link #flowStep}. */
    public double molesPerSecond() {
        return Math.pow(2, flowStep / STEPS_PER_DOUBLING) - 1;
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }
}
