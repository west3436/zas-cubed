package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.classify.WorldLimits;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Where the world ends: its build height and border count as open, or sealed if it says so (GEO-031). */
class WorldLimitsTest {
    private static final OpOutcome.Outcome AMBIENT = OpOutcome.Outcome.AMBIENT;
    private static final OpOutcome.Outcome ZONE = OpOutcome.Outcome.ZONE;
    private static final int TOP = TestWorld.MAX_SECTION_Y * 16 + 15;
    private static final int BOTTOM = TestWorld.MIN_SECTION_Y * 16;
    private static final WorldLimits SEALED =
            new WorldLimits(CellIds.MIN_XZ, CellIds.MIN_XZ, CellIds.MAX_XZ, CellIds.MAX_XZ, true);
    /** Enough gas not to vent away during a test. */
    private static final double PLENTY = 50_000;

    private static Sim sim() {
        return new Sim(Ambient.STANDARD_AIR);
    }

    /** The world's border at {@code maxX}, its other sides far away. */
    private static WorldLimits borderAt(int maxX, boolean sealed) {
        return new WorldLimits(CellIds.MIN_XZ, CellIds.MIN_XZ, maxX, CellIds.MAX_XZ, sealed);
    }

    private static OpOutcome.Outcome add(Sim sim, int x, int y, int z, double moles) {
        long seq = sim.add(x, y, z, 0, moles);
        for (int i = 0; i < 16 && !sim.outcomes.containsKey(seq); i++) {
            sim.step();
        }
        OpOutcome outcome = sim.outcomes.get(seq);
        assertNotNull(outcome, "the add didn't finish");
        return outcome.outcome();
    }

    /** Walls and a floor, x and z 0 to 2, from y {@code y} up to the top of the world, with no roof. */
    private static void roofedByTheWorld(Sim sim, int y) {
        sim.world.box(-1, y - 1, -1, 3, TOP, 3, TestWorld.SOLID).box(0, y, 0, 2, TOP, 2, TestWorld.AIR);
    }

    /** The same with a roof at the top of the world: a sealed room of 27 cells. */
    private static void roofedRoom(Sim sim) {
        roofedByTheWorld(sim, TOP - 3);
        sim.world.box(0, TOP, 0, 2, TOP, 2, TestWorld.SOLID);
    }

    /** A sealed room, x and z 0 to 2, y 64 to 66, whose wall at x = 3 may lie past the border. */
    private static void room(Sim sim) {
        sim.world.room(0, 64, 0, 2, 66, 2);
    }

    private static GeometryAudit.Report audit(Sim sim, GeometryAudit.Region region) {
        List<SectionSnapshot> fresh = new ArrayList<>();
        for (long key : region.sections()) {
            SectionSource s = sim.world.capture(key);
            if (s != null) {
                fresh.add(s.decode());
            }
        }
        return sim.scheduler
                .inspect(engine -> GeometryAudit.run(engine, region, fresh, 100))
                .join();
    }

    // ---- The build height -------------------------------------------------------------------

    @Test
    void geo_031_the_top_of_the_world_is_open() {
        Sim sim = sim();
        roofedByTheWorld(sim, TOP - 2);
        assertEquals(AMBIENT, add(sim, 1, TOP - 1, 1, 1));
    }

    @Test
    void geo_031_the_bottom_of_the_world_is_open() {
        Sim sim = sim();
        sim.world
                .box(-1, BOTTOM, -1, 3, BOTTOM + 3, 3, TestWorld.SOLID)
                .box(0, BOTTOM, 0, 2, BOTTOM + 2, 2, TestWorld.AIR);
        assertEquals(AMBIENT, add(sim, 1, BOTTOM + 1, 1, 1));
    }

    @Test
    void geo_031_a_sealed_world_seals_its_top_and_bottom() {
        Sim sim = sim();
        sim.world.limits = SEALED;
        roofedByTheWorld(sim, TOP - 2);
        assertEquals(ZONE, add(sim, 1, TOP - 1, 1, 1));
        assertEquals(27, sim.zoneAt(1, TOP - 1, 1).cellCount());

        sim.world
                .box(-1, BOTTOM, -1, 3, BOTTOM + 3, 3, TestWorld.SOLID)
                .box(0, BOTTOM, 0, 2, BOTTOM + 2, 2, TestWorld.AIR);
        assertEquals(ZONE, add(sim, 1, BOTTOM + 1, 1, 1));
        assertEquals(27, sim.zoneAt(1, BOTTOM + 1, 1).cellCount());
    }

    /** A hole in a roof that opens only past the limit vents: an ambient edge with no cell at its end. */
    @Test
    void geo_031_a_roof_opened_at_the_limit_vents() {
        Sim sim = sim();
        roofedRoom(sim);
        assertEquals(ZONE, add(sim, 1, TOP - 2, 1, PLENTY));
        double full = sim.zoneAt(1, TOP - 2, 1).totalMoles();
        assertEquals(0, sim.zoneAt(1, TOP - 2, 1).ambientEdges());

        sim.world.set(1, TOP, 1, TestWorld.AIR);
        sim.steps(3);
        ZoneView open = sim.zoneAt(1, TOP - 2, 1);
        assertTrue(open.ambientEdges() > 0, () -> "the zone isn't open: " + open);
        assertTrue(open.totalMoles() < full, () -> "the zone didn't vent: " + open);
    }

    // ---- The border ---------------------------------------------------------------------------

    @Test
    void geo_031_the_border_is_open_whatever_lies_past_it() {
        Sim sim = sim();
        sim.world.limits = borderAt(2, false);
        room(sim);
        assertEquals(AMBIENT, add(sim, 1, 65, 1, 1), "the room's wall at x = 3 is past the border");
    }

    @Test
    void geo_031_a_wall_on_the_border_seals_it() {
        Sim sim = sim();
        sim.world.limits = borderAt(3, false);
        room(sim);
        assertEquals(ZONE, add(sim, 1, 65, 1, 1), "the wall at x = 3 is the last block inside");
    }

    @Test
    void geo_031_a_sealed_world_seals_its_border() {
        Sim sim = sim();
        sim.world.limits = borderAt(2, true);
        room(sim);
        assertEquals(ZONE, add(sim, 1, 65, 1, 1));
    }

    /** No gas is kept past the border, even if the world there is an enclosed room or sealed. */
    @Test
    void geo_031_gas_added_past_the_border_goes_to_the_open_air() {
        Sim sim = sim();
        sim.world.limits = borderAt(-5, true);
        room(sim);
        assertEquals(AMBIENT, add(sim, 1, 65, 1, 1));
        assertNull(sim.zoneAt(1, 65, 1));
    }

    // ---- Limits that change -------------------------------------------------------------------

    /** A dimension's definition can change with a datapack reload. */
    @Test
    void geo_031_unsealing_a_world_vents_its_zones() {
        Sim sim = sim();
        sim.world.limits = SEALED;
        roofedByTheWorld(sim, TOP - 2);
        assertEquals(ZONE, add(sim, 1, TOP - 1, 1, PLENTY));
        double full = sim.zoneAt(1, TOP - 1, 1).totalMoles();

        sim.world.limits = WorldLimits.OPEN;
        sim.steps(2);
        ZoneView open = sim.zoneAt(1, TOP - 1, 1);
        assertTrue(open.ambientEdges() > 0, () -> "the zone isn't open: " + open);
        assertTrue(open.totalMoles() < full, () -> "the zone didn't vent: " + open);
    }

    /** What was found open while the world wasn't sealed doesn't stay open. */
    @Test
    void geo_031_sealing_a_world_forgets_what_was_open() {
        Sim sim = sim();
        roofedByTheWorld(sim, TOP - 2);
        assertEquals(AMBIENT, add(sim, 1, TOP - 1, 1, 1));
        sim.world.limits = SEALED;
        assertEquals(ZONE, add(sim, 1, TOP - 1, 1, 1));
    }

    /** A shaft out of a zone that opens only past the limit closes when the limit is sealed. */
    @Test
    void geo_031_sealing_a_world_closes_a_breach_at_its_limit() {
        Sim sim = sim();
        roofedRoom(sim);
        assertEquals(ZONE, add(sim, 1, TOP - 2, 1, PLENTY));
        assertEquals(27, sim.zoneAt(1, TOP - 2, 1).cellCount());

        sim.world.set(1, TOP, 1, TestWorld.AIR);
        sim.steps(3);
        assertTrue(sim.zoneAt(1, TOP - 2, 1).ambientEdges() > 0, "the hole in the roof vents");

        sim.world.limits = SEALED;
        sim.steps(3);
        ZoneView sealed = sim.zoneAt(1, TOP - 2, 1);
        assertEquals(0, sealed.ambientEdges(), () -> "still open: " + sealed);
        assertEquals(28, sealed.cellCount(), "the shaft is part of the zone now");
    }

    /** Cells past a border that moves in leave their zone, as filled cells would, and their gas stays. */
    @Test
    void geo_031_a_border_moving_in_takes_cells_out() {
        Sim sim = sim();
        sim.world.limits = borderAt(10, true);
        sim.world.room(0, 64, 0, 4, 66, 2);
        assertEquals(ZONE, add(sim, 1, 65, 1, PLENTY));
        ZoneView before = sim.zoneAt(1, 65, 1);
        assertEquals(45, before.cellCount());

        sim.world.limits = borderAt(3, true);
        sim.step();
        ZoneView after = sim.zoneAt(1, 65, 1);
        assertEquals(36, after.cellCount());
        assertEquals(0, after.ambientEdges(), "sealed");
        assertEquals(before.totalMoles(), after.totalMoles(), 1e-6, "the gas stays in the zone");
        assertNull(sim.zoneAt(4, 65, 1), "past the border");
    }

    /** In an unsealed world the cells left by the border now face out of the world. */
    @Test
    void geo_031_a_border_moving_in_opens_a_zone_and_moving_back_closes_it() {
        Sim sim = sim();
        sim.world.limits = borderAt(10, false);
        sim.world.room(0, 64, 0, 4, 66, 2);
        assertEquals(ZONE, add(sim, 1, 65, 1, PLENTY));
        double full = sim.zoneAt(1, 65, 1).totalMoles();

        sim.world.limits = borderAt(2, false);
        sim.steps(2);
        ZoneView open = sim.zoneAt(1, 65, 1);
        assertEquals(27, open.cellCount());
        assertTrue(open.ambientEdges() > 0, () -> "the zone isn't open: " + open);
        assertTrue(open.totalMoles() < full, () -> "the zone didn't vent: " + open);

        sim.world.limits = borderAt(10, false);
        sim.steps(3);
        ZoneView closed = sim.zoneAt(1, 65, 1);
        assertEquals(0, closed.ambientEdges(), () -> "still open: " + closed);
        assertEquals(45, closed.cellCount(), "the room is whole again");
    }

    /** A paused zone keeps still (GEO-037), and takes in the change when its chunk comes back. */
    @Test
    void geo_031_a_paused_zone_takes_in_a_change_when_it_resumes() {
        Sim sim = sim();
        sim.world.limits = SEALED;
        sim.world.box(13, TOP - 3, -1, 18, TOP, 3, TestWorld.SOLID).box(14, TOP - 2, 0, 17, TOP, 2, TestWorld.AIR);
        assertEquals(ZONE, add(sim, 14, TOP - 1, 1, PLENTY));
        double full = sim.zoneAt(14, TOP - 1, 1).totalMoles();
        long chunk = SectionKeys.chunk(1, 0);
        ChunkRecord saved = sim.unload(chunk);
        sim.step();
        assertTrue(sim.zoneAt(14, TOP - 1, 1).paused(), "the test needs the zone paused");

        sim.world.limits = WorldLimits.OPEN;
        sim.steps(2);
        ZoneView paused = sim.zoneAt(14, TOP - 1, 1);
        assertEquals(0, paused.ambientEdges(), () -> "a paused zone changed: " + paused);
        assertEquals(full, paused.totalMoles(), 1e-6);

        sim.load(chunk, saved);
        sim.steps(3);
        ZoneView open = sim.zoneAt(14, TOP - 1, 1);
        assertFalse(open.paused());
        assertTrue(open.ambientEdges() > 0, () -> "the zone isn't open: " + open);
    }

    // ---- The audit (GEO-032) ------------------------------------------------------------------

    @Test
    void geo_031_the_audit_agrees_with_the_limits() {
        GeometryAudit.Region region = new GeometryAudit.Region(-1, TOP - 3, -1, 3, TOP, 3);
        Sim sim = sim();
        sim.world.limits = SEALED;
        roofedByTheWorld(sim, TOP - 2);
        assertEquals(ZONE, add(sim, 1, TOP - 1, 1, PLENTY));
        GeometryAudit.Report sealed = audit(sim, region);
        assertTrue(sealed.clean(), () -> "sealed: " + sealed.findings());

        sim.world.limits = WorldLimits.OPEN;
        sim.steps(2);
        GeometryAudit.Report open = audit(sim, region);
        assertTrue(open.clean(), () -> "open: " + open.findings());
    }
}
