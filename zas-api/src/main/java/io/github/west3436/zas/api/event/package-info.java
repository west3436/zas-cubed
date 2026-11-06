/**
 * Events ZAS³ fires on {@code NeoForge.EVENT_BUS}, on the server thread, when the atmos tick that
 * causes them is applied, or for {@link io.github.west3436.zas.api.event.HazardTickEvent}, just before
 * a hazard harms an entity (API-032). They carry snapshot values, never live engine objects. The
 * fallback fires none.
 *
 * <p>A listener that throws is skipped for that event: the other listeners still hear it, and the
 * fault is logged at most once a minute (NFR-013).
 */
package io.github.west3436.zas.api.event;
