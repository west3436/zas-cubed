package io.github.west3436.zas.engine.zone;

import static io.github.west3436.zas.engine.zone.Grid.cell;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CELL;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.N2;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.O2;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.REGISTRY;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.ROOM_T;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.air;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.assertConsistent;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.fillWith;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.graph;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasMixture;
import org.junit.jupiter.api.Test;

class EdgesTest {
    private static final double EPS = 1e-9;

    /**
     * Two rows of {@code length} cells, A at z = 0 and B at z = 1, each a zone.
     * The first {@code openings} faces between them are open, as doors so the
     * zones never merge; the rest are closed.
     */
    private static Grid rows(int length, int openings) {
        Grid grid = new Grid().box(0, 0, 0, length - 1, 0, 1, CELL);
        for (int x = 0; x < length; x++) {
            if (x < openings) {
                grid.door(cell(x, 0, 0), cell(x, 0, 1));
            } else {
                grid.close(cell(x, 0, 0), cell(x, 0, 1));
            }
        }
        return grid;
    }

    @Test
    void core_030_edges() {
        Grid grid = rows(5, 3);
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 4, 0, 0));
        Zone b = graph.createZone(Grid.cells(0, 0, 1, 4, 0, 1));

        assertEquals(1, a.edges().size());
        assertEquals(1, b.edges().size());
        Edge edge = a.edges().get(0);
        assertSame(edge, b.edges().get(0), "both zones share the one edge");
        assertSame(edge, graph.edgeBetween(b, a));
        assertEquals(3, edge.connections());
        assertEquals(1, graph.edges().size());

        // A zone's connections to the ambient are one edge too.
        Grid open = new Grid().box(0, 0, 0, 2, 0, 1, CELL);
        ZoneGraph openGraph = graph(open, vacuum());
        Zone room = openGraph.createZone(Grid.cells(0, 0, 0, 2, 0, 0));
        assertEquals(1, room.edges().size());
        assertTrue(room.ambientEdge().isAmbient());
        assertEquals(3, room.ambientEdge().connections());
        assertConsistent(graph, grid);
        assertConsistent(openGraph, open);
    }

    @Test
    void core_037_edge_connection_counts() {
        Grid grid = rows(4, 0);
        // Two direct openings and one door.
        grid.open(cell(0, 0, 0), cell(0, 0, 1));
        grid.open(cell(1, 0, 0), cell(1, 0, 1));
        grid.door(cell(2, 0, 0), cell(2, 0, 1));
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 3, 0, 0));
        Zone b = graph.createZone(Grid.cells(0, 0, 1, 3, 0, 1));
        Edge edge = graph.edgeBetween(a, b);
        assertEquals(3, edge.connections());
        assertEquals(2, edge.directConnections());

        // Close the door.
        grid.close(cell(2, 0, 0), cell(2, 0, 1));
        graph.refresh(cell(2, 0, 0));
        assertEquals(2, edge.connections());
        assertEquals(2, edge.directConnections());
        // Open it again.
        grid.door(cell(2, 0, 0), cell(2, 0, 1));
        graph.refresh(cell(2, 0, 1));
        assertEquals(3, edge.connections());
        assertEquals(2, edge.directConnections());
        // A direct opening becomes a door.
        grid.door(cell(0, 0, 0), cell(0, 0, 1));
        graph.refresh(cell(0, 0, 0));
        assertEquals(3, edge.connections());
        assertEquals(1, edge.directConnections());
        // Closing every opening removes the edge.
        for (int x = 0; x < 3; x++) {
            grid.close(cell(x, 0, 0), cell(x, 0, 1));
            graph.refresh(cell(x, 0, 0));
        }
        assertFalse(edge.isAlive());
        assertNull(graph.edgeBetween(a, b));
        assertConsistent(graph, grid);
    }

    @Test
    void core_031_exchange_rule() {
        // The default table, from ZAS's 2 s values.
        double[] expectedDefault = {0.163, 0.225, 0.279, 0.322, 0.368, 0.417};
        for (int k = 1; k <= 6; k++) {
            assertEquals(expectedDefault[k - 1], ExchangeSettings.DEFAULT.ratio(k), 5e-4);
        }
        assertEquals(ExchangeSettings.DEFAULT.ratio(6), ExchangeSettings.DEFAULT.ratio(9), 0);

        for (int run = 0; run < 14; run++) {
            // Each connection count under each venting model (CORE-035, CORE-068).
            ExchangeSettings settings =
                    ExchangeSettings.DEFAULT.withVenting(ExchangeSettings.Venting.values()[run / 7]);
            int k = run % 7 + 1;
            Grid grid = rows(7, k);
            // Unequal volumes, so the weighting shows: B's cells are half cells.
            grid.box(0, 0, 1, 6, 0, 1, 500);
            int openings = k;
            ZoneGraph graph = graph(grid, vacuum(), settings);
            Zone a = graph.createZone(Grid.cells(0, 0, 0, 6, 0, 0));
            Zone b = graph.createZone(Grid.cells(0, 0, 1, 6, 0, 1));
            fillWith(graph, a, air(150, CELL, 350));
            fillWith(graph, b, air(20, CELL, 250));
            assertEquals(openings, graph.edgeBetween(a, b).connections());

            double va = a.volume();
            double vb = b.volume();
            double r = ExchangeSettings.DEFAULT.ratio(openings);
            if (settings.venting() == ExchangeSettings.Venting.VOLUME_AWARE) {
                // By default the opening's sharer weights the ratio too (CORE-035).
                double vs = settings.sharerVolume();
                r *= vs / (vs + va * vb / (va + vb));
            }
            double[] na = {a.mixture().moles(0), a.mixture().moles(1)};
            double[] nb = {b.mixture().moles(0), b.mixture().moles(1)};
            double ea = a.mixture().thermalEnergy();
            double eb = b.mixture().thermalEnergy();

            graph.exchange();

            double fa = r * vb / (va + vb);
            double fb = r * va / (va + vb);
            for (int i = 0; i < 2; i++) {
                String what = settings.venting() + ", k = " + k;
                assertEquals(na[i] * (1 - fa) + nb[i] * fb, a.mixture().moles(i), EPS, "A, " + what);
                assertEquals(nb[i] * (1 - fb) + na[i] * fa, b.mixture().moles(i), EPS, "B, " + what);
            }
            assertEquals(ea * (1 - fa) + eb * fb, a.mixture().thermalEnergy(), 1e-9 * ea);
        }
    }

    @Test
    void core_031_two_one_second_ticks_match_one_zas_tick() {
        for (int k = 1; k <= 6; k++) {
            double zasRatio = new double[] {0.30, 0.40, 0.48, 0.54, 0.60, 0.66}[k - 1];
            assertEquals(zasRatio, ExchangeSettings.DEFAULT.withPeriod(2).ratio(k), 1e-12);
            for (ExchangeSettings.Venting venting : ExchangeSettings.Venting.values()) {
                ExchangeSettings settings = ExchangeSettings.DEFAULT.withVenting(venting);
                GasMixture[] perSecond = runTicks(k, settings, 2);
                GasMixture[] zas = runTicks(k, settings.withPeriod(2), 1);
                for (int side = 0; side < 2; side++) {
                    for (int i = 0; i < REGISTRY.size(); i++) {
                        assertEquals(zas[side].moles(i), perSecond[side].moles(i), 1e-9, venting + ", k = " + k);
                    }
                    assertEquals(zas[side].temperature(), perSecond[side].temperature(), 1e-9, venting + ", k = " + k);
                }
            }
        }
    }

    private static GasMixture[] runTicks(int k, ExchangeSettings settings, int ticks) {
        Grid grid = rows(6, k);
        ZoneGraph graph = graph(grid, vacuum(), settings);
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 5, 0, 0));
        Zone b = graph.createZone(Grid.cells(0, 0, 1, 5, 0, 1));
        // Far apart, so the edge doesn't go to sleep.
        fillWith(graph, a, air(300, CELL, 400));
        fillWith(graph, b, air(5, CELL, 200));
        for (int t = 0; t < ticks; t++) {
            graph.exchange();
        }
        return new GasMixture[] {a.mixture().copy(), b.mixture().copy()};
    }

    /** A 2×1×2 room with one face open to an outside cell at x = 2. */
    private static Grid roomWithOpening() {
        return new Grid().box(0, 0, 0, 1, 0, 1, CELL).box(2, 0, 0, 2, 0, 0, CELL);
    }

    @Test
    void core_032_ambient_edges() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, ambient);
        Zone room = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        assertEquals(1, room.ambientEdge().connections());
        fillWith(graph, room, air(200, CELL, 330));

        double previous = room.mixture().pressure();
        double previousT = room.mixture().temperature();
        for (int t = 0; t < 5; t++) {
            graph.exchange();
            double p = room.mixture().pressure();
            assertTrue(p < previous && p > ambient.pressure(), "pressure moves toward the ambient: " + p);
            assertTrue(room.mixture().temperature() < previousT);
            previous = p;
            previousT = room.mixture().temperature();
        }
        // Eventually it reaches the ambient and dissolves into it (CORE-076).
        for (int t = 0; t < 200 && room.isAlive(); t++) {
            graph.exchange();
        }
        assertFalse(room.isAlive());
        assertEquals(0, graph.zoneCount());
    }

    @Test
    void core_038_ambient_is_never_changed() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        GasMixture before = ambient.copy();
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, ambient);
        long[] roomCells = Grid.cells(0, 0, 0, 1, 0, 1);
        Zone room = graph.createZone(roomCells);
        GasMixture held = graph.ambient().copy();
        for (int t = 0; t < 500; t++) {
            if (!room.isAlive()) {
                room = graph.createZone(roomCells);
            }
            if (t % 50 == 0) {
                // Keep the zone far from the ambient, alternately high and low.
                fillWith(graph, room, air(t % 100 == 0 ? 500 : 1, CELL, t % 100 == 0 ? 600 : 100));
            }
            graph.exchange();
        }
        for (int i = 0; i < REGISTRY.size(); i++) {
            assertEquals(held.moles(i), graph.ambient().moles(i), 0);
            assertEquals(before.moles(i), ambient.moles(i), 0);
        }
        assertEquals(held.temperature(), graph.ambient().temperature(), 0);
        assertEquals(held.volume(), graph.ambient().volume(), 0);
    }

    @Test
    void core_039_snap_to_ambient() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, ambient);
        Zone room = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        // Inside tolerance: 2 % more pressure, 1 K warmer, a little more oxygen.
        GasMixture near = air(103.3, CELL, ROOM_T + 1);
        near.setMoles(O2, near.moles(O2) * 1.01);
        fillWith(graph, room, near);
        assertTrue(room.mixture().isEquivalent(ambient));

        graph.beginConservationPeriod();
        graph.exchange();

        // CORE-076 now governs a zone with an ambient edge: it dissolves, so its
        // cells hold exactly the ambient (CORE-050).
        assertFalse(room.isAlive());
        for (long c : Grid.cells(0, 0, 0, 1, 0, 1)) {
            assertNull(graph.zoneOf(c), "exactly the ambient");
        }
        var report = graph.checkConservation(1);
        assertTrue(report.conserved(), "dissolving is recorded as exchange with the ambient");
        assertTrue(report.recorded(LedgerCategory.AMBIENT, N2) < 0);
    }

    @Test
    void core_033_sleeping_edges() {
        // Two rooms joined by a door, with equivalent air: the edge sleeps.
        Grid grid = rows(3, 1);
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 2, 0, 0));
        Zone b = graph.createZone(Grid.cells(0, 0, 1, 2, 0, 1));
        fillWith(graph, a, air(101, CELL, ROOM_T));
        fillWith(graph, b, air(100, CELL, ROOM_T));
        Edge edge = graph.edgeBetween(a, b);

        graph.exchange();
        assertTrue(edge.isSleeping());
        assertEquals(2, graph.zoneCount(), "a door edge sleeps instead of merging");
        long calls = graph.exchangeCount();
        for (int t = 0; t < 20; t++) {
            graph.exchange();
        }
        assertEquals(calls, graph.exchangeCount(), "sleeping edges get no exchange calls");
        assertTrue(edge.isSleeping());

        // A zone that matches its ambient dissolves (CORE-076), so nothing is left to exchange.
        Grid open = roomWithOpening();
        ZoneGraph openGraph = graph(open, air(101.325, CELL, ROOM_T));
        Zone room = openGraph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        openGraph.exchange();
        assertFalse(room.isAlive());
        long before = openGraph.exchangeCount();
        for (int t = 0; t < 20; t++) {
            openGraph.exchange();
        }
        assertEquals(before, openGraph.exchangeCount());
    }

    @Test
    void core_066_waking_edges() {
        Grid grid = rows(3, 1);
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 2, 0, 0));
        Zone b = graph.createZone(Grid.cells(0, 0, 1, 2, 0, 1));
        fillWith(graph, a, air(100, CELL, ROOM_T));
        fillWith(graph, b, air(100, CELL, ROOM_T));
        Edge edge = graph.edgeBetween(a, b);
        graph.exchange();
        assertTrue(edge.isSleeping());

        // Add gas to one side through the graph: the next tick exchanges.
        graph.add(b, air(200, 3 * CELL, ROOM_T), LedgerCategory.API);
        long calls = graph.exchangeCount();
        graph.exchange();
        assertEquals(calls + 1, graph.exchangeCount());
        assertFalse(edge.isSleeping());

        // Let it settle, then change the other side directly and say so.
        for (int t = 0; t < 100 && !edge.isSleeping(); t++) {
            graph.exchange();
        }
        assertTrue(edge.isSleeping());
        a.mixture().addThermalEnergy(1e6);
        graph.markChanged(a);
        calls = graph.exchangeCount();
        graph.exchange();
        assertEquals(calls + 1, graph.exchangeCount());
    }
}
