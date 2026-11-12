/**
 * The CC: Tweaked adapter (COMPAT-020 to COMPAT-022): each atmospherics device with a window is a
 * peripheral, and a computer attached to an area air controller hears of its zone's breaches and
 * fires.
 *
 * <p><b>One source for what a computer sees and does.</b> A peripheral reads the values its device's
 * window shows from the device itself, the same getters the window's menu syncs. It changes a
 * setting through the same setter the window's buttons call, so the device's own checks apply:
 * its ranges, a controller's orders winning over a hand's, a cycle refused without power. The
 * adapter holds no range of its own. Where a setter clamps, the peripheral asks the device's own
 * clamp whether the value would be changed and refuses it if so, since a computer names any value
 * and a window's buttons only step ({@link io.github.west3436.zas.atmos.compat.cc.Lua#inRange}).
 *
 * <p><b>Units</b> are the ones the devices hold: kPa, K, mol, L/s, FE, mB of water, and seconds.
 * States and modes are lower-case words, a gas is its id and a gas tag its id after a {@code #}, and
 * a position is a table of {@code x}, {@code y} and {@code z}. A value the window shows a dash for
 * is {@code nil}.
 *
 * <p><b>Threads.</b> Every method runs on the server thread ({@code mainThread = true}): a device's
 * state is the server's alone. {@code attach} and {@code detach} may come from a computer's thread,
 * so {@link io.github.west3436.zas.atmos.compat.cc.ZoneEvents} keeps its computers in concurrent
 * sets.
 *
 * <p><b>Loading.</b> These classes are part of {@code zas_atmos} in the release jar and are built
 * against CC: Tweaked's API, which is never bundled. {@code zas}'s adapter loader starts {@link
 * io.github.west3436.zas.atmos.compat.cc.CcCompat} by name, only with CC: Tweaked installed in a
 * supported version (COMPAT-001, COMPAT-002), so without it none of them is ever loaded.
 */
package io.github.west3436.zas.atmos.compat.cc;
