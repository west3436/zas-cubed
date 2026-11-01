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

    /** Zone-boundary flags are per face; {@link Geometry#FLAG_ZONE_BOUNDARY} is all six (GEO-007). */
    @Test
    void geo_007_boundary_faces() {
        Geometry door = Geometry.AIR.withFlags(Geometry.boundaryFlag(Face.WEST) | Geometry.boundaryFlag(Face.NORTH));
        for (Face f : Face.values()) {
            assertEquals(f == Face.WEST || f == Face.NORTH, door.isBoundary(f), f.name());
            assertFalse(Geometry.AIR.isBoundary(f), "air is no boundary");
        }
        assertTrue(door.hasFlag(Geometry.FLAG_ZONE_BOUNDARY), "a boundary across some faces is a zone boundary");
        assertFalse(door.equals(Geometry.AIR), "the flags tell a palette entry apart");

        Geometry every = Geometry.declared(EnumSet.noneOf(Face.class), 1000, Geometry.FLAG_ZONE_BOUNDARY);
        for (Face f : Face.values()) {
            assertTrue(every.isBoundary(f), f.name());
        }
        assertTrue(every.hasFlag(Geometry.FLAG_OVERRIDE), "other flags are kept");
        int flags = 0;
        for (Face f : Face.values()) {
            assertEquals(0, flags & Geometry.boundaryFlag(f), "one bit per face");
            assertEquals(
                    0,
                    Geometry.boundaryFlag(f)
                            & (Geometry.FLAG_OVERRIDE
                                    | Geometry.FLAG_FLUID
                                    | Geometry.FLAG_HEAT_SOURCE
                                    | Geometry.FLAG_POSITIONAL),
                    "apart from the other flags");
            flags |= Geometry.boundaryFlag(f);
        }
        assertEquals(Geometry.FLAG_ZONE_BOUNDARY, flags);
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

    /** The voxel counts of each cell in a {@link GeometryDeriver#cellOfVoxels} map, free voxels only. */
    private static int[] freeVoxelsPerCell(BitSet solid, byte[] cellOf, int cells) {
        int[] count = new int[cells];
        for (int i = 0; i < GeometryDeriver.VOXELS; i++) {
            if (!solid.get(i)) {
                count[cellOf[i]]++;
            }
        }
        return count;
    }

    /**
     * Each free voxel of a pane maps to the sub-cell holding it, in the order {@link
     * GeometryDeriver#derive} lists cells (API-015, GEO-004).
     */
    @Test
    void api_015_voxels_map_to_pane_sub_cells() {
        BitSet pane = box(7, 0, 0, 9, 16, 16);
        byte[] cellOf = GeometryDeriver.cellOfVoxels(pane);
        assertEquals(0, cellOf[GeometryDeriver.index(0, 5, 5)], "the west half");
        assertEquals(0, cellOf[GeometryDeriver.index(6, 15, 0)], "the west half's edge");
        assertEquals(1, cellOf[GeometryDeriver.index(9, 5, 5)], "the east half");
        assertEquals(1, cellOf[GeometryDeriver.index(15, 0, 15)], "the east half's corner");
        Geometry g = GeometryDeriver.derive(pane);
        int[] counts = freeVoxelsPerCell(pane, cellOf, g.cellCount());
        for (int c = 0; c < g.cellCount(); c++) {
            assertEquals(g.cell(c).volume(), counts[c] * Geometry.VOXEL_VOLUME, "cell " + c + "'s volume");
        }
    }

    /** A cross pane's four quarters map to its four sub-cells, matching their volumes (API-015). */
    @Test
    void api_015_voxels_map_to_cross_pane_sub_cells() {
        BitSet cross = box(7, 0, 0, 9, 16, 16);
        GeometryDeriver.fill(cross, 0, 0, 7, 16, 16, 9);
        byte[] cellOf = GeometryDeriver.cellOfVoxels(cross);
        Geometry g = GeometryDeriver.derive(cross);
        int[] counts = freeVoxelsPerCell(cross, cellOf, g.cellCount());
        for (int c = 0; c < g.cellCount(); c++) {
            assertEquals(g.cell(c).volume(), counts[c] * Geometry.VOXEL_VOLUME, "cell " + c + "'s volume");
        }
        java.util.Set<Byte> quarters = new java.util.HashSet<>();
        quarters.add(cellOf[GeometryDeriver.index(1, 8, 1)]);
        quarters.add(cellOf[GeometryDeriver.index(14, 8, 1)]);
        quarters.add(cellOf[GeometryDeriver.index(1, 8, 14)]);
        quarters.add(cellOf[GeometryDeriver.index(14, 8, 14)]);
        assertEquals(4, quarters.size(), "the quarters share a sub-cell");
    }

    /**
     * A voxel inside the shape maps to the nearest cell: the glass of a pane to the half on its
     * side, a slab's solid half to its one cell, an enclosed pocket to a cell around it (API-015).
     */
    @Test
    void api_015_voxels_inside_the_shape_map_to_the_nearest_cell() {
        byte[] pane = GeometryDeriver.cellOfVoxels(box(7, 0, 0, 9, 16, 16));
        assertEquals(0, pane[GeometryDeriver.index(7, 8, 8)], "the glass's west layer");
        assertEquals(1, pane[GeometryDeriver.index(8, 8, 8)], "the glass's east layer");
        byte[] slab = GeometryDeriver.cellOfVoxels(box(0, 0, 0, 16, 8, 16));
        assertEquals(0, slab[GeometryDeriver.index(3, 0, 3)], "deep in a slab");
        BitSet pocket = box(0, 0, 0, 16, 8, 16);
        pocket.andNot(box(6, 2, 6, 10, 6, 10));
        GeometryDeriver.fill(pocket, 7, 8, 0, 9, 16, 16);
        byte[] cellOf = GeometryDeriver.cellOfVoxels(pocket);
        assertEquals(2, GeometryDeriver.derive(pocket).cellCount(), "the pocket isn't a cell");
        int inPocket = cellOf[GeometryDeriver.index(7, 3, 7)];
        assertTrue(inPocket == 0 || inPocket == 1, "the pocket maps to a cell: " + inPocket);
    }

    /** A block with no cells has no map: a point inside it looks to its neighbours (API-015). */
    @Test
    void api_015_full_block_has_no_voxel_map() {
        assertEquals(null, GeometryDeriver.cellOfVoxels(box(0, 0, 0, 16, 16, 16)));
        BitSet hollow = box(0, 0, 0, 16, 16, 16);
        hollow.andNot(box(4, 4, 4, 12, 12, 12));
        assertEquals(null, GeometryDeriver.cellOfVoxels(hollow), "a sealed void is no cell");
        for (byte b : GeometryDeriver.cellOfVoxels(new BitSet())) {
            assertEquals(0, b);
        }
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
