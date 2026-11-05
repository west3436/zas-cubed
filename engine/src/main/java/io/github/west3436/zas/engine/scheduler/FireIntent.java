package io.github.west3436.zas.engine.scheduler;

import java.util.Objects;

/**
 * A world write the worker asks for about a fire (architecture §5): place or remove a {@code
 * zas:gas_fire} block (FIRE-018), or light a vanilla fire beside a flammable block (FIRE-033,
 * FIRE-034). The server thread applies it with the result, unless the zone's exchange component is
 * rejected (§3.3). It checks the block first: a gas fire is placed only in air and removed only
 * where one is, and a vanilla fire is lit only in air or a gas fire, so an intent the world has moved
 * past does nothing, and the next tick works from the blocks as they are.
 *
 * @param kind place, remove or light
 * @param block the position ({@link io.github.west3436.zas.engine.SectionKeys#block})
 * @param zoneId the hotspot's zone, or 0 if its cell is no longer zoned
 * @param catches whether this is a {@link Kind#PLACE} of the first gas fire in its zone: gas catching
 *     fire, not a fire spreading (FIRE-036)
 * @param fuelMoles the moles of its zone's most plentiful fuel, for an intent that {@code catches};
 *     otherwise 0
 */
public record FireIntent(Kind kind, long block, long zoneId, boolean catches, double fuelMoles) {
    /** What to do. */
    public enum Kind {
        /** Place {@code zas:gas_fire} if the position is air. */
        PLACE,
        /** Remove {@code zas:gas_fire} if it is there. */
        REMOVE,
        /**
         * Light a vanilla fire, under vanilla's limits, if the position is air or a gas fire: a
         * flammable block next to it caught (FIRE-033, FIRE-034).
         */
        LIGHT
    }

    public FireIntent {
        Objects.requireNonNull(kind, "kind");
    }

    /** An intent that isn't gas catching fire. */
    public FireIntent(Kind kind, long block, long zoneId) {
        this(kind, block, zoneId, false, 0);
    }
}
