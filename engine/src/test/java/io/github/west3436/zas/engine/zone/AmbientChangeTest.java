package io.github.west3436.zas.engine.zone;

import static io.github.west3436.zas.engine.zone.Grid.cell;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CELL;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.REGISTRY;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.ROOM_T;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.air;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.fillWith;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.graph;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.gas.GasMixture;
import org.junit.jupiter.api.Test;

/**
 * What a changed ambient does to edges: it adds no work (ENV-017), and the edges that exchange with
 * the ambient use the new one from the next exchange (ENV-006).
 *
 * <p>Ambient edges don't sleep (D-63, D-64): a zone that matches its ambient dissolves unless pinned,
 * and a pinned one keeps exchanging. So the only edges that can sleep are between zones, and they
 * have nothing to do with the ambient.
 */
class AmbientChangeTest {
    /** Ambients from a change too small to matter to one that changes everything. */
    private static final GasMixture[] CHANGES = {
        air(101.325, CELL, ROOM_T + 0.01),
        air(101.4, CELL, ROOM_T + 0.1),
        air(101.4, CELL, ROOM_T + 100),
        air(40, CELL, 200),
        vacuum(),
        air(300, CELL, 1000)
    };

    /** A 2×1×2 room with one face open to an outside cell at x = 2. */
    private static Grid roomWithOpening() {
        return new Grid().box(0, 0, 0, 1, 0, 1, CELL).box(2, 0, 0, 2, 0, 0, CELL);
    }

    /** Two rows of three cells joined by one door at x = 0. */
    private static Grid twoRoomsWithADoor() {
        Grid grid = new Grid().box(0, 0, 0, 2, 0, 1, CELL);
        grid.door(cell(0, 0, 0), cell(0, 0, 1));
        grid.close(cell(1, 0, 0), cell(1, 0, 1));
        grid.close(cell(2, 0, 0), cell(2, 0, 1));
        return grid;
    }

    private static double[] moles(Zone zone) {
        double[] m = new double[REGISTRY.size()];
        for (int gas = 0; gas < m.length; gas++) {
            m[gas] = zone.mixture().moles(gas);
        }
        return m;
    }

    /** A door edge asleep between two zones stays asleep, however far the ambient moves. */
    @Test
    void env_017_changing_the_ambient_wakes_no_sleeping_edge() {
        ZoneGraph graph = graph(twoRoomsWithADoor(), air(101.325, CELL, ROOM_T));
        Zone a = graph.createZone(Grid.cells(0, 0, 0, 2, 0, 0));
        Zone b = graph.createZone(Grid.cells(0, 0, 1, 2, 0, 1));
        fillWith(graph, a, air(101, CELL, ROOM_T));
        fillWith(graph, b, air(100, CELL, ROOM_T));
        Edge edge = graph.edgeBetween(a, b);
        assertNotNull(edge);
        graph.exchange();
        assertTrue(edge.isSleeping());

        long calls = graph.exchangeCount();
        long equalized = graph.equalizeCount();
        double[] molesA = moles(a);
        double[] molesB = moles(b);
        for (GasMixture ambient : CHANGES) {
            graph.setAmbient(ambient);
            graph.exchange();
            assertTrue(edge.isSleeping(), "the edge woke at " + ambient);
            assertEquals(calls, graph.exchangeCount(), "an exchange at " + ambient);
            assertEquals(equalized, graph.equalizeCount(), "an equalization at " + ambient);
            assertArrayEquals(molesA, moles(a), "the first zone's gas at " + ambient);
            assertArrayEquals(molesB, moles(b), "the second zone's gas at " + ambient);
        }
    }

    /** A sealed zone has no ambient edge, so a changed ambient gives it no exchange and leaves it be. */
    @Test
    void env_017_changing_the_ambient_adds_no_exchange_to_a_sealed_zone() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        Zone room = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        fillWith(graph, room, air(90, CELL, 280));
        double[] before = moles(room);
        double temperature = room.mixture().temperature();
        graph.exchange();
        long calls = graph.exchangeCount();
        for (GasMixture ambient : CHANGES) {
            graph.setAmbient(ambient);
            graph.exchange();
            assertEquals(calls, graph.exchangeCount(), "an exchange at " + ambient);
            assertArrayEquals(before, moles(room), "the gas at " + ambient);
            assertEquals(temperature, room.mixture().temperature(), "the temperature at " + ambient);
        }
    }

    /** An ambient edge exchanges once a tick whether the ambient holds still or changes every tick. */
    @Test
    void env_017_an_ambient_edge_exchanges_once_a_tick_either_way() {
        ZoneGraph graph = graph(roomWithOpening(), air(101.325, CELL, ROOM_T));
        Zone room = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        graph.setPinned(room, true);
        for (int tick = 0; tick < 5; tick++) {
            long calls = graph.exchangeCount();
            graph.exchange();
            assertEquals(calls + 1, graph.exchangeCount(), "calls with a still ambient");
        }
        for (GasMixture ambient : CHANGES) {
            graph.setAmbient(ambient);
            long calls = graph.exchangeCount();
            graph.exchange();
            assertEquals(calls + 1, graph.exchangeCount(), "calls at " + ambient);
        }
    }

    /** A zone's ambient edge moves it toward the new ambient at the very next exchange. */
    @Test
    void env_006_an_ambient_edge_uses_the_new_ambient_from_the_next_exchange() {
        ZoneGraph graph = graph(roomWithOpening(), air(101.325, CELL, ROOM_T));
        Zone room = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 1));
        graph.setPinned(room, true);
        graph.exchange();
        assertEquals(ROOM_T, room.mixture().temperature(), 1e-9);

        graph.setAmbient(air(101.325, CELL, ROOM_T + 50));
        graph.exchange();
        double warmer = room.mixture().temperature();
        assertTrue(warmer > ROOM_T + 0.01 && warmer < ROOM_T + 50, "the room is at " + warmer);

        graph.setAmbient(air(50, CELL, ROOM_T + 50));
        double pressure = room.mixture().pressure();
        graph.exchange();
        assertTrue(room.mixture().pressure() < pressure, "the room didn't vent toward a thinner ambient");
    }
}
