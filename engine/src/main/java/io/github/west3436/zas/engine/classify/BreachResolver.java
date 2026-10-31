package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.zone.CellTopology;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Decides what a breach of zones opens onto (GEO-023, GEO-025, GEO-026).
 *
 * <p>In an atmos tick the scheduler, after {@link ZoneGraph#refresh refreshing} the cells a block
 * change touched:
 *
 * <ol>
 *   <li>finds the unzoned cells newly open to a zone with {@link #breachSeeds};
 *   <li>classifies them with {@link #classify}, one {@link Breach} per region, each region filled
 *       on the unzoned side only (zoned cells are walls to the fill);
 *   <li>carries each out with {@link #apply}, and pauses the zones of every {@link Breach.Hold}.
 * </ol>
 *
 * <p>A held breach is classified again, from its seeds, once its missing sections arrive.
 * Deterministic: seeds are handled in ascending order and each region is filled once.
 */
public final class BreachResolver {
    private final EnclosureFill fill;

    /** @param fill the fill to classify with; its bound applies to the unzoned regions */
    public BreachResolver(EnclosureFill fill) {
        this.fill = Objects.requireNonNull(fill, "fill");
    }

    /**
     * The unzoned cells that open onto a zone at the given cells: unzoned open neighbours of the
     * zoned ones, and the unzoned ones themselves if they open onto a zone. Call it after
     * refreshing the cells.
     *
     * @return the seeds, sorted ascending, without duplicates
     */
    public static long[] breachSeeds(ZoneGraph graph, CellTopology topology, long[] changedCells) {
        LongList seeds = new LongList();
        boolean[] touches = new boolean[1];
        for (long cell : changedCells) {
            if (graph.zoneOf(cell) != null) {
                topology.forEachOpenNeighbour(cell, (n, direct) -> {
                    if (graph.zoneOf(n) == null) {
                        seeds.add(n);
                    }
                });
            } else if (topology.volume(cell) > 0) {
                touches[0] = false;
                topology.forEachOpenNeighbour(cell, (n, direct) -> {
                    if (graph.zoneOf(n) != null) {
                        touches[0] = true;
                    }
                });
                if (touches[0]) {
                    seeds.add(cell);
                }
            }
        }
        return seeds.sortedDistinct();
    }

    /**
     * Classifies the regions of unzoned cells around the seeds. Changes nothing.
     *
     * @param graph the zone graph, to tell zoned cells from unzoned
     * @param topology the view of the world
     * @param seeds unzoned cells, as {@link #breachSeeds} gives them; zoned cells and positions
     *     that are not cells are skipped
     * @return one breach per region, in order of each region's lowest seed
     */
    public List<Breach> classify(ZoneGraph graph, ResolvableTopology topology, long[] seeds) {
        long[] sorted = seeds.clone();
        Arrays.sort(sorted);
        boolean[] done = new boolean[sorted.length];
        List<Breach> out = new ArrayList<>();
        LongList zones = new LongList();
        LongList regionSeeds = new LongList();
        for (int i = 0; i < sorted.length; i++) {
            long seed = sorted[i];
            if (done[i] || (i > 0 && seed == sorted[i - 1])) {
                continue;
            }
            if (graph.zoneOf(seed) != null || (topology.isKnown(seed) && !(topology.volume(seed) > 0))) {
                continue;
            }
            zones.clear();
            FillResult result = fill.fill(topology, seed, cell -> {
                Zone z = graph.zoneOf(cell);
                if (z == null) {
                    return false;
                }
                zones.add(z.id());
                return true;
            });
            regionSeeds.clear();
            regionSeeds.add(seed);
            for (int j = i + 1; j < sorted.length; j++) {
                if (!done[j] && fill.visitedInLastFill(sorted[j])) {
                    done[j] = true;
                    regionSeeds.add(sorted[j]);
                }
            }
            long[] s = regionSeeds.sortedDistinct();
            long[] z = zones.sortedDistinct();
            out.add(
                    switch (result) {
                        case FillResult.Enclosed e -> new Breach.Pocket(s, z, e);
                        case FillResult.Open o -> new Breach.Vent(s, z, o.reason());
                        case FillResult.Unresolved u -> new Breach.Hold(s, z, u.missingSections());
                    });
        }
        return out;
    }

    /**
     * Carries out a breach: a {@link Breach.Pocket} joins the zones ({@link
     * EnclosureZones#absorb}); a {@link Breach.Vent} or {@link Breach.Hold} changes nothing in the
     * graph (the caller pauses a held breach's zones).
     *
     * @return the zones that took in cells, as {@link EnclosureZones#absorb} returns them; empty
     *     unless the breach is a pocket
     */
    public static List<Zone> apply(ZoneGraph graph, CellTopology topology, Breach breach) {
        if (breach instanceof Breach.Pocket pocket) {
            return EnclosureZones.absorb(graph, topology, pocket.region().cells());
        }
        return List.of();
    }

    /** A growable list of longs. */
    static final class LongList {
        private long[] values = new long[8];
        private int size;

        void add(long v) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = v;
        }

        void clear() {
            size = 0;
        }

        long[] sortedDistinct() {
            long[] out = Arrays.copyOf(values, size);
            Arrays.sort(out);
            int n = 0;
            for (int i = 0; i < out.length; i++) {
                if (n == 0 || out[i] != out[n - 1]) {
                    out[n++] = out[i];
                }
            }
            return n == out.length ? out : Arrays.copyOf(out, n);
        }
    }
}
