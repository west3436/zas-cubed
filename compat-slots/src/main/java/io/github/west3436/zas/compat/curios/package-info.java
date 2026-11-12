/**
 * The Curios adapter (COMPAT-040, COMPAT-042): tanks and masks work from Curios slots. It is built
 * on the public API and Curios' own API only, so another mod with equipment slots can copy it.
 * {@code zas} starts it, through {@link io.github.west3436.zas.compat.curios.CuriosCompat#init},
 * only when Curios is installed in a version it supports (COMPAT-002); without Curios none of these
 * classes is loaded.
 *
 * <p>Which items fit which slot is data, in the content mod's pack (COMPAT-043): Curios' item tags
 * for the back, belt and head slots, and the file that gives a player those three slots.
 */
package io.github.west3436.zas.compat.curios;
