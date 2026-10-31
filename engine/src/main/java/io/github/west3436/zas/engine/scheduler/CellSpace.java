package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.CellTopology;
import java.util.function.LongConsumer;

/**
 * A {@link CellTopology} over the section mirror that can also map between cells and block
 * positions, which the scheduler needs to route operations, to refresh cells after block
 * changes, and to build the published position lookup.
 *
 * <p>Answers come from the mirror ({@link SectionLookup}); a position in a section the mirror
 * lacks has no cells.
 */
public interface CellSpace extends CellTopology {
    /** What {@link #cellAt} returns for a position with no cell. */
    long NO_CELL = -1;

    /**
     * Visits the cells at a block position ({@link SectionKeys#block}), in a fixed order; none if
     * the position is solid or its section isn't mirrored.
     */
    void forEachCellAt(long block, LongConsumer action);

    /** The block position ({@link SectionKeys#block}) of a cell. */
    long position(long cell);

    /**
     * The cell at a block position that an operation there acts on: its largest cell (API-015),
     * or {@link #NO_CELL} if it has none. The default picks the largest by {@link #volume}, the
     * first on a tie.
     */
    default long cellAt(long block) {
        long[] best = {NO_CELL};
        double[] bestVolume = {0};
        forEachCellAt(block, cell -> {
            double v = volume(cell);
            if (v > bestVolume[0]) {
                bestVolume[0] = v;
                best[0] = cell;
            }
        });
        return best[0];
    }
}
