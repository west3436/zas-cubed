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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.gas.GasMixture;
import org.junit.jupiter.api.Test;

/**
 * Zones joined through zone boundaries such as open doors (CORE-034, CORE-067, GEO-044): they
 * exchange but never merge, equalize as a group once equivalent, and opening or closing the door
 * changes only edge counts.
 */
class BoundaryTest {
    private static final double EPS = 1e-12;

    /**
     * Rooms of two cells in a row along x, room {@code i} at x = 2i and 2i + 1, each a zone, with a
     * door between each pair of neighbouring rooms.
     */
    private static Grid row(int rooms) {
        Grid grid = new Grid().box(0, 0, 0, 2 * rooms - 1, 0, 0, CELL);
        for (int i = 1; i < rooms; i++) {
            grid.door(cell(2 * i - 1, 0, 0), cell(2 * i, 0, 0));
        }
        return grid;
    }

    private static Zone[] zones(ZoneGraph graph, int rooms) {
        Zone[] out = new Zone[rooms];
        for (int i = 0; i < rooms; i++) {
            out[i] = graph.createZone(cell(2 * i, 0, 0), cell(2 * i + 1, 0, 0));
        }
        return out;
    }

    private static double[] totals(ZoneGraph graph) {
        double[] t = new double[REGISTRY.size()];
        graph.totalMoles(t);
        return t;
    }

    private static double thermalEnergy(Zone... zones) {
        double e = 0;
        for (Zone z : zones) {
            e += z.mixture().thermalEnergy();
        }
        return e;
    }

    /** Checks the zones hold one mixture: the same molar density of every gas and temperature. */
    private static void assertOneMixture(Zone... zones) {
        GasMixture first = zones[0].mixture();
        for (Zone z : zones) {
            assertEquals(first.temperature(), z.mixture().temperature(), EPS * first.temperature(), z + " temperature");
            for (int g = 0; g < REGISTRY.size(); g++) {
                assertEquals(
                        first.molarDensity(g),
                        z.mixture().molarDensity(g),
                        EPS * first.molarDensity(g),
                        z + " gas " + g);
            }
        }
    }

    // ---- CORE-034 ---------------------------------------------------------------------

    /**
     * Two rooms joined only by a door stay two zones however long they share gas, even holding the
     * same air; the door edge sleeps instead, and an explicit merge is refused (CORE-034).
     */
    @Test
    void core_034_door_separated_zones_never_merge() {
        Grid grid = row(2);
        ZoneGraph graph = graph(grid, vacuum());
        Zone[] z = zones(graph, 2);
        fillWith(graph, z[0], air(150, CELL, ROOM_T));
        fillWith(graph, z[1], air(50, CELL, 350));
        Edge door = graph.edgeBetween(z[0], z[1]);
        assertEquals(0, door.directConnections());

        for (int t = 0; t < 200; t++) {
            graph.exchange();
            assertEquals(2, graph.zoneCount(), "merged at tick " + t);
        }
        assertTrue(door.isSleeping(), "the door edge sleeps once the rooms are equivalent");
        assertTrue(z[0].isAlive() && z[1].isAlive());
        assertEquals(0, graph.mergeCount());
        assertThrows(IllegalArgumentException.class, () -> graph.merge(z[0], z[1]), "only direct openings merge");

        // A direct opening beside the door merges them (CORE-023): it's the door that keeps them apart.
        grid.open(cell(1, 0, 0), cell(2, 0, 0));
        graph.refresh(cell(1, 0, 0));
        graph.exchange();
        assertEquals(1, graph.zoneCount());
        assertConsistent(graph, grid);
    }

    // ---- CORE-067 ---------------------------------------------------------------------

    /**
     * Three rooms in a row joined by doors, each equivalent to its neighbours but not equal: once
     * their edges fall asleep, all three hold one combined mixture, with every gas and the thermal
     * energy conserved and no exchange spent (CORE-067).
     */
    @Test
    void core_067_door_group_equalizes_to_one_mixture() {
        Grid grid = row(3);
        ZoneGraph graph = graph(grid, vacuum());
        Zone[] z = zones(graph, 3);
        fillWith(graph, z[0], air(100, CELL, 293));
        fillWith(graph, z[1], air(102, CELL, 294));
        fillWith(graph, z[2], air(104, CELL, 296));
        // Room 2 has more oxygen, so the combination is visible in the composition too.
        z[2].mixture().addMoles(O2, 0.5);
        graph.markChanged(z[2]);
        double[] before = totals(graph);
        double energy = thermalEnergy(z);
        long exchanges = graph.exchangeCount();
        graph.beginConservationPeriod();

        graph.exchange();
        for (Edge e : graph.edges()) {
            assertTrue(e.isSleeping(), e + " should sleep: its sides were equivalent");
        }
        assertEquals(exchanges, graph.exchangeCount(), "equivalent edges don't exchange");
        assertEquals(3, graph.zoneCount(), "door-separated zones never merge (CORE-034)");
        assertOneMixture(z);
        double[] after = totals(graph);
        for (int g = 0; g < before.length; g++) {
            assertEquals(before[g], after[g], EPS * before[g], "gas " + g + " conserved");
            // Each room holds a third of the whole: the rooms are the same size.
            assertEquals(before[g] / 3, z[1].mixture().moles(g), EPS * before[g], "room 1's share of gas " + g);
        }
        assertEquals(energy, thermalEnergy(z), EPS * energy, "thermal energy conserved");
        assertTrue(graph.checkConservation(1).conserved(), "nothing unexplained in the ledger");
        assertTrue(graph.equalizeCount() > 0);

        // Equal now, they stay asleep and unchanged.
        long equalized = graph.equalizeCount();
        GasMixture held = z[0].mixture().copy();
        for (int t = 0; t < 10; t++) {
            graph.exchange();
        }
        assertEquals(exchanges, graph.exchangeCount());
        assertEquals(equalized, graph.equalizeCount(), "nothing more to equalize");
        assertEquals(held.moles(N2), z[0].mixture().moles(N2));
        assertConsistent(graph, grid);
    }

    /**
     * Rooms that start far apart share through their doors until they are equivalent; then the
     * whole group snaps to one mixture, shared by volume between rooms of different sizes
     * (CORE-067).
     */
    @Test
    void core_067_group_equalizes_once_exchange_makes_it_equivalent() {
        // Rooms of 2, 2 and 4 cells: the last one is twice the size.
        Grid grid = new Grid().box(0, 0, 0, 7, 0, 0, CELL);
        grid.door(cell(1, 0, 0), cell(2, 0, 0));
        grid.door(cell(3, 0, 0), cell(4, 0, 0));
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(cell(0, 0, 0), cell(1, 0, 0));
        Zone b = graph.createZone(cell(2, 0, 0), cell(3, 0, 0));
        Zone c = graph.createZone(Grid.cells(4, 0, 0, 7, 0, 0));
        fillWith(graph, a, air(300, CELL, ROOM_T));
        fillWith(graph, b, air(20, CELL, ROOM_T));
        fillWith(graph, c, air(100, CELL, 250));
        double[] before = totals(graph);
        double energy = thermalEnergy(a, b, c);

        int ticks = 0;
        while (!graph.edges().stream().allMatch(Edge::isSleeping) && ticks < 200) {
            graph.exchange();
            ticks++;
        }
        assertTrue(ticks > 1 && ticks < 200, "the doors exchanged, then slept, after " + ticks + " ticks");
        assertTrue(graph.exchangeCount() > 0);
        assertOneMixture(a, b, c);
        double[] after = totals(graph);
        for (int g = 0; g < before.length; g++) {
            assertEquals(before[g], after[g], EPS * before[g], "gas " + g + " conserved");
            assertEquals(after[g] / 2, c.mixture().moles(g), EPS * after[g], "the big room's half of gas " + g);
        }
        assertEquals(energy, thermalEnergy(a, b, c), EPS * energy, "thermal energy conserved");
    }

    /**
     * The group is what sleeping edges reach, and a paused zone keeps its last-known mixture
     * (GEO-035) though a sleeping edge joins it (CORE-067).
     */
    @Test
    void core_067_group_stops_at_paused_zones() {
        // P | A | B in a row; the door between A and B starts closed.
        Grid grid = row(3);
        grid.close(cell(3, 0, 0), cell(4, 0, 0));
        ZoneGraph graph = graph(grid, vacuum());
        Zone[] z = zones(graph, 3);
        Zone p = z[0];
        Zone a = z[1];
        Zone b = z[2];
        fillWith(graph, p, air(100, CELL, ROOM_T));
        fillWith(graph, a, air(101, CELL, ROOM_T));
        fillWith(graph, b, air(400, CELL, ROOM_T));
        graph.exchange();
        assertTrue(graph.edgeBetween(p, a).isSleeping(), "P and A were equivalent");
        assertOneMixture(p, a);
        GasMixture b0 = b.mixture().copy();
        assertEquals(b0.moles(N2), b.mixture().moles(N2), "B isn't joined to them");

        graph.setPaused(p, true);
        GasMixture paused = p.mixture().copy();
        // Open the door to B, now equivalent to A but not equal.
        fillWith(graph, b, air(101.5, CELL, ROOM_T));
        grid.door(cell(3, 0, 0), cell(4, 0, 0));
        graph.refresh(cell(4, 0, 0));
        graph.exchange();
        assertTrue(graph.edgeBetween(p, a).isSleeping());
        assertTrue(graph.edgeBetween(a, b).isSleeping());
        assertOneMixture(a, b);
        for (int g = 0; g < REGISTRY.size(); g++) {
            assertEquals(paused.moles(g), p.mixture().moles(g), "the paused zone kept gas " + g);
        }
        assertNotEquals(p.mixture().moles(N2), a.mixture().moles(N2), "the paused zone wasn't equalized");
    }

    // ---- GEO-044 ----------------------------------------------------------------------

    /**
     * Opening and closing a door between two zones changes only the door edge's connection count:
     * no split check, split, merge or new zone, and every cell stays in its zone (GEO-044).
     */
    @Test
    void geo_044_toggling_a_door_never_splits() {
        // Rooms A (x 0 to 1) and B (x 2 to 3), two cells deep, with a door at each depth.
        Grid grid = new Grid().box(0, 0, 0, 3, 0, 1, CELL);
        grid.door(cell(1, 0, 1), cell(2, 0, 1));
        long[] doorCells = {cell(1, 0, 0), cell(2, 0, 0)};
        grid.door(doorCells[0], doorCells[1]);
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        Zone b = graph.createZone(Grid.cells(2, 0, 0, 3, 0, 1));
        fillWith(graph, a, air(120, CELL, ROOM_T));
        fillWith(graph, b, air(80, CELL, ROOM_T));
        long splitChecks = graph.splitCheckCount();
        long next = graph.nextZoneId();
        long[] cellsA = a.cells();
        long[] cellsB = b.cells();
        Edge door = graph.edgeBetween(a, b);
        assertEquals(2, door.connections());

        for (int round = 0; round < 20; round++) {
            boolean open = round % 2 == 1;
            if (open) {
                grid.door(doorCells[0], doorCells[1]);
            } else {
                grid.close(doorCells[0], doorCells[1]);
            }
            graph.refresh(doorCells[round % 2]);
            graph.exchange();
            assertEquals(splitChecks, graph.splitCheckCount(), "a toggle checked for a split");
            assertEquals(0, graph.splitCount());
            assertEquals(0, graph.mergeCount());
            assertEquals(next, graph.nextZoneId(), "a toggle created a zone");
            assertTrue(a.isAlive() && b.isAlive());
            assertArrayEquals(cellsA, a.cells(), "a toggle moved A's cells");
            assertArrayEquals(cellsB, b.cells(), "a toggle moved B's cells");
            assertSame(door, graph.edgeBetween(a, b), "the edge stays; only its count changes");
            assertEquals(open ? 2 : 1, door.connections());
            assertEquals(0, door.directConnections());
        }
        assertConsistent(graph, grid);
    }
}
