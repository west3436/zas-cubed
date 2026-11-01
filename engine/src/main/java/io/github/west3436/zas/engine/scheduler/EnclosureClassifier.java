package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.classify.Breach;
import io.github.west3436.zas.engine.classify.BreachResolver;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.classify.EnclosureFill;
import io.github.west3436.zas.engine.classify.EnclosureZones;
import io.github.west3436.zas.engine.classify.FillResult;
import io.github.west3436.zas.engine.classify.SectionTopology;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * The classifier used in game: bounded enclosure detection ({@code engine.classify}) over the
 * section mirror (GEO-020 to GEO-028, architecture §4.3).
 *
 * <ul>
 *   <li>The cell topology is a {@link SectionTopology} over the mirror, with {@link CellIds} cell
 *       ids, shared by the zone graph and the fill.
 *   <li>A zone trigger at an unzoned cell runs one {@link EnclosureFill} (default bound 65,536
 *       cells, GEO-020), with zoned cells as walls. Enclosed: its cells join the zones they open
 *       onto, or become new zones ({@link EnclosureZones#absorb}, CORE-051). Open: the ambient.
 *       If the fill reached sections the mirror lacks, those whose chunks are loaded are fetched
 *       for the next tick, and the operation waits (GEO-028); if all of them are unloaded the
 *       region is unresolved (GEO-024).
 *   <li>After geometry changes, {@link BreachResolver} classifies the unzoned cells newly open to a
 *       zone: an enclosed pocket joins it (GEO-023), the open air leaves an ambient edge (GEO-025),
 *       and a region reaching missing sections is held and classified again each tick until it
 *       resolves (GEO-026). Once every section it still lacks is in an unloaded chunk, its zones
 *       are {@linkplain #heldZones() held}, and the scheduler pauses them (GEO-030) until it
 *       classifies, through any rounds of fetching after the chunks load. While loaded sections
 *       are still being fetched, the opening counts as an ambient connection, as before. A held
 *       region that no zone opens onto any more is dropped.
 * </ul>
 *
 * <p>Single-threaded, like the engine that owns it.
 */
public final class EnclosureClassifier implements Classifier {
    /** The factory the platform passes to {@link DimensionEngine}, with the default fill bound. */
    public static final Classifier.Factory FACTORY = EnclosureClassifier::new;

    private final SectionLookup sections;
    private final GeometryPalette palette;
    private final SectionTopology topology;
    private final Space space;
    private final EnclosureFill fill;
    private final BreachResolver breaches;
    /** Seeds of breaches held at missing sections, classified again each tick. */
    private long[] held = new long[0];
    /** Whether a held breach asked for sections this tick. */
    private boolean fetching;
    /** Zones of breaches held at unloaded chunks, ascending. */
    private long[] heldZones = new long[0];
    /** Seeds of the breaches that pause those zones, ascending. */
    private long[] pausingSeeds = new long[0];

    public EnclosureClassifier(SectionLookup sections, GeometryPalette palette) {
        this(sections, palette, EnclosureFill.DEFAULT_MAX_CELLS);
    }

    /**
     * @param sections the mirror
     * @param palette the palette its geometry ids index
     * @param maxCells the fill bound (GEO-020)
     */
    public EnclosureClassifier(SectionLookup sections, GeometryPalette palette, int maxCells) {
        this.sections = Objects.requireNonNull(sections, "sections");
        this.palette = Objects.requireNonNull(palette, "palette");
        this.topology = new SectionTopology(
                palette, sections::section, sections.minSectionY() * 16, sections.maxSectionY() * 16 + 15);
        this.space = new Space();
        this.fill = new EnclosureFill(maxCells);
        this.breaches = new BreachResolver(fill);
    }

    /** The topology the graph and the fill share. */
    public SectionTopology topology() {
        return topology;
    }

    @Override
    public CellSpace space() {
        return space;
    }

    @Override
    public Classification classify(ZoneGraph graph, long cell) {
        FillResult result = fill.fill(topology, cell, c -> graph.zoneOf(c) != null);
        return switch (result) {
            case FillResult.Enclosed e -> new Classification.Enclosed(e.cells());
            case FillResult.Open o -> Classification.OPEN;
            case FillResult.Unresolved u -> missing(u.missingSections());
        };
    }

    /** Splits missing sections into ones to fetch and unloaded ones (architecture §4.3, GEO-024). */
    private Classification missing(long[] missingSections) {
        long[] fetch = loadedOnly(missingSections);
        return fetch.length > 0
                ? new Classification.NeedsSections(fetch)
                : new Classification.Unresolved(missingSections);
    }

    private long[] loadedOnly(long[] keys) {
        long[] out = new long[keys.length];
        int n = 0;
        for (long k : keys) {
            int y = SectionKeys.y(k);
            if (y >= sections.minSectionY() && y <= sections.maxSectionY() && !sections.isUnloaded(k)) {
                out[n++] = k;
            }
        }
        return Arrays.copyOf(out, n);
    }

    @Override
    public void zone(ZoneGraph graph, Classification.Enclosed enclosure) {
        EnclosureZones.absorb(graph, topology, enclosure.cells());
    }

    @Override
    public void afterGeometry(ZoneGraph graph, long[] refreshedCells, LongConsumer requestSection) {
        long[] seeds = BreachResolver.breachSeeds(graph, topology, refreshedCells);
        if (held.length > 0) {
            long[] all = Arrays.copyOf(seeds, seeds.length + held.length);
            System.arraycopy(held, 0, all, seeds.length, held.length);
            Arrays.sort(all);
            seeds = all;
        }
        fetching = false;
        long[] previouslyHeld = heldZones;
        heldZones = new long[0];
        pausingSeeds = new long[0];
        if (seeds.length == 0) {
            held = new long[0];
            return;
        }
        List<Long> stillHeld = new ArrayList<>();
        java.util.TreeSet<Long> pausedBy = new java.util.TreeSet<>();
        java.util.TreeSet<Long> pausing = new java.util.TreeSet<>();
        for (Breach b : breaches.classify(graph, topology, seeds)) {
            if (b instanceof Breach.Hold hold) {
                if (hold.zones().length == 0) {
                    continue; // no zone opens onto it any more
                }
                for (long s : hold.seeds()) {
                    stillHeld.add(s);
                }
                long[] fetch = loadedOnly(hold.missingSections());
                for (long k : fetch) {
                    requestSection.accept(k);
                    fetching = true;
                }
                boolean wasPaused = false;
                for (long z : hold.zones()) {
                    wasPaused |= Arrays.binarySearch(previouslyHeld, z) >= 0;
                }
                // Pause once only unloaded chunks stand in the way, and stay paused while the
                // sections arrive after they load.
                if (fetch.length == 0 || wasPaused) {
                    for (long z : hold.zones()) {
                        pausing.add(z);
                    }
                    for (long s : hold.seeds()) {
                        pausedBy.add(s);
                    }
                }
            } else {
                BreachResolver.apply(graph, topology, b);
            }
        }
        held = new long[stillHeld.size()];
        for (int i = 0; i < held.length; i++) {
            held[i] = stillHeld.get(i);
        }
        heldZones = new long[pausing.size()];
        int i = 0;
        for (long z : pausing) {
            heldZones[i++] = z;
        }
        pausingSeeds = new long[pausedBy.size()];
        i = 0;
        for (long s : pausedBy) {
            pausingSeeds[i++] = s;
        }
    }

    @Override
    public boolean hasPending() {
        return fetching;
    }

    @Override
    public long[] unresolvedSeeds() {
        return held.clone();
    }

    @Override
    public long[] heldZones() {
        return heldZones.clone();
    }

    @Override
    public long[] heldSeeds() {
        return pausingSeeds.clone();
    }

    @Override
    public long sectionOf(long cell) {
        return CellIds.section(cell);
    }

    /** The topology plus the block-position mapping the scheduler needs. */
    private final class Space implements CellSpace {
        @Override
        public double volume(long cell) {
            return topology.volume(cell);
        }

        @Override
        public void forEachOpenNeighbour(long cell, NeighbourVisitor visitor) {
            topology.forEachOpenNeighbour(cell, visitor);
        }

        @Override
        public void forEachCellAt(long block, LongConsumer action) {
            int x = SectionKeys.blockX(block);
            int y = SectionKeys.blockY(block);
            int z = SectionKeys.blockZ(block);
            if (!topology.inWorld(x, y, z)) {
                return;
            }
            SectionSnapshot s = sections.section(SectionKeys.sectionOfBlock(x, y, z));
            if (s == null) {
                return;
            }
            int count = Math.min(
                    palette.geometry(s.geometryId(x & 15, y & 15, z & 15)).cellCount(), CellIds.MAX_SUB_CELLS);
            for (int sub = 0; sub < count; sub++) {
                action.accept(CellIds.of(x, y, z, sub));
            }
        }

        @Override
        public long position(long cell) {
            return SectionKeys.block(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell));
        }

        @Override
        public int subIndex(long cell) {
            return CellIds.sub(cell);
        }
    }
}
