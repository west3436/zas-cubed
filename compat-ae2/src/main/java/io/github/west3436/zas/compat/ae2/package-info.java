/**
 * The Applied Energistics 2 adapter (COMPAT-070 to COMPAT-077): each gas a storable type of AE2's,
 * AE2's import, export and storage buses on any block that provides {@code IGasHandler}, and gas
 * storage cells.
 *
 * <p><b>Units.</b> ME storage counts a gas in whole millimoles and shows moles ({@link
 * io.github.west3436.zas.compat.ae2.GasUnits}). Gas in it has no temperature: what leaves it is at
 * the configured temperature, 293.15 K by default ({@link
 * io.github.west3436.zas.compat.ae2.Ae2Settings}).
 *
 * <p><b>Storage.</b> AE2's own storage cells each hold one type of stack, items or fluids, so none
 * holds gas. ME storage holds gas in gas storage cells ({@link
 * io.github.west3436.zas.compat.ae2.GasCells}), 1k to 256k and portable, which are AE2's own cell
 * classes for gas, and in the blocks its storage buses touch: ZAS³'s pipes, pipe caps, connector
 * ports and large tanks, and any mod's block that provides the capability. A storage bus with one
 * gas in its filter keeps that gas apart, so a mixture taken in by an import bus is stored as its
 * gases.
 *
 * <p><b>Threads.</b> Everything runs on the server thread, as AE2's buses and ZAS³'s handlers do,
 * but drawing, which runs on the client's render thread.
 *
 * <p><b>Loading.</b> These classes are part of {@code zas} in the release jar and are built against
 * AE2's API, which is never bundled. {@code zas}'s adapter loader starts {@link
 * io.github.west3436.zas.compat.ae2.Ae2Compat} by name, only with AE2 installed in a supported version
 * (COMPAT-001, COMPAT-002), so without it none of them is ever loaded.
 */
package io.github.west3436.zas.compat.ae2;
