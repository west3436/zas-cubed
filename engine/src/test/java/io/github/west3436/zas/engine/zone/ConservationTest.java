package io.github.west3436.zas.engine.zone;

import static io.github.west3436.zas.engine.zone.Grid.cell;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CELL;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CONFIG;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.N2;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.O2;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.REGISTRY;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.ROOM_T;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.air;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.assertConsistent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.conservation.ConservationLedger;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasMixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConservationTest {
    private static final int SX = 8;
    private static final int SY = 3;
    private static final int SZ = 8;
    private static final LedgerCategory[] OP_CATEGORIES = {
        LedgerCategory.API, LedgerCategory.DEVICE, LedgerCategory.REACTION, LedgerCategory.LIFE, LedgerCategory.OPERATOR
    };

    /**
     * Random sequences of geometry changes, zone creation, joins, leaves, gas
     * operations, ambient changes and exchange, with the ledger checked every
     * tick and the graph checked against a brute-force recomputation.
     */
    @Test
    void core_036_conservation_invariant() {
        String first = fuzz(0x5A5C0DEL, 3000, ExchangeSettings.DEFAULT);
        // The same seed gives bit-identical results (architecture §3.5).
        assertEquals(first, fuzz(0x5A5C0DEL, 3000, ExchangeSettings.DEFAULT));
        fuzz(42L, 3000, ExchangeSettings.DEFAULT);
        // ZAS's faithful venting model too (CORE-068).
        ExchangeSettings faithful = ExchangeSettings.DEFAULT.withVenting(ExchangeSettings.Venting.FAITHFUL);
        fuzz(0x5A5C0DEL, 3000, faithful);
        fuzz(42L, 3000, faithful);
    }

    private static String fuzz(long seed, int ticks, ExchangeSettings settings) {
        Random random = new Random(seed);
        Grid grid = new Grid();
        for (long c : Grid.cells(0, 0, 0, SX - 1, SY - 1, SZ - 1)) {
            grid.set(c, random.nextInt(10) < 3 ? 0 : (random.nextInt(5) == 0 ? 500 : CELL));
        }
        List<String> warnings = new ArrayList<>();
        ConservationLedger ledger = new ConservationLedger(REGISTRY, CONFIG.pruneEpsilon(), 1e-12, warnings::add);
        ZoneGraph graph = new ZoneGraph(REGISTRY, CONFIG, grid, air(101.325, CELL, ROOM_T), settings, ledger);
        graph.beginConservationPeriod();
        int created = 0;
        for (int tick = 1; tick <= ticks; tick++) {
            int ops = 1 + random.nextInt(6);
            for (int op = 0; op < ops; op++) {
                created += randomOperation(random, grid, graph) ? 1 : 0;
            }
            graph.exchange();
            ConservationReport report = graph.checkConservation(tick);
            assertTrue(report.conserved(), () -> "seed " + seed + ": " + report.violations());
            assertConsistent(graph, grid);
        }
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertTrue(created > 50, "the fuzz should create many zones, created " + created);
        assertTrue(graph.mergeCount() > 20, "merges: " + graph.mergeCount());
        assertTrue(graph.dissolveCount() > 20, "dissolves: " + graph.dissolveCount());
        // Splits are rarer, and how rare depends on how fast zones exchange: 6 to 21 in these runs.
        assertTrue(graph.splitCount() > 5, "splits: " + graph.splitCount());
        StringBuilder state = new StringBuilder();
        for (Zone z : graph.zones()) {
            state.append(z.id())
                    .append(':')
                    .append(z.mixture())
                    .append(z.edges())
                    .append('\n');
        }
        return state.toString();
    }

    /** Runs one random operation; returns true if it created a zone. */
    private static boolean randomOperation(Random random, Grid grid, ZoneGraph graph) {
        long c = cell(random.nextInt(SX), random.nextInt(SY), random.nextInt(SZ));
        switch (random.nextInt(12)) {
            case 0 -> {
                // Place or break a block, or swap it for a partial one.
                int pick = random.nextInt(3);
                grid.set(c, pick == 0 ? 0 : pick == 1 ? CELL : 500);
                graph.refreshAll(Grid.around(c));
            }
            case 1 -> {
                // Open, close or door a face.
                long[] around = Grid.around(c);
                long n = around[1 + random.nextInt(around.length - 1)];
                switch (random.nextInt(3)) {
                    case 0 -> grid.open(c, n);
                    case 1 -> grid.close(c, n);
                    default -> grid.door(c, n);
                }
                graph.refresh(random.nextBoolean() ? c : n);
            }
            case 2, 3 -> {
                if (grid.volume(c) > 0 && graph.zoneOf(c) == null) {
                    // A trigger modifies the air (CORE-051), so the zone doesn't
                    // dissolve straight back into the ambient (CORE-076).
                    Zone z = graph.createZone(c);
                    graph.add(
                            z,
                            air(50 + random.nextDouble() * 300, CELL, 200 + random.nextDouble() * 300),
                            LedgerCategory.API);
                    return true;
                }
            }
            case 4, 5 -> {
                if (grid.volume(c) > 0 && graph.zoneOf(c) == null) {
                    Zone[] target = {null};
                    grid.forEachOpenNeighbour(c, (n, direct) -> {
                        if (direct && target[0] == null) {
                            target[0] = graph.zoneOf(n);
                        }
                    });
                    if (target[0] != null) {
                        if (random.nextBoolean()) {
                            graph.join(c, target[0]);
                        } else {
                            graph.join(
                                    c,
                                    target[0],
                                    air(random.nextDouble() * 300, CELL, 200 + random.nextDouble() * 300),
                                    LedgerCategory.API);
                        }
                    }
                }
            }
            case 6 -> {
                if (graph.zoneOf(c) != null) {
                    graph.leave(c);
                }
            }
            case 7 -> {
                Zone z = randomZone(random, graph);
                if (z != null) {
                    GasMixture gas = new GasMixture(REGISTRY, CONFIG, CELL, 150 + random.nextDouble() * 500);
                    gas.setMoles(random.nextInt(REGISTRY.size()), random.nextDouble() * 100);
                    graph.add(z, gas, OP_CATEGORIES[random.nextInt(OP_CATEGORIES.length)]);
                }
            }
            case 8 -> {
                Zone z = randomZone(random, graph);
                if (z != null) {
                    if (random.nextBoolean()) {
                        graph.remove(z, random.nextDouble(), OP_CATEGORIES[random.nextInt(OP_CATEGORIES.length)]);
                    } else {
                        graph.removeMoles(z, random.nextDouble() * 50, LedgerCategory.DEVICE);
                    }
                }
            }
            case 9 -> {
                Zone z = randomZone(random, graph);
                if (z != null) {
                    if (random.nextBoolean()) {
                        graph.addThermalEnergy(z, random.nextDouble() * 1e5);
                    } else {
                        graph.removeThermalEnergy(z, random.nextDouble() * 1e5);
                    }
                }
            }
            case 10 -> {
                if (random.nextInt(4) == 0) {
                    graph.setAmbient(air(random.nextDouble() * 150, CELL, 150 + random.nextDouble() * 250));
                }
            }
            default -> {
                // Tiny removals exercise pruning.
                Zone z = randomZone(random, graph);
                if (z != null) {
                    graph.remove(z, 1 - 1e-12, LedgerCategory.DEVICE);
                }
            }
        }
        return false;
    }

    private static Zone randomZone(Random random, ZoneGraph graph) {
        if (graph.zoneCount() == 0) {
            return null;
        }
        int index = random.nextInt(graph.zoneCount());
        for (Zone z : graph.zones()) {
            if (index-- == 0) {
                return z;
            }
        }
        throw new AssertionError();
    }

    @Test
    void core_073_invariant_checker() {
        List<String> warnings = new ArrayList<>();
        ConservationLedger ledger = new ConservationLedger(
                REGISTRY, CONFIG.pruneEpsilon(), ConservationLedger.DEFAULT_RELATIVE_TOLERANCE, warnings::add);
        Grid grid = new Grid().box(0, 0, 0, 2, 0, 2, CELL);
        ZoneGraph graph =
                new ZoneGraph(REGISTRY, CONFIG, grid, air(101.325, CELL, ROOM_T), ExchangeSettings.DEFAULT, ledger);
        assertThrows(IllegalStateException.class, () -> graph.checkConservation(0));

        graph.beginConservationPeriod();
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 2, 0, 2));
        graph.add(zone, air(50, CELL, ROOM_T), LedgerCategory.DEVICE);
        graph.remove(zone, 0.25, LedgerCategory.LIFE);
        ConservationReport ok = graph.checkConservation(1);
        assertTrue(ok.conserved(), ok.violations()::toString);
        assertTrue(ok.recorded(LedgerCategory.AMBIENT, N2) > 0, "the new zone's ambient gas");
        assertTrue(ok.recorded(LedgerCategory.DEVICE, N2) > 0);
        assertTrue(ok.recorded(LedgerCategory.LIFE, N2) < 0);
        assertTrue(warnings.isEmpty());

        // Inject an unattributed change of 5 mol O₂.
        double o2 = zone.mixture().moles(O2);
        zone.mixture().setMoles(O2, o2 + 5);
        ConservationReport bad = graph.checkConservation(2);
        assertFalse(bad.conserved());
        assertEquals(1, bad.violations().size());
        ConservationReport.Violation v = bad.violations().get(0);
        assertEquals(O2, v.gas());
        assertEquals("test:oxygen", v.gasId());
        assertEquals(5, v.unexplained(), 1e-9);
        assertEquals(2, v.tick());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("test:oxygen"), warnings.get(0));

        // The next period starts from the new totals, and a change below the epsilon isn't reported.
        double n2 = zone.mixture().moles(N2);
        zone.mixture().setMoles(N2, n2 - CONFIG.pruneEpsilon() / 2);
        assertTrue(graph.checkConservation(3).conserved());
        assertEquals(1, warnings.size());

        // A change attributed to the wrong amount is still caught.
        GasMixture parcel = graph.remove(zone, 0.1, LedgerCategory.DEVICE);
        ledger.record(LedgerCategory.DEVICE, N2, parcel.moles(N2)); // undoes the record of the N₂ removed
        ConservationReport wrong = graph.checkConservation(4);
        assertEquals(1, wrong.violations().size());
        assertEquals(N2, wrong.violations().get(0).gas());
        assertEquals(-parcel.moles(N2), wrong.violations().get(0).unexplained(), 1e-9);
    }
}
