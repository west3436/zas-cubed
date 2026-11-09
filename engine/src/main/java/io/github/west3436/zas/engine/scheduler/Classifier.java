package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.classify.WorldLimits;
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
     * A block's geometry changed in a section, whether the mirror holds it or not: in the geometry phase
     * of the tick that sees the change, or of the tick that applies it if it was held for a paused zone
     * (GEO-037). Anything the classifier remembers that rests on the section no longer holds (GEO-027).
     */
    default void sectionChanged(long section) {}

    /** A chunk unloaded: {@link #sectionChanged} for the sections in it. */
    default void chunkUnloaded(long chunk) {}

    /**
     * Where the world now ends and what its edges do (GEO-031), at the start of the tick that sees a
     * change. Anything the classifier remembers that rests on the old limits no longer holds.
     */
    default void limits(WorldLimits limits) {}

    /**
     * The most cells an enclosure can have (GEO-020, D-23), at the start of every tick, from the tick's
     * settings. A new bound applies to fills from this tick on; zones that formed under another stay
     * as they are. The default runs no fills, so has no bound.
     */
    default void fillBound(int maxCells) {}

    /** Whether a cell lies outside the world or has a face leading out of it. */
    default boolean touchesLimit(long cell) {
        return false;
    }

    /**
     * Tells {@code listener} the seed of each fill from now on, on the worker; null for no one. For
     * tests of when a fill runs (GEO-021, GEO-027). The default runs no fills.
     */
    default void onFill(LongConsumer listener) {}

    /**
     * Whether a classification is waiting for sections. While one is, the scheduler keeps every
     * mirrored section, so repeated rounds of fetching always make progress.
     */
    default boolean hasPending() {
        return false;
    }

    /**
     * Whether a classification waiting for sections concerns a zone: the zone opens onto the region
     * being classified. A zone rebuilt from its saved shares stays paused while this holds. The
     * default treats any waiting classification as the zone's.
     */
    default boolean hasPendingFor(long zone) {
        return hasPending();
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
     * The zones that open onto a region the last {@link #afterGeometry} couldn't classify because
     * it reaches an unloaded chunk (GEO-026), ascending. The scheduler pauses them (GEO-030) until
     * the region classifies. The default holds none.
     */
    default long[] heldZones() {
        return new long[0];
    }

    /**
     * Seeds of the regions whose breaches pause the {@link #heldZones} (GEO-026), ascending: the
     * unzoned cells next to those zones. The published table lists the unzoned cells reachable
     * from them, so queries there read as unresolved (API-010). A region still waiting for loaded
     * sections isn't held yet, and isn't listed. The default holds none.
     */
    default long[] heldSeeds() {
        return new long[0];
    }

    /**
     * The section ({@link io.github.west3436.zas.engine.SectionKeys}) a cell id is in. A pure
     * function of the id, so the server thread may call it to know which sections to capture for
     * a chunk's saved shares (DATA-002). The default maps through {@link CellSpace#position}.
     */
    default long sectionOf(long cell) {
        return io.github.west3436.zas.engine.SectionKeys.sectionOfBlockKey(space().position(cell));
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
