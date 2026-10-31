package io.github.west3436.zas.api.mutation;

/** Where a mutation's gas went, or came from (API-016). */
public enum MutationOutcome {
    /** A zone: the gas was added to it or removed from it. */
    ZONE,
    /**
     * The ambient: added gas was lost to the open air, and removed gas was
     * supplied from it without depleting it.
     */
    AMBIENT,
    /**
     * A paused zone: added gas was voided, and removed gas was supplied from
     * its last-known mixture without depleting it (GEO-036, GEO-038).
     */
    PAUSED,
    /**
     * ZAS³ is not installed: nothing happened and nothing moved (API-009,
     * D-17).
     */
    ABSENT
}
