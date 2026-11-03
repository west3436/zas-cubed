package io.github.west3436.zas.engine.scheduler;

import java.util.Objects;

/**
 * A zone caught fire or stopped burning (API-031): its first hotspot appeared, or its last one went
 * out. Carried in a {@link Result} and fired on the server thread at apply, unless the zone's
 * exchange component is rejected (architecture §6.4).
 *
 * <p>Zones are named by id, so a fire that a merge or split carries into a zone with another id
 * goes out in the old zone and starts in the new one.
 *
 * @param kind started or stopped
 * @param zoneId the zone
 * @param block a hotspot of the zone ({@link io.github.west3436.zas.engine.SectionKeys#block}):
 *     for {@link Kind#IGNITED} the lowest of its hotspots now, for {@link Kind#EXTINGUISHED} the
 *     lowest it had before
 */
public record FireEvent(Kind kind, long zoneId, long block) {
    /** What happened. */
    public enum Kind {
        IGNITED,
        EXTINGUISHED
    }

    public FireEvent {
        Objects.requireNonNull(kind, "kind");
    }
}
