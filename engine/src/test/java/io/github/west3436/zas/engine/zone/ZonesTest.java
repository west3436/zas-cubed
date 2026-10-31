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
import static io.github.west3436.zas.engine.zone.ZoneFixtures.total;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasMixture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZonesTest {
    private static final double EPS = 1e-9;

    @Test
    void core_020_zone_definition() {
        Grid grid = new Grid().box(0, 0, 0, 2, 0, 2, CELL);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 2, 0, 2));

        for (long c : Grid.cells(0, 0, 0, 2, 0, 2)) {
            assertSame(zone, graph.zoneOf(c), "every connected zoned cell is in the one zone");
        }
        assertEquals(9, zone.cellCount());
        assertEquals(1, graph.zoneCount());
        assertConsistent(graph, grid);

        // A zone must be connected: two cells with a solid block between them can't be one zone.
        Grid apart = new Grid().box(0, 0, 0, 0, 0, 0, CELL).box(2, 0, 0, 2, 0, 0, CELL);
        ZoneGraph other = graph(apart, air(101.325, CELL, ROOM_T));
        assertThrows(IllegalArgumentException.class, () -> other.createZone(cell(0, 0, 0), cell(2, 0, 0)));
        assertEquals(0, other.zoneCount());
        // So must a joining cell: across a door is not a direct opening.
        Grid door = new Grid().box(0, 0, 0, 1, 0, 0, CELL);
        door.door(cell(0, 0, 0), cell(1, 0, 0));
        ZoneGraph doorGraph = graph(door, air(101.325, CELL, ROOM_T));
        Zone z = doorGraph.createZone(cell(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> doorGraph.join(cell(1, 0, 0), z));
    }

    @Test
    void core_027_zone_volume() {
        // Four full cells and two half cells (slabs).
        Grid grid = new Grid().box(0, 0, 0, 3, 0, 0, CELL).box(4, 0, 0, 5, 0, 0, 500);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 5, 0, 0));
        assertEquals(5000, zone.volume(), EPS);
        assertEquals(5000, zone.mixture().volume(), EPS);

        // A full cell becoming a half cell changes the volume, and the gas stays.
        double moles = zone.mixture().totalMoles();
        grid.set(cell(0, 0, 0), 500);
        graph.refreshAll(Grid.around(cell(0, 0, 0)));
        assertEquals(4500, zone.volume(), EPS);
        assertEquals(moles, zone.mixture().totalMoles(), EPS * moles);
        assertConsistent(graph, grid);
    }

    @Test
    void core_021_joining_cells() {
        Grid grid = new Grid().box(0, 0, 0, 2, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        Zone zone = graph.createZone(cell(0, 0, 0), cell(1, 0, 0));
        // 10 mol N₂ at 300 K in the zone, then a cell holding 5 mol O₂ at 400 K joins.
        GasMixture start = new GasMixture(REGISTRY, ZoneFixtures.CONFIG, 1, 300);
        start.setMoles(N2, 10);
        graph.add(zone, start, LedgerCategory.API);

        GasMixture cellGas = new GasMixture(REGISTRY, ZoneFixtures.CONFIG, CELL, 400);
        cellGas.setMoles(O2, 5);
        graph.join(cell(2, 0, 0), zone, cellGas, LedgerCategory.API);

        assertEquals(10, zone.mixture().moles(N2), EPS);
        assertEquals(5, zone.mixture().moles(O2), EPS);
        double cN = 10 * 20.8;
        double cO = 5 * 21.0;
        assertEquals((cN * 300 + cO * 400) / (cN + cO), zone.mixture().temperature(), EPS);
        assertEquals(3 * CELL, zone.volume(), EPS);
        assertConsistent(graph, grid);
    }

    @Test
    void core_028_unzoned_cells_bring_ambient_gas() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 0, CELL).box(2, 0, 0, 2, 0, 0, 500);
        ZoneGraph graph = graph(grid, ambient);

        Zone zone = graph.createZone(cell(0, 0, 0));
        assertEquals(ambient.moles(N2), zone.mixture().moles(N2), EPS, "a new zone holds the ambient");
        fillWith(graph, zone, air(50, CELL, ROOM_T));
        double n2 = zone.mixture().moles(N2);
        double o2 = zone.mixture().moles(O2);

        graph.join(cell(1, 0, 0), zone);
        assertEquals(n2 + ambient.moles(N2), zone.mixture().moles(N2), EPS);
        assertEquals(o2 + ambient.moles(O2), zone.mixture().moles(O2), EPS);
        // A half cell brings half the ambient moles.
        graph.join(cell(2, 0, 0), zone);
        assertEquals(n2 + 1.5 * ambient.moles(N2), zone.mixture().moles(N2), EPS);
        assertEquals(o2 + 1.5 * ambient.moles(O2), zone.mixture().moles(O2), EPS);
        assertEquals(
                1.5 * ambient.moles(N2), graph.ledger().recorded(LedgerCategory.AMBIENT, N2) - ambient.moles(N2), EPS);
    }

    @Test
    void core_022_displaced_gas_is_conserved() {
        Grid grid = new Grid().box(0, 0, 0, 4, 0, 1, CELL);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 4, 0, 1));
        graph.beginConservationPeriod();
        double moles = zone.mixture().totalMoles();
        double pressure = zone.mixture().pressure();
        double volume = zone.volume();

        grid.set(cell(2, 0, 0), 0); // a solid block fills one cell
        graph.refreshAll(Grid.around(cell(2, 0, 0)));

        assertEquals(1, graph.zoneCount());
        assertEquals(moles, zone.mixture().totalMoles(), EPS * moles, "total moles unchanged");
        assertEquals(volume - CELL, zone.volume(), EPS);
        assertEquals(pressure * volume / (volume - CELL), zone.mixture().pressure(), EPS * pressure);
        assertNull(graph.zoneOf(cell(2, 0, 0)));
        assertTrue(graph.checkConservation(1).conserved());
        assertConsistent(graph, grid);
    }

    @Test
    void core_075_displaced_gas_from_last_cell() {
        // Zones: left 3,000 L | middle one cell | right 1,000 L, joined through doors.
        Grid grid = new Grid().box(0, 0, 0, 4, 0, 0, CELL);
        grid.door(cell(2, 0, 0), cell(3, 0, 0));
        grid.door(cell(3, 0, 0), cell(4, 0, 0));
        // Make the left zone 3 cells: x = 0..2; the middle is x = 3, the right x = 4.
        ZoneGraph graph = graph(grid, vacuum());
        Zone left = graph.createZone(cell(0, 0, 0), cell(1, 0, 0), cell(2, 0, 0));
        Zone middle = graph.createZone(cell(3, 0, 0));
        Zone right = graph.createZone(cell(4, 0, 0));
        fillWith(graph, middle, air(400, CELL, ROOM_T));
        graph.beginConservationPeriod();
        double displaced = middle.mixture().moles(N2);

        grid.set(cell(3, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(3, 0, 0)));

        assertFalse(middle.isAlive());
        assertEquals(0.75 * displaced, left.mixture().moles(N2), EPS);
        assertEquals(0.25 * displaced, right.mixture().moles(N2), EPS);
        assertTrue(graph.checkConservation(1).conserved());
        assertConsistent(graph, grid);

        // With no neighbouring zone the gas goes to the ambient.
        Grid lone = new Grid().box(0, 0, 0, 0, 0, 0, CELL);
        ZoneGraph loneGraph = graph(lone, vacuum());
        Zone only = loneGraph.createZone(cell(0, 0, 0));
        fillWith(loneGraph, only, air(400, CELL, ROOM_T));
        loneGraph.beginConservationPeriod();
        double moles = only.mixture().moles(N2);
        lone.set(cell(0, 0, 0), 0);
        loneGraph.refresh(cell(0, 0, 0));
        assertFalse(only.isAlive());
        assertEquals(0, loneGraph.zoneCount());
        var report = loneGraph.checkConservation(1);
        assertTrue(report.conserved());
        assertEquals(-moles, report.recorded(LedgerCategory.AMBIENT, N2), EPS);
    }

    @Test
    void core_023_zone_merge() {
        // Two 3×1×3 rooms at 100 and 50 kPa, separated by a wall at x = 3 with one opening.
        Grid grid = new Grid().box(0, 0, 0, 2, 0, 2, CELL).box(4, 0, 0, 6, 0, 2, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 2, 0, 2));
        Zone b = graph.createZone(Grid.cells(4, 0, 0, 6, 0, 2));
        fillWith(graph, a, air(100, CELL, ROOM_T));
        fillWith(graph, b, air(50, CELL, ROOM_T));
        graph.beginConservationPeriod();
        double moles = total(graph);

        grid.set(cell(3, 0, 1), CELL); // knock a hole in the wall
        graph.refreshAll(Grid.around(cell(3, 0, 1)));
        graph.join(cell(3, 0, 1), a); // classification puts the opening in a zone
        Edge edge = graph.edgeBetween(a, b);
        assertEquals(1, edge.connections());
        assertEquals(1, edge.directConnections());
        double withHole = total(graph);

        int ticks = 0;
        while (graph.zoneCount() > 1 && ticks < 100) {
            graph.exchange();
            ticks++;
        }
        assertEquals(1, graph.zoneCount(), "merged after " + ticks + " ticks");
        Zone merged = graph.zones().iterator().next();
        assertSame(a, merged, "the larger zone keeps its id");
        assertFalse(b.isAlive());
        assertFalse(edge.isAlive());
        assertEquals(19 * CELL, merged.volume(), EPS);
        assertEquals(withHole, merged.mixture().totalMoles(), 1e-12 * moles, "moles conserved");
        assertTrue(graph.checkConservation(1).conserved());
        assertConsistent(graph, grid);
    }

    @Test
    void core_024_zone_split() {
        Grid grid = new Grid().box(0, 0, 0, 4, 0, 2, CELL);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 4, 0, 2));
        long id = zone.id();

        // Wall the room in two along x = 2.
        for (int z = 0; z < 3; z++) {
            grid.set(cell(2, 0, z), 0);
            graph.refreshAll(Grid.around(cell(2, 0, z)));
            assertConsistent(graph, grid);
        }
        assertEquals(2, graph.zoneCount());
        Zone left = graph.zoneOf(cell(0, 0, 0));
        Zone right = graph.zoneOf(cell(4, 0, 0));
        assertNotSame(left, right);
        assertEquals(6, left.cellCount());
        assertEquals(6, right.cellCount());
        assertTrue(left.id() == id || right.id() == id, "one remnant keeps the zone's id");
        assertEquals(0, left.edges().size(), "no edge across a solid wall");
    }

    @Test
    void core_064_gas_shared_on_split() {
        // A row of five cells; filling the second leaves remnants of 1 and 3 cells.
        Grid grid = new Grid().box(0, 0, 0, 4, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 4, 0, 0));
        GasMixture gas = air(120, CELL, 310);
        fillWith(graph, zone, gas);
        graph.beginConservationPeriod();
        double moles = zone.mixture().totalMoles();

        grid.set(cell(1, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(1, 0, 0)));

        Zone small = graph.zoneOf(cell(0, 0, 0));
        Zone large = graph.zoneOf(cell(4, 0, 0));
        assertNotSame(small, large);
        assertEquals(CELL, small.volume(), EPS);
        assertEquals(3 * CELL, large.volume(), EPS);
        assertEquals(310, small.mixture().temperature(), EPS);
        assertEquals(310, large.mixture().temperature(), EPS);
        assertEquals(3, large.mixture().totalMoles() / small.mixture().totalMoles(), 1e-12);
        for (int i = 0; i < REGISTRY.size(); i++) {
            assertEquals(
                    small.mixture().moles(i) / small.mixture().totalMoles(),
                    large.mixture().moles(i) / large.mixture().totalMoles(),
                    1e-12,
                    "same composition");
        }
        // The filled cell's gas stayed (CORE-022) and was shared by volume too.
        assertEquals(moles, small.mixture().totalMoles() + large.mixture().totalMoles(), EPS * moles);
        assertTrue(graph.checkConservation(1).conserved());
        assertConsistent(graph, grid);
    }

    @Test
    void core_025_local_split_detection() {
        List<Integer> visits = new ArrayList<>();
        for (int size : new int[] {8, 16, 32}) {
            Grid grid = new Grid().box(0, 0, 0, size - 1, size - 1, size - 1, CELL);
            ZoneGraph graph = graph(grid, vacuum());
            graph.createZone(Grid.cells(0, 0, 0, size - 1, size - 1, size - 1));
            long centre = cell(size / 2, size / 2, size / 2);
            grid.set(centre, 0);
            graph.refreshAll(Grid.around(centre));
            assertEquals(1, graph.zoneCount());
            visits.add(graph.lastSplitVisits());
        }
        // Zones of 512, 4,096 and 32,768 cells: the work stays the same.
        assertEquals(visits.get(0), visits.get(1), "visits " + visits);
        assertEquals(visits.get(0), visits.get(2), "visits " + visits);
        assertTrue(visits.get(0) < 50, "visits " + visits);
    }

    @Test
    void core_026_zone_identity() {
        Grid grid = new Grid().box(0, 0, 0, 9, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        Set<Long> ids = new HashSet<>();
        for (int round = 0; round < 50; round++) {
            List<Zone> created = new ArrayList<>();
            for (int x = 0; x < 10; x += 2) {
                Zone z = graph.createZone(cell(x, 0, 0));
                assertTrue(ids.add(z.id()), "id " + z.id() + " reused");
                created.add(z);
            }
            for (Zone z : created) {
                long id = z.id();
                graph.join(Grid.cell((int) Grid.x(z.cells()[0]) + 1, 0, 0), z);
                assertEquals(id, z.id(), "id unchanged while the zone lives");
                assertSame(z, graph.zone(id));
            }
            for (Zone z : created) {
                for (long c : z.cells()) {
                    graph.leave(c);
                }
                assertFalse(z.isAlive());
                assertNull(graph.zone(z.id()));
            }
        }
        assertEquals(250, ids.size());
    }

    @Test
    void core_065_empty_zones_are_destroyed() {
        Grid grid = new Grid().box(0, 0, 0, 3, 0, 0, CELL).box(4, 0, 0, 4, 0, 0, CELL);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 3, 0, 0));
        assertEquals(1, zone.edges().size(), "an ambient edge to the open cell at x = 4");
        Edge ambientEdge = zone.ambientEdge();

        for (long c : Grid.cells(0, 0, 0, 3, 0, 0)) {
            grid.set(c, 0);
            graph.refreshAll(Grid.around(c));
        }
        assertFalse(zone.isAlive());
        assertEquals(0, graph.zoneCount());
        assertNull(graph.zone(zone.id()));
        assertTrue(graph.edges().isEmpty());
        assertFalse(ambientEdge.isAlive());
        assertThrows(IllegalArgumentException.class, () -> graph.markChanged(zone));
    }
}
