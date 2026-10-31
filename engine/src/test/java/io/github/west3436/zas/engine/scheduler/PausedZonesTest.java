package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import org.junit.jupiter.api.Test;

/**
 * Paused zones (GEO-030 to GEO-038, architecture §4.6), driven through the pipeline with chunks
 * unloading and loading as the server would.
 */
class PausedZonesTest {
    private static final double EPS = 1e-9;
    private static final long WEST = SectionKeys.chunk(0, 0);
    private static final long EAST = SectionKeys.chunk(1, 0);

    /** A sealed room from x 12 to 19, across the border of chunks (0, 0) and (1, 0), with gas in it. */
    private static Sim spanningRoom(Ambient ambient) {
        Sim sim = new Sim(ambient);
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.add(13, 65, 2, 40, 10);
        sim.step();
        assertNotNull(sim.zoneAt(13, 65, 2));
        assertEquals(sim.zoneAt(13, 65, 2), sim.zoneAt(18, 65, 2));
        return sim;
    }

    private static void assertSameState(ZoneView expected, ZoneView actual) {
        assertEquals(expected.id(), actual.id(), "id");
        assertEquals(expected.cellCount(), actual.cellCount(), "cells");
        assertEquals(expected.volume(), actual.volume(), "volume");
        assertArrayEquals(expected.moles(), actual.moles(), "moles");
        assertEquals(expected.temperature(), actual.temperature(), "temperature");
    }

    /** A zone with a cell in an unloaded chunk pauses as a whole: nothing in it changes (GEO-030). */
    @Test
    void geo_030_zone_in_unloaded_chunk_pauses() {
        Sim sim = spanningRoom(Ambient.VACUUM);
        sim.world.set(11, 65, 2, TestWorld.AIR); // a hole in the west wall: it vents
        sim.step();
        ZoneView venting = sim.zoneAt(13, 65, 2);
        assertTrue(venting.breached());
        assertFalse(venting.paused());

        sim.unload(EAST);
        sim.step();
        ZoneView paused = sim.zoneAt(13, 65, 2);
        assertTrue(paused.paused(), "the zone didn't pause");
        assertSameState(venting, paused);
        for (int i = 0; i < 10; i++) {
            sim.step();
        }
        assertTrue(sim.zoneAt(13, 65, 2).paused());
        assertSameState(venting, sim.zoneAt(13, 65, 2));
    }

    /** A paused zone resumes with its mixture unchanged once its chunks load (GEO-033). */
    @Test
    void geo_033_resumes_unchanged() {
        Sim sim = spanningRoom(Ambient.VACUUM);
        ZoneView before = sim.zoneAt(13, 65, 2);
        ChunkRecord saved = sim.unload(EAST);
        sim.steps(3);
        sim.add(13, 65, 2, 5, 0); // voided
        sim.steps(2);
        sim.load(EAST, saved);
        sim.step();
        ZoneView after = sim.zoneAt(13, 65, 2);
        assertFalse(after.paused(), "the zone didn't resume");
        assertSameState(before, after);
    }

    /** Gas sent into a paused zone is voided, and the checker records it (GEO-036). */
    @Test
    void geo_036_gas_into_paused_zone_is_voided() {
        Sim sim = spanningRoom(Ambient.VACUUM);
        ZoneView before = sim.zoneAt(13, 65, 2);
        sim.unload(EAST);
        sim.step();
        long seq = sim.add(13, 65, 2, 5, 1);
        DimensionPipeline.Applied a = sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, o.outcome());
        assertEquals(before.id(), o.zoneId());
        assertEquals(5, o.moved(N2), EPS);
        assertEquals(1, o.moved(O2), EPS);
        assertSameState(before, sim.zoneAt(13, 65, 2));
        ConservationReport report = a.conservation();
        assertTrue(report.conserved());
        assertEquals(5, report.voided(N2), EPS);
        assertEquals(1, report.voided(O2), EPS);
    }

    /** Gas drawn from a paused zone is supplied at its last-known state without depleting it (GEO-038). */
    @Test
    void geo_038_paused_zone_supplies_without_depleting() {
        Sim sim = spanningRoom(Ambient.VACUUM);
        ZoneView before = sim.zoneAt(13, 65, 2);
        ChunkRecord saved = sim.unload(EAST);
        sim.step();
        long seq = sim.remove(13, 65, 2, 3, 100);
        DimensionPipeline.Applied a = sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, o.outcome());
        assertEquals(3, o.moved(N2), EPS);
        assertEquals(before.moles(O2), o.moved(O2), EPS, "more than the zone holds");
        assertEquals(before.temperature(), o.temperature(), EPS);
        assertEquals(3, a.conservation().supplied(N2), EPS);
        sim.load(EAST, saved);
        sim.step();
        assertSameState(before, sim.zoneAt(13, 65, 2));
    }

    /**
     * Breaking a wall of a paused zone in its loaded part is held, and applies when the zone
     * resumes (GEO-037).
     */
    @Test
    void geo_037_changes_to_paused_zone_are_held() {
        Sim sim = spanningRoom(Ambient.VACUUM);
        ChunkRecord saved = sim.unload(EAST);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        ZoneView held = sim.zoneAt(13, 65, 2);
        assertTrue(held.paused());
        assertFalse(held.breached(), "the change applied while paused");
        assertArrayEquals(new long[] {SectionKeys.block(11, 65, 2)}, sim.pipeline.heldBlocks());
        sim.load(EAST, saved);
        sim.step();
        ZoneView resumed = sim.zoneAt(13, 65, 2);
        assertFalse(resumed.paused());
        assertTrue(resumed.breached(), "the held change didn't apply on resuming");
        assertEquals(0, sim.pipeline.heldBlocks().length);
    }

    /**
     * An active zone across an edge from a paused one exchanges with the paused zone's last-known
     * state, which doesn't change (GEO-035, GEO-036, GEO-038).
     */
    @Test
    void geo_035_active_zone_sees_paused_zone_last_known_state() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(12, 64, 1, 19, 66, 3); // B, across both chunks
        sim.world.room(4, 64, 1, 10, 66, 3); // A, west only; they share the wall at x = 11
        sim.add(13, 65, 2, 80, 0);
        sim.add(5, 65, 2, 1, 0);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertTrue(sim.zoneAt(5, 65, 2).zoneEdges() == 1, "no edge between the rooms");
        sim.unload(EAST);
        sim.step();
        ZoneView paused = sim.zoneAt(13, 65, 2);
        assertTrue(paused.paused());
        double a = sim.zoneAt(5, 65, 2).moles(N2);
        DimensionPipeline.Applied step = sim.step();
        assertFalse(sim.zoneAt(5, 65, 2).paused());
        assertTrue(sim.zoneAt(5, 65, 2).moles(N2) > a, "the active zone didn't draw from the paused one");
        assertSameState(paused, sim.zoneAt(13, 65, 2));
        assertTrue(
                step.conservation().recorded(io.github.west3436.zas.engine.conservation.LedgerCategory.PAUSED, N2) > 0);
    }

    /**
     * A zone that opens onto a region reaching an unloaded chunk pauses, and resumes once the
     * region can be classified (GEO-026).
     */
    @Test
    void geo_026_breach_into_unresolved_region_pauses_zone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(4, 64, 1, 10, 66, 3); // A, in the west chunk
        sim.world.room(12, 64, 1, 19, 66, 3); // B, unzoned, across both chunks
        sim.add(5, 65, 2, 30, 0);
        sim.step();
        ZoneView a = sim.zoneAt(5, 65, 2);
        assertNull(sim.zoneAt(13, 65, 2));
        ChunkRecord saved = sim.unload(EAST);
        assertNull(saved, "the east chunk holds no zone");
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertTrue(sim.zoneAt(5, 65, 2).paused(), "the zone didn't pause at the unresolved breach");
        sim.steps(2);
        assertSameState(a, sim.zoneAt(5, 65, 2));
        sim.load(EAST, null);
        sim.steps(3);
        ZoneView joined = sim.zoneAt(5, 65, 2);
        assertFalse(joined.paused(), "the zone didn't resume");
        assertEquals(joined.id(), sim.zoneAt(18, 65, 2).id(), "the pocket didn't join");
        assertEquals(30, joined.moles(N2), EPS);
    }

    /** A zone-triggering operation in a room that reaches an unloaded chunk is unresolved (GEO-024). */
    @Test
    void geo_024_fill_reaching_unloaded_chunk_is_unresolved() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.unload(EAST);
        long seq = sim.add(13, 65, 2, 10, 0);
        sim.steps(2);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, sim.outcomes.get(seq).outcome());
        assertNull(sim.zoneAt(13, 65, 2));
        sim.load(EAST, null);
        long again = sim.add(13, 65, 2, 10, 0);
        sim.steps(3);
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(again).outcome());
        assertEquals(10, sim.zoneAt(18, 65, 2).moles(N2), EPS);
    }
}
