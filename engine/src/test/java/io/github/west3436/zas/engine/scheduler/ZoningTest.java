package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import org.junit.jupiter.api.Test;

/** Ambient and sparse zoning through the whole pipeline (CORE-050 to CORE-058, GEO-024, GEO-028). */
class ZoningTest {
    private static final double EPS = 1e-9;

    /** A query for an unzoned cell finds no zone, so it reads the ambient (CORE-050). */
    @Test
    void core_050_unzoned_cell_is_ambient() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.steps(2);
        assertNull(sim.zoneAt(1, 65, 1), "a sealed room nobody touched");
        assertNull(sim.zoneAt(10, 65, 10), "open air");
        assertEquals(0, sim.snapshot().zoneCount());
    }

    /**
     * A sealed room stays unzoned until a triggering operation adds gas to it; then exactly it
     * becomes a zone, holding the ambient for its volume plus everything added (CORE-051,
     * CORE-055, GEO-028).
     */
    @Test
    void core_051_sealed_room_zones_only_on_demand() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.steps(3);
        assertEquals(0, sim.snapshot().zoneCount(), "zones without a trigger");

        long seq = sim.add(1, 65, 1, 0, 10);
        sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertNotNull(o, "the add didn't finish in one atmos tick");
        assertEquals(OpOutcome.Outcome.ZONE, o.outcome());
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertNotNull(zone, "no zone formed");
        assertEquals(o.zoneId(), zone.id());
        assertEquals(27, zone.cellCount());
        assertEquals(27_000, zone.volume(), EPS);
        double ambientO2 = Ambient.STANDARD_AIR.moles(Ambient.OXYGEN, 27_000);
        assertEquals(ambientO2 + 10, zone.moles(O2), 1e-9 * ambientO2, "O2: the room's ambient plus the add");
        assertEquals(Ambient.STANDARD_AIR.moles(Ambient.NITROGEN, 27_000), zone.moles(N2), 1e-6);
        assertEquals(0, zone.ambientEdges(), "a sealed room has no ambient edge");
        assertNull(sim.zoneAt(1, 68, 1), "a zone outside the room");
    }

    /** Adding gas in the open air releases it to the ambient and reports so; no zone forms (CORE-053). */
    @Test
    void core_053_open_air_add_goes_to_ambient() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        long seq = sim.add(5, 70, 5, 1, 2);
        sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.AMBIENT, o.outcome());
        assertEquals(1, o.moved(N2), EPS);
        assertEquals(2, o.moved(O2), EPS);
        assertEquals(0, sim.snapshot().zoneCount());
        assertEquals(1, sim.events.size(), "open-air event (CORE-058)");
        OpenAirEvent e = sim.events.get(0);
        assertTrue(e.added());
        assertEquals(seq, e.seq());
        assertEquals(SectionKeys.block(5, 70, 5), e.block());
        assertEquals(2, e.moles()[O2], EPS);
    }

    /**
     * Removing gas from the open air yields gas of the ambient's composition: the gases it
     * holds, at its temperature, and none it lacks (CORE-057), with an event (CORE-058).
     */
    @Test
    void core_057_open_air_remove_yields_ambient_gas() {
        Ambient onlyN2 = new Ambient(new java.util.TreeMap<>(java.util.Map.of(Ambient.NITROGEN, 80.0)), 250);
        Sim sim = new Sim(onlyN2);
        long seq = sim.remove(5, 70, 5, 3, 4);
        sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.AMBIENT, o.outcome());
        assertEquals(3, o.moved(N2), EPS);
        assertEquals(0, o.moved(O2), "the ambient holds no O2");
        assertEquals(250, o.temperature(), EPS);
        assertEquals(1, sim.events.size());
        assertFalse(sim.events.get(0).added());

        // A vacuum supplies nothing, and nothing left the simulation, so there is no event.
        Sim vacuum = new Sim(Ambient.VACUUM);
        long none = vacuum.remove(5, 70, 5, 3, 4);
        vacuum.step();
        assertEquals(0, vacuum.outcomes.get(none).moved(N2));
        assertTrue(vacuum.events.isEmpty());
    }

    /** Operations into an existing zone change it, and removals deliver what it held (API-012). */
    @Test
    void zoned_add_and_remove_change_the_zone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 5, 5);
        sim.step();
        long take = sim.remove(0, 64, 0, 2, 20);
        sim.step();
        OpOutcome o = sim.outcomes.get(take);
        assertEquals(OpOutcome.Outcome.ZONE, o.outcome());
        assertEquals(2, o.moved(N2), EPS);
        assertEquals(5, o.moved(O2), EPS, "clamped to what the zone held");
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertEquals(3, zone.moles(N2), EPS);
        assertEquals(0, zone.moles(O2), EPS);
    }

    /** A zone vents through a broken wall, and its ambient edge shows it (GEO-025 path). */
    @Test
    void breaking_a_wall_vents_the_zone() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 100);
        sim.step();
        double before = sim.zoneAt(1, 65, 1).pressure();
        sim.world.set(3, 65, 1, TestWorld.AIR);
        sim.step();
        ZoneView z = sim.zoneAt(1, 65, 1);
        assertNotNull(z);
        assertEquals(1, z.ambientEdges(), "the opening is an ambient edge");
        assertTrue(z.pressure() < before, "the zone didn't vent");
        sim.steps(60);
        assertNull(sim.zoneAt(1, 65, 1), "a vented zone dissolves into the ambient (CORE-076)");
    }

    /**
     * A tunnel longer than the prefetch takes extra atmos ticks to classify; the operation waits
     * and the zone that forms holds all of it (GEO-028, architecture §4.3).
     */
    @Test
    void geo_028_operations_wait_for_classification() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 120, 64, 0);
        long seq = sim.add(0, 64, 0, 7, 0);
        sim.step();
        assertNull(sim.outcomes.get(seq), "classified within the prefetch");
        assertEquals(1, sim.engine.bufferedOps().size(), "the add waits");
        for (int i = 0; i < 10 && !sim.outcomes.containsKey(seq); i++) {
            sim.step();
        }
        OpOutcome o = sim.outcomes.get(seq);
        assertNotNull(o, "never classified");
        assertEquals(OpOutcome.Outcome.ZONE, o.outcome());
        ZoneView z = sim.zoneAt(120, 64, 0);
        assertEquals(121, z.cellCount());
        assertEquals(7, z.moles(N2), EPS, "all the buffered gas");
    }

    /**
     * A room reaching an unloaded chunk stays unresolved: the add is voided as at a paused
     * region and no zone forms; once the chunk loads, a new add forms the zone (GEO-024).
     */
    @Test
    void geo_024_room_reaching_unloaded_chunk_is_unresolved() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 40, 64, 0); // chunks x 0, 1, 2
        long far = SectionKeys.chunk(2, 0);
        sim.world.unload(far);
        long seq = sim.add(0, 64, 0, 7, 0);
        for (int i = 0; i < 5 && !sim.outcomes.containsKey(seq); i++) {
            sim.step();
        }
        OpOutcome o = sim.outcomes.get(seq);
        assertNotNull(o);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, o.outcome());
        assertEquals(7, o.moved(N2), EPS, "voided, as offered");
        assertEquals(0, sim.snapshot().zoneCount());

        sim.world.load(far);
        sim.pipeline.chunkLoaded(far);
        long again = sim.add(0, 64, 0, 7, 0);
        for (int i = 0; i < 5 && !sim.outcomes.containsKey(again); i++) {
            sim.step();
        }
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(again).outcome());
        assertEquals(41, sim.zoneAt(40, 64, 0).cellCount());
    }

    /** Block changes away from zones are dropped at batch close: nothing is captured (GEO-011, CORE-054). */
    @Test
    void core_054_changes_away_from_zones_cost_no_captures() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        int before = sim.world.captures;
        sim.world.box(200, 60, 200, 260, 70, 260, TestWorld.SOLID);
        sim.steps(2);
        assertEquals(before, sim.world.captures, "far changes were captured");
        assertEquals(1, sim.snapshot().zoneCount());
        // A change next to the zone is captured.
        sim.world.set(0, 64, 0, TestWorld.SOLID);
        sim.step();
        assertTrue(sim.world.captures > before);
        assertEquals(26, sim.zoneAt(1, 65, 1).cellCount());
    }

    /**
     * Walling a zone in two shares its gas by the remnants' volumes, the wall's displaced gas
     * included: changed cells are refreshed before their neighbours (CORE-022, CORE-024, CORE-064).
     */
    @Test
    void wall_splits_zone_by_volume() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 5, 66, 3);
        sim.add(1, 65, 2, 30, 0);
        sim.step();
        sim.world.box(3, 64, 1, 3, 66, 3, TestWorld.SOLID);
        sim.step();
        ZoneView west = sim.zoneAt(1, 65, 2);
        ZoneView east = sim.zoneAt(5, 65, 2);
        assertNotNull(west);
        assertNotNull(east);
        assertTrue(west.id() != east.id(), "still one zone");
        assertEquals(18, west.cellCount());
        assertEquals(18, east.cellCount());
        assertEquals(15, west.moles(N2), EPS);
        assertEquals(15, east.moles(N2), EPS);
    }

    /** An operation above the world acts on the open air at once instead of waiting forever. */
    @Test
    void operation_outside_world_height_is_open_air() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        long seq = sim.add(0, TestWorld.MAX_SECTION_Y * 16 + 40, 0, 1, 0);
        sim.step();
        assertEquals(OpOutcome.Outcome.AMBIENT, sim.outcomes.get(seq).outcome());
    }
}
