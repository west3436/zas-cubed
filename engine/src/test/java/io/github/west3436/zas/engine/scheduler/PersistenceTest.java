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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Chunk-aligned persistence (DATA-002, DATA-008, DATA-010, DATA-011, DATA-004, architecture
 * §4.5): zones saved as per-chunk shares, taken out of memory once all their chunks unload, and
 * rebuilt from the shares as the chunks load again.
 */
class PersistenceTest {
    private static final double REL = 1e-9;
    private static final long[] CHUNKS = {
        SectionKeys.chunk(0, 0), SectionKeys.chunk(1, 0), SectionKeys.chunk(0, 1), SectionKeys.chunk(1, 1)
    };

    private static void close(double expected, double actual, String what) {
        assertTrue(
                Math.abs(expected - actual) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** Three sealed rooms across the four chunks around the origin. */
    private static void rooms(TestWorld world) {
        world.room(10, 64, 2, 21, 66, 5); // two chunks along x
        world.room(3, 70, 12, 6, 72, 19); // two chunks along z
        world.room(13, 64, 13, 18, 67, 18); // all four chunks
    }

    private static final int[][] SEEDS = {{11, 65, 3}, {4, 71, 13}, {14, 65, 14}};

    /**
     * Random zones are saved chunk by chunk, leave memory, and are rebuilt one chunk at a time in
     * random order: each rebuilt zone holds the sum of its shares, which is what it held before, to
     * 1e-9 per gas, and stays paused until its last chunk loads (DATA-002, DATA-008, GEO-030).
     */
    @Test
    void data_008_random_zones_reassemble_from_their_chunks() {
        for (long seed = 1; seed <= 8; seed++) {
            reassemble(seed);
        }
    }

    private static void reassemble(long seed) {
        Random random = new Random(seed);
        Sim sim = new Sim(Ambient.VACUUM);
        rooms(sim.world);
        for (int[] p : SEEDS) {
            sim.add(p[0], p[1], p[2], 1 + random.nextDouble() * 50, random.nextDouble() * 20);
        }
        sim.steps(2);
        Map<Long, ZoneView> before = new HashMap<>();
        for (ZoneView z : sim.snapshot().zones()) {
            before.put(z.id(), z);
        }
        assertEquals(3, before.size());

        List<Long> order = new ArrayList<>();
        for (long c : CHUNKS) {
            order.add(c);
        }
        Collections.shuffle(order, random);
        Map<Long, ChunkRecord> saved = new HashMap<>();
        Map<Long, double[]> shareSums = new HashMap<>();
        for (long chunk : order) {
            ChunkRecord r = sim.unload(chunk);
            assertNotNull(r, "a chunk with zoned cells saved nothing");
            saved.put(chunk, r);
            for (ZoneShare share : r.zones()) {
                double[] sum = shareSums.computeIfAbsent(share.zoneId(), k -> new double[2]);
                sum[N2] += share.moles(N2);
                sum[O2] += share.moles(O2);
            }
            sim.step();
            for (ZoneView z : sim.snapshot().zones()) {
                boolean touches = false;
                for (long c : sim.snapshot().zoneChunks(z.id())) {
                    touches |= saved.containsKey(c);
                }
                assertEquals(touches, z.paused(), "zone " + z.id() + " paused wrongly");
            }
        }
        assertEquals(0, sim.snapshot().zoneCount(), "zones stayed in memory with every chunk unloaded");
        for (ZoneView z : before.values()) {
            close(z.moles(N2), shareSums.get(z.id())[N2], "N2 shares of zone " + z.id());
            close(z.moles(O2), shareSums.get(z.id())[O2], "O2 shares of zone " + z.id());
        }

        Collections.shuffle(order, random);
        for (int i = 0; i < order.size(); i++) {
            sim.load(order.get(i), saved.get(order.get(i)));
            sim.step();
            for (ZoneView z : sim.snapshot().zones()) {
                boolean complete = true;
                for (long c : sim.snapshot().zoneChunks(z.id())) {
                    complete &= order.subList(0, i + 1).contains(c);
                }
                if (!complete) {
                    assertTrue(z.paused(), "a partly rebuilt zone isn't paused");
                }
            }
        }
        assertEquals(3, sim.snapshot().zoneCount());
        for (ZoneView z : before.values()) {
            ZoneView now = sim.snapshot().zone(z.id());
            assertNotNull(now, "zone " + z.id() + " wasn't rebuilt under its id");
            assertFalse(now.paused());
            assertEquals(z.cellCount(), now.cellCount());
            close(z.volume(), now.volume(), "volume");
            close(z.moles(N2), now.moles(N2), "N2 of zone " + z.id());
            close(z.moles(O2), now.moles(O2), "O2 of zone " + z.id());
            close(z.temperature(), now.temperature(), "temperature");
        }
    }

    /**
     * Each chunk saves only its own cells and its volume share of each zone: a zone across four
     * chunks has four shares with the same pressure, summing to the zone (DATA-002).
     */
    @Test
    void data_002_each_chunk_saves_its_own_cells() {
        Sim sim = new Sim(Ambient.VACUUM);
        rooms(sim.world);
        sim.add(14, 65, 14, 36, 0);
        sim.step();
        ZoneView z = sim.zoneAt(14, 65, 14);
        assertEquals(4, sim.snapshot().zoneChunks(z.id()).length);
        int cells = 0;
        for (long chunk : CHUNKS) {
            ChunkRecord r = sim.pipeline.recordFor(chunk, false);
            ZoneShare share = r.share(z.id());
            assertNotNull(share);
            cells += share.cellCount();
            for (long cell : share.cells()) {
                assertEquals(chunk, SectionKeys.chunkOf(io.github.west3436.zas.engine.classify.CellIds.section(cell)));
            }
            close(z.moles(N2) * share.cellCount() / z.cellCount(), share.moles(N2), "the share by volume");
        }
        assertEquals(z.cellCount(), cells);
        assertNull(sim.pipeline.recordFor(SectionKeys.chunk(5, 5), false));
    }

    /**
     * A change held for a paused zone is saved with its chunk, and applies when the zone is rebuilt
     * and resumes, even after the zone left memory (DATA-010, GEO-037).
     */
    @Test
    void data_010_held_change_persists_through_storage() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.add(13, 65, 2, 40, 0);
        sim.step();
        long id = sim.zoneAt(13, 65, 2).id();
        long west = SectionKeys.chunk(0, 0);
        long east = SectionKeys.chunk(1, 0);
        ChunkRecord eastSaved = sim.unload(east);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR); // a hole in the west wall, held
        sim.step();
        ChunkRecord westSaved = sim.unload(west);
        assertEquals(1, westSaved.heldBlocks().length, "the held change wasn't saved");
        sim.step();
        assertEquals(0, sim.snapshot().zoneCount());

        sim.load(west, westSaved);
        sim.load(east, eastSaved);
        sim.steps(3); // the rebuilt zone's surface may take a round of fetching to classify
        ZoneView z = sim.snapshot().zone(id);
        assertNotNull(z);
        assertFalse(z.paused());
        assertTrue(z.breached(), "the held change didn't apply after the restart");
    }

    /**
     * Queued additions are saved at shutdown and applied after load; removals are not saved
     * (DATA-011). A new engine stands for the next session.
     */
    @Test
    void data_011_queued_additions_saved_at_shutdown() {
        Sim before = new Sim(Ambient.VACUUM);
        before.world.room(2, 64, 2, 5, 66, 5);
        before.add(3, 65, 3, 10, 0);
        before.step();
        long chunk = SectionKeys.chunk(0, 0);
        before.add(3, 65, 3, 7, 2); // queued, not yet applied
        before.remove(3, 65, 3, 1, 0); // queued removal: not saved
        ChunkRecord saved = before.pipeline.recordFor(chunk, true);
        assertEquals(1, saved.queuedAdditions().size());
        assertEquals(1, before.pipeline.outstandingAdditions().size());
        assertEquals(2, before.pipeline.outstanding().size());
        assertTrue(before.pipeline.recordFor(chunk, false).queuedAdditions().isEmpty());

        Sim after = new Sim(Ambient.VACUUM);
        after.world.room(2, 64, 2, 5, 66, 5);
        after.load(chunk, saved);
        after.steps(2);
        ZoneView z = after.zoneAt(3, 65, 3);
        assertNotNull(z);
        assertEquals(17, z.moles(N2), 1e-9);
        assertEquals(2, z.moles(O2), 1e-9);
        assertTrue(after.pipeline.outstanding().isEmpty());
    }

    /**
     * A chunk whose saved data is lost loads with its cells unzoned; the rest of its zone is
     * rebuilt from the other chunks' shares (DATA-004).
     */
    @Test
    void data_004_lost_chunk_reverts_to_unzoned() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.add(13, 65, 2, 40, 0);
        sim.step();
        long id = sim.zoneAt(13, 65, 2).id();
        long west = SectionKeys.chunk(0, 0);
        long east = SectionKeys.chunk(1, 0);
        ChunkRecord westSaved = sim.unload(west);
        sim.step();
        sim.unload(east);
        sim.step();
        sim.load(east, null); // its data was corrupt
        sim.load(west, westSaved);
        sim.steps(3);
        ZoneView z = sim.snapshot().zone(id);
        assertNotNull(z);
        assertFalse(z.paused());
        assertEquals(westSaved.share(id).moles(N2), z.moles(N2), 1e-9, "only the west share's gas");
    }

    /** Zone ids saved in a chunk are never issued again in a later session (CORE-026). */
    @Test
    void saved_ids_are_not_reused() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.engine.reserveZoneIds(50);
        sim.world.room(2, 64, 2, 5, 66, 5);
        sim.add(3, 65, 3, 1, 0);
        sim.step();
        assertEquals(50, sim.zoneAt(3, 65, 3).id());
        assertEquals(51, sim.pipeline.nextZoneId());
    }

    /**
     * A pinned zone is saved with its pin, leaves memory, and is rebuilt pinned from the saved
     * bytes; an unpinned zone beside it comes back unpinned (D-64, API-018, DATA-002).
     */
    @Test
    void d_064_pin_survives_save_and_load() throws Exception {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 3, 66, 3);
        sim.world.room(6, 64, 1, 8, 66, 3);
        sim.add(2, 65, 2, 10, 0);
        sim.add(7, 65, 2, 10, 0);
        sim.step();
        long pinnedId = sim.zoneAt(2, 65, 2).id();
        long plainId = sim.zoneAt(7, 65, 2).id();
        sim.engine.graph().setPinned(sim.engine.graph().zone(pinnedId), true);
        sim.step();
        assertTrue(sim.zoneAt(2, 65, 2).pinned(), "the snapshot shows the pin");
        assertFalse(sim.zoneAt(7, 65, 2).pinned());

        long chunk = SectionKeys.chunk(0, 0);
        byte[] bytes = ChunkRecordCodec.encode(sim.unload(chunk), Sim.REGISTRY);
        sim.step();
        assertEquals(0, sim.snapshot().zoneCount());
        ChunkRecord saved = ChunkRecordCodec.decode(bytes, chunk, Sim.REGISTRY, m -> {
            throw new AssertionError(m);
        });
        sim.load(chunk, saved);
        sim.step();
        assertTrue(sim.engine.graph().zone(pinnedId).isPinned(), "the pin was lost on reload");
        assertFalse(sim.engine.graph().zone(plainId).isPinned());
        assertTrue(sim.snapshot().zone(pinnedId).pinned());
        close(10, sim.snapshot().zone(pinnedId).moles(N2), "N2 of the pinned zone");
    }

    /**
     * A new engine takes over from an old one, as after a geometry palette change: the loaded
     * chunks' records go to it as chunk loads, and the outstanding operations are queued again.
     * Zones in loaded chunks come back with their gas and pins, the queued operations apply once,
     * a zone that spans an unloaded chunk waits for it, and nothing breaks conservation (DATA-008,
     * DATA-009, architecture §4.5).
     */
    @Test
    void data_009_new_engine_rebuilds_from_loaded_records() {
        Sim old = new Sim(Ambient.VACUUM);
        old.world.room(10, 64, 2, 21, 66, 5); // chunks (0, 0) and (1, 0)
        old.world.room(42, 64, 2, 53, 66, 5); // chunks (2, 0) and (3, 0)
        old.add(11, 65, 3, 30, 5);
        old.add(43, 65, 3, 12, 0);
        old.step();
        long loadedId = old.zoneAt(11, 65, 3).id();
        long pausedId = old.zoneAt(43, 65, 3).id();
        old.engine.graph().setPinned(old.engine.graph().zone(pausedId), true);
        old.step();
        long far = SectionKeys.chunk(3, 0);
        ChunkRecord farSaved = old.unload(far);
        old.step();
        assertTrue(old.snapshot().zone(pausedId).paused());
        ZoneView loadedBefore = old.snapshot().zone(loadedId);
        ZoneView pausedBefore = old.snapshot().zone(pausedId);
        old.add(12, 65, 3, 0, 4); // queued, not yet in a batch
        old.remove(12, 65, 3, 2, 0);

        Sim fresh = new Sim(old.world, null, EnclosureClassifier.FACTORY);
        fresh.engine.reserveZoneIds(old.pipeline.nextZoneId());
        for (long chunk : old.pipeline.recordChunks()) {
            if (old.world.isLoaded(chunk)) {
                fresh.pipeline.chunkLoaded(chunk, old.pipeline.recordFor(chunk, false));
            }
        }
        for (GasOp op : old.pipeline.outstanding()) {
            fresh.pipeline.resubmit(op);
        }
        assertEquals(2, fresh.pipeline.outstanding().size());
        fresh.steps(3);
        ZoneView loaded = fresh.snapshot().zone(loadedId);
        assertNotNull(loaded, "the loaded zone wasn't rebuilt under its id");
        assertFalse(loaded.paused());
        assertEquals(loadedBefore.cellCount(), loaded.cellCount());
        close(loadedBefore.moles(N2) - 2, loaded.moles(N2), "N2: the removal, once");
        close(loadedBefore.moles(O2) + 4, loaded.moles(O2), "O2: the addition, once");
        assertTrue(fresh.pipeline.outstanding().isEmpty());
        ZoneView waiting = fresh.snapshot().zone(pausedId);
        assertNotNull(waiting);
        assertTrue(waiting.paused(), "a zone across an unloaded chunk resumed");
        assertTrue(waiting.pinned(), "the pin was lost");

        fresh.load(far, farSaved);
        fresh.steps(2);
        ZoneView paused = fresh.snapshot().zone(pausedId);
        assertFalse(paused.paused());
        assertTrue(paused.pinned());
        assertEquals(pausedBefore.cellCount(), paused.cellCount());
        close(pausedBefore.moles(N2), paused.moles(N2), "N2 of the zone that waited");
    }

    /** {@link DimensionPipeline#resubmit} keeps an operation's kind, amounts and source. */
    @Test
    void resubmit_keeps_the_operation() {
        Sim sim = new Sim(Ambient.VACUUM);
        GasOp add = new GasOp(
                7,
                GasOp.Kind.ADD,
                SectionKeys.block(1, 65, 1),
                Sim.moles(3, 1),
                300,
                io.github.west3436.zas.engine.conservation.LedgerCategory.API,
                false,
                "a");
        GasOp remove = new GasOp(
                9,
                GasOp.Kind.REMOVE,
                SectionKeys.block(2, 65, 1),
                Sim.moles(1, 0),
                0,
                io.github.west3436.zas.engine.conservation.LedgerCategory.API,
                true,
                "b");
        long s1 = sim.pipeline.resubmit(add);
        long s2 = sim.pipeline.resubmit(remove);
        List<GasOp> out = sim.pipeline.outstanding();
        assertEquals(List.of(s1, s2), List.of(out.get(0).seq(), out.get(1).seq()));
        assertEquals(new GasOp(s1, add.kind(), add.block(), add.moles(), 300, add.category(), false, "a"), out.get(0));
        assertEquals(
                new GasOp(s2, remove.kind(), remove.block(), remove.moles(), 0, remove.category(), true, "b"),
                out.get(1));
    }
}
