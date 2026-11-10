/**
 * The Mekanism adapter (MEK), a reference integration built on the public API and Mekanism's own
 * API only (D-11), so that another mod can copy it. {@code zas} starts it, through {@link
 * io.github.west3436.zas.compat.mekanism.MekanismCompat#init}, only when Mekanism is installed in a
 * version it supports (MEK-001); without Mekanism none of these classes is loaded.
 *
 * <p>What the other parts of the adapter build on:
 *
 * <ul>
 *   <li>{@link io.github.west3436.zas.compat.mekanism.ChemicalGases}: which gas a Mekanism chemical
 *       is, and which chemical a gas is (MEK-010), with the amounts converted (MEK-002);
 *   <li>{@link io.github.west3436.zas.compat.mekanism.MekanismSettings}: the adapter's server
 *       settings, in a config file of its own;
 *   <li>{@link io.github.west3436.zas.compat.mekanism.ZasChemicals}: ZAS³'s gases that Mekanism
 *       lacks, registered as chemicals (MEK-011);
 *   <li>{@code ChemicalConnection}: tubes, tanks and machines joined to pipes with no block in
 *       between, each side seeing the other's capability (MEK-013), under the rule for mixtures in
 *       {@link io.github.west3436.zas.compat.mekanism.GasPurity};
 *   <li>the {@code mixin} package: the only place that may name a class of Mekanism's outside its
 *       API, applied only to a Mekanism the adapter supports.
 * </ul>
 */
package io.github.west3436.zas.compat.mekanism;
