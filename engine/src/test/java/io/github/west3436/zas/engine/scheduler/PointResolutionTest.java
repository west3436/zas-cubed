package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/**
 * The published table's answers for one cell of a position (API-015, D-44) and for the cells of a
 * region held unresolved at a breach (API-010, GEO-026), and operations aimed at one sub-cell.
 */
class PointResolutionTest {
    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    /** A glass pane running north to south: sub-cell 0 is its west half, 1 its east half. */
    private static final int PANE = BUILDER.intern(GeometryDeriver.derive(paneVoxels()));

    private static final GeometryPalette PALETTE = BUILDER.build();

    private static BitSet paneVoxels() {
        BitSet s = new BitSet(GeometryDeriver.VOXELS);
        GeometryDeriver.fill(s, 7, 0, 0, 9, 16, 16);
        return s;
    }

    /**
     * A sealed room whose east wall holds a pane at (3, 65, 1): the pane's west half belongs to the
     * room, its east half to the open air outside.
     */
    private static Sim roomWithPane() {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), PALETTE);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.set(3, 65, 1, PANE);
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        assertNotNull(sim.zoneAt(1, 65, 1), "the room didn't zone");
        return sim;
    }

    /** Each sub-cell of a pane reads its own zone: the room's half is zoned, the outside half isn't. */
    @Test
    void api_015_each_pane_sub_cell_reads_its_own_zone() {
        Sim sim = roomWithPane();
        AtmosSnapshot t = sim.snapshot();
        ZoneView room = sim.zoneAt(1, 65, 1);
        long pane = SectionKeys.block(3, 65, 1);
        assertSame(room, t.zoneAt(pane, 0), "the west half, inside the room");
        assertNull(t.zoneAt(pane, 1), "the east half, in the open air");
        assertSame(room, t.zoneAt(pane), "the position's largest zoned cell");
        assertSame(room, t.zoneAt(pane, -1), "no sub-cell: the position");
        assertEquals(28, room.cellCount(), "the room's 27 cells and the pane's west half");
    }

    /** A position holding one full cell answers the same for any sub-cell asked about (API-015). */
    @Test
    void api_015_full_cell_answers_for_its_position() {
        Sim sim = roomWithPane();
        AtmosSnapshot t = sim.snapshot();
        long inside = SectionKeys.block(1, 65, 1);
        assertSame(sim.zoneAt(1, 65, 1), t.zoneAt(inside, 0));
        assertNull(t.zoneAt(SectionKeys.block(5, 65, 1), 0), "the open air");
    }

    /**
     * An operation aimed at one sub-cell acts on it: gas added to the pane's west half goes into the
     * room, to its east half into the open air (API-015, API-012).
     */
    @Test
    void api_015_operations_act_on_their_sub_cell() {
        Sim sim = roomWithPane();
        long west = sim.add(3, 65, 1, 0, 0, 5);
        long east = sim.add(3, 65, 1, 1, 0, 5);
        // The open air classifies once the sections around it arrive.
        for (int i = 0; i < 8 && !sim.outcomes.containsKey(east); i++) {
            sim.step();
        }
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(west).outcome(), "the west half");
        assertEquals(sim.zoneAt(1, 65, 1).id(), sim.outcomes.get(west).zoneId(), "the room's zone");
        assertEquals(OpOutcome.Outcome.AMBIENT, sim.outcomes.get(east).outcome(), "the east half");
    }

    /** A sub-cell the position no longer has falls back to its largest cell (API-015). */
    @Test
    void api_015_missing_sub_cell_falls_back_to_largest() {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), PALETTE);
        sim.world.room(0, 64, 0, 2, 66, 2);
        long seq = sim.add(1, 65, 1, 3, 0, 5);
        sim.step();
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(seq).outcome());
    }

    /**
     * The unzoned cells of a region held at a breach because it reaches an unloaded chunk are
     * listed as held, so they read as unresolved, and none are once it classifies (API-010,
     * GEO-026).
     */
    @Test
    void api_010_held_breach_region_is_listed_as_held() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(4, 64, 1, 10, 66, 3); // A, in the west chunk
        sim.world.room(12, 64, 1, 19, 66, 3); // B, unzoned, across both chunks
        sim.add(5, 65, 2, 30, 0);
        sim.step();
        assertEquals(0, sim.snapshot().heldCells(), "held cells before any breach");
        long east = SectionKeys.chunk(1, 0);
        sim.unload(east);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        AtmosSnapshot t = sim.snapshot();
        assertTrue(sim.zoneAt(5, 65, 2).paused(), "the zone didn't pause at the held breach");
        assertTrue(t.heldAt(SectionKeys.block(11, 65, 2), 0), "the opening");
        assertTrue(t.heldAt(SectionKeys.block(14, 65, 2), 0), "B's loaded part");
        assertTrue(t.heldAt(SectionKeys.block(15, 66, 3), -1), "any cell of a position");
        assertFalse(t.heldAt(SectionKeys.block(5, 65, 2), -1), "A's zoned cells");
        assertFalse(t.heldAt(SectionKeys.block(2, 65, 2), -1), "the open air outside both rooms");
        assertFalse(t.heldAt(SectionKeys.block(11, 64, 2), -1), "a solid wall");
        assertEquals(1 + 4 * 3 * 3, t.heldCells(), "the opening and B's cells in the loaded chunk");
        sim.step();
        assertEquals(t.heldCells(), sim.snapshot().heldCells(), "the next tick, unchanged");
        sim.load(east, null);
        sim.steps(3);
        assertFalse(sim.zoneAt(5, 65, 2).paused(), "the zone didn't resume");
        assertEquals(0, sim.snapshot().heldCells(), "held cells after classifying");
        assertFalse(sim.snapshot().heldAt(SectionKeys.block(14, 65, 2), -1));
    }
}
