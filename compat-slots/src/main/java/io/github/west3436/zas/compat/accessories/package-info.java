/**
 * The Accessories adapter (COMPAT-041, COMPAT-042): tanks and masks work from Accessories slots. It
 * is built on the public API and Accessories' own API only (COMPAT-003), so another mod with
 * equipment slots can copy it. {@code zas} starts it, through {@link
 * io.github.west3436.zas.compat.accessories.AccessoriesCompat#init}, only when Accessories is
 * installed in a version it supports (COMPAT-002); without Accessories none of these classes is
 * loaded.
 *
 * <p>Which items fit which slot is data, in the content mod's pack (COMPAT-043): Accessories' item
 * tags for the back, belt and face slots, which a player has from Accessories itself.
 */
package io.github.west3436.zas.compat.accessories;
