package io.github.west3436.zas.api.fire;

import io.github.west3436.zas.api.AnyThread;

/** What an ignition found (API-056, API-058). */
@AnyThread
public enum IgnitionOutcome {
    /** A new fire burns: a hotspot started at {@link Ignition#hotspot()}, in {@link Ignition#zone()}. */
    IGNITED,
    /** A fire already burned at the position or next to it, at {@link Ignition#hotspot()}. */
    BURNING,
    /**
     * A zone was there, but its gas won't burn: no fuel within its flammability limits, or too
     * little O₂ (FIRE-030).
     */
    NOT_FLAMMABLE,
    /**
     * A zone with gas that would burn was there, but neither the position nor any of its six
     * neighbours in that zone is air a fire block can go in (API-058). Nothing burns; try again
     * once there is room.
     */
    NO_SPACE,
    /** No zone was there: the position and its neighbours are open air or solid. */
    OPEN,
    /** Only a paused zone or an unresolved region was there (GEO-030); nothing burns until it resumes. */
    PAUSED,
    /** ZAS³ isn't installed (D-17): nothing burns. */
    ABSENT
}
