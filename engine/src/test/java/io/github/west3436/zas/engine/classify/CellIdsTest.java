package io.github.west3436.zas.engine.classify;

import static io.github.west3436.zas.engine.classify.TestWorld.AIR;
import static io.github.west3436.zas.engine.classify.TestWorld.DIVIDER;
import static io.github.west3436.zas.engine.classify.TestWorld.cell;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The cell-id layout (#105, architecture §4.3): range, round trips, order and old ids. */
class CellIdsTest {
    /** The vanilla world border's extent. */
    private static final int BORDER = 29_999_984;

    private static final int[] XZ = {
        0, 1, -1, 15, 16, -16, -17, 8_388_607, 8_388_608, -8_388_609, BORDER, -BORDER, CellIds.MAX_XZ, CellIds.MIN_XZ
    };

    private static final int[] Y = {0, 1, -1, -64, 319, 255, CellIds.MAX_Y, CellIds.MIN_Y};

    @Test
    void geo_022_cell_ids_round_trip_to_the_world_border() {
        for (int x : XZ) {
            for (int z : XZ) {
                for (int y : Y) {
                    for (int sub = 0; sub < CellIds.MAX_SUB_CELLS; sub++) {
                        long id = CellIds.of(x, y, z, sub);
                        assertEquals(x, CellIds.x(id));
                        assertEquals(y, CellIds.y(id));
                        assertEquals(z, CellIds.z(id));
                        assertEquals(sub, CellIds.sub(id));
                        assertEquals(CellIds.of(x, y, z), CellIds.block(id));
                        assertEquals(SectionKeys.sectionOfBlock(x, y, z), CellIds.section(id));
                        assertEquals(SectionKeys.index(x & 15, y & 15, z & 15), CellIds.indexInSection(id));
                        assertNotEquals(CellIds.NONE, id);
                    }
                }
            }
        }
    }

    @Test
    void geo_022_cell_id_range() {
        assertTrue(CellIds.MAX_XZ >= BORDER && CellIds.MIN_XZ <= -BORDER, "the world border");
        assertTrue(CellIds.MIN_Y <= -64 && CellIds.MAX_Y >= 319, "the Overworld's build height");
        assertEquals(4, CellIds.MAX_SUB_CELLS, "the most cells a vanilla shape has");
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(CellIds.MAX_XZ + 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(CellIds.MIN_XZ - 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, 0, CellIds.MAX_XZ + 1));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, 0, CellIds.MIN_XZ - 1));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, CellIds.MIN_Y - 1, 0));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, CellIds.MAX_Y + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, 0, 0, CellIds.MAX_SUB_CELLS));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, 0, 0, -1));
        // NONE decodes to a position no id can hold.
        assertFalse(CellIds.inRange(CellIds.x(CellIds.NONE), CellIds.y(CellIds.NONE), CellIds.z(CellIds.NONE)));
    }

    /** The documented order: x, then z, then y, then sub-cell, with z's and y's negatives last. */
    private static final Comparator<int[]> ORDER = Comparator.<int[]>comparingInt(p -> p[0])
            .thenComparing(p -> p[2], Integer::compareUnsigned)
            .thenComparing(p -> p[1], Integer::compareUnsigned)
            .thenComparingInt(p -> p[3]);

    @Test
    void geo_022_cell_ids_sort_by_x_z_y_sub() {
        Random random = new Random(105);
        List<int[]> cells = new ArrayList<>();
        for (int x : XZ) {
            for (int y : Y) {
                cells.add(new int[] {x, y, -1 - x, 0});
                cells.add(new int[] {x, y, x, CellIds.MAX_SUB_CELLS - 1});
            }
        }
        for (int i = 0; i < 2000; i++) {
            cells.add(new int[] {
                CellIds.MIN_XZ + random.nextInt(CellIds.MAX_XZ - CellIds.MIN_XZ + 1),
                CellIds.MIN_Y + random.nextInt(CellIds.MAX_Y - CellIds.MIN_Y + 1),
                CellIds.MIN_XZ + random.nextInt(CellIds.MAX_XZ - CellIds.MIN_XZ + 1),
                random.nextInt(CellIds.MAX_SUB_CELLS)
            });
        }
        int[][] expected = cells.toArray(int[][]::new);
        Arrays.sort(expected, ORDER);
        long[] ids = new long[expected.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = CellIds.of(cells.get(i)[0], cells.get(i)[1], cells.get(i)[2], cells.get(i)[3]);
        }
        Arrays.sort(ids);
        for (int i = 0; i < ids.length; i++) {
            int[] p = expected[i];
            assertEquals(CellIds.of(p[0], p[1], p[2], p[3]), ids[i], "position " + i);
        }
        // Sub-cells of one block sort together, after the block's first cell.
        assertTrue(CellIds.of(3, 4, 5, 0) < CellIds.of(3, 4, 5, 1));
        assertTrue(CellIds.of(3, 4, 5, CellIds.MAX_SUB_CELLS - 1) < CellIds.of(3, 5, 5, 0));
        assertTrue(CellIds.of(-BORDER, 0, 0) < CellIds.of(BORDER, 0, 0));
    }

    /** An id in the layout of chunk record formats 1 and 2. */
    static long format2(int x, int y, int z, int sub) {
        return ((long) (x & 0xFFFFFF) << 40) | ((long) (z & 0xFFFFFF) << 16) | ((long) (y & 0xFFF) << 4) | sub;
    }

    @Test
    void data_003_cell_ids_of_format_2_convert() {
        int[] xz = {0, 1, -1, 4096, -4096, 8_388_607, -8_388_608};
        int[] ys = {0, -64, 319, CellIds.MIN_Y, CellIds.MAX_Y};
        List<Long> old = new ArrayList<>();
        for (int x : xz) {
            for (int z : xz) {
                for (int y : ys) {
                    for (int sub = 0; sub < CellIds.MAX_SUB_CELLS; sub++) {
                        long v2 = format2(x, y, z, sub);
                        assertEquals(CellIds.of(x, y, z, sub), CellIds.fromFormat2(v2));
                        old.add(v2);
                    }
                }
            }
        }
        // The conversion keeps the order, so sorted old ids stay sorted.
        long[] sortedOld = old.stream().mapToLong(Long::longValue).sorted().toArray();
        long[] converted = Arrays.stream(sortedOld).map(CellIds::fromFormat2).toArray();
        long[] resorted = converted.clone();
        Arrays.sort(resorted);
        assertArrayEquals(resorted, converted);
        // What the new layout can't name: a y beyond its range, a fifth sub-cell.
        assertEquals(CellIds.NONE, CellIds.fromFormat2(format2(0, CellIds.MAX_Y + 1, 0, 0)));
        assertEquals(CellIds.NONE, CellIds.fromFormat2(format2(0, CellIds.MIN_Y - 1, 0, 0)));
        assertEquals(CellIds.NONE, CellIds.fromFormat2(format2(0, 2047, 0, 0)));
        assertEquals(CellIds.NONE, CellIds.fromFormat2(format2(0, 0, 0, CellIds.MAX_SUB_CELLS)));
        assertEquals(CellIds.NONE, CellIds.fromFormat2(format2(0, 0, 0, 15)));
    }

    @Test
    void geo_022_rooms_enclose_at_the_world_border() {
        EnclosureFill fill = new EnclosureFill();
        for (int x0 : new int[] {BORDER - 1000, -BORDER + 10}) {
            for (int z0 : new int[] {BORDER - 1000, -BORDER + 10}) {
                // A 3 × 3 × 3 room with a divider in its middle.
                TestWorld world =
                        new TestWorld().box(x0, 0, z0, x0 + 2, 2, z0 + 2, AIR).set(x0 + 1, 1, z0 + 1, DIVIDER);
                FillResult.Enclosed e = assertInstanceOf(
                        FillResult.Enclosed.class, fill.fill(world.topology(), cell(x0, 0, z0)), x0 + ", " + z0);
                assertEquals(28, e.cells().length, "26 blocks and the divider's two cells");
                assertEquals((26 + 14.0 / 16) * Geometry.FULL_VOLUME, e.volume(), 1e-9, "the divider fills 2/16");
                assertTrue(e.contains(CellIds.of(x0 + 1, 1, z0 + 1, 1)));
            }
        }
    }

    @Test
    void geo_031_beyond_the_world_limit_is_outside() {
        EnclosureFill fill = new EnclosureFill();
        // A room whose east wall is missing at the last x a cell can have.
        int x1 = CellIds.MAX_XZ;
        TestWorld world = new TestWorld().box(x1 - 2, 0, 0, x1, 2, 2, AIR);
        FillResult.Open o = assertInstanceOf(FillResult.Open.class, fill.fill(world.topology(), cell(x1 - 1, 1, 1)));
        assertEquals(FillResult.Open.Reason.OUTSIDE_WORLD, o.reason());
        // The same room one block in, walled, is enclosed.
        TestWorld walled = new TestWorld().box(x1 - 3, 0, 0, x1 - 1, 2, 2, AIR);
        assertInstanceOf(FillResult.Enclosed.class, fill.fill(walled.topology(), cell(x1 - 2, 1, 1)));
        // And likewise at the lowest x, and above the highest y.
        TestWorld west = new TestWorld().box(CellIds.MIN_XZ, 0, 0, CellIds.MIN_XZ + 2, 2, 2, AIR);
        assertInstanceOf(FillResult.Open.class, fill.fill(west.topology(), cell(CellIds.MIN_XZ + 1, 1, 1)));
        TestWorld top = new TestWorld().box(0, CellIds.MAX_Y - 2, 0, 2, CellIds.MAX_Y, 2, AIR);
        assertInstanceOf(FillResult.Open.class, fill.fill(top.topology(), cell(1, CellIds.MAX_Y - 1, 1)));
    }
}
