/**
 * The PneumaticCraft: Repressurized adapter (COMPAT-010 to COMPAT-013), built on the public API and
 * PneumaticCraft's own API package ({@code me.desht.pneumaticcraft.api}) only, with no mixins.
 * {@code zas} starts it, through {@link
 * io.github.west3436.zas.compat.pneumaticcraft.PneumaticCraftCompat#init}, only when PneumaticCraft
 * is installed in a version it supports; without PneumaticCraft none of these classes is loaded.
 *
 * <ul>
 *   <li>Pneumatic Armor's protection is data, in the adapter's data pack (COMPAT-010);
 *   <li>{@code ArmorAir}: a piece's air as a tank for internals (COMPAT-011);
 *   <li>{@link io.github.west3436.zas.compat.pneumaticcraft.PncLeaks}: machine leaks into rooms
 *       (COMPAT-012);
 *   <li>{@link io.github.west3436.zas.compat.pneumaticcraft.PncHeat}: heat between heat blocks and
 *       zones (COMPAT-013);
 *   <li>{@link io.github.west3436.zas.compat.pneumaticcraft.AirUnits} and {@link
 *       io.github.west3436.zas.compat.pneumaticcraft.HeatExchange}: the units (Q-24, Q-25) and the
 *       arithmetic, pure;
 *   <li>{@link io.github.west3436.zas.compat.pneumaticcraft.PncSettings}: the server settings, in a
 *       config file of the adapter's own.
 * </ul>
 */
package io.github.west3436.zas.compat.pneumaticcraft;
