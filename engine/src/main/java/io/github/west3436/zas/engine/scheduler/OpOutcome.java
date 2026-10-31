package io.github.west3436.zas.engine.scheduler;

import java.util.Arrays;
import java.util.Objects;

/**
 * What one {@link GasOp} did (API-016). The server thread completes the operation's future with it
 * when the result is applied, unless the operation's component is rejected (CORE-070), in which
 * case the operation runs again in a later tick (CORE-074).
 *
 * @param seq the operation's sequence number
 * @param outcome where the gas went or came from
 * @param zoneId the zone, for {@link Outcome#ZONE}, or the paused zone for {@link
 *     Outcome#UNRESOLVED} at a paused zone; otherwise 0
 * @param moved per gas: for an addition, the moles taken; for a removal, the moles delivered
 * @param temperature the temperature of the moved gas in K
 * @param component the index of the operation's exchange component in {@link
 *     Result#components()}, or -1 if it touched no zone
 */
public record OpOutcome(long seq, Outcome outcome, long zoneId, double[] moved, double temperature, int component) {

    /** Where an operation's gas went or came from. */
    public enum Outcome {
        /** A zone: the gas was added to it or removed from it. */
        ZONE,
        /**
         * The ambient: added gas was released to the open air and removed gas was supplied from
         * it without depleting it (CORE-053, CORE-057).
         */
        AMBIENT,
        /**
         * An unresolved region or a paused zone: added gas was voided and removed gas was supplied
         * without depletion (GEO-036, GEO-038). The API reports it as paused.
         */
        UNRESOLVED
    }

    public OpOutcome {
        Objects.requireNonNull(outcome, "outcome");
        moved = moved.clone();
    }

    @Override
    public double[] moved() {
        return moved.clone();
    }

    /** The moles moved of one gas, without copying. */
    public double moved(int gas) {
        return moved[gas];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OpOutcome r
                && seq == r.seq
                && outcome == r.outcome
                && zoneId == r.zoneId
                && Arrays.equals(moved, r.moved)
                && Double.compare(temperature, r.temperature) == 0
                && component == r.component;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(seq) * 31 + outcome.hashCode();
    }

    @Override
    public String toString() {
        return "OpOutcome#" + seq + "[" + outcome + (zoneId != 0 ? " " + zoneId : "") + ", " + Arrays.toString(moved)
                + " at " + temperature + " K" + (component >= 0 ? ", component " + component : "") + "]";
    }
}
