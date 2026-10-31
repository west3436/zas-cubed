package io.github.west3436.zas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class GeometryTest {
    private static BitSet box(int x0, int y0, int z0, int x1, int y1, int z1) {
        BitSet s = new BitSet(GeometryDeriver.VOXELS);
        GeometryDeriver.fill(s, x0, y0, z0, x1, y1, z1);
        return s;
    }

    static Geometry bottomSlab() {
        return GeometryDeriver.derive(box(0, 0, 0, 16, 8, 16));
    }

    static Geometry topSlab() {
        return GeometryDeriver.derive(box(0, 8, 0, 16, 16, 16));
    }

    /** A glass pane running north to south: a post with two arms. */
    static Geometry straightPane() {
        return GeometryDeriver.derive(box(7, 0, 0, 9, 16, 16));
    }

    static Geometry crossPane() {
        BitSet s = box(7, 0, 0, 9, 16, 16);
        GeometryDeriver.fill(s, 0, 0, 7, 16, 16, 9);
        return GeometryDeriver.derive(s);
    }

    @Test
    void geo_001_cell_counts() {
        assertEquals(1, GeometryDeriver.derive(new BitSet()).cellCount(), "air");
        assertEquals(1, bottomSlab().cellCount(), "slab");
        assertEquals(2, straightPane().cellCount(), "straight pane");
        assertEquals(4, crossPane().cellCount(), "cross pane");
        assertEquals(0, GeometryDeriver.derive(box(0, 0, 0, 16, 16, 16)).cellCount(), "full block");
    }

    @Test
    void geo_001_sealed_internal_void_is_ignored() {
        BitSet s = box(0, 0, 0, 16, 16, 16);
        s.andNot(box(4, 4, 4, 12, 12, 12));
        Geometry g = GeometryDeriver.derive(s);
        assertEquals(0, g.cellCount());
        assertEquals(0.0, g.volume());
        for (Face f : Face.values()) {
            assertTrue(g.isFaceFull(f), f.name());
        }
    }

    @Test
    void geo_001_void_beside_an_open_region_is_not_merged() {
        // A bottom slab with a sealed pocket inside it: one cell, the pocket ignored.
        BitSet s = box(0, 0, 0, 16, 8, 16);
        s.andNot(box(4, 2, 4, 12, 6, 12));
        Geometry g = GeometryDeriver.derive(s);
        assertEquals(1, g.cellCount());
        assertEquals(500.0, g.volume());
    }

    @Test
    void geo_002_volumes() {
        assertEquals(1000.0, GeometryDeriver.derive(new BitSet()).volume(), "air");
        assertEquals(500.0, bottomSlab().volume(), "bottom slab");
        // Stairs facing north: a bottom slab plus solid quarters on top.
        BitSet straight = box(0, 0, 0, 16, 8, 16);
        GeometryDeriver.fill(straight, 0, 8, 0, 16, 16, 8);
        assertEquals(250.0, GeometryDeriver.derive(straight).volume(), "straight stairs");
        BitSet outer = box(0, 0, 0, 16, 8, 16);
        GeometryDeriver.fill(outer, 0, 8, 0, 8, 16, 8);
        assertEquals(375.0, GeometryDeriver.derive(outer).volume(), "outer stairs");
        BitSet inner = box(0, 0, 0, 16, 8, 16);
        GeometryDeriver.fill(inner, 0, 8, 0, 16, 16, 8);
        GeometryDeriver.fill(inner, 0, 8, 8, 8, 16, 16);
        assertEquals(125.0, GeometryDeriver.derive(inner).volume(), "inner stairs");
        // A straight pane: two 7/16 halves.
        assertEquals(437.5, straightPane().cell(0).volume());
        assertEquals(437.5, straightPane().cell(1).volume());
    }

    @Test
    void geo_003_slab_masks() {
        Geometry slab = bottomSlab();
        assertTrue(slab.isFaceFull(Face.DOWN));
        assertTrue(slab.coverage(Face.UP).isEmpty());
        for (Face side : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            assertEquals(FaceMask.rect(0, 0, 16, 8), slab.coverage(side), side.name());
            assertEquals(FaceMask.rect(0, 8, 16, 16), slab.cell(0).footprint(side), side.name());
        }
    }

    @Test
    void geo_003_complementary_slabs_seal() {
        GeometryPalette.Builder b = GeometryPalette.builder();
        int bottom = b.intern(bottomSlab());
        int top = b.intern(topSlab());
        GeometryPalette p = b.build();
        assertTrue(p.faceSealed(bottom, Face.EAST, top));
        assertTrue(p.faceSealed(top, Face.WEST, bottom));
        assertFalse(p.connects(bottom, 0, Face.EAST, top, 0));
    }

    @Test
    void geo_003_slab_pairs_and_air() {
        GeometryPalette.Builder b = GeometryPalette.builder();
        int slab = b.intern(bottomSlab());
        GeometryPalette p = b.build();
        int air = GeometryPalette.AIR_ID;
        // Two bottom slabs side by side share their open top half.
        assertFalse(p.faceSealed(slab, Face.EAST, slab));
        assertTrue(p.connects(slab, 0, Face.EAST, slab, 0));
        // A slab against air: open above, connected.
        assertFalse(p.faceSealed(slab, Face.NORTH, air));
        assertTrue(p.connects(slab, 0, Face.NORTH, air, 0));
        // The slab's own bottom face is full, whatever lies below.
        assertTrue(p.faceSealed(slab, Face.DOWN, air));
        assertFalse(p.connects(air, 0, Face.UP, slab, 0));
        // Air against a solid block is sealed; air against air is open.
        assertTrue(p.faceSealed(air, Face.UP, GeometryPalette.SOLID_ID));
        assertFalse(p.faceSealed(air, Face.UP, air));
        assertTrue(p.connects(air, 0, Face.UP, air, 0));
    }

    @Test
    void geo_003_inset_block_leaks() {
        // Like a chest: inset by 1/16 on four sides and the top.
        Geometry chest = GeometryDeriver.derive(box(1, 0, 1, 15, 14, 15));
        assertEquals(1, chest.cellCount());
        for (Face f : Face.values()) {
            assertFalse(GeometryPalette.sealed(chest, f, Geometry.AIR), f.name());
            assertTrue(chest.cell(0).reaches(f), f.name());
        }
    }

    @Test
    void geo_004_pane_sub_cells() {
        Geometry pane = straightPane();
        assertEquals(2, pane.cellCount());
        Geometry.Cell west = pane.cell(0);
        Geometry.Cell east = pane.cell(1);
        assertTrue(west.reaches(Face.WEST));
        assertFalse(west.reaches(Face.EAST));
        assertTrue(east.reaches(Face.EAST));
        assertFalse(east.reaches(Face.WEST));
        // Both halves reach north, south, up and down, on their own side of the post.
        assertFalse(west.footprint(Face.NORTH).intersects(east.footprint(Face.NORTH)));
    }

    @Test
    void geo_004_stacked_panes_connect_half_to_half() {
        GeometryPalette.Builder b = GeometryPalette.builder();
        int pane = b.intern(straightPane());
        GeometryPalette p = b.build();
        assertTrue(p.connects(pane, 0, Face.UP, pane, 0));
        assertTrue(p.connects(pane, 1, Face.UP, pane, 1));
        assertFalse(p.connects(pane, 0, Face.UP, pane, 1));
        assertFalse(p.connects(pane, 1, Face.UP, pane, 0));
        // A line of panes: west halves meet across north/south, never the east halves.
        assertTrue(p.connects(pane, 0, Face.NORTH, pane, 0));
        assertFalse(p.connects(pane, 0, Face.NORTH, pane, 1));
        // Air above connects to both halves.
        assertTrue(p.connects(pane, 0, Face.UP, GeometryPalette.AIR_ID, 0));
        assertTrue(p.connects(pane, 1, Face.UP, GeometryPalette.AIR_ID, 0));
    }

    @Test
    void geo_005_declared_geometry() {
        Geometry g = Geometry.declared(EnumSet.of(Face.NORTH, Face.SOUTH), 250, 0);
        assertEquals(1, g.cellCount());
        assertEquals(250.0, g.volume());
        assertTrue(g.hasFlag(Geometry.FLAG_OVERRIDE));
        assertTrue(GeometryPalette.sealed(g, Face.NORTH, Geometry.AIR));
        assertFalse(GeometryPalette.sealed(g, Face.EAST, Geometry.AIR));
        assertTrue(GeometryPalette.connects(g, 0, Face.EAST, Geometry.AIR, 0));
        assertFalse(g.cell(0).reaches(Face.SOUTH));

        Geometry tight = Geometry.declared(EnumSet.allOf(Face.class), 0, 0);
        assertEquals(0, tight.cellCount());
        assertThrows(IllegalArgumentException.class, () -> Geometry.declared(EnumSet.noneOf(Face.class), 1001, 0));
    }

    @Test
    void geo_040_palette_deduplicates() {
        GeometryPalette.Builder b = GeometryPalette.builder();
        int a = b.intern(bottomSlab());
        int c = b.intern(bottomSlab());
        assertEquals(a, c);
        assertEquals(GeometryPalette.AIR_ID, b.intern(GeometryDeriver.derive(new BitSet())));
        assertEquals(GeometryPalette.SOLID_ID, b.intern(GeometryDeriver.derive(box(0, 0, 0, 16, 16, 16))));
        GeometryPalette p = b.build();
        assertEquals(3, p.size());
        assertSame(Geometry.AIR, p.geometry(GeometryPalette.AIR_ID));
        GeometryPalette.Builder again = p.toBuilder();
        assertEquals(a, again.intern(bottomSlab()));
    }

    @Test
    void face_mask_and_face_basics() {
        for (Face f : Face.values()) {
            assertSame(f, f.opposite().opposite());
            assertEquals(0, f.dx() + f.opposite().dx());
            assertSame(f, Face.of(f.ordinal()));
        }
        assertSame(FaceMask.FULL, FaceMask.rect(0, 0, 16, 8).or(FaceMask.rect(0, 8, 16, 16)));
        assertSame(FaceMask.EMPTY, FaceMask.rect(0, 0, 16, 8).and(FaceMask.rect(0, 8, 16, 16)));
        assertEquals(128, FaceMask.rect(0, 0, 16, 8).cardinality());
        assertTrue(FaceMask.rect(3, 4, 5, 6).get(4, 5));
        assertFalse(FaceMask.rect(3, 4, 5, 6).get(5, 5));
        assertSame(FaceMask.EMPTY, FaceMask.FULL.not());
    }
}
