package io.github.west3436.zas.engine.classify;

import static io.github.west3436.zas.engine.classify.TestWorld.AIR;
import static io.github.west3436.zas.engine.classify.TestWorld.DIVIDER;
import static io.github.west3436.zas.engine.classify.TestWorld.DOOR;
import static io.github.west3436.zas.engine.classify.TestWorld.SOLID;
import static io.github.west3436.zas.engine.classify.TestWorld.cell;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EnclosureFillTest {
    /** A 64 × 16 × 64 hall: exactly 65,536 cells, across 16 sections. */
    private static TestWorld hall() {
        return new TestWorld().box(0, 0, 0, 63, 15, 63, AIR);
    }

    @Test
    void geo_020_bounded_enclosure_test() {
        EnclosureFill fill = new EnclosureFill();
        assertEquals(65_536, fill.maxCells(), "the default bound (D-23)");

        // 65,535 cells: one corner block filled in.
        TestWorld below = hall().set(63, 15, 63, SOLID);
        FillResult r = fill.fill(below.topology(), cell(10, 5, 10));
        FillResult.Enclosed e = assertInstanceOf(FillResult.Enclosed.class, r);
        assertEquals(65_535, e.cells().length);
        assertEquals(65_535 * Geometry.FULL_VOLUME, e.volume());

        // Exactly at the bound is still enclosed.
        e = assertInstanceOf(FillResult.Enclosed.class, fill.fill(hall().topology(), cell(0, 0, 0)));
        assertEquals(65_536, e.cells().length);

        // 65,537 cells: an alcove through the wall. Exceeding the bound means open.
        TestWorld above = hall().set(64, 0, 0, AIR);
        FillResult.Open o = assertInstanceOf(FillResult.Open.class, fill.fill(above.topology(), cell(10, 5, 10)));
        assertEquals(FillResult.Open.Reason.BOUND, o.reason());
        assertEquals(65_536, o.visited());

        // The bound is configurable.
        TestWorld small = new TestWorld().box(0, 0, 0, 4, 1, 0, AIR); // 10 cells
        assertInstanceOf(FillResult.Enclosed.class, new EnclosureFill(10).fill(small.topology(), cell(0, 0, 0)));
        assertInstanceOf(FillResult.Open.class, new EnclosureFill(9).fill(small.topology(), cell(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new EnclosureFill(0));
    }

    @Test
    void geo_020_enclosed_cells_sorted_and_deterministic() {
        TestWorld world = new TestWorld().box(-3, -2, -5, 4, 3, 2, AIR).box(5, 0, 0, 9, 0, 0, AIR);
        EnclosureFill fill = new EnclosureFill();
        FillResult.Enclosed a = (FillResult.Enclosed) fill.fill(world.topology(), cell(0, 0, 0));
        FillResult.Enclosed b = (FillResult.Enclosed) fill.fill(world.topology(), cell(9, 0, 0));
        assertEquals(8 * 6 * 8 + 5, a.cells().length, "the room and the corridor off it");
        long[] sorted = a.cells().clone();
        Arrays.sort(sorted);
        assertArrayEquals(sorted, a.cells(), "sorted ascending");
        assertArrayEquals(a.cells(), b.cells(), "the same region from any seed");
        assertEquals(a.volume(), b.volume());
        assertTrue(a.contains(cell(-3, -2, -5)));
        assertFalse(a.contains(cell(-4, -2, -5)));
        // A fresh fill gives the same answer.
        assertArrayEquals(
                a.cells(), ((FillResult.Enclosed) new EnclosureFill().fill(world.topology(), cell(1, 1, 1))).cells());
        assertThrows(IllegalArgumentException.class, () -> fill.fill(world.topology(), cell(0, 10, 0)), "solid");
    }

    @Test
    void geo_020_fill_follows_sub_cells_and_crosses_doors() {
        // Two rooms with a divider block in the wall between them: each side reaches one of its cells.
        TestWorld world = new TestWorld()
                .box(0, 0, 0, 2, 2, 2, AIR)
                .box(4, 0, 0, 6, 2, 2, AIR)
                .set(3, 1, 1, DIVIDER);
        EnclosureFill fill = new EnclosureFill();
        FillResult.Enclosed west = (FillResult.Enclosed) fill.fill(world.topology(), cell(0, 0, 0));
        assertEquals(27 + 1, west.cells().length);
        assertTrue(west.contains(CellIds.of(3, 1, 1, 0)));
        assertFalse(west.contains(CellIds.of(3, 1, 1, 1)));
        double half = world.topology().volume(CellIds.of(3, 1, 1, 0));
        assertEquals(27 * Geometry.FULL_VOLUME + half, west.volume());
        FillResult.Enclosed east = (FillResult.Enclosed) fill.fill(world.topology(), cell(6, 2, 2));
        assertTrue(east.contains(CellIds.of(3, 1, 1, 1)));
        assertFalse(east.contains(cell(0, 0, 0)));

        // An open door passes gas, so the fill crosses it (zones don't, but enclosure does).
        world.set(3, 0, 0, DOOR);
        FillResult.Enclosed both = (FillResult.Enclosed) fill.fill(world.topology(), cell(0, 0, 0));
        assertTrue(both.contains(cell(6, 2, 2)));
    }

    @Test
    void geo_020_fill_cost() {
        // Bench (S3): a fill at the 65,536-cell cap, on the worker's view of 16 sections plus shell.
        TestWorld world = hall();
        SectionTopology topology = world.topology();
        EnclosureFill fill = new EnclosureFill();
        for (int i = 0; i < 20; i++) {
            fill.fill(topology, cell(i, 0, 0));
        }
        long[] times = new long[21];
        for (int i = 0; i < times.length; i++) {
            long t0 = System.nanoTime();
            FillResult r = fill.fill(topology, cell(i, 1, 1));
            times[i] = System.nanoTime() - t0;
            assertEquals(65_536, r.visited());
        }
        Arrays.sort(times);
        double median = times[times.length / 2] / 1e6;
        System.out.printf("geo_020_fill_cost: 65,536-cell fill median %.2f ms, min %.2f ms%n", median, times[0] / 1e6);
        assertTrue(median < 200, "a capped fill took " + median + " ms");
    }

    @Test
    void geo_024_fill_reaching_unknown_sections_is_unresolved() {
        // A corridor from section (0,0,0) into (1,0,0) and (2,0,0).
        TestWorld world = new TestWorld().box(2, 2, 2, 40, 2, 2, AIR);
        long s1 = SectionKeys.section(1, 0, 0);
        long s2 = SectionKeys.section(2, 0, 0);
        short[] saved1 = world.save(s1);
        short[] saved2 = world.save(s2);
        world.forget(s1).forget(s2);
        EnclosureFill fill = new EnclosureFill();

        FillResult.Unresolved u =
                assertInstanceOf(FillResult.Unresolved.class, fill.fill(world.topology(), cell(2, 2, 2)), "never open");
        assertArrayEquals(new long[] {s1}, u.missingSections(), "only the section the region reaches");
        assertEquals(14, u.visited());

        // A seed in an unknown section.
        u = assertInstanceOf(FillResult.Unresolved.class, fill.fill(world.topology(), cell(20, 2, 2)));
        assertArrayEquals(new long[] {s1}, u.missingSections());

        // One section arrives: the fill goes further and needs the next.
        world.restore(s1, saved1);
        u = assertInstanceOf(FillResult.Unresolved.class, fill.fill(world.topology(), cell(2, 2, 2)));
        assertArrayEquals(new long[] {s2}, u.missingSections());

        // Both: the region classifies.
        world.restore(s2, saved2);
        FillResult.Enclosed e = assertInstanceOf(FillResult.Enclosed.class, fill.fill(world.topology(), cell(2, 2, 2)));
        assertEquals(39, e.cells().length);

        // Every missing section is reported at once, in SectionKeys order.
        TestWorld cross = new TestWorld().box(-20, 5, 5, 20, 5, 5, AIR).box(5, 5, -20, 5, 5, 20, AIR);
        long west = SectionKeys.section(-2, 0, 0);
        long north = SectionKeys.section(0, 0, -2);
        long east = SectionKeys.section(1, 0, 0);
        cross.forget(west).forget(north).forget(east);
        u = assertInstanceOf(FillResult.Unresolved.class, fill.fill(cross.topology(), cell(5, 5, 5)));
        long[] expected = {west, north, east};
        assertArrayEquals(expected, u.missingSections());

        // A region over the bound is open even if it also reaches an unknown section.
        assertInstanceOf(FillResult.Open.class, new EnclosureFill(5).fill(cross.topology(), cell(5, 5, 5)));
    }

    @Test
    void geo_031_faces_outside_the_world_count_as_open() {
        // A room whose ceiling is the build limit: open (GEO-031's default).
        TestWorld world = new TestWorld().box(0, 60, 0, 3, 63, 3, AIR);
        EnclosureFill fill = new EnclosureFill();
        FillResult.Open o = assertInstanceOf(FillResult.Open.class, fill.fill(world.topology(0, 63), cell(1, 61, 1)));
        assertEquals(FillResult.Open.Reason.OUTSIDE_WORLD, o.reason());
        // With a higher limit the section above is known and solid, so the room is sealed.
        assertInstanceOf(FillResult.Enclosed.class, fill.fill(world.topology(0, 127), cell(1, 61, 1)));
        // A sealed block face at the limit doesn't open the room.
        world.box(0, 63, 0, 3, 63, 3, SOLID);
        assertInstanceOf(FillResult.Enclosed.class, fill.fill(world.topology(0, 63), cell(1, 61, 1)));
    }
}
