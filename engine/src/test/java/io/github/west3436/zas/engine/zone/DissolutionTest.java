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
import static io.github.west3436.zas.engine.zone.ZoneFixtures.fillWith;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.graph;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasMixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Zones dissolving into the ambient (CORE-076, D-63), and pinned zones that don't (D-64). */
class DissolutionTest {
    private static final long[] ROOM = Grid.cells(0, 0, 0, 1, 0, 1);

    /** A 2×1×2 room with one face open to an outside cell at x = 2. */
    private static Grid roomWithOpening() {
        return new Grid().box(0, 0, 0, 1, 0, 1, CELL).box(2, 0, 0, 2, 0, 0, CELL);
    }

    /** Deep space: no gas at 2.7 K. */
    private static GasMixture deepSpace() {
        return new GasMixture(REGISTRY, CONFIG, CELL, 2.7);
    }

    @Test
    void core_076_zone_venting_to_vacuum_dissolves() {
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, deepSpace());
        Zone room = graph.createZone(ROOM);
        fillWith(graph, room, air(101.325, CELL, ROOM_T));
        graph.beginConservationPeriod();

        int ticks = 0;
        double lastTemperature = 0;
        double lastPressure = 0;
        while (room.isAlive() && ticks < 500) {
            lastTemperature = room.mixture().temperature();
            lastPressure = room.mixture().pressure();
            long calls = graph.exchangeCount();
            graph.exchange();
            ticks++;
            ConservationReport report = graph.checkConservation(ticks);
            assertTrue(report.conserved(), report::toString);
            if (room.isAlive()) {
                assertEquals(calls + 1, graph.exchangeCount(), "the ambient edge exchanges every tick until then");
            }
            assertConsistent(graph, grid);
        }
        assertFalse(room.isAlive(), "the room dissolves into the vacuum");
        assertTrue(ticks > 10 && ticks < 200, "dissolved after " + ticks + " ticks");
        // The near-empty zone kept its own temperature: it was never equivalent under CORE-006.
        assertTrue(lastTemperature > 100, "temperature before dissolving: " + lastTemperature);
        assertTrue(lastPressure < 1, "pressure before dissolving: " + lastPressure);
        assertEquals(0, graph.zoneCount());
        assertEquals(1, graph.dissolveCount());
        assertTrue(graph.edges().isEmpty());
        for (long c : ROOM) {
            assertNull(graph.zoneOf(c));
        }

        // Its edge is gone: nothing exchanges any more.
        long calls = graph.exchangeCount();
        for (int t = 0; t < 20; t++) {
            graph.exchange();
        }
        assertEquals(calls, graph.exchangeCount());

        // A later trigger zones the cells again.
        Zone again = graph.createZone(ROOM);
        assertTrue(again.isAlive());
        assertEquals(0, again.mixture().totalMoles(), 0, "the cells bring the vacuum");
        assertNotNull(again.ambientEdge());
        assertConsistent(graph, grid);
    }

    @Test
    void core_076_zone_near_standard_air_dissolves() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, ambient);
        Zone room = graph.createZone(ROOM);
        // Inside tolerance: 2 % more pressure, 1 K warmer, a little more oxygen.
        GasMixture near = air(103.3, CELL, ROOM_T + 1);
        near.setMoles(O2, near.moles(O2) * 1.01);
        fillWith(graph, room, near);
        double heldN2 = room.mixture().moles(N2);
        graph.beginConservationPeriod();

        graph.exchange();

        assertFalse(room.isAlive());
        for (long c : ROOM) {
            assertNull(graph.zoneOf(c), "the cells hold the ambient again (CORE-050)");
        }
        ConservationReport report = graph.checkConservation(1);
        assertTrue(report.conserved(), report::toString);
        assertEquals(
                -heldN2, report.recorded(LedgerCategory.AMBIENT, N2), 1e-9 * heldN2, "the gas went to the ambient");

        Zone again = graph.createZone(ROOM);
        double scale = again.volume() / ambient.volume();
        assertEquals(ambient.moles(N2) * scale, again.mixture().moles(N2), 1e-9);
        assertConsistent(graph, grid);
    }

    @Test
    void core_076_zone_without_ambient_edge_is_kept() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        // A sealed room: nothing around it is a cell.
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = graph(grid, ambient);
        Zone room = graph.createZone(ROOM);
        assertNull(room.ambientEdge());
        assertTrue(room.mixture().isEquivalent(ambient));
        for (int t = 0; t < 10; t++) {
            graph.exchange();
        }
        assertTrue(room.isAlive());
        assertEquals(0, graph.dissolveCount());
    }

    @Test
    void core_076_zone_differing_in_temperature_at_normal_pressure_is_kept() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, ambient);
        Zone room = graph.createZone(ROOM);
        // Same pressure and nearly the same densities, but 10 K warmer.
        fillWith(graph, room, air(101.325, CELL, ROOM_T + 10));

        graph.exchange();

        assertTrue(room.isAlive(), "temperature is compared at normal pressure");
        assertTrue(room.mixture().temperature() > ROOM_T + 4);
        assertNotNull(room.ambientEdge());
        assertFalse(room.ambientEdge().isSleeping());
    }

    @Test
    void core_076_neighbours_take_over_the_openings() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        // Room A opens to the outside; room B, at z = 2, is joined to A by two doors.
        Grid grid = roomWithOpening().box(0, 0, 2, 1, 0, 2, CELL);
        grid.door(cell(0, 0, 1), cell(0, 0, 2));
        grid.door(cell(1, 0, 1), cell(1, 0, 2));
        ZoneGraph graph = graph(grid, ambient);
        Zone a = graph.createZone(ROOM);
        Zone b = graph.createZone(Grid.cells(0, 0, 2, 1, 0, 2));
        fillWith(graph, b, air(300, CELL, ROOM_T));
        assertNull(b.ambientEdge());
        graph.beginConservationPeriod();

        // A takes gas from B and vents it until it matches the ambient and dissolves.
        int tick = 0;
        while (a.isAlive() && tick < 500) {
            graph.exchange();
            assertTrue(graph.checkConservation(++tick).conserved());
            assertConsistent(graph, grid);
        }
        assertFalse(a.isAlive());
        assertTrue(b.isAlive(), "B had no ambient edge when A dissolved");
        Edge vent = b.ambientEdge();
        assertNotNull(vent, "B now borders the ambient through the doors");
        assertEquals(2, vent.connections());
        assertEquals(0, vent.directConnections());
        assertFalse(vent.isSleeping());

        // B vents through the doors and dissolves in turn.
        while (b.isAlive() && tick < 1000) {
            graph.exchange();
            assertTrue(graph.checkConservation(++tick).conserved());
        }
        assertFalse(b.isAlive());
        assertEquals(2, graph.dissolveCount());
        assertConsistent(graph, grid);
    }

    /**
     * A pinned room venting into a vacuum never dissolves: its ambient edge exchanges every tick,
     * long after its unpinned twin dissolved, and the ledger balances throughout (CORE-076, D-64).
     */
    @Test
    void core_076_d_064_pinned_zone_venting_to_vacuum_never_dissolves() {
        Grid grid = roomWithOpening();
        ZoneGraph pinnedGraph = graph(grid, deepSpace());
        Zone pinned = pinnedGraph.createZone(ROOM);
        fillWith(pinnedGraph, pinned, air(101.325, CELL, ROOM_T));
        pinnedGraph.setPinned(pinned, true);
        assertTrue(pinned.isPinned());
        ZoneGraph twinGraph = graph(grid, deepSpace());
        Zone twin = twinGraph.createZone(ROOM);
        fillWith(twinGraph, twin, air(101.325, CELL, ROOM_T));
        assertFalse(twin.isPinned(), "zones start unpinned");
        pinnedGraph.beginConservationPeriod();
        twinGraph.beginConservationPeriod();

        int twinTicks = 0;
        for (int tick = 1; tick <= 500; tick++) {
            long calls = pinnedGraph.exchangeCount();
            pinnedGraph.exchange();
            assertEquals(calls + 1, pinnedGraph.exchangeCount(), "the pinned zone's ambient edge exchanged");
            ConservationReport report = pinnedGraph.checkConservation(tick);
            assertTrue(report.conserved(), report::toString);
            if (twin.isAlive()) {
                twinGraph.exchange();
                assertTrue(twinGraph.checkConservation(tick).conserved());
                twinTicks = tick;
            }
        }
        assertFalse(twin.isAlive(), "the unpinned twin dissolves");
        assertTrue(twinTicks < 200, "the twin dissolved after " + twinTicks + " ticks");
        assertEquals(1, twinGraph.dissolveCount());

        assertTrue(pinned.isAlive(), "the pinned zone never dissolves");
        assertEquals(0, pinnedGraph.dissolveCount());
        assertEquals(1, pinnedGraph.zoneCount());
        for (long c : ROOM) {
            assertSame(pinned, pinnedGraph.zoneOf(c));
        }
        Edge vent = pinned.ambientEdge();
        assertNotNull(vent);
        assertFalse(vent.isSleeping(), "ambient edges never sleep");
        assertTrue(
                pinned.mixture().pressure() < 1e-6,
                "it vented: " + pinned.mixture().pressure() + " kPa");
        assertConsistent(pinnedGraph, grid);

        // Unpinned, it dissolves at the next exchange.
        pinnedGraph.setPinned(pinned, false);
        pinnedGraph.exchange();
        assertTrue(pinnedGraph.checkConservation(501).conserved());
        assertFalse(pinned.isAlive());
        assertEquals(1, pinnedGraph.dissolveCount());
    }

    /** A pinned room equal to standard air keeps exchanging with it instead of dissolving (D-64). */
    @Test
    void core_076_d_064_pinned_zone_matching_the_ambient_keeps_exchanging() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, ambient);
        Zone room = graph.createZone(ROOM);
        graph.setPinned(room, true);
        graph.beginConservationPeriod();
        for (int tick = 1; tick <= 10; tick++) {
            long calls = graph.exchangeCount();
            graph.exchange();
            assertEquals(calls + 1, graph.exchangeCount());
            assertTrue(graph.checkConservation(tick).conserved());
        }
        assertTrue(room.isAlive());
        assertTrue(room.mixture().isEquivalent(ambient));
        assertEquals(0, graph.dissolveCount());
    }

    /**
     * The pin follows the zone's cells and gas (D-64, CORE-026): a merge keeps it whichever zone
     * survives, and every part of a pinned zone that splits is pinned.
     */
    @Test
    void d_064_merge_and_split_keep_the_pin() {
        // A sealed row of five cells: a pinned zone of two and an unpinned zone of three.
        Grid grid = new Grid().box(0, 0, 0, 4, 0, 0, CELL);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone small = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 0));
        Zone big = graph.createZone(Grid.cells(2, 0, 0, 4, 0, 0));
        graph.setPinned(small, true);
        graph.beginConservationPeriod();

        Zone merged = graph.merge(small, big);
        assertSame(big, merged, "the larger zone keeps its id");
        assertFalse(small.isAlive());
        assertTrue(merged.isPinned(), "the survivor takes the absorbed zone's pin");
        assertTrue(graph.checkConservation(1).conserved());

        // Two unpinned zones merge unpinned.
        ZoneGraph plain = graph(grid, air(101.325, CELL, ROOM_T));
        Zone p = plain.merge(
                plain.createZone(Grid.cells(0, 0, 0, 1, 0, 0)), plain.createZone(Grid.cells(2, 0, 0, 4, 0, 0)));
        assertFalse(p.isPinned());

        // Wall off cells 1 and 3: three parts, one keeping the id, all pinned.
        grid.set(cell(1, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(1, 0, 0)));
        grid.set(cell(3, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(3, 0, 0)));
        assertConsistent(graph, grid);
        List<Zone> parts = new ArrayList<>(graph.zones());
        assertEquals(3, parts.size());
        boolean idKept = false;
        for (Zone part : parts) {
            assertTrue(part.isPinned(), part + " lost the pin");
            idKept |= part == merged;
        }
        assertTrue(idKept, "one part keeps the zone's id");
        assertNotSame(parts.get(0), parts.get(1));
        assertTrue(graph.checkConservation(2).conserved());

        // Unpinning one part leaves the others pinned.
        graph.setPinned(parts.get(0), false);
        assertFalse(parts.get(0).isPinned());
        assertTrue(parts.get(1).isPinned());
    }

    /** A saved pin comes back with the zone's share, and an unpinned share doesn't clear it (D-64, API-018). */
    @Test
    void d_064_load_share_restores_the_pin() {
        Grid grid = new Grid().box(0, 0, 0, 3, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        graph.beginConservationPeriod();
        GasMixture gas = air(101.325, CELL, ROOM_T);
        Zone zone = graph.loadShare(7, Grid.cells(0, 0, 0, 1, 0, 0), gas, true);
        assertTrue(zone.isPinned());
        assertSame(zone, graph.loadShare(7, Grid.cells(2, 0, 0, 3, 0, 0), gas, false));
        assertTrue(zone.isPinned(), "an unpinned share of a pinned zone doesn't unpin it");
        assertNull(graph.loadShare(8, new long[] {}, gas, true), "a share with no cells creates nothing");
        assertTrue(graph.checkConservation(1).conserved());
    }
}
