package io.github.west3436.zas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The pure parts of change tracking: coalescing, the drain filter, generations and snapshots. */
class ChangeTrackingTest {

    private static List<Long> keys(List<DirtySection> drained) {
        return drained.stream().map(DirtySection::key).toList();
    }

    // ---- section keys ----

    @Test
    void section_keys_round_trip() {
        int[][] coords = {{0, 0, 0}, {-1, -4, -1}, {1_874_999, 2047, -1_875_000}, {-1_875_000, -2048, 1_874_999}};
        for (int[] c : coords) {
            long key = SectionKeys.section(c[0], c[1], c[2]);
            assertEquals(c[0], SectionKeys.x(key));
            assertEquals(c[1], SectionKeys.y(key));
            assertEquals(c[2], SectionKeys.z(key));
            long chunk = SectionKeys.chunkOf(key);
            assertEquals(c[0], SectionKeys.chunkX(chunk));
            assertEquals(c[2], SectionKeys.chunkZ(chunk));
        }
        assertEquals(SectionKeys.section(-1, -1, -1), SectionKeys.sectionOfBlock(-1, -1, -16));
        assertEquals(SectionKeys.section(0, 0, -2), SectionKeys.sectionOfBlock(15, 15, -17));
    }

    // ---- GEO-012 ----

    /** 100 changes in one atmos tick come out of one drain, and the next drain has nothing. */
    @Test
    void geo_012_hundred_changes_one_pass() {
        DirtySections dirty = new DirtySections();
        // 100 changes: 50 positions in two sections, each changed twice.
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 50; i++) {
                dirty.mark(i % 20, 64, i / 20);
            }
        }
        List<DirtySection> pass = dirty.drain();
        assertEquals(2, pass.size(), "two sections");
        assertEquals(50, pass.stream().mapToInt(DirtySection::count).sum(), "each position once");
        assertTrue(dirty.isEmpty());
        assertTrue(dirty.drain().isEmpty(), "a second pass has nothing to do");
    }

    /** Repeated changes to one position coalesce into one dirty bit. */
    @Test
    void geo_012_repeated_changes_coalesce() {
        DirtySections dirty = new DirtySections();
        assertTrue(dirty.mark(5, 70, -3));
        for (int i = 0; i < 99; i++) {
            assertFalse(dirty.mark(5, 70, -3));
        }
        List<DirtySection> pass = dirty.drain();
        assertEquals(1, pass.size());
        assertEquals(1, pass.get(0).count());
        assertTrue(pass.get(0).isDirty(5, 70 & 15, -3 & 15));
    }

    /** The drain order depends on the dirty positions, never on the order of the changes. */
    @Test
    void geo_012_drain_order_is_deterministic() {
        List<int[]> positions = new ArrayList<>();
        Random random = new Random(12);
        for (int i = 0; i < 500; i++) {
            positions.add(new int[] {random.nextInt(200) - 100, random.nextInt(128) - 64, random.nextInt(200) - 100});
        }
        DirtySections a = new DirtySections();
        positions.forEach(p -> a.mark(p[0], p[1], p[2]));
        Collections.shuffle(positions, new Random(34));
        DirtySections b = new DirtySections();
        positions.forEach(p -> b.mark(p[0], p[1], p[2]));

        List<DirtySection> da = a.drain();
        List<DirtySection> db = b.drain();
        assertEquals(keys(da), keys(db));
        for (int i = 1; i < da.size(); i++) {
            assertTrue(SectionKeys.compare(da.get(i - 1).key(), da.get(i).key()) < 0, "sorted and distinct");
        }
        for (int i = 0; i < da.size(); i++) {
            List<Integer> ia = new ArrayList<>();
            List<Integer> ib = new ArrayList<>();
            da.get(i).forEachIndex(ia::add);
            db.get(i).forEachIndex(ib::add);
            assertEquals(ia, ib);
        }
    }

    // ---- GEO-011 ----

    /** Sections the filter rejects are dropped at drain without being returned. */
    @Test
    void geo_011_drain_drops_sections_outside_interest() {
        DirtySections dirty = new DirtySections();
        long near = SectionKeys.sectionOfBlock(0, 0, 0);
        dirty.mark(1, 1, 1);
        for (int i = 0; i < 1000; i++) {
            dirty.mark(1000 + i, 5, 1000);
        }
        List<DirtySection> pass = dirty.drain(key -> key == near);
        assertEquals(List.of(near), keys(pass));
        assertTrue(dirty.isEmpty(), "rejected sections are cleared too");
    }

    /**
     * A rough bench of the per-change work (a dirty bit and a block generation): the cost per change
     * doesn't grow with the number of changes or of sections touched. The geometry-id lookup in
     * front of it is benched by geo_040.
     */
    @Test
    void geo_011_cost_per_change_is_constant() {
        double small = nsPerChange(1 << 14);
        double large = nsPerChange(1 << 20);
        System.out.printf("geo_011: %.1f ns per change at 16k changes, %.1f ns at 1M%n", small, large);
        assertTrue(large < 4 * small + 100, "per-change cost grew from " + small + " to " + large + " ns");
    }

    /** Best of five rounds of {@code n} changes to distinct positions, spread over many sections. */
    private static double nsPerChange(int n) {
        double best = Double.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            DirtySections dirty = new DirtySections();
            ChunkGenerations gens = new ChunkGenerations();
            long start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                // Rows along x: runs of 16 consecutive changes share a section, as in a fill.
                int x = i & 255;
                int z = (i >> 8) & 255;
                int y = (i >> 16) - 64;
                dirty.mark(x, y, z);
                gens.bumpBlock(SectionKeys.chunk(x >> 4, z >> 4));
            }
            best = Math.min(best, (System.nanoTime() - start) / (double) n);
            assertEquals(n, dirty.drain().stream().mapToInt(DirtySection::count).sum());
        }
        return best;
    }

    // ---- regions and unloads ----

    @Test
    void drain_within_takes_only_the_box() {
        DirtySections dirty = new DirtySections();
        dirty.mark(3, 3, 3);
        dirty.mark(4, 3, 3);
        dirty.mark(20, 3, 3);
        List<DirtySection> taken = dirty.drainWithin(0, 0, 0, 3, 15, 15);
        assertEquals(1, taken.size());
        assertEquals(1, taken.get(0).count());
        assertTrue(taken.get(0).isDirty(3, 3, 3));
        assertFalse(dirty.isDirty(3, 3, 3));
        assertTrue(dirty.isDirty(4, 3, 3));
        assertTrue(dirty.isDirty(20, 3, 3));
        assertEquals(2, dirty.drain().stream().mapToInt(DirtySection::count).sum());
    }

    @Test
    void unloading_a_chunk_drops_its_dirty_sections() {
        DirtySections dirty = new DirtySections();
        dirty.mark(1, 1, 1);
        dirty.mark(1, 100, 1);
        dirty.mark(17, 1, 1);
        dirty.removeChunk(0, 0);
        assertFalse(dirty.isDirty(1, 1, 1));
        assertFalse(dirty.isDirty(1, 100, 1));
        assertTrue(dirty.isDirty(17, 1, 1));
        assertTrue(dirty.mark(1, 1, 1), "marking after removal starts a new section");
    }

    // ---- generations (architecture §3.3) ----

    @Test
    void block_generation_changes_on_every_write() {
        ChunkGenerations gens = new ChunkGenerations();
        long chunk = SectionKeys.chunk(3, -7);
        gens.load(chunk);
        long life = gens.lifecycle(chunk);
        long block = gens.block(chunk);
        assertNotEquals(ChunkGenerations.UNLOADED, life);
        Set<Long> seen = new HashSet<>(Set.of(block));
        for (int i = 0; i < 10; i++) {
            assertTrue(seen.add(gens.bumpBlock(chunk)), "a fresh block generation");
            assertEquals(life, gens.lifecycle(chunk), "block writes don't touch the lifecycle generation");
        }
        assertEquals(ChunkGenerations.UNLOADED, gens.block(SectionKeys.chunk(4, -7)), "other chunks untouched");
    }

    @Test
    void lifecycle_generation_changes_across_unload_and_never_repeats() {
        ChunkGenerations gens = new ChunkGenerations();
        long chunk = SectionKeys.chunk(0, 0);
        gens.load(chunk);
        long first = gens.lifecycle(chunk);
        long block = gens.bumpBlock(chunk);
        gens.load(chunk);
        assertEquals(first, gens.lifecycle(chunk), "loading a loaded chunk changes nothing");
        gens.unload(chunk);
        assertEquals(ChunkGenerations.UNLOADED, gens.lifecycle(chunk));
        assertEquals(ChunkGenerations.UNLOADED, gens.block(chunk));
        assertFalse(gens.isTracked(chunk));
        gens.load(chunk);
        assertNotEquals(first, gens.lifecycle(chunk));
        assertNotEquals(block, gens.block(chunk));
        assertTrue(gens.lifecycle(chunk) > block, "stamps only go up");
    }

    @Test
    void a_write_to_an_untracked_chunk_starts_tracking_it() {
        ChunkGenerations gens = new ChunkGenerations();
        long chunk = SectionKeys.chunk(-1, 1);
        long block = gens.bumpBlock(chunk);
        assertTrue(gens.isTracked(chunk));
        assertEquals(block, gens.block(chunk));
        assertNotEquals(ChunkGenerations.UNLOADED, gens.lifecycle(chunk));
    }

    // ---- section snapshots ----

    @Test
    void snapshot_copies_ids_and_compacts_uniform_sections() {
        short[] ids = new short[SectionSnapshot.SIZE];
        ids[SectionKeys.index(1, 2, 3)] = 1;
        ids[SectionKeys.index(15, 15, 15)] = (short) 40000;
        long key = SectionKeys.section(1, 2, 3);
        SectionSnapshot s = SectionSnapshot.of(key, 7, 9, ids);
        ids[0] = 5;
        assertEquals(0, s.geometryId(0), "the input array is copied");
        assertEquals(1, s.geometryId(1, 2, 3));
        assertEquals(40000, s.geometryId(15, 15, 15), "ids are unsigned");
        assertFalse(s.isUniform());
        assertEquals(key, s.key());
        assertEquals(7, s.lifecycleGeneration());
        assertEquals(9, s.blockGeneration());

        SectionSnapshot air = SectionSnapshot.of(key, 7, 9, new short[SectionSnapshot.SIZE]);
        assertTrue(air.isUniform());
        assertEquals(GeometryPalette.AIR_ID, air.geometryId(4095));
        assertArrayEquals(new short[SectionSnapshot.SIZE], air.geometryIds());
        assertThrows(IllegalArgumentException.class, () -> SectionSnapshot.of(key, 0, 0, new short[10]));
    }
}
