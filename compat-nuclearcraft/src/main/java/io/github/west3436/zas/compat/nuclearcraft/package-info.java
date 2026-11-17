/**
 * The NuclearCraft: Neoteric adapter (COMPAT-150 to COMPAT-155), built on the public API and the
 * public fields of NuclearCraft's fission reactor controller only, with no mixins. {@code zas}
 * starts it, through {@link io.github.west3436.zas.compat.nuclearcraft.NuclearCraftCompat#init},
 * only when NuclearCraft is installed in a version it supports; without NuclearCraft none of these
 * classes is loaded.
 *
 * <ul>
 *   <li>NuclearCraft's gases, their hazards, the fluid tags they map from and the HEV suit's
 *       protection are data, in the adapter's data pack and in the pack of gases it shares with the
 *       Mekanism adapter (COMPAT-151, COMPAT-152, COMPAT-154);
 *   <li>{@link io.github.west3436.zas.compat.nuclearcraft.ReactorHeat}: a fission reactor's heat in
 *       the rooms round it (COMPAT-153), with {@link
 *       io.github.west3436.zas.compat.nuclearcraft.SinkHeat}, its arithmetic, pure;
 *   <li>{@link io.github.west3436.zas.compat.nuclearcraft.NuclearCraftSettings}: the server
 *       settings, in a config file of the adapter's own.
 * </ul>
 *
 * <p>Nuclear Radiation's view of ZAS³'s masks (COMPAT-155) needs no code: it is data in the content
 * mod's pack, which Nuclear Radiation reads wherever it is installed.
 */
package io.github.west3436.zas.compat.nuclearcraft;
