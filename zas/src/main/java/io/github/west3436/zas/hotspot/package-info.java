/**
 * Hotspots on the server (FIRE-010 to FIRE-019): the {@code zas:gas_fire} block that shows a
 * hotspot burning in gas, and the server-thread side of the engine's hotspot phase. The hotspots
 * themselves live on the worker ({@code io.github.west3436.zas.engine.scheduler.Hotspots},
 * architecture §5); the server places and removes gas fires as the worker asks ({@link
 * io.github.west3436.zas.hotspot.FireIntents}). {@link io.github.west3436.zas.hotspot.GasFireScan}
 * finds gas fire blocks around a point, for their sound (FIRE-036).
 */
package io.github.west3436.zas.hotspot;
