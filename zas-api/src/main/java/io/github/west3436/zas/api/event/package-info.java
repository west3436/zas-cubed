/**
 * Events ZAS³ fires on {@code NeoForge.EVENT_BUS}, on the server thread, when the atmos tick that
 * causes them is applied (API-032). They carry snapshot values, never live engine objects. The
 * fallback fires none.
 */
package io.github.west3436.zas.api.event;
