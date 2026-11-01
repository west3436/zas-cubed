package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.zone.Edge;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Doors between rooms through the whole pipeline (GEO-007, GEO-044, CORE-034, CORE-067): an open
 * door passes gas but keeps the rooms' zones apart, its own cells belong to the room it opens from,
 * and opening or closing it never splits or rebuilds a zone.
 */
class ZoneBoundaryTest {
    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    /** The faces a door keeps zones apart across: where its panel lies closed (west) and open (north). */
    private static final int DOOR_FACES = Geometry.boundaryFlag(Face.WEST) | Geometry.boundaryFlag(Face.NORTH);
    /** A closed door, its panel against the west face, as a vanilla door 3/16 thick. */
    private static final int CLOSED = BUILDER.intern(Geometry.declared(EnumSet.of(Face.WEST), 812.5, DOOR_FACES));
    /** The door open, its panel swung against the north face. */
    private static final int OPEN = BUILDER.intern(Geometry.declared(EnumSet.of(Face.NORTH), 812.5, DOOR_FACES));

    private static final GeometryPalette PALETTE = BUILDER.build();

    /**
     * Rooms in a row along x, 3 × 3 × 3 inside, room {@code i} from x = 4i to 4i + 2, with a
     * two-block door in each wall between them at x = 4i + 3, z = 1.
     */
    private static Sim rooms(int count, int door) {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), PALETTE);
        for (int i = 0; i < count; i++) {
            sim.world.room(4 * i, 64, 0, 4 * i + 2, 66, 2);
        }
        for (int i = 0; i + 1 < count; i++) {
            setDoor(sim, i, door);
        }
        return sim;
    }

    private static void setDoor(Sim sim, int wall, int door) {
        sim.world.set(4 * wall + 3, 64, 1, door);
        sim.world.set(4 * wall + 3, 65, 1, door);
    }

    private static ZoneView room(Sim sim, int i) {
        return sim.zoneAt(4 * i + 1, 65, 1);
    }

    private static boolean allAsleep(ZoneGraph graph) {
        return graph.edges().stream().allMatch(Edge::isSleeping);
    }

    /**
     * Opening a door into an untouched room: the room and the door become a zone of their own,
     * meeting the first room only across the door, and gas passes while the zones stay apart
     * (GEO-007, #107).
     */
    @Test
    void geo_007_open_door_passes_gas_between_separate_zones() {
        Sim sim = rooms(2, CLOSED);
        sim.add(1, 65, 1, 0, 200);
        sim.step();
        ZoneView west = room(sim, 0);
        assertNotNull(west);
        assertEquals(27, west.cellCount(), "the closed door seals the west room");
        assertNull(room(sim, 1), "the east room is untouched");

        setDoor(sim, 0, OPEN);
        sim.step();
        ZoneView east = room(sim, 1);
        assertNotNull(east, "the door opened the east room onto the west one's zone");
        assertNotEquals(west.id(), east.id(), "the door keeps the zones apart");
        assertEquals(27 + 2, east.cellCount(), "the east room and both door blocks: no zone of the door's own");
        assertEquals(east.id(), sim.zoneAt(3, 64, 1).id(), "the door belongs to the room it opens from");
        assertEquals(2, sim.snapshot().zoneCount());
        assertEquals(1, east.zoneEdges());

        double o2 = room(sim, 1).moles(O2) / room(sim, 1).volume();
        sim.steps(5);
        assertTrue(room(sim, 1).moles(O2) / room(sim, 1).volume() > o2, "oxygen crosses the open door");
        assertEquals(west.id(), room(sim, 0).id());
        assertEquals(east.id(), room(sim, 1).id());
        assertEquals(0, sim.engine.graph().mergeCount());
    }

    /**
     * Opening and closing the door between two zoned rooms ten times changes only the door's
     * edge: no split check, no new zone, every cell kept (GEO-044).
     */
    @Test
    void geo_044_toggling_a_door_never_splits_or_rebuilds() {
        Sim sim = rooms(2, OPEN);
        sim.add(1, 65, 1, 0, 200);
        sim.step();
        ZoneView west = room(sim, 0);
        ZoneView east = room(sim, 1);
        assertNotEquals(west.id(), east.id());
        ZoneGraph graph = sim.engine.graph();
        long splitChecks = graph.splitCheckCount();
        long next = graph.nextZoneId();

        for (int toggle = 0; toggle < 20; toggle++) {
            boolean open = toggle % 2 == 1;
            setDoor(sim, 0, open ? OPEN : CLOSED);
            sim.step();
            assertEquals(splitChecks, graph.splitCheckCount(), "toggle " + toggle + " checked for a split");
            assertEquals(0, graph.splitCount());
            assertEquals(next, graph.nextZoneId(), "toggle " + toggle + " made a zone");
            assertEquals(west.id(), room(sim, 0).id());
            assertEquals(east.id(), room(sim, 1).id());
            assertEquals(east.id(), sim.zoneAt(3, 65, 1).id(), "the door stays with its room");
            assertEquals(27, room(sim, 0).cellCount());
            assertEquals(29, room(sim, 1).cellCount());
            assertEquals(open ? 1 : 0, room(sim, 0).zoneEdges(), "only the edge changes");
        }
    }

    /** Rooms joined by an open door hold the same air for a long time and never merge (CORE-034). */
    @Test
    void core_034_door_separated_rooms_never_merge() {
        Sim sim = rooms(2, OPEN);
        sim.add(1, 65, 1, 0, 200);
        sim.steps(300);
        assertEquals(2, sim.snapshot().zoneCount());
        assertEquals(0, sim.engine.graph().mergeCount());
        assertTrue(allAsleep(sim.engine.graph()), "the door edge sleeps once the rooms are equivalent");
    }

    /**
     * Three rooms in a row joined by open doors, gas added in the first: they share until they are
     * equivalent, then all three take one combined mixture, with nothing lost (CORE-067).
     */
    @Test
    void core_067_three_rooms_equalize() {
        Sim sim = rooms(3, OPEN);
        sim.add(1, 65, 1, 300, 200);
        sim.step();
        assertEquals(3, sim.snapshot().zoneCount());
        double volume = 0;
        double n2 = 0;
        double o2 = 0;
        for (ZoneView z : sim.snapshot().zones()) {
            volume += z.volume();
            n2 += z.moles(N2);
            o2 += z.moles(O2);
        }
        int ticks = 0;
        while (!allAsleep(sim.engine.graph()) && ticks < 1000) {
            sim.step();
            ticks++;
        }
        assertTrue(ticks > 1 && ticks < 1000, "the doors should share, then sleep; ticks: " + ticks);
        assertTrue(sim.engine.graph().equalizeCount() > 0);
        assertEquals(3, sim.snapshot().zoneCount(), "the rooms never merge (CORE-034)");
        ZoneView first = room(sim, 0);
        for (int i = 0; i < 3; i++) {
            ZoneView z = room(sim, i);
            assertEquals(first.temperature(), z.temperature(), 1e-9, "room " + i + " temperature");
            assertEquals(first.moles(N2) / first.volume(), z.moles(N2) / z.volume(), 1e-14, "room " + i + " N2");
            assertEquals(first.moles(O2) / first.volume(), z.moles(O2) / z.volume(), 1e-14, "room " + i + " O2");
        }
        assertEquals(n2 / volume, first.moles(N2) / first.volume(), 1e-12, "the combined nitrogen");
        assertEquals(o2 / volume, first.moles(O2) / first.volume(), 1e-12, "the combined oxygen");
    }
}
