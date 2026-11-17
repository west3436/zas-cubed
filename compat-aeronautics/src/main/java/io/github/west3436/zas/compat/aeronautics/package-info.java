/**
 * The Create Aeronautics adapter (COMPAT-080 to COMPAT-083): lift follows the density of the
 * dimension's air. {@code zas} starts it, through {@link
 * io.github.west3436.zas.compat.aeronautics.AeronauticsCompat#init}, only when Aeronautics is
 * installed in a version it supports; without it none of these classes is loaded.
 *
 * <p>Aeronautics' lift reads Sable's air pressure, so the adapter compiles against Sable and names
 * nothing of Aeronautics'. It uses ZAS³'s internals for the ambient and the gases' molar masses,
 * which the public API doesn't give (COMPAT-001 allows it for these adapters).
 *
 * <ul>
 *   <li>{@code mixin.AirPressureMixin}: the hook into Sable's pressure (COMPAT-080);
 *   <li>{@link io.github.west3436.zas.compat.aeronautics.LiftFactors}: each dimension's factor on
 *       the server and on clients (COMPAT-080 to COMPAT-083), and {@link
 *       io.github.west3436.zas.compat.aeronautics.LiftFactor}, its arithmetic, pure;
 *   <li>{@link io.github.west3436.zas.compat.aeronautics.SableSeed}: ambients seeded from Sable's
 *       {@code dimension_physics} (COMPAT-082);
 *   <li>{@link io.github.west3436.zas.compat.aeronautics.AeroSettings}: the server settings, in a
 *       config file of the adapter's own.
 * </ul>
 */
package io.github.west3436.zas.compat.aeronautics;
