package io.github.west3436.zas.engine.scheduler;

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

    /** Findings past the limit are counted but not listed. */
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
