package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     * A wall built across a zone while it was saved, which nothing saw, makes it two zones when it's
     * rebuilt (GEO-013), and they share its gas by volume (CORE-064).
     */
    @Test
    void geo_013_a_wall_built_while_saved_splits_the_rebuilt_zone() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(11, 64, 0, 20, 66, 2);
        sim.add(12, 65, 1, 0, 1000);
        sim.steps(2);
        ZoneView before = sim.zoneAt(12, 65, 1);
        assertEquals(90, before.cellCount());

        long west = SectionKeys.chunk(0, 0);
        long east = SectionKeys.chunk(1, 0);
        ChunkRecord westRecord = sim.unload(west);
        ChunkRecord eastRecord = sim.unload(east);
        sim.step();
        assertNull(sim.zoneAt(12, 65, 1), "the zone stayed in memory");

        sim.world.box(18, 64, 0, 18, 66, 2, TestWorld.SOLID);
        sim.load(west, westRecord);
        sim.load(east, eastRecord);
        sim.steps(2);

        ZoneView westPart = sim.zoneAt(12, 65, 1);
        ZoneView eastPart = sim.zoneAt(19, 65, 1);
        assertNotEquals(westPart.id(), eastPart.id());
        assertEquals(63, westPart.cellCount());
        assertEquals(18, eastPart.cellCount());
        assertFalse(westPart.paused());
        assertFalse(eastPart.paused());
        assertEquals(0, westPart.ambientEdges());
        assertEquals(0, eastPart.ambientEdges());
        close(before.totalMoles() * 63 / 81, westPart.totalMoles(), "the west part's gas");
        close(before.totalMoles() * 18 / 81, eastPart.totalMoles(), "the east part's gas");
    }

    /**
     * A zone stays in memory while one of its chunks is unloaded, and that chunk's cells are all filled
     * in meanwhile. The chunk's saved share is gas the zone still holds, so it isn't taken in again
     * when the chunk loads and those cells go (DATA-008). This is the case behind seed 1 of the
     * integrity fuzz (NFR-010).
     */
    @Test
    void data_008_a_share_isnt_taken_twice_when_its_cells_were_filled_while_unloaded() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        sim.world.room(12, 68, 1, 16, 70, 3); // only x = 16 is in the far chunk
        sim.add(13, 69, 2, 50, 0);
        sim.steps(2);
        ZoneView before = sim.zoneAt(13, 69, 2);
        assertEquals(45, before.cellCount());

        long far = SectionKeys.chunk(1, 0);
        ChunkRecord saved = sim.unload(far);
        sim.step();
        assertTrue(sim.zoneAt(13, 69, 2).paused(), "the zone isn't paused");
        for (int y = 68; y <= 70; y++) {
            for (int z = 1; z <= 3; z++) {
                sim.world.set(16, y, z, TestWorld.SOLID);
            }
        }
        sim.load(far, saved);
        sim.steps(2);
        ZoneView after = sim.zoneAt(13, 69, 2);
        assertEquals(before.id(), after.id(), "the zone's id");
        assertEquals(36, after.cellCount(), "the zone's cells");
        close(before.moles(N2), after.moles(N2), "the zone's nitrogen");
    }

    /**
     * A zone across two chunks leaves memory, while another zone keeps the far chunk's section
     * mirrored. When the near chunk loads, the zone comes back with only that chunk's cells and share:
     * its cells in the far chunk are still unloaded, so nothing may classify them, even though the
     * mirror holds them (GEO-024). When the far chunk loads, its share joins, and the zone holds what
     * it held, with no ambient gas in it (DATA-008). This is the case behind seed 5 of the integrity fuzz
     * (NFR-010): the far cells joined with the ambient's gas, and the far chunk's share was then ignored.
     */
    @Test
    void data_008_a_stored_zone_doesnt_take_in_cells_of_a_chunk_still_unloaded() {
        Sim sim = new Sim(Ambient.STANDARD_AIR).withoutHeat();
        sim.world.room(12, 68, 1, 19, 70, 3); // chunks (0, 0) and (1, 0)
        sim.world.room(28, 68, 8, 35, 70, 10); // chunks (1, 0) and (2, 0), the same section there
        sim.add(13, 69, 2, 0, 50);
        sim.add(29, 69, 9, 0, 10);
        sim.steps(2);
        ZoneView before = sim.zoneAt(13, 69, 2);
        long id = before.id();
        long keeper = sim.zoneAt(29, 69, 9).id();
        assertEquals(72, before.cellCount());

        long near = SectionKeys.chunk(0, 0);
        long far = SectionKeys.chunk(1, 0);
        ChunkRecord nearRecord = sim.unload(near);
        ChunkRecord farRecord = sim.unload(far);
        sim.step();
        assertNull(sim.snapshot().zone(id), "the zone stayed in memory");
        assertTrue(sim.snapshot().zone(keeper).paused(), "the other zone isn't paused");
        assertTrue(
                sim.engine.mirror().contains(SectionKeys.sectionOfBlock(16, 69, 2)),
                "the far chunk's section isn't mirrored");

        sim.load(near, nearRecord);
        sim.steps(2);
        ZoneView part = sim.snapshot().zone(id);
        assertNotNull(part, "the zone wasn't rebuilt");
        assertTrue(part.paused(), "the zone resumed without its far chunk");
        assertEquals(nearRecord.share(id).cellCount(), part.cellCount(), "only the near chunk's cells");
        close(nearRecord.share(id).moles(N2), part.moles(N2), "only the near share's nitrogen");
        close(nearRecord.share(id).moles(O2), part.moles(O2), "only the near share's oxygen");

        sim.load(far, farRecord);
        sim.steps(2);
        ZoneView whole = sim.snapshot().zone(id);
        assertNotNull(whole, "the zone isn't there");
        assertFalse(whole.paused(), "the zone is still paused");
        assertEquals(before.cellCount(), whole.cellCount(), "the zone's cells");
        close(before.moles(N2), whole.moles(N2), "the zone's nitrogen");
        close(before.moles(O2), whole.moles(O2), "the zone's oxygen");
    }

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
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
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
     * A sealed shaft from the ceiling of a room whose interior tops out at y 66, rising past the
     * sections kept mirrored around a zone, so a fill up it asks for a section the mirror lacks.
     */
    private static void shaftAbove(TestWorld world, int x, int z) {
        world.room(x, 68, z, x, 100, z);
    }

    /**
     * A rebuilt zone waits only for what its own surface opens onto. Another zone's breach, still
     * fetching sections elsewhere in the dimension, doesn't keep it paused (GEO-033, #209).
     */
    @Test
    void geo_033_rebuilt_zone_doesnt_wait_for_another_zones_breach() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        long chunk = SectionKeys.chunk(0, 0);
        sim.world.room(1, 64, 1, 4, 66, 3);
        sim.world.room(100, 64, 1, 104, 66, 3);
        shaftAbove(sim.world, 102, 2);
        sim.add(2, 65, 2, 40, 0);
        sim.add(101, 65, 2, 40, 0);
        sim.step();
        long id = sim.zoneAt(2, 65, 2).id();
        long other = sim.zoneAt(101, 65, 2).id();
        ChunkRecord saved = sim.unload(chunk);
        sim.step();
        assertNull(sim.snapshot().zone(id));

        sim.load(chunk, saved);
        sim.world.set(102, 67, 2, TestWorld.AIR); // the other zone's ceiling opens into its shaft
        sim.step();
        assertTrue(sim.engine.classifier().hasPending(), "the other zone's breach isn't waiting for a section");
        assertTrue(sim.engine.classifier().hasPendingFor(other), "the wait isn't the other zone's");
        assertFalse(sim.engine.classifier().hasPendingFor(id), "the wait is the rebuilt zone's");
        assertFalse(sim.snapshot().zone(id).paused(), "the rebuilt zone waited for another zone's breach");
        close(40, sim.snapshot().zone(id).moles(Sim.N2), "the rebuilt zone's gas");

        sim.step();
        assertFalse(sim.engine.classifier().hasPending(), "the other zone's breach didn't finish");
    }

    /**
     * A rebuilt zone stays paused while what its own surface opens onto waits for sections, and
     * resumes once that is classified (GEO-023, GEO-033, architecture §4.5).
     */
    @Test
    void geo_033_rebuilt_zone_waits_for_its_own_surface() {
        Sim sim = new Sim(Ambient.VACUUM).withoutHeat();
        long chunk = SectionKeys.chunk(0, 0);
        sim.world.room(1, 64, 1, 4, 66, 3);
        shaftAbove(sim.world, 2, 2);
        sim.add(2, 65, 2, 40, 0);
        sim.step();
        long id = sim.zoneAt(2, 65, 2).id();
        int cells = sim.snapshot().zone(id).cellCount();
        ChunkRecord saved = sim.unload(chunk);
        sim.step();
        assertNull(sim.snapshot().zone(id));

        sim.world.set(2, 67, 2, TestWorld.AIR); // its ceiling opens into the shaft while it's stored
        sim.load(chunk, saved);
        sim.step();
        assertTrue(sim.engine.classifier().hasPendingFor(id), "the zone's surface isn't waiting for a section");
        assertTrue(sim.snapshot().zone(id).paused(), "the zone resumed before its surface was classified");

        sim.steps(2);
        ZoneView z = sim.snapshot().zone(id);
        assertFalse(z.paused(), "the zone didn't resume");
        assertTrue(z.cellCount() > cells, "the shaft didn't join the zone");
        close(40, z.moles(Sim.N2), "the zone's gas");
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

    /**
     * A chunk that loads and is gone again before the next atmos tick keeps its saved shares: its
     * sections can't be captured, though the server hasn't reported its unload yet, so its record
     * waits instead of going to the worker without its cells, where the shares would be dropped. What
     * the chunk saves at its unload still holds them, and the zone comes back when it loads again
     * (DATA-002, #159).
     */
    @Test
    void data_002_record_of_a_chunk_gone_before_its_batch_is_kept() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(2, 64, 2, 5, 66, 5);
        sim.add(3, 65, 3, 36, 0);
        sim.step();
        long id = sim.zoneAt(3, 65, 3).id();
        long chunk = SectionKeys.chunk(0, 0);
        ChunkRecord saved = sim.unload(chunk);
        sim.step();
        assertNull(sim.snapshot().zone(id), "the zone is still in memory");

        sim.load(chunk, saved);
        sim.world.unload(chunk); // gone, before the server reports the unload
        sim.steps(2);
        ChunkRecord atUnload = sim.pipeline.recordFor(chunk, false);
        assertNotNull(atUnload, "the chunk saves nothing at its unload");
        assertNotNull(atUnload.share(id), "the chunk's save lost the zone's share");
        assertEquals(saved.share(id).moles(N2), atUnload.share(id).moles(N2), 1e-9, "the saved share's gas");
        sim.pipeline.chunkUnloaded(chunk);

        sim.load(chunk, atUnload);
        sim.steps(2);
        ZoneView z = sim.snapshot().zone(id);
        assertNotNull(z, "the zone didn't come back");
        assertEquals(saved.share(id).moles(N2), z.moles(N2), 1e-9, "the zone's gas");
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
