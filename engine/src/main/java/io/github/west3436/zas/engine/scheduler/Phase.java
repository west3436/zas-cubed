package io.github.west3436.zas.engine.scheduler;

/**
 * The phases of one atmos tick, in the order they run (CORE-040, architecture §3.2). {@link
 * #RESTORE} is the scheduler's own step 0; the others are CORE-040's eight phases.
 */
public enum Phase {
    /** 0: restore components the server thread rejected and re-queue their operations (CORE-074). */
    RESTORE,
    /** 1: geometry updates from coalesced block changes (GEO-012). */
    GEOMETRY,
    /** 2: deferred zone-boundary updates: breaches (GEO-023, GEO-025, GEO-026). */
    BOUNDARIES,
    /** 3: queued gas operations; zone creation happens here (CORE-051). */
    GAS_OPERATIONS,
    /** 4: edge exchange (CORE-031), merges and dissolution (CORE-023, CORE-076). */
    EXCHANGE,
    /** 5: heat conduction (HEAT-001, R1). */
    HEAT,
    /** 6: hotspots (R1). */
    HOTSPOTS,
    /** 7: zone updates: reactions and events (R1). */
    ZONE_UPDATES,
    /** 8: zone garbage collection (CORE-052, R1). */
    GARBAGE_COLLECTION;

    /**
     * Observes phase starts, for tests and the profiler (OPS-004). Called on the thread that runs
     * the tick: the worker, or the server thread in single-threaded mode.
     */
    @FunctionalInterface
    public interface Listener {
        Listener NONE = phase -> {};

        /** A phase starts; the previous one, if any, has ended. */
        void enter(Phase phase);

        /**
         * The tick has ended: the last phase, the invariant checker and building the result are
         * done. The default does nothing.
         */
        default void finished() {}
    }
}
