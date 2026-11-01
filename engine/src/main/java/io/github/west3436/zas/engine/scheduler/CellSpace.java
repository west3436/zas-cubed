package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.CellIds;
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
    /**
     * What {@link #cellAt} returns for a position with no cell: {@link CellIds#NONE}, which is
     * never a cell id (-1, for one, is the last sub-cell of block (-1, -1, -1)).
     */
    long NO_CELL = CellIds.NONE;

    /**
     * Visits the cells at a block position ({@link SectionKeys#block}), in a fixed order; none if
     * the position is solid or its section isn't mirrored.
     */
    void forEachCellAt(long block, LongConsumer action);

    /** The block position ({@link SectionKeys#block}) of a cell. */
    long position(long cell);

    /**
     * How this space's cell ids name positions and sub-cells, consistent with {@link #position}
     * and {@link #forEachCellAt}'s order. Pure, so published tables may keep it.
     */
    CellNaming naming();

    /**
     * Cell {@code sub} of a block position, the cell an operation resolved to a sub-cell acts on
     * (API-015); if the position doesn't have that many cells any more, its {@linkplain #cellAt
     * largest cell}. A negative {@code sub} means the largest cell.
     */
    default long cellAt(long block, int sub) {
        if (sub < 0) {
            return cellAt(block);
        }
        long[] found = {NO_CELL};
        int[] n = {0};
        forEachCellAt(block, c -> {
            if (n[0]++ == sub) {
                found[0] = c;
            }
        });
        return found[0] != NO_CELL ? found[0] : cellAt(block);
    }

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
