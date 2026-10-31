package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The worker-side export for the operator debug overlay (CLIENT-005, CLIENT-011). */
class DebugViewTest {

    /** With no regions asked for, nothing is exported. */
    @Test
    void client_005_no_regions_no_export() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        assertSame(DebugView.EMPTY, sim.scheduler.debugView());
    }

    /** A sealed room's cells are exported with its zone; a sealed room has no faces out (CLIENT-005). */
    @Test
    void client_005_room_cells_exported_with_zone() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.scheduler.setDebugRegions(List.of(DebugRegion.around(1, 65, 1, 32)));
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertNotNull(zone);
        DebugView view = sim.scheduler.debugView();
        assertEquals(sim.tick, view.tick());
        assertEquals(27, view.cells().size());
        for (DebugView.CellEntry c : view.cells()) {
            assertEquals(zone.id(), c.zone());
            assertEquals(0, c.sub());
        }
        assertTrue(view.faces().isEmpty(), "faces out of a sealed room");
        assertTrue(view.unresolved().isEmpty());
        assertFalse(view.truncated());
    }

    /** Only cells inside a region are exported (CLIENT-005's 2-chunk limit). */
    @Test
    void client_005_export_limited_to_region() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.scheduler.setDebugRegions(List.of(new DebugRegion(0, 64, 0, 0, 66, 2)));
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        DebugView view = sim.scheduler.debugView();
        assertEquals(9, view.cells().size(), "the x = 0 slice of the room");
        for (DebugView.CellEntry c : view.cells()) {
            assertEquals(0, SectionKeys.blockX(c.block()));
        }
    }

    /** A hole in the wall gives one direct face to the ambient (CLIENT-011). */
    @Test
    void client_011_breach_face_to_ambient() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.scheduler.setDebugRegions(List.of(DebugRegion.around(1, 65, 1, 32)));
        sim.add(1, 65, 1, 0, 100);
        sim.step();
        sim.world.set(3, 65, 1, TestWorld.AIR);
        sim.step();
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertNotNull(zone, "the zone dissolved at once");
        DebugView view = sim.scheduler.debugView();
        assertEquals(1, view.faces().size(), () -> "faces: " + view.faces());
        DebugView.FaceEntry f = view.faces().get(0);
        assertEquals(SectionKeys.block(2, 65, 1), f.block());
        assertEquals(Face.EAST, f.face());
        assertTrue(f.ambient());
        assertTrue(f.direct());
        assertEquals(zone.id(), f.zone());
    }
}
