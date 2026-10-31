package io.github.west3436.zas.engine.conservation;

/**
 * The sources and sinks that may change the moles of a gas (CORE-036). Every
 * change the engine makes to zoned gas is recorded against one of these; a
 * change none of them explains is a conservation bug (CORE-073).
 */
public enum LedgerCategory {
    /** API calls, including gas-editing commands (OPS-009). */
    API,
    /** Devices, including leaks from pipes, tanks and canisters. */
    DEVICE,
    /** Gas reactions. */
    REACTION,
    /** Respiration (LIFE-005) and photosynthesis (CONT-053). */
    LIFE,
    /**
     * Exchange with the ambient: ambient edges (CORE-032), snapping to the ambient
     * (CORE-039), open-air operations (CORE-053, CORE-057) and zone dissolution
     * (CORE-052). Unzoned cells hold the ambient implicitly, so ambient gas
     * brought in by a joining cell (CORE-028) and gas released when a cell leaves
     * a zone also count here.
     */
    AMBIENT,
    /** Pruning amounts below the epsilon (CORE-007). */
    PRUNING,
    /** Gas definitions removed from the registry (CORE-013). */
    REMOVED_GAS,
    /** The ZAS displaced-gas option, which deletes a filled cell's gas (CORE-029). */
    DISPLACED_DELETED,
    /** Gas voided at, or supplied by, paused zones and unresolved regions (GEO-036, GEO-038). */
    PAUSED,
    /** Operator commands that reset a region to ambient (OPS-013). */
    OPERATOR
}
