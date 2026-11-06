/**
 * Life support: the breathability helper and its verdict (API-014), and hazards of your own (LIFE-032).
 * {@link io.github.west3436.zas.api.life.Breathing#of} is called on the main thread of the entity's
 * logical side; the {@link io.github.west3436.zas.api.life.Breathability} it returns is immutable
 * and may be used from any thread. {@link io.github.west3436.zas.api.life.HazardHandlers} registers
 * on any thread, and ZAS³ calls a {@link io.github.west3436.zas.api.life.HazardHandler} on the server
 * thread.
 */
package io.github.west3436.zas.api.life;
