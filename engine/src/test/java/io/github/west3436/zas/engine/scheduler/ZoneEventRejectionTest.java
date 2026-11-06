package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.ZoneEvent;
import io.github.west3436.zas.engine.zone.ZoneEvent.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Zone events through a whole atmos tick (API-030): what a rejected component takes back fires once
 * it is redone, and what happened before the undo point fires once, rejected or not (CORE-074).
 */
class ZoneEventRejectionTest {
    /** Unloads and reloads a chunk between batch close and apply, as the server could. */
    private static void bounce(Sim sim, long chunk) {
        sim.world.unload(chunk);
        sim.pipeline.chunkUnloaded(chunk);
        sim.world.load(chunk);
        sim.pipeline.chunkLoaded(chunk);
    }

    private static List<Kind> kinds(DimensionPipeline.Applied applied) {
        return applied.zoneEvents().stream().map(ZoneEvent::kind).toList();
    }

    private static DimensionPipeline.Applied rejectedStep(Sim sim) {
        sim.betweenCloseAndApply = s -> bounce(s, SectionKeys.chunk(0, 0));
        DimensionPipeline.Applied applied = sim.step();
        sim.betweenCloseAndApply = s -> {};
        assertEquals(1, applied.rejected(), "the component wasn't rejected");
        return applied;
    }

    @Test
    void api_030_a_new_zone_reaches_the_apply_once() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 10, 0);

        DimensionPipeline.Applied first = sim.step();
        assertEquals(List.of(Kind.CREATED), kinds(first));
        assertEquals(sim.zoneAt(1, 65, 1).id(), first.zoneEvents().get(0).zoneId());

        assertEquals(List.of(), kinds(sim.step()));
    }

    /** Created before the undo point, so it stands: the gas is what the rejection puts off. */
    @Test
    void api_030_a_zone_created_in_a_rejected_tick_fires_once() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 10, 0);

        List<Kind> seen = new ArrayList<>(kinds(rejectedStep(sim)));
        seen.addAll(kinds(sim.step()));
        seen.addAll(kinds(sim.step()));

        assertEquals(1, seen.stream().filter(Kind.CREATED::equals).count(), seen.toString());
    }

    @Test
    void api_030_a_rejected_merge_fires_when_it_is_redone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 3, 66, 3);
        sim.world.room(5, 64, 1, 7, 66, 3);
        sim.add(2, 65, 2, 10, 0);
        sim.add(6, 65, 2, 10, 0);
        sim.step();
        sim.world.set(4, 65, 2, TestWorld.AIR);

        assertFalse(kinds(rejectedStep(sim)).contains(Kind.MERGED), "a merge that didn't stand");

        List<ZoneEvent> redone = sim.step().zoneEvents();
        assertEquals(1, redone.stream().filter(e -> e.kind() == Kind.MERGED).count(), "the merge that did: " + redone);
        assertEquals(1, sim.snapshot().zoneCount());
    }

    /** A breach is geometry, so it stands in a rejected tick, and the restored zone doesn't fire it again. */
    @Test
    void api_030_a_breach_in_a_rejected_tick_fires_once() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 500);
        sim.step();
        long zone = sim.zoneAt(1, 65, 1).id();
        sim.world.set(3, 65, 1, TestWorld.AIR);

        List<ZoneEvent> seen = new ArrayList<>(rejectedStep(sim).zoneEvents());
        for (int i = 0; i < 4; i++) {
            seen.addAll(sim.step().zoneEvents());
        }

        List<ZoneEvent> breached =
                seen.stream().filter(e -> e.kind() == Kind.BREACHED).toList();
        assertEquals(1, breached.size(), seen.toString());
        assertEquals(zone, breached.get(0).zoneId());
    }

    /**
     * A sealed room at the ambient is opened, and the tick it dissolves in is rejected. The dissolution
     * fires when it's redone. It goes in the tick it's opened, so nothing says it was breached.
     */
    @Test
    void api_030_a_rejected_dissolution_fires_when_it_is_redone() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 1);
        sim.step();
        long zone = sim.zoneAt(1, 65, 1).id();
        sim.world.set(3, 65, 1, TestWorld.AIR);

        boolean[] bounced = {false};
        sim.betweenCloseAndApply = s -> {
            if (!bounced[0] && s.engine.graph().dissolveCount() == 1) {
                bounced[0] = true;
                bounce(s, SectionKeys.chunk(0, 0));
            }
        };
        List<ZoneEvent> seen = new ArrayList<>();
        int rejections = 0;
        for (int i = 0; i < 16; i++) {
            DimensionPipeline.Applied applied = sim.step();
            rejections += applied.rejected();
            assertFalse(applied.rejected() > 0 && kinds(applied).contains(Kind.DISSOLVED), "a dissolution undone");
            seen.addAll(applied.zoneEvents());
        }

        assertEquals(1, rejections, "the tick it dissolved in wasn't rejected: " + seen);
        assertEquals(2, sim.engine.graph().dissolveCount(), "it dissolved, was restored and dissolved again");
        List<ZoneEvent> dissolved =
                seen.stream().filter(e -> e.kind() == Kind.DISSOLVED).toList();
        assertEquals(1, dissolved.size(), seen.toString());
        assertEquals(zone, dissolved.get(0).zoneId());
        assertEquals(0, seen.stream().filter(e -> e.kind() == Kind.BREACHED).count(), seen.toString());
    }
}
