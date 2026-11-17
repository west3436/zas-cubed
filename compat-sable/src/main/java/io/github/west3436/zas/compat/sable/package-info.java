/**
 * The Sable adapter (COMPAT-100 to COMPAT-102): rooms on Sable's moving structures. {@code zas}
 * starts it, through {@link io.github.west3436.zas.compat.sable.SableCompat#init}, only when Sable is
 * installed in a version it supports; without it none of these classes is loaded.
 *
 * <p>A sub-level's blocks sit in a plot of the level's own, far from where they show, so its rooms
 * are rooms of that level, and a hole in one opens to the level's air (COMPAT-102) with nothing of
 * the adapter's. The adapter carries their air across Sable's moves, saves and unloads, and lets an
 * entity on board breathe it. It uses ZAS³'s internals where the public API doesn't reach (COMPAT-001
 * allows it for these adapters).
 *
 * <ul>
 *   <li>{@code mixin.MoveBlocksMixin} and {@link io.github.west3436.zas.compat.sable.SableMoves}:
 *       rooms carried along when Sable moves blocks, through ZAS³'s relocation API (COMPAT-100);
 *   <li>{@code mixin.PlotSaveMixin} and {@link io.github.west3436.zas.compat.sable.PlotAir}: the
 *       plots' air saved with the sub-level, and kept or dropped when it unloads or is removed
 *       (COMPAT-100);
 *   <li>{@code Aboard}: the air on board, where ZAS³ resolves a point (COMPAT-101).
 * </ul>
 */
package io.github.west3436.zas.compat.sable;
