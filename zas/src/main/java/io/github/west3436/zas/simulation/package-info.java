/**
 * The atmos scheduler on the server (architecture §3): {@link
 * io.github.west3436.zas.simulation.AtmosService} times atmos ticks and owns one {@link
 * io.github.west3436.zas.simulation.DimensionAtmos} per level, which closes batches from the live
 * world on the server thread, hands them to the engine's workers, and applies and publishes the
 * results (CORE-042, CORE-045, CORE-069).
 */
package io.github.west3436.zas.simulation;
