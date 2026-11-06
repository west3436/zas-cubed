package io.github.west3436.zas.engine.zone;

import java.util.Objects;

/**
 * A change in a zone's life (API-030). The graph collects these as they happen ({@link
 * ZoneGraph#drainZoneEvents()}), and they are fired on the server thread at apply.
 *
 * @param kind what happened
 * @param zoneId the zone: for {@link Kind#MERGED} the survivor, for {@link Kind#SPLIT} the zone that
 *     keeps its id
 * @param otherId for {@link Kind#MERGED} the zone absorbed, for {@link Kind#SPLIT} the new zone, otherwise 0
 * @param cell a cell of the zone, or for a zone now gone, a cell it had
 * @param undoable whether a rejected exchange component takes this back. What happens before the
 *     undo point stands, so a rejected component's zones still fire those events
 */
public record ZoneEvent(Kind kind, long zoneId, long otherId, long cell, boolean undoable) {
    /** What happened. */
    public enum Kind {
        /** A zone formed. Not a split's remnant, and not a zone coming back from chunk storage. */
        CREATED,
        /** Two zones became one. */
        MERGED,
        /** Part of a zone became a new zone. */
        SPLIT,
        /** A zone with no ambient edge gained one. */
        BREACHED,
        /** A zone with an ambient edge lost its last. */
        SEALED,
        /** A zone stopped existing, and its gas went with it. */
        DISSOLVED,
        /** A zone stopped being simulated. */
        PAUSED,
        /** A paused zone, or one back from chunk storage, is simulated again. */
        RESUMED
    }

    public ZoneEvent {
        Objects.requireNonNull(kind, "kind");
    }

    /** This event as one a rejected exchange component takes back. */
    public ZoneEvent asUndoable() {
        return new ZoneEvent(kind, zoneId, otherId, cell, true);
    }
}
