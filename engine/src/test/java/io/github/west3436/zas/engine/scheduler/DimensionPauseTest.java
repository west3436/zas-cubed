package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import org.junit.jupiter.api.Test;

/**
 * The whole dimension paused (OPS-005): every zone behaves as a paused zone (GEO-030, GEO-035 to
 * GEO-038), whichever of them would otherwise be running, and nothing is classified.
 */
class DimensionPauseTest {
    private static final double EPS = 1e-9;
    private static final long WEST = SectionKeys.chunk(0, 0);
    private static final long EAST = SectionKeys.chunk(1, 0);

    private static void pause(Sim sim) {
        sim.settings = sim.settings.withDimensionPaused(true);
    }

    private static void resume(Sim sim) {
        sim.settings = sim.settings.withDimensionPaused(false);
    }

    private static void assertSameState(ZoneView expected, ZoneView actual) {
        assertEquals(expected.id(), actual.id(), "id");
        assertEquals(expected.cellCount(), actual.cellCount(), "cells");
        assertEquals(expected.volume(), actual.volume(), "volume");
        assertArrayEquals(expected.moles(), actual.moles(), "moles");
        assertEquals(expected.temperature(), actual.temperature(), "temperature");
    }

    /** Rooms A and B share a hole at x = 11, at different pressures, in chunk (0, 0) and across two. */
    private static Sim twoRoomsWithAHole() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(4, 64, 1, 10, 66, 3);
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.add(5, 65, 2, 1, 0);
        sim.add(13, 65, 2, 80, 10);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertEquals(1, sim.zoneAt(5, 65, 2).zoneEdges(), "no edge between the rooms");
        return sim;
    }

    /**
     * Every zone pauses, with no chunk unloaded, and nothing in any of them changes: not across an
     * edge between zones, through an opening to the vacuum, or by radiating heat to it (GEO-030).
     */
    @Test
    void ops_005_every_zone_pauses_and_keeps_its_state() {
        Sim sim = twoRoomsWithAHole();
        sim.world.room(4, 64, 10, 10, 66, 12);
        sim.add(5, 65, 11, 30, 0);
        sim.step();
        sim.world.set(3, 65, 11, TestWorld.AIR); // venting to the vacuum
        sim.step();
        assertTrue(sim.zoneAt(5, 65, 11).breached(), "the third room isn't venting");
        assertFalse(sim.zoneAt(5, 65, 2).paused());

        pause(sim);
        sim.step();
        ZoneView a = sim.zoneAt(5, 65, 2);
        ZoneView b = sim.zoneAt(13, 65, 2);
        ZoneView c = sim.zoneAt(5, 65, 11);
        assertTrue(a.paused() && b.paused() && c.paused(), "not every zone paused");
        sim.steps(5);
        assertSameState(a, sim.zoneAt(5, 65, 2));
        assertSameState(b, sim.zoneAt(13, 65, 2));
        assertSameState(c, sim.zoneAt(5, 65, 11));
    }

    /** Gas sent to any zone is voided and gas drawn from it supplied, and the checker records both. */
    @Test
    void ops_005_gas_is_voided_and_supplied_at_every_zone() {
        Sim sim = twoRoomsWithAHole();
        pause(sim);
        sim.step();
        ZoneView a = sim.zoneAt(5, 65, 2);
        long added = sim.add(5, 65, 2, 5, 1);
        long removed = sim.remove(13, 65, 2, 3, 0);
        ConservationReport report = sim.step().conservation();

        OpOutcome in = sim.outcomes.get(added);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, in.outcome());
        assertEquals(a.id(), in.zoneId());
        assertEquals(5, in.moved(N2), EPS);
        OpOutcome out = sim.outcomes.get(removed);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, out.outcome());
        assertEquals(3, out.moved(N2), EPS);

        assertSameState(a, sim.zoneAt(5, 65, 2));
        assertTrue(report.conserved());
        assertEquals(5, report.voided(N2), EPS);
        assertEquals(1, report.voided(O2), EPS);
        assertEquals(3, report.supplied(N2), EPS);
    }

    /**
     * No zone forms while paused: an operation that would form one is voided as at an unresolved
     * region, in a section the engine has and in one it hasn't. Once resumed, one forms as usual.
     */
    @Test
    void ops_005_no_zone_forms_while_paused() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        sim.world.room(4, 64, 1, 7, 66, 3);
        sim.add(5, 65, 2, 10, 0);
        sim.step();
        sim.world.room(9, 64, 1, 13, 66, 3); // in a section the engine has
        sim.world.room(100, 64, 100, 103, 66, 103); // in one it hasn't
        pause(sim);

        long near = sim.add(10, 65, 2, 4, 0);
        long far = sim.add(101, 65, 101, 4, 0);
        ConservationReport report = sim.step().conservation();
        assertEquals(OpOutcome.Outcome.UNRESOLVED, sim.outcomes.get(near).outcome());
        assertEquals(OpOutcome.Outcome.UNRESOLVED, sim.outcomes.get(far).outcome());
        assertEquals(0, sim.pipeline.outstanding().size(), "an operation still waits");
        assertNull(sim.zoneAt(10, 65, 2), "a zone formed in the near room");
        assertNull(sim.zoneAt(101, 65, 101), "a zone formed in the far room");
        assertEquals(8, report.voided(N2), EPS);

        resume(sim);
        sim.step();
        long again = sim.add(10, 65, 2, 4, 0);
        sim.steps(3);
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(again).outcome());
        assertNotNull(sim.zoneAt(10, 65, 2), "no zone formed after the pause");
    }

    /** A block change touching a zone is held, however many there are, and applies on resuming (GEO-037). */
    @Test
    void ops_005_block_changes_are_held_until_it_resumes() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        sim.world.room(4, 64, 1, 10, 66, 3);
        sim.add(5, 65, 2, 10, 0);
        sim.step();
        ZoneView before = sim.zoneAt(5, 65, 2);
        pause(sim);
        sim.step();

        sim.world.set(3, 65, 2, TestWorld.AIR);
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.steps(2);
        assertFalse(sim.zoneAt(5, 65, 2).breached(), "a change applied while paused");
        assertSameState(before, sim.zoneAt(5, 65, 2));
        assertArrayEquals(
                new long[] {SectionKeys.block(3, 65, 2), SectionKeys.block(11, 65, 2)}, sim.pipeline.heldBlocks());

        resume(sim);
        sim.step();
        assertFalse(sim.zoneAt(5, 65, 2).paused());
        assertTrue(sim.zoneAt(5, 65, 2).breached(), "the held changes didn't apply");
        assertEquals(0, sim.pipeline.heldBlocks().length);
    }

    /** On resuming, zones carry on from where they stopped (GEO-033): rooms with a hole exchange again. */
    @Test
    void ops_005_zones_carry_on_after_it_resumes() {
        Sim sim = twoRoomsWithAHole();
        pause(sim);
        sim.steps(3);
        ZoneView a = sim.zoneAt(5, 65, 2);
        resume(sim);
        sim.step();
        ZoneView later = sim.zoneAt(5, 65, 2);
        assertFalse(later.paused());
        assertTrue(later.moles(N2) > a.moles(N2), "the rooms don't exchange again");
    }

    /**
     * A zone can still be stored in its chunks and rebuilt from them, which isn't the simulation
     * running. Rebuilt in a paused dimension, it keeps still, even with a hole made while it was
     * stored, and only vents once the dimension resumes (DATA-010).
     */
    @Test
    void ops_005_a_zone_loaded_while_paused_keeps_still_until_it_resumes() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.add(13, 65, 2, 40, 10);
        sim.step();
        ZoneView before = sim.zoneAt(13, 65, 2);
        ChunkRecord west = sim.unload(WEST);
        ChunkRecord east = sim.unload(EAST);
        sim.step();
        assertNull(sim.zoneAt(13, 65, 2), "the zone stayed in memory");
        pause(sim);
        sim.step();

        sim.load(WEST, west);
        sim.load(EAST, east);
        sim.world.set(12, 67, 2, TestWorld.AIR); // a hole in the roof, made while it was stored
        sim.steps(3);
        ZoneView rebuilt = sim.zoneAt(13, 65, 2);
        assertNotNull(rebuilt, "the zone wasn't rebuilt");
        assertTrue(rebuilt.paused(), "the rebuilt zone isn't paused");
        assertSameState(before, rebuilt);

        resume(sim);
        sim.steps(2);
        ZoneView resumed = sim.zoneAt(13, 65, 2);
        assertTrue(resumed == null || resumed.moles(N2) < before.moles(N2), "the hole didn't vent it");
    }

    /**
     * A breach held at an unloaded chunk (GEO-026) isn't resolved while paused, even when the chunk
     * loads: the room it opens onto joins only once the dimension resumes.
     */
    @Test
    void ops_005_a_held_breach_waits_until_it_resumes() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        sim.world.room(4, 64, 1, 10, 66, 3); // A, in the west chunk
        sim.world.room(12, 64, 1, 19, 66, 3); // B, unzoned, across both chunks
        sim.add(5, 65, 2, 30, 0);
        sim.step();
        assertNull(sim.unload(EAST), "the east chunk holds no zone");
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertTrue(sim.zoneAt(5, 65, 2).paused(), "the zone didn't pause at the held breach");
        ZoneView a = sim.zoneAt(5, 65, 2);

        pause(sim);
        sim.step();
        sim.load(EAST, null);
        sim.steps(3);
        assertTrue(sim.zoneAt(5, 65, 2).paused(), "the zone resumed");
        assertSameState(a, sim.zoneAt(5, 65, 2));
        assertNull(sim.zoneAt(18, 65, 2), "the room joined while paused");

        resume(sim);
        sim.steps(3);
        ZoneView joined = sim.zoneAt(5, 65, 2);
        assertFalse(joined.paused(), "the zone didn't resume");
        assertEquals(joined.id(), sim.zoneAt(18, 65, 2).id(), "the room didn't join");
    }

    /** Pausing changes nothing in a dimension's other state: a sealed zone comes back identical. */
    @Test
    void ops_005_a_sealed_zone_resumes_unchanged() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        sim.world.room(4, 64, 1, 10, 66, 3);
        sim.add(5, 65, 2, 10, 2);
        sim.step();
        ZoneView before = sim.zoneAt(5, 65, 2);
        pause(sim);
        sim.steps(4);
        resume(sim);
        sim.step();
        ZoneView after = sim.zoneAt(5, 65, 2);
        assertFalse(after.paused());
        assertSameState(before, after);
        assertNotEquals(0, after.moles(N2));
    }
}
