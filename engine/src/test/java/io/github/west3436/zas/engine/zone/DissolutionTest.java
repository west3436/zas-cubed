package io.github.west3436.zas.engine.zone;

import static io.github.west3436.zas.engine.zone.Grid.cell;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CELL;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CONFIG;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.N2;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.O2;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.REGISTRY;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.ROOM_T;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.TRACE;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.air;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.assertConsistent;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.fillWith;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.graph;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.nitrogen;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
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

    // ---- Sealed zones (CORE-052) -------------------------------------------------------------

    /** A graph whose sealed zones wait 3 s, at the 1 s period. */
    private static ZoneGraph collecting(Grid grid) {
        ZoneGraph graph = graph(grid, air(101.325, CELL, ROOM_T));
        graph.setZoneSettings(ZoneSettings.DEFAULT.withSealedDissolveSeconds(3));
        return graph;
    }

    private static void collect(ZoneGraph graph, int times) {
        for (int i = 0; i < times; i++) {
            graph.collectSealed(id -> false);
        }
    }

    /** Two sealed one-cell rooms joined through a door. */
    private static Grid roomsThroughADoor() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 0, CELL);
        grid.door(cell(0, 0, 0), cell(1, 0, 0));
        return grid;
    }

    @Test
    void core_052_a_sealed_zone_at_the_ambient_dissolves_after_the_delay() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        Zone room = graph.createZone(ROOM);
        assertNull(room.ambientEdge());
        double heldN2 = room.mixture().moles(N2);
        graph.beginConservationPeriod();

        collect(graph, 2);
        assertTrue(room.isAlive(), "2 s isn't 3 s");
        assertEquals(0, graph.dissolveCount());

        collect(graph, 1);
        assertFalse(room.isAlive(), "3 s is");
        assertEquals(1, graph.dissolveCount());
        for (long c : ROOM) {
            assertNull(graph.zoneOf(c), "the cells hold the ambient again (CORE-050)");
        }
        ConservationReport report = graph.checkConservation(1);
        assertTrue(report.conserved(), report::toString);
        assertEquals(
                -heldN2, report.recorded(LedgerCategory.AMBIENT, N2), 1e-9 * heldN2, "the gas went to the ambient");

        Zone again = graph.createZone(ROOM);
        assertEquals(heldN2, again.mixture().moles(N2), 1e-9 * heldN2, "a later trigger zones the cells anew");
        assertConsistent(graph, grid);
    }

    @Test
    void core_052_the_delay_is_in_seconds() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        graph.setSettings(ExchangeSettings.DEFAULT.withPeriod(0.5));
        Zone room = graph.createZone(ROOM);

        collect(graph, 5);
        assertTrue(room.isAlive(), "2.5 s isn't 3 s");
        collect(graph, 1);
        assertFalse(room.isAlive(), "3 s is");
    }

    @Test
    void core_052_with_no_delay_a_sealed_zone_goes_at_the_first_match() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL).box(0, 0, 3, 1, 0, 3, CELL);
        ZoneGraph graph = collecting(grid);
        graph.setZoneSettings(ZoneSettings.DEFAULT.withSealedDissolveSeconds(0));
        Zone matching = graph.createZone(ROOM);
        Zone thick = graph.createZone(Grid.cells(0, 0, 3, 1, 0, 3));
        fillWith(graph, thick, air(150, CELL, ROOM_T));

        collect(graph, 1);

        assertFalse(matching.isAlive());
        assertTrue(thick.isAlive(), "no delay doesn't take a zone that doesn't match");
    }

    @Test
    void core_052_a_zone_that_stops_matching_starts_over() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        Zone room = graph.createZone(ROOM);

        collect(graph, 2);
        fillWith(graph, room, air(150, CELL, ROOM_T));
        collect(graph, 20);
        assertTrue(room.isAlive(), "it doesn't match");

        fillWith(graph, room, ambient);
        collect(graph, 2);
        assertTrue(room.isAlive(), "its time started over");
        collect(graph, 1);
        assertFalse(room.isAlive());
    }

    /**
     * Respiration changes a room's air by far less than the tolerances, and needs a zone to do it
     * (CORE-038), so a zone that keeps changing stays, however close to the ambient it is.
     */
    @Test
    void core_052_a_zone_that_keeps_changing_stays() {
        GasMixture ambient = air(101.325, CELL, ROOM_T);
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        Zone room = graph.createZone(ROOM);
        GasMixture breath = new GasMixture(REGISTRY, CONFIG, CELL, ROOM_T);
        breath.setMoles(O2, 0.001);

        for (int t = 0; t < 20; t++) {
            graph.add(room, breath.copy(), LedgerCategory.LIFE);
            graph.exchange();
            graph.collectSealed(id -> false);
        }
        assertTrue(room.isAlive(), "something is still changing it");
        assertTrue(room.mixture().isEquivalent(ambient), "by less than the tolerances");

        // It has been left alone for 1 s of the 3 s.
        for (int t = 0; t < 2; t++) {
            assertTrue(room.isAlive());
            graph.exchange();
            graph.collectSealed(id -> false);
        }
        assertFalse(room.isAlive());
    }

    @Test
    void core_052_a_pinned_zone_stays_and_waits_the_delay_once_unpinned() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        Zone room = graph.createZone(ROOM);
        graph.pin(room, "test:controller");

        collect(graph, 20);
        assertTrue(room.isAlive(), "nothing pinned dissolves");

        graph.unpinEverywhere("test:controller");
        collect(graph, 2);
        assertTrue(room.isAlive(), "the wait starts when the pin goes");
        collect(graph, 1);
        assertFalse(room.isAlive());
    }

    @Test
    void core_052_a_paused_zone_stays() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        Zone room = graph.createZone(ROOM);
        graph.setPaused(room, true);

        collect(graph, 20);
        assertTrue(room.isAlive(), "a paused zone's reading is only its last-known state");

        graph.setPaused(room, false);
        collect(graph, 2);
        assertTrue(room.isAlive(), "the wait starts when it resumes");
        collect(graph, 1);
        assertFalse(room.isAlive());
    }

    /** The time isn't saved: a zone back from chunk storage waits the whole delay again. */
    @Test
    void core_052_a_zone_back_from_storage_starts_over() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = collecting(grid);
        Zone room = graph.createZone(ROOM);
        long id = room.id();
        collect(graph, 2);

        GasMixture gas = graph.evict(room);
        Zone back = graph.loadShare(id, ROOM, gas, false);
        collect(graph, 2);
        assertTrue(back.isAlive(), "2 s since it came back isn't 3 s");
        collect(graph, 1);
        assertFalse(back.isAlive());
    }

    @Test
    void core_052_a_zone_with_an_ambient_edge_is_left_to_core_076() {
        ZoneGraph graph = collecting(roomWithOpening());
        Zone room = graph.createZone(ROOM);
        assertNotNull(room.ambientEdge());

        collect(graph, 20);

        assertTrue(room.isAlive());
    }

    @Test
    void core_052_a_sealed_zone_in_a_vacuum_dissolves() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = graph(grid, deepSpace());
        graph.setZoneSettings(ZoneSettings.DEFAULT.withSealedDissolveSeconds(3));
        Zone room = graph.createZone(ROOM);

        collect(graph, 3);

        assertFalse(room.isAlive());
    }

    @Test
    void core_052_zones_joined_by_a_door_dissolve_together() {
        Grid grid = roomsThroughADoor();
        ZoneGraph graph = collecting(grid);
        Zone a = graph.createZone(cell(0, 0, 0));
        Zone b = graph.createZone(cell(1, 0, 0));
        graph.beginConservationPeriod();

        collect(graph, 3);

        assertFalse(a.isAlive());
        assertFalse(b.isAlive());
        assertEquals(0, graph.zoneCount());
        assertTrue(graph.edges().isEmpty());
        ConservationReport report = graph.checkConservation(1);
        assertTrue(report.conserved(), report::toString);
        assertConsistent(graph, grid);
    }

    @Test
    void core_052_a_zone_stays_while_a_zone_joined_to_it_cant_go() {
        // Its neighbour would otherwise vent through the room as if the room were the open air.
        for (String reason : List.of("pinned", "paused", "thick", "undecided")) {
            Grid grid = roomsThroughADoor();
            ZoneGraph graph = collecting(grid);
            Zone a = graph.createZone(cell(0, 0, 0));
            Zone b = graph.createZone(cell(1, 0, 0));
            switch (reason) {
                case "pinned" -> graph.pin(b, "test:controller");
                case "paused" -> graph.setPaused(b, true);
                case "thick" -> fillWith(graph, b, air(150, CELL, ROOM_T));
                default -> {}
            }
            for (int i = 0; i < 3; i++) {
                graph.collectSealed(id -> reason.equals("undecided") && id == b.id());
            }
            assertTrue(a.isAlive(), reason);
            assertTrue(b.isAlive(), reason);
            assertNull(a.ambientEdge(), reason);
            assertNull(b.ambientEdge(), reason);
            assertConsistent(graph, grid);
        }
    }

    @Test
    void core_052_zones_joined_by_a_door_wait_for_the_later_one() {
        Grid grid = roomsThroughADoor();
        ZoneGraph graph = collecting(grid);
        Zone a = graph.createZone(cell(0, 0, 0));
        Zone b = graph.createZone(cell(1, 0, 0));
        fillWith(graph, b, air(150, CELL, ROOM_T));
        collect(graph, 3);
        assertTrue(a.isAlive(), "its time is up, but its neighbour doesn't match");

        fillWith(graph, b, air(101.325, CELL, ROOM_T));
        collect(graph, 2);
        assertTrue(a.isAlive(), "its neighbour has matched for 2 s");
        assertTrue(b.isAlive());
        collect(graph, 1);
        assertFalse(a.isAlive());
        assertFalse(b.isAlive());
    }

    // ---- Sealed zones holding a gas past one of its levels (CORE-052) -------------------------

    /** A graph in plain nitrogen whose sealed zones wait 3 s and are kept by {@link ZoneFixtures#TRACE}. */
    private static ZoneGraph guarded(Grid grid) {
        ZoneGraph graph = graph(grid, nitrogen(0));
        graph.setZoneSettings(ZoneSettings.DEFAULT.withSealedDissolveSeconds(3).withGuard(TRACE));
        return graph;
    }

    @Test
    void core_052_a_sealed_zone_holding_a_gas_past_its_level_stays() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = guarded(grid);
        Zone room = graph.createZone(ROOM);
        // Twice what harms, and a thousandth of what the tolerances let through.
        fillWith(graph, room, nitrogen(0.002));
        double held = room.mixture().moles(O2);
        assertTrue(room.mixture().isEquivalent(graph.ambient()), "the tolerances take it for the ambient");

        for (int t = 0; t < 20; t++) {
            graph.exchange();
            graph.collectSealed(id -> false);
        }
        assertTrue(room.isAlive(), "the room is kept well past the 3 s");
        assertEquals(held, room.mixture().moles(O2), 0, "with its gas");
        assertEquals(0, graph.dissolveCount());

        // Once the gas is taken out, the room waits the delay like any other.
        fillWith(graph, room, nitrogen(0));
        collect(graph, 2);
        assertTrue(room.isAlive(), "2 s since the gas went isn't 3 s");
        collect(graph, 1);
        assertFalse(room.isAlive());
        assertConsistent(graph, grid);
    }

    @Test
    void core_052_a_trace_under_every_level_dissolves_on_time() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = guarded(grid);
        Zone room = graph.createZone(ROOM);
        fillWith(graph, room, nitrogen(0.00004));
        double held = room.mixture().moles(O2);
        assertTrue(held > 0);
        graph.beginConservationPeriod();

        collect(graph, 2);
        assertTrue(room.isAlive(), "2 s isn't 3 s");
        collect(graph, 1);
        assertFalse(room.isAlive(), "under half the lowest level, 3 s is");
        ConservationReport report = graph.checkConservation(1);
        assertTrue(report.conserved(), report::toString);
        assertEquals(-held, report.recorded(LedgerCategory.AMBIENT, O2), 1e-9 * held, "the trace went to the ambient");
    }

    @Test
    void core_052_a_sealed_zone_at_the_ambient_dissolves_on_time_whatever_the_levels() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = guarded(grid);
        Zone room = graph.createZone(ROOM);

        collect(graph, 2);
        assertTrue(room.isAlive(), "2 s isn't 3 s");
        collect(graph, 1);
        assertFalse(room.isAlive(), "3 s is");
    }

    @Test
    void core_052_a_zone_joined_to_a_kept_zone_stays() {
        Grid grid = roomsThroughADoor();
        ZoneGraph graph = guarded(grid);
        Zone clean = graph.createZone(cell(0, 0, 0));
        Zone foul = graph.createZone(cell(1, 0, 0));
        fillWith(graph, foul, nitrogen(0.002));

        collect(graph, 20);

        assertTrue(foul.isAlive());
        assertTrue(clean.isAlive(), "dissolved, it would be open air through the foul room's door");
        assertConsistent(graph, grid);
    }

    @Test
    void core_052_a_zone_cleaner_than_the_ambient_stays() {
        Grid grid = new Grid().box(0, 0, 0, 1, 0, 1, CELL);
        ZoneGraph graph = graph(grid, nitrogen(0.002));
        graph.setZoneSettings(ZoneSettings.DEFAULT.withSealedDissolveSeconds(3).withGuard(TRACE));
        Zone room = graph.createZone(ROOM);
        collect(graph, 2);
        // Scrubbed: dissolving would bring back what the open air holds.
        fillWith(graph, room, nitrogen(0));
        assertTrue(room.mixture().isEquivalent(graph.ambient()));

        collect(graph, 20);

        assertTrue(room.isAlive());
    }

    @Test
    void core_052_gas_levels_for_other_gases_are_refused() {
        ZoneGraph graph = collecting(new Grid().box(0, 0, 0, 1, 0, 1, CELL));
        GasRegistry other = GasRegistry.of(List.of(ZoneFixtures.gas("test:toxin", 0.071, 33.9)));
        DissolveGuard foreign =
                DissolveGuard.builder(other).level(0, 0.001, false).build();

        assertThrows(
                IllegalArgumentException.class, () -> graph.setZoneSettings(ZoneSettings.DEFAULT.withGuard(foreign)));
        graph.setZoneSettings(ZoneSettings.DEFAULT.withGuard(DissolveGuard.NONE));
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

    // ---- Opened zones holding a gas past one of its levels (CORE-076) -------------------------

    /**
     * A room opened to the ambient with a harmful trace isn't dissolved for matching it. It goes on
     * venting, the trace falling by the venting ratio each tick, and dissolves once the trace is
     * under its lowest level. All of the trace goes to the ambient, in the ledger. Without the
     * levels the tolerances lose it in one exchange.
     */
    @Test
    void core_076_an_opened_zone_holding_a_gas_past_its_level_vents_it_over_time() {
        Grid grid = roomWithOpening();
        ZoneGraph graph = guarded(grid);
        Zone room = graph.createZone(ROOM);
        // Twice what harms, and a thousandth of what the tolerances let through.
        fillWith(graph, room, nitrogen(0.002));
        double held = room.mixture().moles(O2);
        assertTrue(room.mixture().isEquivalent(graph.ambient()), "the tolerances take it for the ambient");
        double r = ExchangeSettings.DEFAULT.ambientRatio(room.ambientEdge().connections(), room.volume());
        // The first exchange after which 0.002 kPa is under 0.00005 kPa, a fortieth of it.
        int expected = (int) Math.floor(Math.log(40) / -Math.log(1 - r)) + 1;
        graph.beginConservationPeriod();

        int ticks = 0;
        double last = held;
        while (room.isAlive() && ticks < 10 * expected) {
            graph.exchange();
            ticks++;
            if (room.isAlive()) {
                double now = room.mixture().moles(O2);
                assertEquals(last * (1 - r), now, 1e-9 * held, "exchange " + ticks + " left its share");
                assertTrue(room.mixture().partialPressure(O2) >= 0.00005, "it stays while at its lowest level");
                last = now;
            }
        }

        assertFalse(room.isAlive(), "under its lowest level it dissolves");
        assertEquals(expected, ticks, "the time venting takes");
        assertTrue(ticks > 10, "over time, not at once");
        ConservationReport report = graph.checkConservation(ticks);
        assertTrue(report.conserved(), report::toString);
        assertEquals(-held, report.recorded(LedgerCategory.AMBIENT, O2), 1e-9 * held, "all of it went to the ambient");
        for (long c : ROOM) {
            assertNull(graph.zoneOf(c));
        }
        assertConsistent(graph, grid);

        ZoneGraph plain = graph(roomWithOpening(), nitrogen(0));
        Zone lost = plain.createZone(ROOM);
        fillWith(plain, lost, nitrogen(0.002));
        plain.exchange();
        assertFalse(lost.isAlive(), "with no levels the tolerances lose it in one exchange");
    }

    @Test
    void core_076_an_opened_zone_with_a_trace_under_every_level_dissolves_at_once() {
        Grid grid = roomWithOpening();
        ZoneGraph graph = guarded(grid);
        Zone room = graph.createZone(ROOM);
        fillWith(graph, room, nitrogen(0.00004));
        double held = room.mixture().moles(O2);
        assertTrue(held > 0);
        graph.beginConservationPeriod();

        graph.exchange();

        assertFalse(room.isAlive(), "under half the lowest level it is the ambient, as before");
        ConservationReport report = graph.checkConservation(1);
        assertTrue(report.conserved(), report::toString);
        assertEquals(-held, report.recorded(LedgerCategory.AMBIENT, O2), 1e-9 * held, "the trace went to the ambient");
    }

    @Test
    void core_076_an_opened_zone_at_the_ambient_dissolves_at_once_whatever_the_levels() {
        Grid grid = roomWithOpening();
        ZoneGraph graph = guarded(grid);
        Zone room = graph.createZone(ROOM);
        long exchanges = graph.exchangeCount();

        graph.exchange();

        assertFalse(room.isAlive());
        assertEquals(exchanges, graph.exchangeCount(), "nothing was exchanged first");
    }
}
