/**
 * The off-thread atmos scheduler (CORE-040 to CORE-049, CORE-069 to CORE-074; architecture §3):
 * the plain-data {@link io.github.west3436.zas.engine.scheduler.Batch} and {@link
 * io.github.west3436.zas.engine.scheduler.Result} exchanged once per atmos tick, the per-dimension
 * {@link io.github.west3436.zas.engine.scheduler.DimensionEngine} the worker owns (zone graph,
 * section mirror, classifier, waiting operations), the {@link
 * io.github.west3436.zas.engine.scheduler.DimensionScheduler} that runs it threaded or inline, the
 * server-thread {@link io.github.west3436.zas.engine.scheduler.DimensionPipeline}, and the published
 * {@link io.github.west3436.zas.engine.scheduler.AtmosSnapshot}.
 *
 * <p>Nothing here imports Minecraft (CORE-043); the platform supplies the world through {@link
 * io.github.west3436.zas.engine.scheduler.DimensionPipeline.World}.
 */
package io.github.west3436.zas.engine.scheduler;
