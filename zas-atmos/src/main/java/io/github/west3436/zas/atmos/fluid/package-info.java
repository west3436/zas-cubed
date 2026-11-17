/**
 * Gases that other mods store as fluids (COMPAT-140 to COMPAT-146): their fluid tanks joined to
 * pipes, and pipes offered to their fluid pipes and machines, with no block in between.
 *
 * <ul>
 *   <li>{@link io.github.west3436.zas.atmos.fluid.FluidGasFormat}: the datapack format that maps a
 *       fluid tag, such as {@code #c:oxygen}, to a gas;
 *   <li>{@link io.github.west3436.zas.atmos.fluid.FluidGases}: the lookup, resolved against the
 *       server's tags, at the unit factor every mod shares ({@link
 *       io.github.west3436.zas.api.gas.Millibuckets});
 *   <li>{@link io.github.west3436.zas.atmos.fluid.FluidConnection}: the views, one on each side.
 * </ul>
 *
 * <p>It is part of the content mod, not an adapter of its own: it needs no other mod, only
 * NeoForge's fluid capability, and what it joins are the content's pipes and ports, so with the
 * content off (CONT-155) there is nothing for it to do.
 */
package io.github.west3436.zas.atmos.fluid;
