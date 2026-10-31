package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.zone.CellTopology;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns an enclosed region into zones. Zones never span a zone boundary such as an open door
 * (CORE-034), but gas does, so one enclosed region can hold several zones: one per set of its
 * cells connected by direct openings.
 *
 * <p>Deterministic: components are found in ascending order of their lowest cell, and cells join
 * in breadth-first order.
 */
public final class EnclosureZones {
    private EnclosureZones() {}

    /**
     * Creates the zones of an enclosed region of unzoned cells, each cell bringing the ambient gas
     * for its volume (CORE-028). This is what a zone trigger does once its classification comes
     * back enclosed (GEO-028).
     *
     * @param graph the zone graph
     * @param topology the graph's topology
     * @param cells the region's cells, sorted ascending, as {@link FillResult.Enclosed#cells()}
     * @return the new zones, in creation order
     * @throws IllegalArgumentException as {@link ZoneGraph#createZone} does
     */
    public static List<Zone> create(ZoneGraph graph, CellTopology topology, long[] cells) {
        List<Zone> out = new ArrayList<>();
        for (long[] component : components(topology, cells)) {
            out.add(graph.createZone(component));
        }
        return out;
    }

    /**
     * Brings an enclosed region of unzoned cells into the zones around it, after a breach (GEO-023).
     * Each component joins the lowest-id zone it has a direct opening to, cell by cell, each
     * bringing the ambient gas for its volume (CORE-028). A component with no direct opening to a
     * zone, such as a room behind an open door, becomes a zone of its own, so the zone never
     * exchanges with it as if it were the ambient.
     *
     * @param graph the zone graph
     * @param topology the graph's topology
     * @param cells the region's cells, sorted ascending, as {@link FillResult.Enclosed#cells()}
     * @return the zones that took in cells, without duplicates: zones joined in the order first
     *     joined, then new zones in creation order
     */
    public static List<Zone> absorb(ZoneGraph graph, CellTopology topology, long[] cells) {
        List<Zone> joined = new ArrayList<>();
        List<long[]> lonely = new ArrayList<>();
        for (long[] component : components(topology, cells)) {
            Zone target = lowestDirectZone(graph, topology, component);
            if (target == null) {
                lonely.add(component);
                continue;
            }
            joinFrom(graph, topology, component, target);
            if (!joined.contains(target)) {
                joined.add(target);
            }
        }
        for (long[] component : lonely) {
            joined.add(graph.createZone(component));
        }
        return joined;
    }

    /** The region's sets of cells connected by direct openings, each sorted, by lowest cell. */
    static List<long[]> components(CellTopology topology, long[] cells) {
        List<long[]> out = new ArrayList<>();
        boolean[] assigned = new boolean[cells.length];
        long[] queue = new long[cells.length];
        int[] tail = new int[1];
        for (int i = 0; i < cells.length; i++) {
            if (assigned[i]) {
                continue;
            }
            assigned[i] = true;
            int head = 0;
            tail[0] = 0;
            queue[tail[0]++] = cells[i];
            while (head < tail[0]) {
                topology.forEachOpenNeighbour(queue[head++], (n, direct) -> {
                    if (!direct) {
                        return;
                    }
                    int index = Arrays.binarySearch(cells, n);
                    if (index >= 0 && !assigned[index]) {
                        assigned[index] = true;
                        queue[tail[0]++] = n;
                    }
                });
            }
            long[] component = Arrays.copyOf(queue, tail[0]);
            Arrays.sort(component);
            out.add(component);
        }
        return out;
    }

    private static Zone lowestDirectZone(ZoneGraph graph, CellTopology topology, long[] component) {
        Zone[] best = new Zone[1];
        for (long c : component) {
            topology.forEachOpenNeighbour(c, (n, direct) -> {
                if (!direct) {
                    return;
                }
                Zone z = graph.zoneOf(n);
                if (z != null && (best[0] == null || z.id() < best[0].id())) {
                    best[0] = z;
                }
            });
        }
        return best[0];
    }

    /** Joins a component to a zone breadth-first from the cells that open directly onto it. */
    private static void joinFrom(ZoneGraph graph, CellTopology topology, long[] component, Zone target) {
        boolean[] queued = new boolean[component.length];
        long[] queue = new long[component.length];
        int[] tail = new int[1];
        boolean[] touches = new boolean[1];
        for (int i = 0; i < component.length; i++) {
            touches[0] = false;
            topology.forEachOpenNeighbour(component[i], (n, direct) -> {
                if (direct && graph.zoneOf(n) == target) {
                    touches[0] = true;
                }
            });
            if (touches[0]) {
                queued[i] = true;
                queue[tail[0]++] = component[i];
            }
        }
        int head = 0;
        while (head < tail[0]) {
            long cell = queue[head++];
            graph.join(cell, target);
            topology.forEachOpenNeighbour(cell, (n, direct) -> {
                if (!direct) {
                    return;
                }
                int index = Arrays.binarySearch(component, n);
                if (index >= 0 && !queued[index]) {
                    queued[index] = true;
                    queue[tail[0]++] = n;
                }
            });
        }
    }
}
