package io.github.west3436.zas.engine.classify;

import static io.github.west3436.zas.engine.classify.TestWorld.AIR;
import static io.github.west3436.zas.engine.classify.TestWorld.DIVIDER;
import static io.github.west3436.zas.engine.classify.TestWorld.DOOR;
import static io.github.west3436.zas.engine.classify.TestWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionTopologyTest {
    @Test
    void geo_022_cell_ids_round_trip() {
        int[][] positions = {
            {0, 0, 0},
            {-1, -1, -1},
            {CellIds.MAX_XZ, CellIds.MAX_Y, CellIds.MIN_XZ},
            {CellIds.MIN_XZ, CellIds.MIN_Y, 17}
        };
        for (int[] p : positions) {
            for (int sub : new int[] {0, 5, CellIds.MAX_SUB_CELLS - 1}) {
                long id = CellIds.of(p[0], p[1], p[2], sub);
                assertEquals(p[0], CellIds.x(id));
                assertEquals(p[1], CellIds.y(id));
                assertEquals(p[2], CellIds.z(id));
                assertEquals(sub, CellIds.sub(id));
                assertEquals(CellIds.of(p[0], p[1], p[2]), CellIds.block(id));
                assertEquals(SectionKeys.sectionOfBlock(p[0], p[1], p[2]), CellIds.section(id));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(CellIds.MAX_XZ + 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, CellIds.MIN_Y - 1, 0));
        assertThrows(IllegalArgumentException.class, () -> CellIds.of(0, 0, 0, CellIds.MAX_SUB_CELLS));
        // Sub-cells of one block sort together, after the block's first cell.
        assertTrue(CellIds.of(3, 4, 5, 0) < CellIds.of(3, 4, 5, 1));
        assertTrue(CellIds.of(3, 4, 5, 15) < CellIds.of(3, 5, 5, 0));
    }

    @Test
    void geo_022_classification_on_snapshots() {
        // A room with a divider, a door and a section boundary through it.
        TestWorld world = new TestWorld()
                .box(12, 0, 0, 19, 2, 2, AIR)
                .set(14, 1, 1, DIVIDER)
                .set(17, 1, 1, DOOR);
        SectionTopology t = world.topology();

        assertEquals(Geometry.FULL_VOLUME, t.volume(cell(12, 0, 0)));
        assertEquals(0, t.volume(cell(11, 0, 0)), "solid");
        assertTrue(t.volume(CellIds.of(14, 1, 1, 1)) > 0, "the divider's second cell");
        assertEquals(0, t.volume(CellIds.of(14, 1, 1, 2)), "the divider has two cells");
        assertEquals(0, t.volume(CellIds.of(12, 1, 1, 1)), "air has one cell");
        assertEquals(0, t.volume(cell(40, 0, 0)), "unknown section");
        assertFalse(t.isKnown(cell(40, 0, 0)));
        assertTrue(t.isKnown(cell(11, 0, 0)));

        // Neighbours, in Face order (down, up, north, south, west, east).
        List<Long> seen = new ArrayList<>();
        t.forEachOpenNeighbour(cell(13, 1, 1), (n, direct) -> seen.add(n));
        assertEquals(
                List.of(
                        cell(13, 0, 1),
                        cell(13, 2, 1),
                        cell(13, 1, 0),
                        cell(13, 1, 2),
                        cell(12, 1, 1),
                        CellIds.of(14, 1, 1, 0)),
                seen);

        // Openness is symmetric with the same direct flag, and door openings aren't direct.
        Map<Long, Map<Long, Boolean>> open = new HashMap<>();
        for (int x = 11; x <= 20; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -1; z <= 3; z++) {
                    for (int sub = 0; sub < 2; sub++) {
                        long c = CellIds.of(x, y, z, sub);
                        if (t.volume(c) > 0) {
                            Map<Long, Boolean> m = new HashMap<>();
                            t.forEachOpenNeighbour(c, (n, direct) -> {
                                assertTrue(t.volume(n) > 0, "only cells are visited");
                                assertEquals(null, m.put(n, direct), "each neighbour once");
                            });
                            open.put(c, m);
                        }
                    }
                }
            }
        }
        open.forEach((c, m) -> m.forEach((n, direct) -> {
            assertEquals(direct, open.get(n).get(c), "symmetric: " + CellIds.toString(c) + " " + CellIds.toString(n));
            boolean door = CellIds.block(c) == cell(17, 1, 1) || CellIds.block(n) == cell(17, 1, 1);
            assertEquals(!door, direct);
        }));
        assertFalse(open.get(CellIds.of(14, 1, 1, 0)).containsKey(CellIds.of(14, 1, 1, 1)), "no self-links");
    }

    @Test
    void geo_024_topology_reports_unknown_faces() {
        TestWorld world = new TestWorld().box(14, 0, 0, 17, 0, 0, AIR).set(13, 0, 0, DIVIDER);
        long east = SectionKeys.section(1, 0, 0);
        world.forget(east);
        SectionTopology t = world.topology(0, 255);
        List<Long> unknown = new ArrayList<>();
        int[] outside = {0};
        ResolvableTopology.FillVisitor v = new ResolvableTopology.FillVisitor() {
            @Override
            public void accept(long neighbour, boolean direct) {}

            @Override
            public void unknown(long section) {
                unknown.add(section);
            }

            @Override
            public void outsideWorld() {
                outside[0]++;
            }
        };
        t.forEachNeighbour(cell(15, 0, 0), v);
        assertEquals(List.of(east), unknown);
        assertEquals(1, outside[0], "the floor at y = 0 leads below the world");
        // A divider on the section boundary: only its east cell reaches the unknown section.
        TestWorld split = new TestWorld().box(14, 0, 0, 17, 0, 0, AIR).set(15, 0, 0, DIVIDER);
        split.forget(east);
        SectionTopology st = split.topology(0, 255);
        unknown.clear();
        st.forEachNeighbour(CellIds.of(15, 0, 0, 0), v);
        assertEquals(List.of(), unknown);
        st.forEachNeighbour(CellIds.of(15, 0, 0, 1), v);
        assertEquals(List.of(east), unknown);
    }
}
