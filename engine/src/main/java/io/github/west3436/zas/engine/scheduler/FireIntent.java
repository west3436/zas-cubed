package io.github.west3436.zas.engine.scheduler;

import java.util.Objects;

/**
 * A world write the worker asks for about a hotspot (architecture §5, FIRE-018): place or remove a
 * {@code zas:gas_fire} block. The server thread applies it with the result, unless the zone's
 * exchange component is rejected (§3.3). It checks the block first: a gas fire is placed only in
 * air and removed only where one is, so an intent the world has moved past does nothing, and the
 * next tick works from the blocks as they are.
 *
 * @param kind place or remove
 * @param block the position ({@link io.github.west3436.zas.engine.SectionKeys#block})
 * @param zoneId the hotspot's zone, or 0 if its cell is no longer zoned
 */
public record FireIntent(Kind kind, long block, long zoneId) {
    /** What to do. */
    public enum Kind {
        /** Place {@code zas:gas_fire} if the position is air. */
        PLACE,
        /** Remove {@code zas:gas_fire} if it is there. */
        REMOVE
    }

    public FireIntent {
        Objects.requireNonNull(kind, "kind");
    }
}
