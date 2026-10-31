package io.github.west3436.zas.geometry;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The geometry of every block state: a {@link GeometryPalette} plus a dense map from block state
 * id ({@link Block#getId(BlockState)}) to geometry id (architecture §4.1, step 3). Immutable and
 * safe to share between threads.
 *
 * <p>The lookup is a single array read, so change tracking and snapshotting can call {@link
 * #geometryId(BlockState)} for every block they touch (GEO-040).
 */
public final class GeometryTable {
    private final GeometryPalette palette;
    private final int[] byStateId;
    private final int distinctShapes;
    private final int overriddenStates;

    GeometryTable(GeometryPalette palette, int[] byStateId, int distinctShapes, int overriddenStates) {
        this.palette = palette;
        this.byStateId = byStateId;
        this.distinctShapes = distinctShapes;
        this.overriddenStates = overriddenStates;
    }

    /**
     * The geometry id of {@code state}. A state missing from the block state registry, which
     * shouldn't happen, is reported as {@link GeometryPalette#SOLID_ID}.
     */
    public int geometryId(BlockState state) {
        return geometryId(Block.getId(state));
    }

    /** The geometry id of the block state with the given {@link Block#getId(BlockState)} id. */
    public int geometryId(int stateId) {
        return stateId >= 0 && stateId < byStateId.length ? byStateId[stateId] : GeometryPalette.SOLID_ID;
    }

    /** The geometry of {@code state}. */
    public Geometry geometry(BlockState state) {
        return palette.geometry(geometryId(state));
    }

    public GeometryPalette palette() {
        return palette;
    }

    /** The number of block state ids covered. */
    public int stateCount() {
        return byStateId.length;
    }

    /** The number of distinct collision shapes derived. */
    public int distinctShapes() {
        return distinctShapes;
    }

    /** The number of block states whose geometry comes from an override. */
    public int overriddenStates() {
        return overriddenStates;
    }

    int[] byStateIdCopy() {
        return byStateId.clone();
    }
}
