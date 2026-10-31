package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.function.LongConsumer;

/**
 * The classification step of an atmos tick (architecture §4.3): the cell topology the zone graph
 * runs on, and the enclosure test that decides whether a zone-triggering operation creates a zone
 * (CORE-051, CORE-055, GEO-020).
 *
 * <p>This is the seam between the scheduler and enclosure detection. The scheduler owns the
 * section mirror the classifier reads, calls {@link #afterGeometry} in phase 2 and {@link
 * #classify} and {@link #zone} in phase 3 (CORE-040), and acts on the answers. Everything runs on
 * the worker. {@link EnclosureClassifier} is the implementation used in game.
 */
public interface Classifier {
    /** The topology the zone graph runs on, backed by the section mirror. */
    CellSpace space();

    /**
     * Classifies an unzoned cell that a zone-triggering operation targets. The graph is read only;
     * the scheduler then calls {@link #zone} for {@link Classification.Enclosed}.
     */
    Classification classify(ZoneGraph graph, long cell);

    /**
     * Zones an enclosure {@link #classify} found (CORE-051). The default creates one zone of all
     * its cells.
     */
    default void zone(ZoneGraph graph, Classification.Enclosed enclosure) {
        graph.createZone(enclosure.cells());
    }

    /**
     * Called in phase 2, after the geometry phase refreshed cells, with those cells in ascending
     * order. Resolves breaches: a zone's face that opened onto unzoned cells joins an enclosed
     * pocket (GEO-023), stays an ambient edge to the open (GEO-025) or waits at an unresolved
     * region (GEO-026). The default does nothing: an opening onto unzoned cells is then an ambient
     * edge, as the graph already records it.
     *
     * @param requestSection receives loaded sections the classification needs that the mirror
     *     lacks; the next batch brings them
     */
    default void afterGeometry(ZoneGraph graph, long[] refreshedCells, LongConsumer requestSection) {}

    /**
     * Whether a classification is waiting for sections. While one is, the scheduler keeps every
     * mirrored section, so repeated rounds of fetching always make progress.
     */
    default boolean hasPending() {
        return false;
    }

    /**
     * Unzoned cells known to lie in regions left unresolved because they reach unloaded chunks
     * (GEO-024, GEO-026), such as the seeds of breaches held until those chunks load. For the
     * debug overlay (CLIENT-005), which shows those regions; the default knows of none.
     */
    default long[] unresolvedSeeds() {
        return new long[0];
    }

    /**
     * How many sections around a new zone-triggering operation the server thread captures at
     * batch close, so most enclosures classify in the same tick (architecture §4.3: 3, a 7³ cube).
     */
    default int prefetchRadius() {
        return 3;
    }

    /**
     * Creates a classifier over a dimension's section mirror. The engine calls it once, when it is
     * built.
     */
    @FunctionalInterface
    interface Factory {
        Classifier create(SectionLookup sections, GeometryPalette palette);
    }
}
