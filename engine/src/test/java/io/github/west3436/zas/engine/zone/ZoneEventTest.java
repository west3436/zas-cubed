package io.github.west3436.zas.engine.zone;

import static io.github.west3436.zas.engine.zone.Grid.cell;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CELL;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.ROOM_T;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.air;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.fillWith;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.graph;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.zone.ZoneEvent.Kind;
import java.util.List;
import java.util.function.LongPredicate;
import org.junit.jupiter.api.Test;

/**
 * What the graph reports of a zone's life (API-030): each change once, by every way to reach it. The
 * GameTests in {@code ZoneEventTests} see the events fired.
 */
class ZoneEventTest {
    private static final long[] ROOM = Grid.cells(0, 0, 0, 1, 0, 1);
    private static final long INSIDE = cell(1, 0, 0);
    private static final long OUTSIDE = cell(2, 0, 0);
    private static final LongPredicate DECIDED = id -> false;

    private static List<Kind> kinds(ZoneGraph graph) {
        return graph.drainZoneEvents().stream().map(ZoneEvent::kind).toList();
    }

    /** A 2×1×2 room with one face open to an outside cell at x = 2. */
    private static Grid roomWithOpening() {
        return new Grid().box(0, 0, 0, 1, 0, 1, CELL).box(2, 0, 0, 2, 0, 0, CELL);
    }

    private static Grid sealedRoomBesideOutside() {
        Grid grid = roomWithOpening();
        grid.close(INSIDE, OUTSIDE);
        return grid;
    }

    private static void open(ZoneGraph graph, Grid grid) {
        grid.open(INSIDE, OUTSIDE);
        graph.refresh(INSIDE);
    }

    private static void shut(ZoneGraph graph, Grid grid) {
        grid.close(INSIDE, OUTSIDE);
        graph.refresh(INSIDE);
    }

    @Test
    void api_030_a_new_zone_reports_created_once() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 1, 0, 1, CELL), vacuum());
        Zone room = graph.createZone(ROOM);

        List<ZoneEvent> events = graph.drainZoneEvents();
        assertEquals(1, events.size());
        ZoneEvent created = events.get(0);
        assertEquals(Kind.CREATED, created.kind());
        assertEquals(room.id(), created.zoneId());
        assertEquals(0, created.otherId());
        assertSame(room, graph.zoneOf(created.cell()));
        assertFalse(created.undoable());
        assertEquals(List.of(), kinds(graph), "told once");
    }

    @Test
    void api_030_a_merge_reports_survivor_and_absorbed_and_no_dissolution() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 4, 0, 0, CELL), vacuum());
        Zone small = graph.createZone(Grid.cells(0, 0, 0, 1, 0, 0));
        Zone big = graph.createZone(Grid.cells(2, 0, 0, 4, 0, 0));
        long absorbed = small.id();
        graph.drainZoneEvents();

        Zone merged = graph.merge(small, big);

        List<ZoneEvent> events = graph.drainZoneEvents();
        assertEquals(1, events.size());
        ZoneEvent event = events.get(0);
        assertEquals(Kind.MERGED, event.kind());
        assertEquals(merged.id(), event.zoneId());
        assertEquals(absorbed, event.otherId());
        assertSame(merged, graph.zoneOf(event.cell()));
    }

    @Test
    void api_030_a_split_reports_the_remnant_once_per_cut_and_no_creation() {
        Grid grid = new Grid().box(0, 0, 0, 4, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        Zone zone = graph.createZone(Grid.cells(0, 0, 0, 4, 0, 0));
        graph.drainZoneEvents();

        grid.set(cell(1, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(1, 0, 0)));

        List<ZoneEvent> events = graph.drainZoneEvents();
        assertEquals(1, events.size());
        ZoneEvent split = events.get(0);
        assertEquals(Kind.SPLIT, split.kind());
        assertEquals(zone.id(), split.zoneId());
        assertSame(zone, graph.zoneOf(split.cell()));
        assertNotNull(graph.zone(split.otherId()));
        assertEquals(2, graph.zoneCount());

        grid.set(cell(3, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(3, 0, 0)));
        assertEquals(List.of(Kind.SPLIT), kinds(graph), "the second cut");
        assertEquals(3, graph.zoneCount());
    }

    @Test
    void api_030_breached_and_sealed_report_once_each() {
        Grid grid = sealedRoomBesideOutside();
        ZoneGraph graph = graph(grid, vacuum());
        Zone room = graph.createZone(ROOM);
        graph.drainZoneEvents();

        graph.reportBreaches(DECIDED);
        assertEquals(List.of(), kinds(graph), "sealed from the start");

        open(graph, grid);
        assertNotNull(room.ambientEdge());
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(Kind.BREACHED), kinds(graph));
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(), kinds(graph), "told once");

        shut(graph, grid);
        assertNull(room.ambientEdge());
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(Kind.SEALED), kinds(graph));
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(), kinds(graph), "told once");
    }

    @Test
    void api_030_a_zone_formed_with_an_opening_reports_no_breach() {
        Grid grid = roomWithOpening();
        ZoneGraph graph = graph(grid, vacuum());
        Zone room = graph.createZone(ROOM);
        assertNotNull(room.ambientEdge());

        graph.reportBreaches(DECIDED);
        assertEquals(List.of(Kind.CREATED), kinds(graph));

        shut(graph, grid);
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(Kind.SEALED), kinds(graph), "it did lose the opening it formed with");
    }

    /** An opening counts as ambient until classified (GEO-026): it isn't news until it's known. */
    @Test
    void api_030_an_undecided_opening_reports_nothing_until_it_is_known() {
        Grid grid = sealedRoomBesideOutside();
        ZoneGraph graph = graph(grid, vacuum());
        graph.createZone(ROOM);
        graph.drainZoneEvents();
        graph.reportBreaches(DECIDED);

        open(graph, grid);
        graph.reportBreaches(id -> true);
        assertEquals(List.of(), kinds(graph), "still being classified");

        shut(graph, grid);
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(), kinds(graph), "it shut before anyone knew");

        open(graph, grid);
        graph.reportBreaches(id -> true);
        assertEquals(List.of(), kinds(graph));
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(Kind.BREACHED), kinds(graph), "once it's known");
    }

    @Test
    void api_030_a_paused_zone_reports_its_breach_once_it_resumes() {
        Grid grid = sealedRoomBesideOutside();
        ZoneGraph graph = graph(grid, vacuum());
        Zone room = graph.createZone(ROOM);
        graph.drainZoneEvents();
        graph.reportBreaches(DECIDED);

        graph.setPaused(room, true);
        open(graph, grid);
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(), kinds(graph));

        graph.setPaused(room, false);
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(Kind.BREACHED), kinds(graph));
    }

    /** A remnant carries on from what its zone last reported: only the part that lost the opening is news. */
    @Test
    void api_030_a_remnant_reports_only_what_differs_from_its_zone() {
        Grid grid = new Grid().box(0, 0, 0, 3, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        graph.createZone(Grid.cells(0, 0, 0, 2, 0, 0));
        graph.reportBreaches(DECIDED);
        graph.drainZoneEvents();

        grid.set(cell(1, 0, 0), 0);
        graph.refreshAll(Grid.around(cell(1, 0, 0)));
        assertEquals(List.of(Kind.SPLIT), kinds(graph));

        graph.reportBreaches(DECIDED);
        List<ZoneEvent> events = graph.drainZoneEvents();
        assertEquals(1, events.size(), "the part still open is no news");
        assertEquals(Kind.SEALED, events.get(0).kind());
        assertEquals(graph.zoneOf(cell(0, 0, 0)).id(), events.get(0).zoneId());
    }

    @Test
    void api_030_dissolving_at_an_opening_reports_once() {
        ZoneGraph graph = graph(roomWithOpening(), air(101.325, CELL, ROOM_T));
        Zone room = graph.createZone(ROOM);
        long id = room.id();
        graph.drainZoneEvents();

        graph.exchange();

        assertFalse(room.isAlive());
        List<ZoneEvent> events = graph.drainZoneEvents();
        assertEquals(1, events.size());
        assertEquals(Kind.DISSOLVED, events.get(0).kind());
        assertEquals(id, events.get(0).zoneId());
        assertEquals(0, events.get(0).otherId());
    }

    @Test
    void api_030_dissolving_sealed_reports_once() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 1, 0, 1, CELL), air(101.325, CELL, ROOM_T));
        graph.setZoneSettings(ZoneSettings.DEFAULT.withSealedDissolveSeconds(1));
        Zone room = graph.createZone(ROOM);
        graph.drainZoneEvents();

        graph.collectSealed(DECIDED);

        assertFalse(room.isAlive());
        assertEquals(List.of(Kind.DISSOLVED), kinds(graph));
    }

    @Test
    void api_030_a_reset_reports_dissolved_once() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 1, 0, 1, CELL), vacuum());
        Zone room = graph.createZone(ROOM);
        graph.drainZoneEvents();

        graph.reset(room, "test");

        assertEquals(List.of(Kind.DISSOLVED), kinds(graph));
    }

    /** A zone's last cell filled, then one whose last cell left: the event still names a cell it had. */
    @Test
    void api_030_losing_the_last_cell_reports_dissolved_once() {
        long only = cell(0, 0, 0);
        Grid grid = new Grid().box(0, 0, 0, 0, 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum());
        graph.createZone(only);
        graph.drainZoneEvents();
        grid.set(only, 0);
        graph.refresh(only);
        List<ZoneEvent> filled = graph.drainZoneEvents();
        assertEquals(
                List.of(Kind.DISSOLVED), filled.stream().map(ZoneEvent::kind).toList());
        assertEquals(only, filled.get(0).cell());

        grid.set(only, CELL);
        graph.refresh(only);
        graph.createZone(only);
        graph.drainZoneEvents();
        graph.leave(only);
        List<ZoneEvent> left = graph.drainZoneEvents();
        assertEquals(List.of(Kind.DISSOLVED), left.stream().map(ZoneEvent::kind).toList());
        assertEquals(only, left.get(0).cell());
    }

    @Test
    void api_030_paused_and_resumed_report_once_each() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 1, 0, 1, CELL), vacuum());
        Zone room = graph.createZone(ROOM);
        graph.drainZoneEvents();
        graph.reportPaused();
        assertEquals(List.of(), kinds(graph), "running from the start");

        graph.setPaused(room, true);
        graph.reportPaused();
        assertEquals(List.of(Kind.PAUSED), kinds(graph));
        graph.reportPaused();
        assertEquals(List.of(), kinds(graph), "told once");

        graph.setPaused(room, false);
        graph.reportPaused();
        assertEquals(List.of(Kind.RESUMED), kinds(graph));
        graph.reportPaused();
        assertEquals(List.of(), kinds(graph), "told once");
    }

    /** A zone stored whole is paused, not dissolved, and comes back resumed, not created. */
    @Test
    void api_030_going_to_storage_and_back_is_paused_and_resumed() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 1, 0, 1, CELL), vacuum());
        Zone room = graph.createZone(ROOM);
        long id = room.id();
        graph.drainZoneEvents();

        GasMixture gas = graph.evict(room);
        assertEquals(List.of(Kind.PAUSED), kinds(graph));

        graph.loadShare(id, ROOM, gas, false);
        assertEquals(List.of(), kinds(graph), "loaded, not created");
        graph.reportPaused();
        assertEquals(List.of(Kind.RESUMED), kinds(graph));
    }

    @Test
    void api_030_a_zone_paused_before_it_goes_to_storage_reports_paused_once() {
        ZoneGraph graph = graph(new Grid().box(0, 0, 0, 1, 0, 1, CELL), vacuum());
        Zone room = graph.createZone(ROOM);
        graph.drainZoneEvents();
        graph.setPaused(room, true);
        graph.reportPaused();
        assertEquals(List.of(Kind.PAUSED), kinds(graph));

        graph.evict(room);

        assertEquals(List.of(), kinds(graph));
    }

    /** What a rejected component restores never went away, and its opening needs no new report. */
    @Test
    void api_030_a_restored_zone_reports_nothing() {
        ZoneGraph graph = graph(roomWithOpening(), vacuum());
        Zone room = graph.createZone(ROOM);
        fillWith(graph, room, air(200, CELL, ROOM_T));
        graph.reportBreaches(DECIDED);
        long id = room.id();
        long[] cells = room.cells();
        GasMixture held = room.mixture().copy();
        graph.reset(room, "test");
        graph.drainZoneEvents();

        graph.restoreZone(id, cells, held, List.of());
        assertEquals(List.of(), kinds(graph));
        graph.reportBreaches(DECIDED);
        assertEquals(List.of(), kinds(graph));
    }
}
