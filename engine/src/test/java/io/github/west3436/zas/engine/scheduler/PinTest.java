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
import io.github.west3436.zas.engine.persist.ChunkRecordCodec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Zone pins with owners (API-017, API-018) and pinning a point (API-050), through the pipeline. */
class PinTest {
    private static final double EPS = 1e-9;

    private static long pin(Sim sim, int x, int y, int z, String owner) {
        return sim.pipeline.pin(SectionKeys.block(x, y, z), -1, owner);
    }

    /**
     * Pinning a point in a sealed room with no zone forms a zone there holding the ambient's gas,
     * with no gas added or removed, and the result names it (API-050).
     */
    @Test
    void api_050_pin_forms_a_pinned_zone_in_a_sealed_room() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.step();
        assertEquals(0, sim.snapshot().zoneCount(), "a sealed room holding ambient air has no zone");

        long seq = pin(sim, 1, 65, 1, "test:controller");
        sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertNotNull(o, "the pin didn't complete");
        assertEquals(OpOutcome.Outcome.ZONE, o.outcome());
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertNotNull(zone, "no zone formed");
        assertEquals(zone.id(), o.zoneId());
        assertEquals(27, zone.cellCount());
        assertEquals(List.of("test:controller"), zone.pins());
        double volume = zone.volume();
        assertEquals(Ambient.STANDARD_AIR.moles(Ambient.NITROGEN, volume), zone.moles(N2), 1e-6);
        assertEquals(Ambient.STANDARD_AIR.moles(Ambient.OXYGEN, volume), zone.moles(O2), 1e-6);
        assertEquals(Ambient.STANDARD_AIR.temperature(), zone.temperature(), EPS);
        assertEquals(0, sum(o.moved()), "a pin moves no gas");
    }

    /** A sealed vacuum room forms an empty pinned zone (API-050). */
    @Test
    void api_050_pin_in_a_vacuum_room_forms_an_empty_zone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        long seq = pin(sim, 1, 65, 1, "test:controller");
        sim.step();
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(seq).outcome());
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertNotNull(zone);
        assertTrue(zone.pinned());
        assertEquals(0, zone.totalMoles(), EPS);
    }

    /** Pinning a point in the open forms nothing, and the result says it is open (API-050). */
    @Test
    void api_050_pin_in_the_open_forms_nothing() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.box(-2, 63, -2, 4, 63, 4, TestWorld.SOLID); // a floor, open above
        long seq = pin(sim, 1, 64, 1, "test:controller");
        sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.AMBIENT, o.outcome());
        assertEquals(0, o.zoneId());
        assertEquals(0, sim.snapshot().zoneCount());
    }

    /** Pinning a point in an existing zone pins that zone and changes nothing else (API-017). */
    @Test
    void api_017_pin_in_a_zone_pins_it() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 10, 0);
        sim.step();
        ZoneView before = sim.zoneAt(1, 65, 1);
        assertFalse(before.pinned());
        long seq = pin(sim, 0, 64, 0, "test:a");
        sim.step();
        assertEquals(before.id(), sim.outcomes.get(seq).zoneId());
        ZoneView after = sim.zoneAt(1, 65, 1);
        assertEquals(before.id(), after.id());
        assertEquals(10, after.moles(N2), EPS);
        assertEquals(List.of("test:a"), after.pins());
    }

    /**
     * A zone stays pinned while any owner pins it; pinning twice under one owner is one pin, and an
     * owner's unpin takes off only its own (API-017).
     */
    @Test
    void api_017_pins_are_held_by_owner() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        pin(sim, 1, 65, 1, "test:b");
        pin(sim, 1, 65, 1, "test:a");
        pin(sim, 1, 65, 1, "test:a");
        sim.step();
        assertEquals(List.of("test:a", "test:b"), sim.zoneAt(1, 65, 1).pins());
        sim.pipeline.unpin("test:a");
        sim.step();
        assertEquals(List.of("test:b"), sim.zoneAt(1, 65, 1).pins());
        sim.pipeline.unpin("test:nobody");
        sim.step();
        assertEquals(List.of("test:b"), sim.zoneAt(1, 65, 1).pins());
        sim.pipeline.unpin("test:b");
        sim.step();
        assertFalse(sim.zoneAt(1, 65, 1).pinned());
    }

    /**
     * A pinned room opened to the open air keeps exchanging and isn't dissolved, however close to
     * the ambient it gets; once unpinned it dissolves (API-017, CORE-076, D-64).
     */
    @Test
    void api_017_pinned_zone_isnt_dissolved_until_unpinned() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(6, 64, 0, 8, 66, 2);
        pin(sim, 1, 65, 1, "test:controller");
        pin(sim, 7, 65, 1, "test:other");
        sim.step();
        long id = sim.zoneAt(1, 65, 1).id();
        long twin = sim.zoneAt(7, 65, 1).id();
        sim.pipeline.unpin("test:other");
        sim.world.set(1, 67, 1, TestWorld.AIR); // a hole in each ceiling
        sim.world.set(7, 67, 1, TestWorld.AIR);
        sim.steps(10);
        assertNull(sim.snapshot().zone(twin), "the unpinned room should dissolve into the ambient");
        ZoneView kept = sim.snapshot().zone(id);
        assertNotNull(kept, "the pinned room was dissolved");
        assertTrue(kept.breached());

        sim.pipeline.unpin("test:controller");
        sim.steps(2);
        assertNull(sim.snapshot().zone(id), "the room should dissolve once unpinned");
        assertNull(sim.zoneAt(1, 65, 1));
    }

    /** A pinned zone that splits leaves every part pinned, and an unpin reaches them all (API-017, D-64). */
    @Test
    void api_017_unpin_reaches_every_part_of_a_split_zone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 4, 66, 2);
        pin(sim, 1, 65, 1, "test:controller");
        sim.step();
        sim.world.box(2, 64, 0, 2, 66, 2, TestWorld.SOLID); // a wall across the middle
        sim.step();
        ZoneView left = sim.zoneAt(1, 65, 1);
        ZoneView right = sim.zoneAt(3, 65, 1);
        assertNotNull(left);
        assertNotNull(right);
        assertTrue(left.id() != right.id(), "the room didn't split");
        assertEquals(List.of("test:controller"), left.pins());
        assertEquals(List.of("test:controller"), right.pins());
        sim.pipeline.unpin("test:controller");
        sim.step();
        assertFalse(sim.zoneAt(1, 65, 1).pinned());
        assertFalse(sim.zoneAt(3, 65, 1).pinned());
    }

    /** Two pinned zones that merge keep both owners' pins (API-017, D-64). */
    @Test
    void api_017_merge_keeps_both_owners() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(4, 64, 0, 6, 66, 2);
        pin(sim, 1, 65, 1, "test:a");
        pin(sim, 5, 65, 1, "test:b");
        sim.step();
        sim.world.set(3, 65, 1, TestWorld.AIR);
        sim.steps(3);
        assertEquals(1, sim.snapshot().zoneCount(), "the rooms didn't merge");
        assertEquals(List.of("test:a", "test:b"), sim.zoneAt(1, 65, 1).pins());
    }

    /**
     * A pin whose tick is rejected as stale isn't undone or run twice: it is applied before the
     * undo point, as the zone it forms is (API-050, CORE-070).
     */
    @Test
    void api_050_rejected_tick_keeps_the_pin() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 10, 0);
        sim.step();
        long id = sim.zoneAt(1, 65, 1).id();
        long add = sim.add(1, 65, 1, 5, 0);
        long seq = pin(sim, 1, 65, 1, "test:controller");
        sim.betweenCloseAndApply = s -> {
            s.world.unload(SectionKeys.chunk(0, 0));
            s.pipeline.chunkUnloaded(SectionKeys.chunk(0, 0));
            s.world.load(SectionKeys.chunk(0, 0));
            s.pipeline.chunkLoaded(SectionKeys.chunk(0, 0));
        };
        DimensionPipeline.Applied a = sim.step();
        sim.betweenCloseAndApply = s -> {};
        assertEquals(1, a.rejected(), "the component wasn't rejected");
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(seq).outcome(), "the pin didn't complete");
        assertNull(sim.outcomes.get(add));
        sim.step();
        assertTrue(sim.engine.graph().zone(id).isPinned(), "the restore undid the pin");
        assertEquals(15, sim.zoneAt(1, 65, 1).moles(N2), EPS);
        assertEquals(1, sim.completions.get(seq));
    }

    /**
     * A pinned zone is saved with its pins' owners, leaves memory, and comes back with them; a
     * record loaded with an owner's pins taken off comes back without them (API-018, API-017).
     */
    @Test
    void api_018_owners_survive_save_and_load() throws Exception {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 3, 66, 3);
        pin(sim, 2, 65, 2, "test:b");
        pin(sim, 2, 65, 2, "test:a");
        sim.step();
        long id = sim.zoneAt(2, 65, 2).id();
        long chunk = SectionKeys.chunk(0, 0);
        byte[] bytes = ChunkRecordCodec.encode(sim.unload(chunk), Sim.REGISTRY);
        sim.step();
        assertEquals(0, sim.snapshot().zoneCount());
        ChunkRecord saved = ChunkRecordCodec.decode(bytes, chunk, Sim.REGISTRY, m -> {
            throw new AssertionError(m);
        });
        assertEquals(List.of("test:a", "test:b"), saved.share(id).pins());
        sim.load(chunk, saved.withoutPins(Set.of("test:b", "test:gone")));
        sim.step();
        assertEquals(List.of("test:a"), sim.snapshot().zone(id).pins());
    }

    /** A pin queued for a section the worker hasn't mirrored waits for it, then applies once. */
    @Test
    void api_050_pin_waits_for_its_section() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(40, 64, 40, 42, 66, 42);
        long seq = pin(sim, 41, 65, 41, "test:controller");
        sim.steps(3);
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(seq).outcome());
        assertEquals(1, sim.completions.get(seq));
        assertTrue(sim.zoneAt(41, 65, 41).pinned());
    }

    private static double sum(double[] values) {
        double s = 0;
        for (double v : values) {
            s += v;
        }
        return s;
    }
}
