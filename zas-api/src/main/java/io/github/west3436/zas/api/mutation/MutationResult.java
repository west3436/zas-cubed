package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.GasParcel;
import java.util.Objects;

/**
 * The result of a gas mutation, once the atmos tick that applied it has been
 * applied (API-016). It is immutable.
 *
 * @param outcome where the gas went or came from
 * @param moved the gas actually moved: for an addition, how much of the
 *     offered gas was taken; for a removal, the gas delivered to the caller
 */
@AnyThread
public record MutationResult(MutationOutcome outcome, GasParcel moved) {
    /** The result of every mutation when ZAS³ is absent: nothing moved. */
    public static final MutationResult ABSENT = new MutationResult(MutationOutcome.ABSENT, GasParcel.EMPTY);

    /**
     * Checks the arguments.
     *
     * @throws NullPointerException if an argument is null
     */
    public MutationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(moved, "moved");
    }
}
