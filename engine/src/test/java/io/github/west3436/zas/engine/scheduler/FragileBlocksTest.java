package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.flow.FragileSettings;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Which fragile blocks a pressure difference breaks (FLOW-007, FLOW-015), what a paused zone, an
 * unresolved region or an unloaded chunk on the far side does (GEO-034, GEO-035), what it costs while
 * nothing is near the threshold, and that a tick's breaks are bounded. The block changes are made here
 * as the server thread would make them.
 */
class FragileBlocksTest {
    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    private static final int GLASS = BUILDER.intern(Geometry.SOLID.withFlags(Geometry.FLAG_PRESSURE_FRAGILE));
    /** A glass pane running north to south: sub-cell 0 is its west half, 1 its east half. */
    private static final int PANE =
            BUILDER.intern(GeometryDeriver.derive(paneVoxels()).withFlags(Geometry.FLAG_PRESSURE_FRAGILE));

    private static final GeometryPalette PALETTE = BUILDER.build();

    private static final FragileSettings ON =
            new FragileSettings(FragileSettings.DEFAULT_THRESHOLD_KPA, FragileSettings.DEFAULT_MAX_BREAKS);

    private static final long EAST = SectionKeys.chunk(1, 0);

    /** Moles of gas that fill this many cells to a pressure at the test's temperature. */
    private static double molesFor(double kpa, int cells) {
        return kpa * 1000 * cells / (io.github.west3436.zas.engine.gas.GasConstants.R * Sim.ROOM_T);
    }

    private static BitSet paneVoxels() {
        BitSet s = new BitSet(GeometryDeriver.VOXELS);
        GeometryDeriver.fill(s, 7, 0, 0, 9, 16, 16);
        return s;
    }

    private static Sim sim(Ambient ambient) {
        Sim sim = new Sim(new TestWorld(ambient), PALETTE).withoutHeat();
        sim.settings = sim.settings.withFragile(ON);
        return sim;
    }

    /** A room of 27 cells at {0..2, 64..66, 0..2} with a window at (3, 65, 1), open air beyond it. */
    private static Sim roomWithWindow(Ambient ambient, int window) {
        Sim sim = sim(ambient);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.set(3, 65, 1, window);
        return sim;
    }

    private static List<Long> blocks(DimensionPipeline.Applied a) {
        List<Long> out = new ArrayList<>();
        for (PressureBreak b : a.pressureBreaks()) {
            out.add(b.block());
        }
        return out;
    }

    private static void breakBlocks(Sim sim, DimensionPipeline.Applied a) {
        for (PressureBreak b : a.pressureBreaks()) {
            sim.world.set(
                    SectionKeys.blockX(b.block()), SectionKeys.blockY(b.block()), SectionKeys.blockZ(b.block()), 0);
        }
    }

    // ---- across a block ----

    /** FLOW-015: 1 atm against a vacuum holds. */
    @Test
    void flow_015_a_window_holds_one_atm_against_vacuum() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.add(1, 65, 1, molesFor(101, 27), 0);
        DimensionPipeline.Applied a = sim.step();
        assertNotNull(sim.zoneAt(1, 65, 1), "the room didn't zone");
        assertEquals(101, sim.zoneAt(1, 65, 1).pressure(), 0.01);
        assertEquals(List.of(), blocks(a));
        assertEquals(List.of(), blocks(sim.step()));
    }

    /** FLOW-015: 160 kPa against a vacuum breaks it. */
    @Test
    void flow_015_a_window_breaks_at_160_kpa_against_vacuum() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.add(1, 65, 1, molesFor(160, 27), 0);
        DimensionPipeline.Applied a = sim.step();
        assertEquals(160, sim.zoneAt(1, 65, 1).pressure(), 0.01);
        assertEquals(
                List.of(new PressureBreak(
                        SectionKeys.block(3, 65, 1), sim.zoneAt(1, 65, 1).id(), 0)),
                a.pressureBreaks());
    }

    /** Glass that a seal provider has answered for: every face sealed, as the block's own data says. */
    private static Geometry glassWithItsOwnGeometry() {
        return Geometry.declared(EnumSet.allOf(Face.class), 0, Geometry.FLAG_POSITIONAL)
                .withDataOf(PALETTE.geometry(GLASS));
    }

    /**
     * A fragile block with a geometry of its own (API-023) is still fragile. The engine's palette takes
     * that geometry in after the engine has started, so not every fragile id is known at the start.
     */
    @Test
    void flow_007_a_fragile_block_with_its_own_geometry_still_breaks() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, molesFor(101, 27), 0);
        assertEquals(List.of(), blocks(sim.step()));
        sim.world.override(3, 65, 1, glassWithItsOwnGeometry());
        assertEquals(List.of(), blocks(sim.step()), "101 kPa against a vacuum holds");
        sim.add(1, 65, 1, molesFor(59, 27), 0);
        assertEquals(List.of(SectionKeys.block(3, 65, 1)), blocks(sim.step()));
    }

    /** The first fragile geometry the engine sees can be a position's, with sections already mirrored. */
    @Test
    void flow_007_the_first_fragile_geometry_can_come_with_a_position() {
        Sim sim = new Sim(new TestWorld(Ambient.VACUUM), TestWorld.PALETTE).withoutHeat();
        sim.settings = sim.settings.withFragile(ON);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, molesFor(160, 27), 0);
        assertEquals(List.of(), blocks(sim.step()), "no fragile block yet");
        sim.world.override(3, 65, 1, glassWithItsOwnGeometry());
        assertEquals(List.of(SectionKeys.block(3, 65, 1)), blocks(sim.step()));
    }

    /** OPS-005: a paused dimension's zones are all paused, so no window breaks until it resumes. */
    @Test
    void ops_005_a_paused_dimension_holds_its_windows_until_it_resumes() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.add(1, 65, 1, molesFor(160, 27), 0);
        sim.step();
        sim.settings = sim.settings.withFragile(ON).withDimensionPaused(true);
        assertEquals(List.of(), blocks(sim.step()), "at 160 kPa, paused");
        assertEquals(List.of(), blocks(sim.step()), "still paused");
        sim.settings = sim.settings.withDimensionPaused(false);
        assertEquals(List.of(SectionKeys.block(3, 65, 1)), blocks(sim.step()), "resumed");
    }

    /** FLOW-007 says "exceeds": a difference of exactly the threshold holds. */
    @Test
    void flow_007_exactly_the_threshold_holds() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.add(1, 65, 1, molesFor(160, 27), 0);
        sim.step();
        double p = sim.zoneAt(1, 65, 1).pressure();
        sim.settings = sim.settings.withFragile(new FragileSettings(p, 32));
        assertEquals(List.of(), blocks(sim.step()));
        sim.settings = sim.settings.withFragile(new FragileSettings(Math.nextDown(p), 32));
        assertEquals(List.of(SectionKeys.block(3, 65, 1)), blocks(sim.step()));
    }

    /** The difference, not which way it points: ambient air above the room's pressure breaks it too. */
    @Test
    void flow_007_a_low_room_under_a_high_ambient_breaks() {
        Ambient high = Ambient.ofMolesPerCubicMetre(Map.of(Ambient.NITROGEN, molesFor(200, 1)), Sim.ROOM_T);
        Sim sim = roomWithWindow(high, GLASS);
        sim.add(1, 65, 1, 1, 0); // the room forms full of the ambient's air, so at no difference
        assertEquals(List.of(), blocks(sim.step()));
        sim.remove(1, 65, 1, molesFor(190, 27), 0);
        DimensionPipeline.Applied a = sim.step();
        ZoneView zone = sim.zoneAt(1, 65, 1);
        assertNotNull(zone, "the room didn't zone");
        assertTrue(zone.pressure() < 50, "pressure " + zone.pressure());
        assertEquals(List.of(new PressureBreak(SectionKeys.block(3, 65, 1), 0, zone.id())), a.pressureBreaks());
    }

    /** A window between two rooms: the difference between their pressures counts, not the ambient's. */
    @Test
    void flow_007_between_two_zones() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(4, 64, 0, 6, 66, 2);
        sim.world.set(3, 65, 1, GLASS);
        sim.add(1, 65, 1, molesFor(240, 27), 0);
        sim.add(5, 65, 1, molesFor(110, 27), 0);
        assertEquals(List.of(), blocks(sim.step()), "240 against 110 kPa, whatever the ambient");
        sim.remove(5, 65, 1, molesFor(50, 27), 0);
        DimensionPipeline.Applied a = sim.step();
        assertEquals(
                List.of(new PressureBreak(
                        SectionKeys.block(3, 65, 1),
                        sim.zoneAt(1, 65, 1).id(),
                        sim.zoneAt(5, 65, 1).id())),
                a.pressureBreaks(),
                "240 against 60 kPa");
    }

    /** A pane's own two cells are its two sides (GEO-004). */
    @Test
    void flow_007_a_pane_between_a_zone_and_the_ambient() {
        Sim hold = roomWithWindow(Ambient.VACUUM, PANE);
        hold.add(1, 65, 1, molesFor(101, 28), 0);
        assertEquals(List.of(), blocks(hold.step()));
        Sim fail = roomWithWindow(Ambient.VACUUM, PANE);
        fail.add(1, 65, 1, molesFor(170, 28), 0);
        DimensionPipeline.Applied a = fail.step();
        assertTrue(
                fail.zoneAt(1, 65, 1).pressure() > 150,
                "pressure " + fail.zoneAt(1, 65, 1).pressure());
        assertEquals(List.of(SectionKeys.block(3, 65, 1)), blocks(a));
    }

    @Test
    void flow_007_a_pane_between_two_zones() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(4, 64, 0, 6, 66, 2);
        sim.world.set(3, 65, 1, PANE);
        sim.add(1, 65, 1, molesFor(170, 28), 0);
        sim.add(5, 65, 1, molesFor(10, 28), 0);
        DimensionPipeline.Applied a = sim.step();
        assertEquals(
                List.of(new PressureBreak(
                        SectionKeys.block(3, 65, 1),
                        sim.zoneAt(1, 65, 1).id(),
                        sim.zoneAt(5, 65, 1).id())),
                a.pressureBreaks());
    }

    /** A second layer of glass is no side: each block has the difference on one side only. */
    @Test
    void flow_007_two_layers_of_glass_hold() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.box(4, 64, 0, 4, 66, 2, TestWorld.SOLID);
        sim.world.set(3, 65, 1, GLASS);
        sim.world.set(4, 65, 1, GLASS);
        sim.add(1, 65, 1, molesFor(400, 27), 0);
        assertEquals(List.of(), blocks(sim.step()));
        assertEquals(List.of(), blocks(sim.step()));
    }

    /** A block with the same zone all around it has no difference across it. */
    @Test
    void flow_007_glass_inside_a_room_holds() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 4, 68, 4);
        sim.world.set(2, 66, 2, GLASS);
        sim.add(1, 65, 1, molesFor(400, 124), 0);
        assertEquals(List.of(), blocks(sim.step()));
    }

    @Test
    void flow_007_a_window_at_the_top_of_the_world_has_the_ambient_above_it() {
        Sim sim = sim(Ambient.VACUUM);
        int top = (TestWorld.MAX_SECTION_Y + 1) * 16 - 1;
        sim.world.box(-1, top - 3, -1, 3, top, 3, TestWorld.SOLID);
        sim.world.box(0, top - 2, 0, 2, top - 1, 2, TestWorld.AIR);
        sim.world.set(1, top, 1, GLASS);
        sim.add(1, top - 1, 1, molesFor(160, 18), 0);
        assertEquals(List.of(SectionKeys.block(1, top, 1)), blocks(sim.step()));
    }

    // ---- the switch, and the cost ----

    /** FLOW-016: off, nothing breaks and no block is looked at. */
    @Test
    void flow_016_off_nothing_breaks() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.add(1, 65, 1, molesFor(400, 27), 0);
        assertEquals(List.of(), blocks(sim.step()));
        assertEquals(0, sim.engine.fragileBlocksJudged());
        sim.settings = sim.settings.withFragile(ON);
        assertEquals(List.of(SectionKeys.block(3, 65, 1)), blocks(sim.step()), "turned on, it breaks");
    }

    /** With nothing near the threshold, the tick costs a comparison per zone and no look at a block. */
    @Test
    void flow_007_no_block_is_looked_at_while_no_difference_could_break_one() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.add(1, 65, 1, molesFor(101, 27), 0);
        sim.steps(3);
        assertEquals(0, sim.engine.fragileBlocksJudged());
        sim.add(1, 65, 1, molesFor(60, 27), 0);
        sim.step();
        assertEquals(1, sim.engine.fragileBlocksJudged(), "over the threshold, the block is judged");
    }

    /** A difference elsewhere opens the gate, but judges only the fragile blocks, and breaks none. */
    @Test
    void flow_007_a_difference_elsewhere_breaks_nothing() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.box(4, 64, 0, 4, 66, 2, TestWorld.SOLID);
        sim.world.set(3, 65, 1, GLASS);
        sim.world.set(4, 65, 1, GLASS); // two layers, so no difference across either
        sim.world.room(0, 70, 0, 2, 72, 2);
        sim.add(1, 65, 1, molesFor(101, 27), 0);
        sim.add(1, 71, 1, molesFor(400, 27), 0);
        assertEquals(List.of(), blocks(sim.step()));
        assertEquals(2, sim.engine.fragileBlocksJudged());
    }

    /** The default palette has no fragile block, so nothing is indexed or looked at. */
    @Test
    void flow_007_a_palette_without_fragile_blocks_costs_nothing() {
        Sim sim = new Sim(new TestWorld(Ambient.VACUUM), TestWorld.PALETTE);
        sim.settings = sim.settings.withFragile(ON);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, molesFor(400, 27), 0);
        assertEquals(List.of(), blocks(sim.step()));
        assertEquals(0, sim.engine.fragileBlocksJudged());
    }

    // ---- the break, as an ordinary block change ----

    /** The rooms merge through the usual path, and the checker (on in {@link Sim}) finds it all. */
    @Test
    void flow_007_a_break_merges_the_zones_and_conserves_the_gas() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(4, 64, 0, 6, 66, 2);
        sim.world.set(3, 65, 1, GLASS);
        double moles = molesFor(170, 27) + molesFor(5, 27);
        sim.add(1, 65, 1, molesFor(170, 27), 0);
        sim.add(5, 65, 1, molesFor(5, 27), 0);
        DimensionPipeline.Applied a = sim.step();
        assertEquals(1, a.pressureBreaks().size());
        breakBlocks(sim, a);
        // CORE-023: joined by a direct opening, they merge once their mixtures are close.
        for (int i = 0;
                i < 400 && sim.zoneAt(1, 65, 1).id() != sim.zoneAt(5, 65, 1).id();
                i++) {
            assertEquals(List.of(), blocks(sim.step()), "the window is gone");
        }
        ZoneView west = sim.zoneAt(1, 65, 1);
        assertEquals(west.id(), sim.zoneAt(5, 65, 1).id(), "the rooms didn't merge");
        assertEquals(moles, west.moles()[Sim.N2], 1e-6);
    }

    /** A window against the ambient opens the room, whose gas leaves through the usual accounting. */
    @Test
    void flow_007_a_break_to_the_ambient_vents_and_conserves() {
        Sim sim = roomWithWindow(Ambient.VACUUM, GLASS);
        sim.add(1, 65, 1, molesFor(160, 27), 0);
        DimensionPipeline.Applied a = sim.step();
        assertEquals(1, a.pressureBreaks().size());
        breakBlocks(sim, a);
        sim.steps(5);
        assertEquals(List.of(), blocks(sim.step()));
    }

    /** A chain as the pressure moves: one window a tick, in order, until the pressure no longer can. */
    private static List<List<Long>> chain() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(4, 64, 0, 6, 66, 2);
        sim.world.room(8, 64, 0, 10, 66, 2);
        sim.world.room(12, 64, 0, 14, 66, 2);
        sim.world.set(3, 65, 1, GLASS);
        sim.world.set(7, 65, 1, GLASS);
        sim.world.set(11, 65, 1, GLASS);
        sim.add(1, 65, 1, molesFor(400, 27), 0);
        List<List<Long>> ticks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            DimensionPipeline.Applied a = sim.step();
            ticks.add(blocks(a));
            breakBlocks(sim, a);
        }
        return ticks;
    }

    @Test
    void flow_007_a_chain_of_windows_fails_in_sequence() {
        List<Long> broken = new ArrayList<>();
        for (List<Long> tick : chain()) {
            assertTrue(tick.size() <= 1, "more than one window in a tick: " + tick);
            broken.addAll(tick);
        }
        assertEquals(List.of(SectionKeys.block(3, 65, 1), SectionKeys.block(7, 65, 1)), broken);
    }

    /** §3.5: the same world asks for the same breaks in the same ticks. */
    @Test
    void flow_007_is_deterministic() {
        assertEquals(chain(), chain());
    }

    /** However many windows fail, a tick asks for no more than the cap. The rest aren't queued. */
    @Test
    void flow_007_a_tick_breaks_no_more_than_the_cap() {
        Sim sim = sim(Ambient.VACUUM);
        sim.settings = sim.settings.withFragile(new FragileSettings(150, 4));
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.box(3, 64, 0, 3, 66, 2, GLASS);
        sim.add(1, 65, 1, molesFor(400, 27), 0);
        DimensionPipeline.Applied first = sim.step();
        assertEquals(4, first.pressureBreaks().size());
        assertEquals(blocks(first), blocks(sim.step()), "nothing changed, so the same four");
        // As if the world had moved past those four: the next tick finds the next four.
        for (long block : blocks(first)) {
            sim.world.set(
                    SectionKeys.blockX(block), SectionKeys.blockY(block), SectionKeys.blockZ(block), TestWorld.SOLID);
        }
        sim.step();
        List<Long> next = blocks(sim.step());
        assertEquals(4, next.size());
        for (long block : next) {
            assertFalse(blocks(first).contains(block), "already asked for");
        }
    }

    /** A break a rejected component's numbers decided isn't applied (§3.3). The next tick finds it. */
    @Test
    void flow_007_a_rejected_zone_breaks_nothing() {
        // Two rooms exchanging heat through the wall are a component across the chunk border.
        Sim sim = new Sim(new TestWorld(Ambient.VACUUM), PALETTE);
        sim.world.room(12, 64, 1, 14, 66, 3);
        sim.world.room(16, 64, 1, 18, 66, 3);
        sim.world.set(15, 65, 2, GLASS);
        sim.add(13, 65, 2, molesFor(200, 27), 0);
        sim.add(17, 65, 2, molesFor(5, 27), 0);
        sim.step();
        sim.settings = sim.settings.withFragile(ON);
        sim.betweenCloseAndApply = s -> {
            s.world.unload(SectionKeys.chunk(0, 0));
            s.pipeline.chunkUnloaded(SectionKeys.chunk(0, 0));
            s.world.load(SectionKeys.chunk(0, 0));
            s.pipeline.chunkLoaded(SectionKeys.chunk(0, 0));
        };
        DimensionPipeline.Applied rejected = sim.step();
        sim.betweenCloseAndApply = s -> {};
        assertTrue(rejected.rejected() >= 1, "nothing was rejected");
        assertEquals(List.of(), blocks(rejected));
        assertEquals(List.of(SectionKeys.block(15, 65, 2)), blocks(sim.step()));
    }

    // ---- paused zones, unresolved regions and unloaded chunks ----

    /**
     * A live zone against a paused one holds: the paused zone's number is its last-known state, and
     * this is no reason to break a window (GEO-035). When the zone resumes, the block is judged on the
     * numbers then.
     */
    @Test
    void geo_035_a_paused_zone_on_the_far_side_holds_until_it_resumes() {
        Sim sim = sim(Ambient.VACUUM);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.world.room(4, 64, 1, 9, 66, 3); // west, in the loaded chunk
        sim.world.room(11, 64, 1, 19, 66, 3); // east, across the chunk border
        sim.world.set(10, 65, 2, GLASS);
        sim.add(5, 65, 2, molesFor(200, 54), 0);
        sim.add(12, 65, 2, molesFor(20, 81), 0);
        sim.step();
        ChunkRecord saved = sim.unload(EAST);
        sim.step();
        assertTrue(sim.zoneAt(12, 65, 2).paused(), "the far side didn't pause");
        assertFalse(sim.zoneAt(5, 65, 2).paused(), "the near side paused");
        sim.settings = sim.settings.withFragile(ON);
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of(), blocks(sim.step()), "broke against a paused zone");
        }
        sim.load(EAST, saved);
        List<Long> after = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            after.addAll(blocks(sim.step()));
        }
        assertFalse(sim.zoneAt(12, 65, 2).paused(), "the far side didn't resume");
        assertEquals(List.of(SectionKeys.block(10, 65, 2)), after.subList(0, 1));
    }

    /** A paused zone against the open air: its number can't have moved, so nothing decides a break. */
    @Test
    void geo_035_a_paused_zone_holds_against_the_ambient_until_it_resumes() {
        Sim sim = sim(Ambient.VACUUM);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.world.room(12, 64, 1, 19, 66, 3); // across the chunk border
        sim.world.set(11, 65, 2, GLASS);
        sim.add(13, 65, 2, molesFor(160, 72), 0);
        sim.step();
        ChunkRecord saved = sim.unload(EAST);
        sim.step();
        assertTrue(sim.zoneAt(13, 65, 2).paused(), "the room didn't pause");
        sim.settings = sim.settings.withFragile(ON);
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of(), blocks(sim.step()), "broke with no live number");
        }
        sim.load(EAST, saved);
        List<Long> after = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            after.addAll(blocks(sim.step()));
        }
        assertEquals(List.of(SectionKeys.block(11, 65, 2)), after.subList(0, 1));
    }

    /**
     * A chunk that isn't loaded across the block: it's never captured, and the block holds. It breaks
     * once the chunk loads and shows open air there.
     */
    @Test
    void geo_034_an_unloaded_chunk_on_the_far_side_holds() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.unload(EAST);
        sim.world.room(12, 64, 1, 14, 66, 3);
        sim.world.set(15, 65, 2, GLASS);
        sim.add(13, 65, 2, molesFor(160, 27), 0);
        for (int i = 0; i < 4; i++) {
            assertEquals(List.of(), blocks(sim.step()), "broke against an unloaded chunk");
        }
        assertFalse(sim.world.isLoaded(EAST), "the chunk was force-loaded");
        sim.load(EAST, null);
        List<Long> after = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            after.addAll(blocks(sim.step()));
        }
        assertEquals(List.of(SectionKeys.block(15, 65, 2)), after.subList(0, 1));
    }

    /** A chunk unloading next to a window with the section mirrored: the block holds, the same. */
    @Test
    void geo_034_a_chunk_unloading_on_the_far_side_holds() {
        Sim sim = sim(Ambient.VACUUM);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.world.room(12, 64, 1, 14, 66, 3);
        sim.world.set(15, 65, 2, GLASS);
        sim.world.room(17, 64, 1, 19, 66, 3); // A room in the east chunk, past the window's far side.
        sim.add(13, 65, 2, molesFor(160, 27), 0);
        sim.add(18, 65, 2, molesFor(1, 27), 0);
        sim.step();
        sim.unload(EAST);
        sim.settings = sim.settings.withFragile(ON);
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of(), blocks(sim.step()));
        }
    }

    /**
     * A region held at a breach because it reaches an unloaded chunk (GEO-026) reads as unresolved,
     * and its number as a guess: a window between it and a live zone holds until it classifies.
     */
    @Test
    void geo_035_an_unresolved_region_on_the_far_side_holds() {
        Sim sim = sim(Ambient.VACUUM);
        sim.settings = sim.settings.withFragile(FragileSettings.OFF);
        sim.world.room(4, 64, 1, 10, 66, 3); // A, in the west chunk
        sim.world.room(12, 64, 1, 19, 66, 3); // B, unzoned, across both chunks
        sim.world.room(12, 68, 1, 14, 70, 3); // C, above B, in the west chunk
        sim.world.set(13, 67, 2, GLASS);
        sim.add(5, 65, 2, 30, 0);
        sim.add(13, 69, 2, molesFor(160, 27), 0);
        sim.step();
        sim.unload(EAST);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        assertTrue(sim.snapshot().heldAt(SectionKeys.block(13, 66, 2), 0), "B isn't held");
        assertFalse(sim.zoneAt(13, 69, 2).paused(), "C paused");
        sim.settings = sim.settings.withFragile(ON);
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of(), blocks(sim.step()), "broke against an unresolved region");
        }
        sim.load(EAST, null);
        List<Long> after = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            after.addAll(blocks(sim.step()));
        }
        assertEquals(List.of(SectionKeys.block(13, 67, 2)), after.subList(0, 1));
    }

    // ---- a section the mirror lacks ----

    /**
     * A corridor of 79 cells from x = 0 with a window in the last block of a section, at x = 79, gas
     * added at its far end: nothing has captured the section across the window, past the prefetch
     * around the operation and past any cell of the corridor. Steps until the corridor has zoned.
     */
    private static Sim corridor(int farSide) {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.box(-1, 64, 0, 79, 66, 2, TestWorld.SOLID);
        sim.world.box(0, 65, 1, 78, 65, 1, TestWorld.AIR);
        sim.world.set(79, 65, 1, GLASS);
        sim.world.set(80, 65, 1, farSide);
        sim.add(1, 65, 1, molesFor(160, 79), 0);
        for (int i = 0; i < 10 && sim.zoneAt(1, 65, 1) == null; i++) {
            assertEquals(List.of(), blocks(sim.step()), "the far side isn't known yet");
        }
        assertNotNull(sim.zoneAt(1, 65, 1), "the corridor didn't zone");
        return sim;
    }

    /** The far side's section was never captured: it's asked for, and the next tick decides. */
    @Test
    void flow_007_an_unmirrored_far_side_is_asked_for() {
        Sim sim = corridor(TestWorld.AIR);
        assertEquals(List.of(SectionKeys.block(79, 65, 1)), blocks(sim.step()));
    }

    /** A solid block there: asked for once, then holding, with no more captures. */
    @Test
    void flow_007_a_solid_far_side_in_another_section_holds_without_asking_again() {
        Sim sim = corridor(TestWorld.SOLID);
        sim.step();
        int captures = sim.world.captures;
        for (int i = 0; i < 4; i++) {
            assertEquals(List.of(), blocks(sim.step()));
        }
        assertEquals(captures, sim.world.captures, "the section is asked for over and over");
    }
}
