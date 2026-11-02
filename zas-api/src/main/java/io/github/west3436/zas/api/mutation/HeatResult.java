package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.AnyThread;
import java.util.Objects;

/**
 * The result of a heat mutation, once the atmos tick that applied it has been applied (API-013,
 * API-016). It is immutable.
 *
 * @param outcome where the heat went or came from: {@link MutationOutcome#ZONE}, {@link
 *     MutationOutcome#AMBIENT} (the open air takes or supplies any amount, unchanged, HEAT-010),
 *     {@link MutationOutcome#PAUSED} (added heat voided, removed heat supplied without cooling the
 *     paused zone) or {@link MutationOutcome#ABSENT}
 * @param joules the energy actually moved, in J: for an addition, how much of the offered heat
 *     was taken; for a removal, the heat delivered to the caller, which may be less than asked
 *     when a zone reaches its temperature floor
 * @param temperature the temperature in K of the zone, after the change, or of the ambient; NaN if
 *     unknown (a paused region with no zone, or ZAS³ absent)
 */
@AnyThread
public record HeatResult(MutationOutcome outcome, double joules, double temperature) {
    /** The result of every heat mutation when ZAS³ is absent: nothing moved. */
    public static final HeatResult ABSENT = new HeatResult(MutationOutcome.ABSENT, 0, Double.NaN);

    /**
     * Checks the arguments.
     *
     * @throws NullPointerException if the outcome is null
     * @throws IllegalArgumentException if the energy is negative or not finite
     */
    public HeatResult {
        Objects.requireNonNull(outcome, "outcome");
        if (!(joules >= 0) || !Double.isFinite(joules)) {
            throw new IllegalArgumentException("joules must be finite and >= 0: " + joules);
        }
    }
}
