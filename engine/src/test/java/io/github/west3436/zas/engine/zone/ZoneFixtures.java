package io.github.west3436.zas.engine.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.EngineLog;
import io.github.west3436.zas.engine.conservation.ConservationLedger;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Gases, graphs and a brute-force consistency check for the zone tests. */
final class ZoneFixtures {
    static final GasRegistry REGISTRY =
            GasRegistry.of(List.of(gas("test:nitrogen", 0.028, 20.8), gas("test:oxygen", 0.032, 21.0)));
    static final int N2 = REGISTRY.indexOf("test:nitrogen");
    static final int O2 = REGISTRY.indexOf("test:oxygen");
    static final double CELL = 1000;
    static final double ROOM_T = 293.15;

    /** A config whose log fails the test: nothing in these tests should be rejected. */
    static final GasConfig CONFIG = GasConfig.DEFAULT.withLog(message -> {
        throw new AssertionError("unexpected engine warning: " + message);
    });

    private ZoneFixtures() {}

    static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(5), molarMass, heatCapacity, Optional.empty());
    }

    /** Air (79 % N₂, 21 % O₂) at a pressure in kPa, a volume and a temperature. */
    static GasMixture air(double kPa, double volume, double temperature) {
        double n = kPa * volume / (GasConstants.R * temperature);
        GasMixture m = new GasMixture(REGISTRY, CONFIG, volume, temperature);
        m.setMoles(N2, 0.79 * n);
        m.setMoles(O2, 0.21 * n);
        return m;
    }

    static GasMixture vacuum() {
        return new GasMixture(REGISTRY, CONFIG, CELL, ROOM_T);
    }

    static ZoneGraph graph(Grid grid, GasMixture ambient) {
        return graph(grid, ambient, ExchangeSettings.DEFAULT);
    }

    static ZoneGraph graph(Grid grid, GasMixture ambient, ExchangeSettings settings) {
        return new ZoneGraph(REGISTRY, CONFIG, grid, ambient, settings, ledger(CONFIG.log()));
    }

    static ConservationLedger ledger(EngineLog log) {
        return new ConservationLedger(
                REGISTRY, CONFIG.pruneEpsilon(), ConservationLedger.DEFAULT_RELATIVE_TOLERANCE, log);
    }

    /** Sets a zone's gas to a mixture's per-litre amounts and temperature, unattributed. */
    static void fillWith(ZoneGraph graph, Zone zone, GasMixture perVolume) {
        GasMixture m = zone.mixture();
        double scale = zone.volume() / perVolume.volume();
        for (int i = 0; i < REGISTRY.size(); i++) {
            m.setMoles(i, perVolume.moles(i) * scale);
        }
        m.setTemperature(perVolume.temperature());
        graph.markChanged(zone);
    }

    static double total(ZoneGraph graph) {
        double[] t = new double[REGISTRY.size()];
        graph.totalMoles(t);
        double sum = 0;
        for (double v : t) {
            sum += v;
        }
        return sum;
    }

    /**
     * Recomputes everything the graph keeps from the topology and membership, and
     * checks it matches: zones are connected by direct openings (CORE-020), their
     * volumes are the sums of their cells' volumes (CORE-027), mixture volumes
     * match, and edges and their counts are exactly the open pairs (CORE-030,
     * CORE-037).
     */
    static void assertConsistent(ZoneGraph graph, Grid grid) {
        Map<Long, Zone> member = new HashMap<>();
        for (Zone z : graph.zones()) {
            assertTrue(z.isAlive());
            assertTrue(z.cellCount() > 0, "empty zone " + z);
            double volume = 0;
            for (long c : z.cells()) {
                assertTrue(grid.volume(c) > 0, "zoned position " + c + " is not a cell");
                assertSame(z, graph.zoneOf(c));
                member.put(c, z);
                volume += grid.volume(c);
            }
            assertEquals(volume, z.volume(), 1e-9 * volume, "volume of " + z);
            assertEquals(z.volume(), z.mixture().volume(), 1e-9 * volume);
        }
        // Connectivity across direct openings.
        for (Zone z : graph.zones()) {
            long[] cells = z.cells();
            Set<Long> seen = new HashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(cells[0]);
            seen.add(cells[0]);
            while (!queue.isEmpty()) {
                long c = queue.poll();
                grid.forEachOpenNeighbour(c, (n, direct) -> {
                    if (direct && member.get(n) == z && seen.add(n)) {
                        queue.add(n);
                    }
                });
            }
            assertEquals(cells.length, seen.size(), z + " is not connected");
        }
        // Edges from the open pairs.
        Map<String, int[]> expected = new HashMap<>();
        for (Zone z : graph.zones()) {
            for (long c : z.cells()) {
                grid.forEachOpenNeighbour(c, (n, direct) -> {
                    Zone other = member.get(n);
                    if (other == z) {
                        return;
                    }
                    if (other != null && other.id() < z.id()) {
                        return; // counted from the lower zone
                    }
                    String key = z.id() + "-" + (other == null ? "ambient" : other.id());
                    int[] counts = expected.computeIfAbsent(key, k -> new int[2]);
                    counts[0]++;
                    if (direct) {
                        counts[1]++;
                    }
                });
            }
        }
        List<String> actual = new ArrayList<>();
        for (Edge e : graph.edges()) {
            assertTrue(e.isAlive());
            String key = e.first().id() + "-"
                    + (e.isAmbient() ? "ambient" : e.second().id());
            actual.add(key);
            int[] counts = expected.get(key);
            assertTrue(counts != null, "unexpected edge " + e);
            assertEquals(counts[0], e.connections(), "connections of " + e);
            assertEquals(counts[1], e.directConnections(), "direct connections of " + e);
            if (!e.isAmbient()) {
                assertSame(e, e.second().edgeTo(e.first()));
            }
        }
        assertEquals(expected.size(), actual.size(), "edges " + actual + " but expected " + expected.keySet());
    }
}
