package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.zone.CellTopology;

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

    /** Receives the open neighbours of a cell and the faces beyond the view. */
    interface FillVisitor extends NeighbourVisitor {
        /** A reached face leads to a position in section {@code section}, which the view doesn't hold. */
        void unknown(long section);

        /** A reached face leads outside the world, which counts as open to ambient (GEO-031). */
        void outsideWorld();
    }
}
