package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.zone.CellTopology;
import java.util.function.LongPredicate;

/**
 * A {@link CellTopology} over a partial view of the world, which can say where its knowledge
 * ends. The enclosure fill needs that to tell an enclosed region from one that reaches sections
 * the view doesn't hold (GEO-024).
 *
 * <p>{@link #forEachOpenNeighbour(long, NeighbourVisitor)} keeps its {@link CellTopology}
 * meaning: it visits only neighbours that are known cells. {@link #forEachNeighbour} also reports
 * the faces it can't answer for.
 */
public interface ResolvableTopology extends CellTopology {
    /**
     * Whether the cell's section is in the view. {@link #volume(long)} of a cell that isn't known
     * is 0.
     */
    boolean isKnown(long cell);

    /** The key of the section a cell lies in ({@link io.github.west3436.zas.engine.SectionKeys}). */
    long sectionOf(long cell);

    /**
     * Visits every open neighbour of a known cell, exactly as {@link
     * #forEachOpenNeighbour(long, NeighbourVisitor)} does and in the same order, and also each face
     * the cell's gas reaches whose neighbouring position is unknown or outside the world.
     *
     * <p>A face counts as reached when gas could cross it if the neighbouring position were air.
     */
    void forEachNeighbour(long cell, FillVisitor visitor);

    /**
     * Looks straight up from a cell (GEO-021), following cells each open to the one above in the same
     * column. Only {@link Column#OPEN} settles anything: a fill would get there too, so a caller can
     * skip it. The default sees nothing, and leaves it to the fill.
     *
     * @param stopAt cells that count as sealed, as for {@link EnclosureFill#fill}
     */
    default Column column(long cell, LongPredicate stopAt) {
        return Column.SEALED;
    }

    /** The sections the view doesn't hold from a cell's own up to the top of the world, lowest first. */
    default long[] columnGaps(long cell) {
        return new long[0];
    }

    /** What looking straight up from a cell finds. */
    enum Column {
        /** Nothing seals the cell from the top of the world. */
        OPEN,
        /** Something seals it. The region may be open some other way, which takes a fill to find. */
        SEALED,
        /**
         * Nothing seals it as far as the view goes, up to a section the view doesn't hold. A fill can't
         * find the region enclosed, only open or unresolved, so seeing the {@linkplain #columnGaps rest
         * of the column} comes first.
         */
        UNKNOWN
    }

    /** Receives the open neighbours of a cell and the faces beyond the view. */
    interface FillVisitor extends NeighbourVisitor {
        /** A reached face leads to a position in section {@code section}, which the view doesn't hold. */
        void unknown(long section);

        /** A reached face leads outside the world, which counts as open to ambient unless sealed (GEO-031). */
        void outsideWorld();
    }
}
