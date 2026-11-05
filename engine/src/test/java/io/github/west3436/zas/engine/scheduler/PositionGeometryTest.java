package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Geometries that come with a section snapshot for one position (API-023), through the whole
 * pipeline: they have no id in the palette the engine starts with, and every phase has to take them
 * in when they arrive.
 */
class PositionGeometryTest {
    private static final Geometry OPEN =
            Geometry.declared(EnumSet.noneOf(Face.class), Geometry.FULL_VOLUME, Geometry.FLAG_POSITIONAL);

    /** Two rooms, x 0 to 2 and x 4 to 6, 3 × 3 × 3 inside, with a wall at x = 3. */
    private static Sim rooms() {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), TestWorld.PALETTE);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(4, 64, 0, 6, 66, 2);
        return sim;
    }

    /** A position's own geometry opens the wall; taking it away closes the wall again. */
    @Test
    void api_023_own_geometry_opens_and_closes_a_wall() {
        Sim sim = rooms();
        sim.add(1, 65, 1, 0, 200);
        sim.step();
        assertEquals(27, sim.zoneAt(1, 65, 1).cellCount());
        assertNull(sim.zoneAt(5, 65, 1), "the wall seals");

        sim.world.override(3, 65, 1, OPEN);
        sim.step();
        ZoneView joined = sim.zoneAt(1, 65, 1);
        assertEquals(55, joined.cellCount(), "the rooms and the opening");
        assertEquals(joined.id(), sim.zoneAt(5, 65, 1).id());
        assertEquals(joined.id(), sim.zoneAt(3, 65, 1).id());

        sim.world.override(3, 65, 1, null);
        sim.step();
        assertEquals(27, sim.zoneAt(1, 65, 1).cellCount(), "the wall seals again");
        assertNull(sim.zoneAt(3, 65, 1));
        assertNotEquals(sim.zoneAt(1, 65, 1).id(), sim.zoneAt(5, 65, 1).id());
    }

    /** The volume comes from the geometry. */
    @Test
    void api_023_own_geometry_gives_the_volume() {
        Sim sim = rooms();
        sim.add(1, 65, 1, 0, 200);
        sim.step();
        Geometry small = Geometry.declared(EnumSet.noneOf(Face.class), 250, Geometry.FLAG_POSITIONAL);
        sim.world.override(3, 65, 1, small);
        sim.world.override(3, 64, 1, small);
        sim.step();
        assertEquals(54_000 + 500, sim.zoneAt(1, 65, 1).volume(), 1e-9);
    }

    /** A position's own zone-boundary faces keep the zones apart while gas crosses (GEO-007). */
    @Test
    void api_023_own_geometry_can_keep_zones_apart() {
        Sim sim = rooms();
        sim.add(1, 65, 1, 0, 200);
        sim.add(5, 65, 1, 0, 100);
        sim.step();
        sim.world.override(
                3,
                65,
                1,
                Geometry.declared(
                        EnumSet.noneOf(Face.class),
                        Geometry.FULL_VOLUME,
                        Geometry.FLAG_POSITIONAL | Geometry.boundaryFlag(Face.WEST)));
        sim.step();
        ZoneView west = sim.zoneAt(1, 65, 1);
        ZoneView east = sim.zoneAt(5, 65, 1);
        assertNotEquals(west.id(), east.id());
        assertEquals(27, west.cellCount());
        assertEquals(28, east.cellCount(), "the opening joins the side that isn't a boundary");
        double before = west.moles(O2) / west.volume();
        sim.step();
        sim.step();
        ZoneView after = sim.zoneAt(1, 65, 1);
        assertTrue(after.moles(O2) / after.volume() < before, "gas crosses");
    }

    /** A fire flag on a position's own geometry reaches the hotspots, which started without it. */
    @Test
    void api_023_own_geometry_reaches_the_hotspots() {
        Sim sim = rooms();
        sim.add(1, 65, 1, 0, 200);
        sim.step();
        assertEquals(0, sim.pipeline.hotspots().size());
        sim.world.override(1, 65, 1, Geometry.AIR.withFlags(Geometry.FLAG_POSITIONAL | Geometry.FLAG_FIRE));
        sim.step();
        assertEquals(1, sim.pipeline.hotspots().size());
    }

    /** A position's own transmittance reaches conduction, which started without it (HEAT-002). */
    @Test
    void api_023_own_geometry_reaches_conduction() {
        double insulated = coolingThroughOwnWalls(1e-6);
        double conducting = coolingThroughOwnWalls(50);
        assertTrue(conducting > 100 * insulated, "cooling " + conducting + " against " + insulated);
    }

    /** How far a one-cell room whose six walls are positions' own geometry cools in one tick. */
    private static double coolingThroughOwnWalls(double transmittance) {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), TestWorld.PALETTE);
        sim.world.room(1, 65, 1, 1, 65, 1);
        Geometry wall = Geometry.SOLID.withThermal(transmittance, Double.NaN).withFlags(Geometry.FLAG_POSITIONAL);
        for (Face f : Face.values()) {
            sim.world.override(1 + f.dx(), 65 + f.dy(), 1 + f.dz(), wall);
        }
        sim.addSeqs.add(
                sim.pipeline.add(SectionKeys.block(1, 65, 1), Sim.moles(30, 0), 350, LedgerCategory.API, true, "test"));
        sim.step();
        ZoneView before = sim.zoneAt(1, 65, 1);
        assertNotNull(before);
        sim.step();
        return before.temperature() - sim.zoneAt(1, 65, 1).temperature();
    }

    /** The engine's copy of a palette takes in new geometries; the palette it came from doesn't change. */
    @Test
    void api_023_extensible_copy_leaves_the_palette_alone() {
        GeometryPalette.Builder builder = GeometryPalette.builder();
        Geometry slab = Geometry.declared(EnumSet.of(Face.DOWN), 500, 0);
        int slabId = builder.intern(slab);
        GeometryPalette palette = builder.build();
        int size = palette.size();
        assertThrows(IllegalStateException.class, () -> palette.internExtra(OPEN), "a built palette doesn't grow");

        GeometryPalette copy = palette.extensible();
        assertEquals(slabId, copy.internExtra(slab), "an equal geometry keeps its id");
        assertEquals(GeometryPalette.AIR_ID, copy.internExtra(Geometry.AIR));
        int id = copy.internExtra(OPEN);
        assertEquals(size, id);
        assertEquals(id, copy.internExtra(OPEN), "and so does one added before");
        assertSame(OPEN, copy.geometry(id));
        assertEquals(size + 1, copy.size());
        assertEquals(size, palette.size(), "the palette it was copied from");
    }

    /** A palette can't hold more ids than a snapshot can store (unsigned shorts). */
    @Test
    void api_023_full_palette_refuses_a_geometry() {
        GeometryPalette palette = TestWorld.PALETTE.extensible();
        for (int i = palette.size(); i < GeometryPalette.MAX_SIZE; i++) {
            assertEquals(i, palette.internExtra(Geometry.SOLID.withIgnition(i, Double.NaN)));
        }
        assertEquals(-1, palette.internExtra(OPEN));
        assertEquals(GeometryPalette.MAX_SIZE, palette.size());
    }

    /** Resolving a snapshot puts the ids in place, and a geometry that can't have one leaves the state's. */
    @Test
    void api_023_resolving_a_snapshot_puts_ids_in_place() {
        short[] ids = new short[SectionSnapshot.SIZE];
        SectionSnapshot s = SectionSnapshot.withOverrides(
                1L, 2L, 3L, ids, new int[] {5, 9}, new Geometry[] {OPEN, Geometry.SOLID.withIgnition(1, Double.NaN)});
        assertTrue(s.hasOverrides());
        assertEquals(0, s.geometryId(5), "before resolving, the block state's");

        SectionSnapshot resolved = s.resolved(g -> g == OPEN ? 0xFFFF : -1);
        assertFalse(resolved.hasOverrides());
        assertEquals(1L, resolved.key());
        assertEquals(2L, resolved.lifecycleGeneration());
        assertEquals(3L, resolved.blockGeneration());
        assertEquals(0xFFFF, resolved.geometryId(5), "the highest id fits");
        assertEquals(0, resolved.geometryId(9), "no id: the block state's");

        SectionSnapshot plain = SectionSnapshot.of(1L, 2L, 3L, ids);
        assertSame(plain, plain.resolved(g -> 7), "nothing to resolve");
        assertArrayEquals(ids, s.geometryIds(), "the snapshot itself doesn't change");
    }
}
