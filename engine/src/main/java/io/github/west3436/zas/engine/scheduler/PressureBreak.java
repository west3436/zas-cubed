package io.github.west3436.zas.engine.scheduler;

/**
 * A fragile block the worker asks the server thread to break: the pressure difference across it is
 * above the threshold (FLOW-007). The server thread applies it with the result, unless a zone it
 * reads was rejected (§3.3), and checks the block is still fragile first. An intent the world has
 * moved past does nothing, and the next tick works from the blocks as they are.
 *
 * @param block the position ({@link io.github.west3436.zas.engine.SectionKeys#block})
 * @param highZone the zone on the high side, or 0 for the ambient
 * @param lowZone the zone on the low side, or 0 for the ambient
 */
public record PressureBreak(long block, long highZone, long lowZone) {}
