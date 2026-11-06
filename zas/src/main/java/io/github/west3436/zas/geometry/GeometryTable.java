package io.github.west3436.zas.geometry;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import java.util.Arrays;
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
    private final int boundaryStates;
    /** One bit per block state id: whether its geometry is worked out per position. */
    private final long[] perPosition;

    GeometryTable(
            GeometryPalette palette, int[] byStateId, int distinctShapes, int overriddenStates, int boundaryStates) {
        this.palette = palette;
        this.byStateId = byStateId;
        this.distinctShapes = distinctShapes;
        this.overriddenStates = overriddenStates;
        this.boundaryStates = boundaryStates;
        this.perPosition = new long[(byStateId.length + 63) >>> 6];
        for (int stateId = 0; stateId < byStateId.length; stateId++) {
            if (palette.geometry(byStateId[stateId]).hasFlag(Geometry.FLAG_POSITIONAL)) {
                perPosition[stateId >>> 6] |= 1L << stateId;
            }
        }
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

    /**
     * Whether the block state with the given {@link Block#getId(BlockState)} id has its geometry
     * worked out per position ({@link PositionGeometries}). A single array read, for the change hook.
     */
    public boolean perPosition(int stateId) {
        return stateId >= 0 && stateId < byStateId.length && (perPosition[stateId >>> 6] & (1L << stateId)) != 0;
    }

    /** Whether {@code state} has its geometry worked out per position. */
    public boolean perPosition(BlockState state) {
        return perPosition(Block.getId(state));
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

    /** The number of block states that are zone boundaries (GEO-007). */
    public int boundaryStates() {
        return boundaryStates;
    }

    /**
     * Whether every block state has the same geometry in both tables, so that the same blocks make
     * the same cells: comparing palettes alone misses a state moving to a geometry already in use.
     */
    public boolean sameGeometry(GeometryTable other) {
        if (palette.size() != other.palette.size() || !Arrays.equals(byStateId, other.byStateId)) {
            return false;
        }
        for (int i = 0; i < palette.size(); i++) {
            if (!palette.geometry(i).equals(other.palette.geometry(i))) {
                return false;
            }
        }
        return true;
    }

    int[] byStateIdCopy() {
        return byStateId.clone();
    }
}
