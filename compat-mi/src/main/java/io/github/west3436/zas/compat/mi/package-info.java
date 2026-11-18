/**
 * The Modern Industrialization adapter (COMPAT-090 to COMPAT-093), built on nothing of Modern
 * Industrialization's: it is data, offered by {@link
 * io.github.west3436.zas.compat.mi.ModernIndustrializationCompat#init}, which {@code zas} calls only
 * when Modern Industrialization is installed in a version it supports. Without it, none of these
 * classes is loaded.
 *
 * <p>Modern Industrialization's gas fluids carry {@code c:} tags, which the content mod's fluid
 * bridge maps to gases. The adapter's data pack adds the gases it has that ZAS³ doesn't, and the
 * tags the content doesn't map (COMPAT-092); the pack of shared gases, which the Mekanism and
 * NuclearCraft adapters offer too, has the rest.
 */
package io.github.west3436.zas.compat.mi;
