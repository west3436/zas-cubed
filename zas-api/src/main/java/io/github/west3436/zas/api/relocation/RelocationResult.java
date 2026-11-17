package io.github.west3436.zas.api.relocation;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a step of a move did, once the atmos tick that applied it has been applied (API-065, API-016).
 * It is immutable.
 *
 * @param outcome what happened
 * @param zones {@link RelocationOutcome#BEGUN}: the zones suspended, each mapped to itself; {@link
 *     RelocationOutcome#MOVED}: each zone where it left, mapped to its id at the destination, which
 *     is the same id within a dimension and a new one in another; {@link
 *     RelocationOutcome#CANCELLED}: the zones carrying on, each mapped to itself; otherwise empty.
 *     Ordered by the first id
 * @param left the zones with cells both inside and outside the box, which stay where they are: their
 *     cells inside the box react to the blocks moving as to any block change. Listed at {@link
 *     RelocationOutcome#BEGUN} and repeated at {@link RelocationOutcome#MOVED}; otherwise empty
 * @since 1.2.0
 */
@AnyThread
public record RelocationResult(RelocationOutcome outcome, Map<ZoneId, ZoneId> zones, Set<ZoneId> left) {
    /** The result of every step when ZAS³ is absent: nothing happened. */
    public static final RelocationResult ABSENT = of(RelocationOutcome.ABSENT);

    /**
     * Checks and copies the arguments.
     *
     * @throws NullPointerException if an argument is null
     */
    public RelocationResult {
        Objects.requireNonNull(outcome, "outcome");
        zones = Collections.unmodifiableMap(new LinkedHashMap<>(zones));
        TreeSet<ZoneId> sorted = new TreeSet<>(Comparator.comparingLong(ZoneId::value));
        sorted.addAll(left);
        left = Collections.unmodifiableSet(sorted);
    }

    /**
     * A result that lists no zones.
     *
     * <p>Thread: any.
     *
     * @param outcome what happened
     * @return the result
     */
    @AnyThread
    public static RelocationResult of(RelocationOutcome outcome) {
        return new RelocationResult(outcome, Map.of(), Set.of());
    }
}
