package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.scheduler.GeometryAudit.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The geometry audit (GEO-032) and inspections between ticks. */
class GeometryAuditTest {
    private static final GeometryAudit.Region ROOM = new GeometryAudit.Region(-1, 63, -1, 3, 67, 3);

    /** A sealed 3 × 3 × 3 room at (0..2, 64..66, 0..2), zoned by an add. */
    private static Sim zonedRoom() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 10);
        sim.steps(2);
        assertNotNull(sim.zoneAt(1, 65, 1), "the room didn't zone");
        return sim;
    }

    private static List<SectionSnapshot> fresh(Sim sim, GeometryAudit.Region region) {
        List<SectionSnapshot> out = new ArrayList<>();
        for (long key : region.sections()) {
            SectionSource s = sim.world.capture(key);
            if (s != null) {
                out.add(s.decode());
            }
        }
        return out;
    }

    private static GeometryAudit.Report audit(Sim sim, GeometryAudit.Region region) {
        List<SectionSnapshot> fresh = fresh(sim, region);
        return sim.scheduler
                .inspect(engine -> GeometryAudit.run(engine, region, fresh, 100))
                .join();
    }

    /** A zone in step with its blocks audits clean. */
    @Test
    void geo_032_matching_world_is_clean() {
        Sim sim = zonedRoom();
        GeometryAudit.Report r = audit(sim, ROOM);
        assertTrue(r.clean(), () -> "findings: " + r.findings());
        assertEquals(1, r.zonesChecked());
        assertTrue(r.sectionsChecked() > 0);
    }

    /**
     * A block placed inside a zone without the change reaching the engine leaves the zone holding
     * a cell the world no longer has; the audit reports the stale geometry and the membership.
     */
    @Test
    void geo_032_missed_fill_is_reported() {
        Sim sim = zonedRoom();
        sim.world.set(1, 65, 1, TestWorld.SOLID);
        sim.world.drainDirty(k -> true); // the change is lost
        sim.step();
        GeometryAudit.Report r = audit(sim, ROOM);
        assertFalse(r.clean());
        long block = SectionKeys.block(1, 65, 1);
        assertEquals(1, r.count(Kind.STALE_GEOMETRY));
        assertEquals(1, r.count(Kind.ZONED_NOT_A_CELL));
        assertTrue(r.findings().contains(new GeometryAudit.Finding(Kind.STALE_GEOMETRY, block, 0)));
        long zone = sim.zoneAt(0, 64, 0).id();
        assertTrue(
                r.findings().contains(new GeometryAudit.Finding(Kind.ZONED_NOT_A_CELL, block, zone)),
                () -> zone + " findings: " + r.findings());
        assertEquals(1, r.staleSections().length);
        assertEquals(SectionKeys.sectionOfBlock(1, 65, 1), r.staleSections()[0]);
    }

    /** A wall broken without the engine knowing leaves a sealed zone open to the air. */
    @Test
    void geo_032_missed_breach_is_reported() {
        Sim sim = zonedRoom();
        sim.world.set(3, 65, 1, TestWorld.AIR);
        sim.world.drainDirty(k -> true);
        sim.step();
        GeometryAudit.Report r = audit(sim, ROOM);
        assertEquals(1, r.count(Kind.STALE_GEOMETRY));
        assertEquals(1, r.count(Kind.UNRECORDED_OPENING), () -> "findings: " + r.findings());
        assertEquals(1, r.count(Kind.LEAKS_TO_OPEN), () -> "unresolved " + r.zonesUnresolved());
        assertTrue(r.findings()
                .contains(new GeometryAudit.Finding(
                        Kind.UNRECORDED_OPENING,
                        SectionKeys.block(3, 65, 1),
                        sim.zoneAt(1, 65, 1).id())));
    }

    /** A change the engine did see audits clean: the audit doesn't report what the engine knows. */
    @Test
    void geo_032_seen_change_is_clean() {
        Sim sim = zonedRoom();
        sim.world.set(1, 65, 1, TestWorld.SOLID);
        sim.steps(2);
        GeometryAudit.Report r = audit(sim, ROOM);
        assertTrue(r.clean(), () -> "findings: " + r.findings());
    }

    /**
     * A wall broken beside a paused zone is held until the zone resumes (GEO-037). The audit counts
     * the held position instead of reporting it as stale geometry, and leaves the paused zone
     * unchecked; once the zone resumes and the change applies, the region audits clean.
     */
    @Test
    void geo_032_held_changes_are_not_stale() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(12, 64, 1, 19, 66, 3); // across chunks (0, 0) and (1, 0)
        sim.add(13, 65, 2, 40, 10);
        sim.step();
        long east = SectionKeys.chunk(1, 0);
        ChunkRecord saved = sim.unload(east);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertArrayEquals(new long[] {SectionKeys.block(11, 65, 2)}, sim.pipeline.heldBlocks());

        GeometryAudit.Region west = new GeometryAudit.Region(8, 63, 0, 15, 67, 4);
        GeometryAudit.Report r = audit(sim, west);
        assertTrue(r.clean(), () -> "findings: " + r.findings());
        assertEquals(1, r.heldPositions());
        assertEquals(1, r.zonesPaused());
        assertEquals(0, r.zonesChecked());
        assertEquals(0, r.staleSections().length);

        sim.load(east, saved);
        sim.step();
        assertEquals(0, sim.pipeline.heldBlocks().length);
        GeometryAudit.Report after = audit(sim, west);
        assertTrue(after.clean(), () -> "findings after resuming: " + after.findings());
        assertEquals(0, after.heldPositions());
        assertEquals(0, after.zonesPaused());
        assertEquals(1, after.zonesChecked());
    }

    /** Findings past the limit are counted but not listed. */
    /**
     * A zone vents through an unzoned corridor, and the corridor's far end is closed: nothing changes at
     * the zone's own opening, but what it opens onto is a pocket now (GEO-023, GEO-025).
     */
    @Test
    void geo_023_closing_the_far_end_of_what_a_zone_vents_through_makes_it_a_pocket() {
        Sim sim = ventingThroughACorridor(15, 15);

        sim.world.set(15, 69, 2, TestWorld.SOLID);
        sim.steps(3);
        GeometryAudit.Report r = audit(sim, new GeometryAudit.Region(0, 66, -1, 20, 72, 5));
        assertTrue(r.clean(), () -> "findings: " + r.findings());
        assertFalse(sim.zoneAt(5, 69, 2).breached(), "the zone is still venting");
        assertEquals(sim.zoneAt(5, 69, 2).id(), sim.zoneAt(13, 69, 2).id(), "the corridor didn't join");
    }

    /** The same way out through a chunk that unloads: nothing says it has closed, so the zone goes on venting. */
    @Test
    void geo_023_a_chunk_unloading_on_the_way_out_doesnt_stop_a_zone_venting() {
        Sim sim = ventingThroughACorridor(22, 22);

        sim.unload(SectionKeys.chunk(1, 0));
        sim.steps(3);
        assertTrue(sim.zoneAt(5, 69, 2).breached(), "the zone stopped venting");
        assertFalse(sim.zoneAt(5, 69, 2).paused(), "the zone paused");
    }

    /**
     * Each check of cells in doubt can take a fill up to the bound, so they wait their turn: a way out
     * that closes just after one takes no fill until the next, which finds the pocket. The way out is a
     * hole in the corridor's roof near the top of the world, where the zone's own sections are enough to
     * reach the world's edge. A check that had to fetch sections would fill again the next tick, whatever
     * the gap.
     */
    @Test
    void geo_023_a_way_out_closing_just_after_a_check_is_found_at_the_next() {
        Sim sim = new Sim(Ambient.STANDARD_AIR).withoutHeat();
        sim.world.room(4, 308, 1, 10, 310, 3); // the zone
        sim.world.room(12, 308, 1, 14, 310, 3); // the corridor
        sim.add(5, 309, 2, 0, 50000);
        sim.steps(2);
        assertNotNull(sim.zoneAt(5, 309, 2), "the room didn't zone");
        sim.world.set(13, 311, 2, TestWorld.AIR); // the way out
        sim.steps(2);
        sim.world.set(11, 309, 2, TestWorld.AIR); // the zone opens into the corridor
        sim.steps(3);
        assertTrue(sim.zoneAt(5, 309, 2).breached(), "the zone isn't venting");
        sim.steps(EnclosureClassifier.DOUBT_CHECK_TICKS);
        int[] fills = {0};
        sim.engine.classifier().onFill(seed -> fills[0]++);

        sim.world.set(13, 308, 1, TestWorld.SOLID); // in the corridor, and the way out stays open
        sim.steps(1);
        int checked = fills[0];
        assertTrue(checked > 0, "the first change wasn't checked at once");
        assertTrue(sim.zoneAt(5, 309, 2).breached(), "the zone stopped venting");

        sim.world.set(13, 311, 2, TestWorld.SOLID); // the way out closes
        sim.steps(EnclosureClassifier.DOUBT_CHECK_TICKS - 1);
        assertEquals(checked, fills[0], "the next change didn't wait its turn");
        assertTrue(sim.zoneAt(5, 309, 2).breached(), "the zone stopped venting before its check");

        sim.steps(1);
        assertTrue(fills[0] > checked, "the next check didn't come");
        assertFalse(sim.zoneAt(5, 309, 2).breached(), "the zone is still venting");
        assertEquals(sim.zoneAt(5, 309, 2).id(), sim.zoneAt(13, 309, 2).id(), "the corridor didn't join");
        GeometryAudit.Report r = audit(sim, new GeometryAudit.Region(0, 306, -1, 20, 313, 5));
        assertTrue(r.clean(), () -> "findings: " + r.findings());
    }

    /** A zone, with a corridor from x 12 to {@code lastX}, opened at {@code openX} and then into the zone. */
    private static Sim ventingThroughACorridor(int lastX, int openX) {
        Sim sim = new Sim(Ambient.STANDARD_AIR).withoutHeat();
        sim.world.room(4, 68, 1, 10, 70, 3); // the zone
        sim.world.room(12, 68, 1, lastX == 15 ? 14 : lastX - 1, 70, 3); // the corridor
        sim.add(5, 69, 2, 0, 50000);
        sim.steps(2);
        assertNotNull(sim.zoneAt(5, 69, 2), "the room didn't zone");
        sim.world.set(openX, 69, 2, TestWorld.AIR); // the corridor's far end opens
        sim.steps(2);
        sim.world.set(11, 69, 2, TestWorld.AIR); // the zone opens into the corridor
        sim.steps(3);
        assertNotNull(sim.zoneAt(5, 69, 2), "the zone has gone");
        assertTrue(sim.zoneAt(5, 69, 2).breached(), "the zone isn't venting");
        return sim;
    }

    @Test
    void geo_032_findings_are_capped() {
        Sim sim = zonedRoom();
        sim.world.box(0, 64, 0, 2, 64, 2, TestWorld.SOLID);
        sim.world.drainDirty(k -> true);
        List<SectionSnapshot> fresh = fresh(sim, ROOM);
        GeometryAudit.Report r = sim.scheduler
                .inspect(engine -> GeometryAudit.run(engine, ROOM, fresh, 3))
                .join();
        assertEquals(9, r.count(Kind.STALE_GEOMETRY));
        assertEquals(3, r.findings().size());
        assertTrue(r.findings().stream().allMatch(f -> f.kind() == Kind.STALE_GEOMETRY));
    }

    /**
     * With worker threads, an inspection asked for while a tick is in flight runs after that tick,
     * and the next tick waits for it (OPS-004, GEO-032).
     */
    @Test
    void ops_004_inspection_runs_between_ticks() throws Exception {
        ExecutorService pool = AtmosWorkers.create(2);
        try {
            Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), pool, EnclosureClassifier.FACTORY);
            sim.world.room(0, 64, 0, 2, 66, 2);
            sim.add(1, 65, 1, 0, 10);
            sim.step();
            sim.pipeline.close(2, sim.settings, () -> false);
            var seen = sim.scheduler.inspect(DimensionEngine::lastTick);
            var zones = sim.scheduler.inspect(e -> e.graph().zoneCount());
            sim.pipeline.apply();
            assertEquals(2, seen.get(10, TimeUnit.SECONDS), "the inspection ran before the tick in flight");
            assertEquals(1, zones.get(10, TimeUnit.SECONDS));
            sim.pipeline.close(3, sim.settings, () -> false);
            assertNotNull(sim.pipeline.apply().outcomes());
            assertEquals(3, sim.engine.lastTick());
        } finally {
            pool.shutdownNow();
        }
    }
}
