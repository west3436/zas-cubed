package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of a pin, once the atmos tick that applied it has been applied (API-050, API-016).
 * It is immutable.
 *
 * @param outcome what the pin found
 * @param zone the zone now pinned, present exactly when the outcome is {@link PinOutcome#ZONE}
 */
@AnyThread
public record PinResult(PinOutcome outcome, Optional<ZoneId> zone) {
    /** The result of every pin when ZAS³ is absent: nothing pinned. */
    public static final PinResult ABSENT = new PinResult(PinOutcome.ABSENT, Optional.empty());

    /**
     * Checks the arguments.
     *
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a zone is given for any outcome but {@link
     *     PinOutcome#ZONE}, or none for it
     */
    public PinResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(zone, "zone");
        if (zone.isPresent() != (outcome == PinOutcome.ZONE)) {
            throw new IllegalArgumentException("a pin names a zone exactly when its outcome is ZONE: " + outcome);
        }
    }

    /**
     * The result of a pin that pinned a zone.
     *
     * <p>Thread: any.
     *
     * @param zone the zone
     * @return the result
     */
    @AnyThread
    public static PinResult pinned(ZoneId zone) {
        return new PinResult(PinOutcome.ZONE, Optional.of(zone));
    }

    /**
     * The result of a pin that pinned nothing.
     *
     * <p>Thread: any.
     *
     * @param outcome why: {@link PinOutcome#OPEN}, {@link PinOutcome#UNRESOLVED} or {@link
     *     PinOutcome#ABSENT}
     * @return the result
     * @throws IllegalArgumentException if the outcome is {@link PinOutcome#ZONE}
     */
    @AnyThread
    public static PinResult none(PinOutcome outcome) {
        return new PinResult(outcome, Optional.empty());
    }
}
