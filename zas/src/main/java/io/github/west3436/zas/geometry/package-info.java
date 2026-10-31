/**
 * Per-block-state gas geometry: cells, seal masks and volumes derived from collision shapes,
 * with datapack overrides (GEO-001 to GEO-005, GEO-040, GEO-041; architecture §4.1).
 *
 * <p>{@link io.github.west3436.zas.geometry.Geometries#table()} gives the current {@link
 * io.github.west3436.zas.geometry.GeometryTable}, whose {@code geometryId(BlockState)} maps a
 * block state to an id in the engine's {@link io.github.west3436.zas.engine.GeometryPalette}.
 */
package io.github.west3436.zas.geometry;
