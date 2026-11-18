/**
 * The Create Cosmonautics adapter (SPACE-012): inside ZAS³'s zones, and wherever ZAS³'s internals
 * give a breath, Cosmonautics' "can't breathe" in space gives way to ZAS³'s verdict.
 *
 * <ul>
 *   <li>{@link io.github.west3436.zas.compat.cosmonautics.CosmonauticsCompat}: where it starts;
 *   <li>{@code ZoneBreath}: the listener, and the answer it sends each player's client.
 * </ul>
 *
 * <p>It names nothing of Cosmonautics', so it compiles against no other mod and works with any
 * build that keeps its breathing check in NeoForge's event.
 */
package io.github.west3436.zas.compat.cosmonautics;
