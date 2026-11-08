package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.classify.OpenCells;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Classifying a cell as open without a fill: straight up its column (GEO-021) or because it was found
 * open before and no block has changed since (GEO-027). What these check is when a fill runs, and that
 * the answers stay right.
 */
class OpenClassificationTest {
    private static final OpOutcome.Outcome AMBIENT = OpOutcome.Outcome.AMBIENT;
    private static final OpOutcome.Outcome ZONE = OpOutcome.Outcome.ZONE;

    /** The seed of every fill the test's simulation ran. */
    private final List<Long> fills = new ArrayList<>();

    private Sim sim() {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), TestWorld.PALETTE);
        sim.engine.classifier().onFill(fills::add);
        return sim;
    }

    /** Adds gas at a cell and runs atmos ticks until the add finishes. */
    private static OpOutcome.Outcome add(Sim sim, int x, int y, int z) {
        long seq = sim.add(x, y, z, 0, 1);
        for (int i = 0; i < 16 && !sim.outcomes.containsKey(seq); i++) {
            sim.step();
        }
        OpOutcome outcome = sim.outcomes.get(seq);
        assertNotNull(outcome, "the add didn't finish");
        return outcome.outcome();
    }

    /** A sealed room, x and z 0 to 2, y 64 to 66, with a hole to the open air in a side wall. */
    private static void roomWithSideHole(Sim sim) {
        sim.world.room(0, 64, 0, 2, 66, 2).set(1, 65, 3, TestWorld.AIR);
    }

    @Test
    void geo_021_open_column_takes_no_fill() {
        Sim sim = sim();
        assertEquals(AMBIENT, add(sim, 5, 65, 5));
        assertEquals(0, fills.size(), "open sky");
    }

    /** The add finishes in its first tick: the column comes with the batch that holds the add. */
    @Test
    void geo_021_the_column_arrives_with_the_add() {
        Sim sim = sim();
        long seq = sim.add(5, 65, 5, 0, 1);
        sim.step();
        assertEquals(AMBIENT, sim.outcomes.get(seq).outcome());
    }

    @Test
    void geo_021_at_the_top_of_the_world() {
        Sim sim = sim();
        assertEquals(AMBIENT, add(sim, 5, TestWorld.MAX_SECTION_Y * 16 + 15, 5));
        assertEquals(0, fills.size());
    }

    @Test
    void geo_021_a_lid_takes_a_fill_however_high() {
        Sim sim = sim();
        sim.world.set(5, 300, 5, TestWorld.SOLID);
        assertEquals(AMBIENT, add(sim, 5, 65, 5), "open around the lid");
        assertEquals(List.of(CellIds.of(5, 65, 5)), fills);
    }

    @Test
    void geo_021_the_column_goes_through_a_block_gas_passes() {
        Sim sim = sim();
        sim.world.override(5, 70, 5, Geometry.declared(EnumSet.noneOf(Face.class), 500, Geometry.FLAG_POSITIONAL));
        assertEquals(AMBIENT, add(sim, 5, 65, 5));
        assertEquals(0, fills.size(), "the column goes through the block");

        sim.world.override(9, 70, 9, Geometry.declared(EnumSet.of(Face.DOWN), 500, Geometry.FLAG_POSITIONAL));
        assertEquals(AMBIENT, add(sim, 9, 65, 9));
        assertEquals(List.of(CellIds.of(9, 65, 9)), fills, "a gas-tight lower face stops the column");
    }

    @Test
    void geo_021_a_sealed_room_is_filled_and_zoned() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2);
        assertEquals(ZONE, add(sim, 1, 65, 1));
        assertEquals(1, fills.size());
        assertEquals(27, sim.zoneAt(1, 65, 1).cellCount());
    }

    /**
     * The tick's settings set the fill's bound (GEO-020, D-23). Over it the room is open and its cells
     * are cached as open; raising the bound puts the cache in doubt, so the next add fills and zones.
     */
    @Test
    void geo_020_the_bound_comes_from_the_tick_settings() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.settings = sim.settings.withEnclosureMaxCells(26);
        assertEquals(AMBIENT, add(sim, 1, 65, 1), "27 cells over a bound of 26");
        assertEquals(1, fills.size());
        assertEquals(AMBIENT, add(sim, 1, 65, 1), "found open before");
        assertEquals(1, fills.size(), "the cache answers");

        sim.settings = sim.settings.withEnclosureMaxCells(27);
        assertEquals(ZONE, add(sim, 1, 65, 1), "27 cells at a bound of 27");
        assertEquals(2, fills.size(), "the raised bound fills again");
        assertEquals(27, sim.zoneAt(1, 65, 1).cellCount());

        sim.settings = sim.settings.withEnclosureMaxCells(26);
        sim.step();
        assertEquals(27, sim.zoneAt(1, 65, 1).cellCount(), "a zone stays when the bound goes down");
    }

    /**
     * A zone's mirror reaches one section past it, so a cell beside the zone may not see the top of its
     * column. The add waits for the sections rather than starting a fill.
     */
    @Test
    void geo_021_beside_a_zone_the_add_waits_for_the_column() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2);
        add(sim, 1, 65, 1);
        fills.clear();
        assertEquals(AMBIENT, add(sim, 8, 65, 8), "same section as the zone, open above");
        assertEquals(0, fills.size());
    }

    /** A zone overhead stops the column, as it stops a fill. */
    @Test
    void geo_021_a_zone_overhead_stops_the_column() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 5000); // enough not to vent away during the test
        sim.step();
        sim.world.set(1, 63, 1, TestWorld.AIR); // a hole in the floor
        sim.step();
        fills.clear();

        assertNotNull(sim.zoneAt(1, 65, 1), "the test needs the zone over the hole");
        assertEquals(AMBIENT, add(sim, 1, 62, 1));
        assertTrue(fills.contains(CellIds.of(1, 62, 1)), "the column runs into the zone");
    }

    /** A breach waits a tick for the column, and takes no fill. */
    @Test
    void geo_021_a_breach_onto_open_sky_takes_no_fill() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2);
        add(sim, 1, 65, 1);
        fills.clear();

        sim.world.set(1, 67, 1, TestWorld.AIR); // a hole in the roof
        sim.steps(4);
        assertEquals(List.of(), fills);
        assertEquals(0, sim.engine.classifier().unresolvedSeeds().length, "nothing is left held");
    }

    /** With a roof over the hole the column says nothing, and a fill finds the pocket as it always did. */
    @Test
    void geo_021_a_breach_into_a_pocket_takes_a_fill() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2).room(4, 64, 0, 6, 66, 2);
        add(sim, 1, 65, 1);
        fills.clear();

        sim.world.set(3, 65, 1, TestWorld.AIR);
        sim.step();
        assertEquals(List.of(CellIds.of(3, 65, 1)), fills);
        assertEquals(55, sim.zoneAt(1, 65, 1).cellCount(), "the room, the opening and the pocket");
    }

    @Test
    void geo_027_second_classification_of_an_open_cell_takes_no_fill() {
        Sim sim = sim();
        roomWithSideHole(sim);
        assertEquals(AMBIENT, add(sim, 1, 65, 1));
        assertEquals(1, fills.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(AMBIENT, add(sim, 1, 65, 1));
            assertEquals(1, fills.size(), "classification " + i);
        }
    }

    /** No zone keeps the section mirrored, so nothing but the batch can tell of the change. */
    @Test
    void geo_027_block_change_in_a_section_not_mirrored_invalidates_it() {
        Sim sim = sim();
        roomWithSideHole(sim);
        assertEquals(AMBIENT, add(sim, 1, 65, 1));
        assertEquals(AMBIENT, add(sim, 1, 65, 1));
        assertEquals(1, fills.size());

        assertFalse(mirrored(sim, 1, 65, 3), "the test needs the section out of the mirror");
        sim.world.set(1, 65, 3, TestWorld.SOLID);
        assertEquals(ZONE, add(sim, 1, 65, 1), "the hole is plugged");
        assertEquals(2, fills.size());
        assertEquals(27, sim.zoneAt(1, 65, 1).cellCount());
    }

    /** A zone next door keeps the section mirrored, so the change comes as a new copy of it. */
    @Test
    void geo_027_block_change_in_a_mirrored_section_invalidates_it() {
        Sim sim = sim();
        sim.world.room(6, 64, 0, 8, 66, 2);
        assertEquals(ZONE, add(sim, 7, 65, 1));
        roomWithSideHole(sim);
        sim.step();
        assertEquals(AMBIENT, add(sim, 1, 65, 1));
        int before = fills.size();
        assertEquals(AMBIENT, add(sim, 1, 65, 1));
        assertEquals(before, fills.size(), "from the cache");

        assertTrue(mirrored(sim, 1, 65, 3), "the test needs the section in the mirror");
        sim.world.set(1, 65, 3, TestWorld.SOLID);
        assertEquals(ZONE, add(sim, 1, 65, 1), "the hole is plugged");
        assertEquals(before + 1, fills.size());
    }

    /** A block changing where the fill never went leaves the answer, and takes no new fill. */
    @Test
    void geo_027_block_change_elsewhere_keeps_it() {
        Sim sim = sim();
        roomWithSideHole(sim);
        add(sim, 1, 65, 1);
        assertEquals(1, fills.size());

        sim.world.set(3000, 65, 3000, TestWorld.SOLID);
        assertEquals(AMBIENT, add(sim, 1, 65, 1));
        assertEquals(1, fills.size());
    }

    /**
     * A cell the column check found open is remembered like one a fill found open: beside a zone the
     * first add waits for the column, and the second doesn't need it.
     */
    @Test
    void geo_027_a_cell_found_open_by_its_column_is_remembered() {
        Sim sim = sim();
        sim.world.room(0, 64, 0, 2, 66, 2);
        add(sim, 1, 65, 1);
        assertEquals(AMBIENT, add(sim, 8, 65, 8));

        assertFalse(mirrored(sim, 8, 300, 8), "the test needs the top of the column out of the mirror");
        long seq = sim.add(8, 65, 8, 0, 1);
        sim.step();
        assertNotNull(sim.outcomes.get(seq), "the add waited for the column again");
        assertEquals(AMBIENT, sim.outcomes.get(seq).outcome());
    }

    /** A column found open rests on the sections up to the top of the world. */
    @Test
    void geo_027_a_change_high_in_the_column_invalidates_it() {
        Sim sim = sim();
        assertEquals(AMBIENT, add(sim, 5, 65, 5));
        sim.world.set(5, 300, 5, TestWorld.SOLID);
        assertEquals(AMBIENT, add(sim, 5, 65, 5));
        assertEquals(List.of(CellIds.of(5, 65, 5)), fills, "the lid means a fill");
    }

    /**
     * The way out of a tube runs through a chunk that then unloads. A new fill finds it unresolved
     * (GEO-024), so what was known can't stand.
     */
    @Test
    void geo_027_unloading_a_chunk_on_the_way_out_invalidates_it() {
        Sim sim = sim();
        sim.world.room(1, 64, 1, 30, 64, 1).set(31, 64, 1, TestWorld.AIR);
        assertEquals(AMBIENT, add(sim, 2, 64, 1));
        assertEquals(AMBIENT, add(sim, 2, 64, 1));
        assertEquals(1, fills.size());

        sim.unload(SectionKeys.chunk(1, 0));
        assertEquals(OpOutcome.Outcome.UNRESOLVED, add(sim, 2, 64, 1));
        assertTrue(fills.size() > 1, "a new fill");
    }

    @Test
    void geo_027_open_cells_follow_their_sections() {
        OpenCells open = new OpenCells();
        open.add(1, new long[] {10, 20});
        open.add(2, new long[] {20, 30});
        assertTrue(open.contains(1));
        assertTrue(open.contains(2));
        assertFalse(open.contains(3));

        open.sectionChanged(40);
        assertEquals(2, open.size(), "a section neither rests on");
        open.sectionChanged(10);
        assertFalse(open.contains(1));
        assertTrue(open.contains(2), "it doesn't rest on that section");
        open.sectionChanged(10);
        open.sectionChanged(20);
        assertFalse(open.contains(2));
        assertEquals(0, open.size());

        open.add(1, new long[] {10});
        open.add(1, new long[] {30});
        open.sectionChanged(10);
        assertTrue(open.contains(1), "adding it again replaced what it rested on");
    }

    @Test
    void geo_027_open_cells_stay_within_their_bound() {
        OpenCells open = new OpenCells();
        long[] sections = new long[1 << 15];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = i;
        }
        open.add(1, sections);
        open.add(2, sections);
        assertEquals(2, open.size());
        open.add(3, new long[] {1});
        assertEquals(1, open.size(), "past the bound everything older goes");
        assertTrue(open.contains(3));
        open.sectionChanged(1);
        assertEquals(0, open.size());
    }

    private static boolean mirrored(Sim sim, int x, int y, int z) {
        long key = SectionKeys.sectionOfBlock(x, y, z);
        for (long k : sim.engine.mirror().keys()) {
            if (k == key) {
                return true;
            }
        }
        return false;
    }
}
