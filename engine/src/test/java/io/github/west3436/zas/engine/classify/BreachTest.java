package io.github.west3436.zas.engine.classify;

import static io.github.west3436.zas.engine.classify.TestWorld.AIR;
import static io.github.west3436.zas.engine.classify.TestWorld.DOOR;
import static io.github.west3436.zas.engine.classify.TestWorld.air;
import static io.github.west3436.zas.engine.classify.TestWorld.cell;
import static io.github.west3436.zas.engine.classify.TestWorld.perLitre;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.zone.Edge;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreachTest {
    private static final double EPS = 1e-9;
    private static final GasMixture AMBIENT = air(101.325);

    /** Creates the zone of the enclosed room around {@code seed} and raises it to 200 kPa. */
    private static Zone zoneAt(ZoneGraph graph, SectionTopology t, long seed) {
        FillResult.Enclosed room = (FillResult.Enclosed) new EnclosureFill().fill(t, seed);
        List<Zone> zones = EnclosureZones.create(graph, t, room.cells());
        assertEquals(1, zones.size());
        Zone zone = zones.get(0);
        GasMixture scaled = new GasMixture(TestWorld.REGISTRY, TestWorld.CONFIG, zone.volume(), TestWorld.ROOM_T);
        scaled.setMoles(0, (200 - 101.325) * zone.volume() / (GasConstants.R * TestWorld.ROOM_T));
        graph.add(zone, scaled, LedgerCategory.API);
        return zone;
    }

    /** Opens a wall block and refreshes it and its neighbours, as the scheduler does for a block change. */
    private static long[] breakWall(TestWorld world, ZoneGraph graph, int x, int y, int z) {
        world.set(x, y, z, AIR);
        long[] changed = {
            cell(x, y, z),
            cell(x - 1, y, z),
            cell(x + 1, y, z),
            cell(x, y - 1, z),
            cell(x, y + 1, z),
            cell(x, y, z - 1),
            cell(x, y, z + 1)
        };
        graph.refreshAll(changed);
        return changed;
    }

    @Test
    void geo_023_breach_into_enclosed_pocket() {
        // Zone A (27 cells), a wall, and a sealed unzoned room B (27 cells).
        TestWorld world = new TestWorld().box(0, 0, 0, 2, 2, 2, AIR).box(4, 0, 0, 6, 2, 2, AIR);
        SectionTopology t = world.topology();
        ZoneGraph graph = TestWorld.graph(t, AMBIENT);
        Zone a = zoneAt(graph, t, cell(0, 0, 0));
        double before = a.mixture().totalMoles();

        long[] changed = breakWall(world, graph, 3, 1, 1);
        long[] seeds = BreachResolver.breachSeeds(graph, t, changed);
        assertArrayEquals(new long[] {cell(3, 1, 1)}, seeds);

        BreachResolver resolver = new BreachResolver(new EnclosureFill());
        List<Breach> breaches = resolver.classify(graph, t, seeds);
        assertEquals(1, breaches.size());
        Breach.Pocket pocket = assertInstanceOf(Breach.Pocket.class, breaches.get(0));
        assertArrayEquals(new long[] {a.id()}, pocket.zones());
        assertEquals(28, pocket.region().cells().length, "the opened wall block and room B");
        assertEquals(27, a.cellCount(), "classifying changes nothing");

        List<Zone> joined = BreachResolver.apply(graph, t, pocket);
        assertEquals(List.of(a), joined);
        assertEquals(1, graph.zoneCount(), "one zone results");
        assertEquals(55, a.cellCount());
        assertSame(a, graph.zoneOf(cell(6, 2, 2)));
        assertNull(a.ambientEdge(), "nothing opens to the ambient");
        double share = perLitre(AMBIENT) * 28 * Geometry.FULL_VOLUME;
        assertEquals(before + share, a.mixture().totalMoles(), EPS * before, "the zone's moles plus B's ambient share");
    }

    @Test
    void geo_023_pocket_behind_a_door_forms_its_own_zone() {
        // Room B has an open door into room C. Gas crosses the door, but zones don't.
        TestWorld world = new TestWorld()
                .box(0, 0, 0, 2, 2, 2, AIR)
                .box(4, 0, 0, 6, 2, 2, AIR)
                .box(8, 0, 0, 9, 2, 2, AIR)
                .set(7, 1, 1, DOOR);
        SectionTopology t = world.topology();
        ZoneGraph graph = TestWorld.graph(t, AMBIENT);
        Zone a = zoneAt(graph, t, cell(0, 0, 0));

        long[] seeds = BreachResolver.breachSeeds(graph, t, breakWall(world, graph, 3, 1, 1));
        List<Breach> breaches = new BreachResolver(new EnclosureFill()).classify(graph, t, seeds);
        Breach.Pocket pocket = assertInstanceOf(Breach.Pocket.class, breaches.get(0));
        assertEquals(28 + 1 + 18, pocket.region().cells().length, "wall, B, the door and C");

        List<Zone> zones = BreachResolver.apply(graph, t, pocket);
        // A takes B; the door block, whose openings are all zone boundaries, and C become zones.
        assertEquals(3, zones.size());
        assertSame(a, zones.get(0));
        assertEquals(55, a.cellCount());
        Zone door = graph.zoneOf(cell(7, 1, 1));
        Zone c = graph.zoneOf(cell(9, 2, 2));
        assertEquals(List.of(a, door, c), zones);
        assertEquals(18, c.cellCount());
        assertEquals(0, a.edgeTo(door).directConnections(), "zones meet across the door");
        assertEquals(0, door.edgeTo(c).directConnections());
        assertNull(a.ambientEdge(), "nothing exchanges with the ambient");
        assertNull(door.ambientEdge());
        assertNull(c.ambientEdge());
    }

    @Test
    void geo_025_breach_to_the_open() {
        // Zone A beside a region bigger than the bound.
        TestWorld world = new TestWorld().box(0, 0, 0, 2, 2, 2, AIR).box(4, 0, 0, 13, 2, 9, AIR);
        SectionTopology t = world.topology();
        ZoneGraph graph = TestWorld.graph(t, AMBIENT);
        Zone a = zoneAt(graph, t, cell(0, 0, 0));
        assertNull(a.ambientEdge());

        long[] seeds = BreachResolver.breachSeeds(graph, t, breakWall(world, graph, 3, 1, 1));
        BreachResolver resolver = new BreachResolver(new EnclosureFill(100));
        List<Breach> breaches = resolver.classify(graph, t, seeds);
        assertEquals(1, breaches.size());
        Breach.Vent vent = assertInstanceOf(Breach.Vent.class, breaches.get(0));
        assertEquals(FillResult.Open.Reason.BOUND, vent.reason());
        assertArrayEquals(new long[] {a.id()}, vent.zones());
        assertEquals(List.of(), BreachResolver.apply(graph, t, vent));

        assertEquals(27, a.cellCount(), "the open region stays unzoned");
        Edge edge = a.ambientEdge();
        assertNotNull(edge, "the opening is an ambient edge");
        assertEquals(1, edge.connections());
        double start = a.mixture().pressure();
        for (int i = 0; i < 20; i++) {
            graph.exchange();
        }
        double end = a.mixture().pressure();
        assertTrue(end < start, "the room vents: " + start + " -> " + end);
        assertTrue(end >= AMBIENT.pressure() - EPS);
    }

    @Test
    void geo_026_breach_into_an_unresolved_region() {
        // Zone A, a wall, and a corridor into a section the worker doesn't have.
        TestWorld world = new TestWorld().box(0, 0, 0, 2, 2, 2, AIR).box(4, 1, 1, 20, 1, 1, AIR);
        long far = SectionKeys.section(1, 0, 0);
        short[] saved = world.save(far);
        world.forget(far);
        SectionTopology t = world.topology();
        ZoneGraph graph = TestWorld.graph(t, AMBIENT);
        Zone a = zoneAt(graph, t, cell(0, 0, 0));
        double moles = a.mixture().totalMoles();

        long[] seeds = BreachResolver.breachSeeds(graph, t, breakWall(world, graph, 3, 1, 1));
        BreachResolver resolver = new BreachResolver(new EnclosureFill());
        Breach.Hold hold = assertInstanceOf(
                Breach.Hold.class, resolver.classify(graph, t, seeds).get(0));
        assertArrayEquals(new long[] {far}, hold.missingSections());
        assertArrayEquals(new long[] {a.id()}, hold.zones(), "the zone to pause");
        assertEquals(List.of(), BreachResolver.apply(graph, t, hold));
        assertEquals(27, a.cellCount());
        assertEquals(moles, a.mixture().totalMoles(), "held: nothing joins");

        // The section arrives: the held seeds classify again, and the region joins.
        world.restore(far, saved);
        Breach.Pocket pocket = assertInstanceOf(
                Breach.Pocket.class, resolver.classify(graph, t, hold.seeds()).get(0));
        BreachResolver.apply(graph, t, pocket);
        assertEquals(27 + 1 + 17, a.cellCount());
        assertFalse(graph.zoneOf(cell(20, 1, 1)) == null);
        assertNull(a.ambientEdge());
    }

    @Test
    void geo_023_seeds_of_one_region_classify_once() {
        // Two openings into the same pocket, and one into a second pocket.
        TestWorld world = new TestWorld()
                .box(0, 0, 0, 2, 2, 2, AIR)
                .box(4, 0, 0, 6, 2, 2, AIR)
                .box(0, 0, 4, 2, 2, 6, AIR);
        SectionTopology t = world.topology();
        ZoneGraph graph = TestWorld.graph(t, AMBIENT);
        Zone a = zoneAt(graph, t, cell(0, 0, 0));
        long[] c1 = breakWall(world, graph, 3, 0, 0);
        long[] c2 = breakWall(world, graph, 3, 2, 2);
        long[] c3 = breakWall(world, graph, 1, 1, 3);
        long[] changed = new long[c1.length + c2.length + c3.length];
        System.arraycopy(c1, 0, changed, 0, c1.length);
        System.arraycopy(c2, 0, changed, c1.length, c2.length);
        System.arraycopy(c3, 0, changed, c1.length + c2.length, c3.length);
        long[] seeds = BreachResolver.breachSeeds(graph, t, changed);
        assertEquals(3, seeds.length);

        List<Breach> breaches = new BreachResolver(new EnclosureFill()).classify(graph, t, seeds);
        assertEquals(2, breaches.size());
        assertEquals(1, breaches.get(0).seeds().length, "the opening into C, the lowest seed");
        assertEquals(2, breaches.get(1).seeds().length, "both openings into B");
        for (Breach b : breaches) {
            BreachResolver.apply(graph, t, b);
        }
        assertEquals(1, graph.zoneCount());
        assertEquals(27 * 3 + 3, a.cellCount());
    }
}
