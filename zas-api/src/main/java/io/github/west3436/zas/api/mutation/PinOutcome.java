package io.github.west3436.zas.api.mutation;

/** What a pin found at its point (API-050). */
public enum PinOutcome {
    /**
     * A zone: it is pinned now. It was already there, or the point's region was enclosed and had no
     * zone, so one formed holding the ambient's gas.
     */
    ZONE,
    /**
     * The point is in the open, or inside a block with no gas around it: no zone exists there and
     * none can form, so nothing was pinned. Pin again once the region is enclosed.
     */
    OPEN,
    /**
     * The point's region can't be classified now: it reaches a chunk that isn't loaded (GEO-024),
     * or the server stopped first. Nothing was pinned; pin again later.
     */
    UNRESOLVED,
    /** ZAS³ is not installed: nothing happened (API-009, D-17). */
    ABSENT
}
