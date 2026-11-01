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
    /**
     * A zone paused by an unloaded chunk keeps its cells in memory, so they are still exported;
     * the overlay greys them through the published paused flag (CLIENT-005, GEO-030).
     */
    @Test
    void client_005_paused_zone_cells_still_exported() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(12, 64, 1, 19, 66, 3); // across chunks (0, 0) and (1, 0)
        sim.scheduler.setDebugRegions(List.of(DebugRegion.around(13, 65, 2, 32)));
        sim.add(13, 65, 2, 40, 10);
        sim.step();
        ZoneView zone = sim.zoneAt(13, 65, 2);
        assertNotNull(zone);
        sim.unload(SectionKeys.chunk(1, 0));
        sim.step();
        assertTrue(sim.zoneAt(13, 65, 2).paused(), "the zone didn't pause");
        DebugView view = sim.scheduler.debugView();
        assertEquals(zone.cellCount(), view.cells().size(), "the paused zone's cells");
        for (DebugView.CellEntry c : view.cells()) {
            assertEquals(zone.id(), c.zone());
        }
    }

    /**
     * Opening a zone onto a region that reaches an unloaded chunk holds the breach (GEO-026): the
     * held region's cells are exported as unresolved, and none once it classifies (CLIENT-005).
     */
    @Test
    void client_005_held_breach_region_exported_as_unresolved() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(4, 64, 1, 10, 66, 3); // A, in the west chunk
        sim.world.room(12, 64, 1, 19, 66, 3); // B, unzoned, across both chunks
        sim.scheduler.setDebugRegions(List.of(DebugRegion.around(8, 65, 2, 32)));
        sim.add(5, 65, 2, 30, 0);
        sim.step();
        long east = SectionKeys.chunk(1, 0);
        sim.unload(east);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertTrue(sim.zoneAt(5, 65, 2).paused(), "the zone didn't pause at the held breach");
        DebugView view = sim.scheduler.debugView();
        assertFalse(view.unresolved().isEmpty(), "no unresolved cells exported");
        boolean breach = false;
        for (DebugView.UnresolvedEntry u : view.unresolved()) {
            int x = SectionKeys.blockX(u.block());
            int y = SectionKeys.blockY(u.block());
            int z = SectionKeys.blockZ(u.block());
            // The opening at x 11, or B's interior (x 12 to 19, y 64 to 66, z 1 to 3).
            assertTrue(
                    x >= 11 && x <= 19 && y >= 64 && y <= 66 && z >= 1 && z <= 3,
                    () -> "an unresolved cell outside B: " + x + ", " + y + ", " + z);
            breach |= x == 11 && y == 65 && z == 2;
        }
        assertTrue(breach, "the opening itself isn't unresolved");
        sim.load(east, null);
        sim.steps(3);
        assertFalse(sim.zoneAt(5, 65, 2).paused(), "the zone didn't resume");
        assertTrue(sim.scheduler.debugView().unresolved().isEmpty(), "unresolved cells after classifying");
    }
}
