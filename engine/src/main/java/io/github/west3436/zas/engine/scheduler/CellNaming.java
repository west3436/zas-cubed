package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.CellIds;

/**
 * How cell ids name block positions and sub-cells: pure functions of their arguments, so the
 * published membership tables (architecture §4.4) can map between cells and positions on any
 * thread without the worker's mirror.
 */
public interface CellNaming {
    /** The {@link CellIds} layout, which the engine uses in the world. */
    CellNaming CELL_IDS = new CellNaming() {
        @Override
        public long position(long cell) {
            return SectionKeys.block(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell));
        }

        @Override
        public int subIndex(long cell) {
            return CellIds.sub(cell);
        }

        @Override
        public long cell(long block, int sub) {
            return CellIds.of(SectionKeys.blockX(block), SectionKeys.blockY(block), SectionKeys.blockZ(block), sub);
        }
    };

    /** The block position ({@link SectionKeys#block}) of a cell. */
    long position(long cell);

    /** The cell's index among the cells of its block (0 for a block's first, often only, cell). */
    int subIndex(long cell);

    /** The id of sub-cell {@code sub} of a block position. */
    long cell(long block, int sub);
}
