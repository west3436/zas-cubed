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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Stale results (CORE-070) and their re-run without loss (CORE-074), architecture §3.3. */
class RejectionTest {
    private static final double EPS = 1e-9;

    /** Unloads and reloads a chunk between batch close and apply, as the server could. */
    private static void bounce(Sim sim, long chunk) {
        sim.world.unload(chunk);
        sim.pipeline.chunkUnloaded(chunk);
        sim.world.load(chunk);
        sim.pipeline.chunkLoaded(chunk);
    }

    /**
     * A chunk under a zone unloads while its tick is in flight: the component is discarded, the
     * previous state stays published, and the operation runs in the next tick, once (CORE-070,
     * CORE-074).
     */
    @Test
    void core_070_stale_component_is_discarded_and_redone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 10, 0);
        sim.step();
        ZoneView before = sim.zoneAt(1, 65, 1);
        assertEquals(10, before.moles(N2), EPS);

        long seq = sim.add(1, 65, 1, 5, 0);
        sim.betweenCloseAndApply = s -> bounce(s, SectionKeys.chunk(0, 0));
        DimensionPipeline.Applied a = sim.step();
        sim.betweenCloseAndApply = s -> {};
        assertEquals(1, a.rejected(), "the component wasn't rejected");
        assertEquals(1, a.requeued());
        assertNull(sim.outcomes.get(seq), "the rejected operation completed");
        assertEquals(before, sim.zoneAt(1, 65, 1), "the previous state isn't published");
        assertTrue(sim.snapshot().hasRejections());

        sim.step();
        OpOutcome o = sim.outcomes.get(seq);
        assertNotNull(o, "the operation wasn't run again");
        assertEquals(OpOutcome.Outcome.ZONE, o.outcome());
        assertEquals(15, sim.zoneAt(1, 65, 1).moles(N2), EPS, "gas was lost or doubled");
        assertEquals(1, sim.completions.get(seq));
    }

    /** A change elsewhere doesn't reject a zone whose chunks stayed loaded. */
    @Test
    void unrelated_unload_rejects_nothing() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 10, 0);
        sim.step();
        sim.add(1, 65, 1, 5, 0);
        sim.betweenCloseAndApply = s -> bounce(s, SectionKeys.chunk(9, 9));
        assertEquals(0, sim.step().rejected());
    }

    /**
     * Two equal rooms are opened to each other and merge during the rejected tick: the restore
     * splits them back under their old ids, and conservation holds (CORE-074).
     */
    @Test
    void core_074_rejected_merge_is_undone() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 3, 66, 3);
        sim.world.room(5, 64, 1, 7, 66, 3);
        sim.add(2, 65, 2, 10, 0);
        sim.add(6, 65, 2, 10, 0);
        sim.step();
        long a = sim.zoneAt(2, 65, 2).id();
        long b = sim.zoneAt(6, 65, 2).id();
        sim.world.set(4, 65, 2, TestWorld.AIR);
        sim.betweenCloseAndApply = s -> bounce(s, SectionKeys.chunk(0, 0));
        DimensionPipeline.Applied rejected = sim.step();
        sim.betweenCloseAndApply = s -> {};
        assertEquals(1, rejected.rejected());
        assertEquals(1, sim.engine.graph().mergeCount(), "the rooms didn't merge in the rejected tick");
        assertEquals(b, sim.zoneAt(6, 65, 2).id(), "the published state shows the merge");
        sim.step();
        assertNotNull(sim.engine.graph().zone(a));
        assertEquals(1, sim.snapshot().zoneCount(), "they should merge again");
        assertEquals(20, sim.snapshot().zones().get(0).moles(N2), 1e-9);
    }

    /**
     * Random operations, walls opening and closing between sealed rooms, and chunks unloading
     * mid-tick: every operation completes exactly once, the checker never reports, and the zones
     * end up holding exactly what the completed operations moved (CORE-074, CORE-036).
     */
    @Test
    void core_074_fuzz_random_discards_conserve_gas() {
        for (long seed = 1; seed <= 6; seed++) {
            fuzz(seed);
        }
    }

    private static void fuzz(long seed) {
        Random random = new Random(seed);
        Sim sim = new Sim(Ambient.VACUUM);
        // Six rooms in a row across two chunks, separated by single walls at x = 4, 8, ... 20.
        int[] xs = {1, 5, 9, 13, 17, 21};
        for (int x : xs) {
            sim.world.room(x, 64, 1, x + 2, 66, 3);
        }
        List<Long> seqs = new ArrayList<>();
        int rejections = 0;
        for (int t = 0; t < 60; t++) {
            int ops = random.nextInt(4);
            for (int i = 0; i < ops; i++) {
                int x = xs[random.nextInt(xs.length)] + random.nextInt(3);
                if (random.nextInt(3) == 0) {
                    seqs.add(sim.remove(x, 65, 2, random.nextDouble() * 3, random.nextDouble() * 3));
                } else {
                    seqs.add(sim.add(x, 65, 2, random.nextDouble() * 5, random.nextDouble() * 5));
                }
            }
            if (random.nextInt(4) == 0) {
                int wall = 4 + 4 * random.nextInt(xs.length - 1);
                boolean open = random.nextBoolean();
                sim.world.set(wall, 65, 2, open ? TestWorld.AIR : TestWorld.SOLID);
            }
            boolean bounceNow = random.nextInt(3) == 0;
            long chunk = SectionKeys.chunk(random.nextInt(2), 0);
            sim.betweenCloseAndApply = s -> {
                if (bounceNow) {
                    bounce(s, chunk);
                }
            };
            rejections += sim.step().rejected();
        }
        sim.betweenCloseAndApply = s -> {};
        sim.steps(3);
        assertTrue(rejections > 0, "seed " + seed + " rejected nothing");
        double[] expected = new double[2];
        for (long seq : seqs) {
            assertEquals(1, sim.completions.getOrDefault(seq, 0), "seed " + seed + " operation " + seq);
            OpOutcome o = sim.outcomes.get(seq);
            if (o.outcome() == OpOutcome.Outcome.ZONE) {
                boolean add = sim.addSeqs.contains(seq);
                expected[N2] += add ? o.moved(N2) : -o.moved(N2);
                expected[O2] += add ? o.moved(O2) : -o.moved(O2);
            }
        }
        double[] actual = new double[2];
        sim.engine.graph().totalMoles(actual);
        double[] published = new double[2];
        for (ZoneView z : sim.snapshot().zones()) {
            published[N2] += z.moles(N2);
            published[O2] += z.moles(O2);
        }
        for (int g = 0; g < 2; g++) {
            double tolerance = 1e-9 * Math.max(1, Math.abs(expected[g]));
            assertEquals(expected[g], actual[g], tolerance, "seed " + seed + " gas " + g);
            assertEquals(actual[g], published[g], tolerance, "seed " + seed + " published gas " + g);
        }
    }

    /**
     * A pinned room merges into an unpinned one during a rejected tick, pinning the survivor: the
     * restore gives the pin back to the recreated room and takes it from the survivor (D-64,
     * CORE-074).
     */
    @Test
    void d_064_rejected_merge_restores_the_pins() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 3, 66, 3);
        sim.world.room(5, 64, 1, 7, 66, 3);
        sim.add(2, 65, 2, 10, 0);
        sim.add(6, 65, 2, 10, 0);
        sim.step();
        // The opening joins room a, so b is absorbed.
        long a = sim.zoneAt(2, 65, 2).id();
        long b = sim.zoneAt(6, 65, 2).id();
        sim.engine.graph().setPinned(sim.engine.graph().zone(b), true);
        sim.world.set(4, 65, 2, TestWorld.AIR);
        sim.betweenCloseAndApply = s -> bounce(s, SectionKeys.chunk(0, 0));
        assertEquals(1, sim.step().rejected());
        sim.betweenCloseAndApply = s -> {};
        assertNull(sim.engine.graph().zone(b), "the rooms didn't merge in the rejected tick");
        assertTrue(sim.engine.graph().zone(a).isPinned(), "the survivor takes the pin");

        // Wall the rooms off again, so the redone tick doesn't merge them.
        sim.world.set(4, 65, 2, TestWorld.SOLID);
        sim.step();
        assertNotNull(sim.engine.graph().zone(b), "the restore recreates the absorbed room");
        assertTrue(sim.engine.graph().zone(b).isPinned(), "the recreated room keeps its pin");
        assertFalse(sim.engine.graph().zone(a).isPinned(), "the survivor's pin is undone");
        assertTrue(sim.snapshot().zone(b).pinned());
        assertFalse(sim.snapshot().zone(a).pinned());
        assertEquals(10, sim.snapshot().zone(b).moles(N2), 1e-9);
    }
}
