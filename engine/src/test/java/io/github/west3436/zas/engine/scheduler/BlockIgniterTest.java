package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.fire.BlockIgnitionSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Blocks catching fire from hot gas and gas fires (FIRE-033, FIRE-034) through the whole pipeline.
 * The test plays the server thread, lighting and placing fires as the intents ask. A room is 3×3×3,
 * at (0..2, 64..66, 0..2), with walls around it.
 */
class BlockIgniterTest {
    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    private static final int FIRE = BUILDER.intern(Geometry.AIR.withFlags(Geometry.FLAG_FIRE));
    private static final int GAS_FIRE = BUILDER.intern(Geometry.AIR.withFlags(Geometry.FLAG_GAS_FIRE));
    private static final int LEVER = BUILDER.intern(Geometry.AIR.withFlags(Geometry.FLAG_OCCUPIED));
    private static final int PLANKS = BUILDER.intern(Geometry.SOLID.withIgnition(5, Double.NaN));
    private static final int WOOL = BUILDER.intern(Geometry.SOLID.withIgnition(30, 500));
    private static final GeometryPalette PALETTE = BUILDER.build();
    private static final int AIR = GeometryPalette.AIR_ID;
    private static final int SOLID = GeometryPalette.SOLID_ID;

    /** Enough hydrogen for the room to burn on for a long time, and to be a little under 10 %. */
    private static final double HYDROGEN = 120;

    private static final BlockIgnitionSettings OFF = BlockIgnitionSettings.DEFAULT.withEnabled(false);

    /** A pipeline whose server thread applies fire intents, keeping every position lit. */
    private static final class Burn {
        final Sim sim;
        final List<Long> lit = new ArrayList<>();

        /** The ambient, at {@code kelvin}, is 1 atm of N₂ and O₂, {@code oxygen} of it O₂. */
        Burn(double kelvin, double oxygen) {
            TreeMap<String, Double> air = new TreeMap<>();
            air.put(Ambient.NITROGEN, 101.325 * (1 - oxygen));
            air.put(Ambient.OXYGEN, 101.325 * oxygen);
            sim = new Sim(new TestWorld(new Ambient(air, kelvin)), PALETTE, HotspotTest.REGISTRY).withoutHeat();
            sim.settings = sim.settings.withReactions(HotspotTest.REACTIONS).withHotspots(new HotspotSettings(0));
        }

        Burn(double kelvin) {
            this(kelvin, 0.21);
        }

        Burn with(BlockIgnitionSettings settings) {
            sim.settings = sim.settings.withBlockIgnition(settings);
            return this;
        }

        /** Builds a room with walls of {@code wall} and forms its zone, holding the ambient's gas. */
        Burn room(int wall) {
            sim.world.box(-1, 63, -1, 3, 67, 3, wall);
            sim.world.box(0, 64, 0, 2, 66, 2, AIR);
            return hydrogen(1e-6);
        }

        /** Adds hydrogen to the room, at the ambient's temperature. */
        Burn hydrogen(double moles) {
            double[] m = new double[HotspotTest.REGISTRY.size()];
            m[HotspotTest.H2] = moles;
            double kelvin = sim.world.ambient.temperature();
            sim.pipeline.add(block(1, 65, 1), m, kelvin, LedgerCategory.API, true, "test");
            return this;
        }

        /** Runs ticks with nothing lighting, until the room's zone is there; the next tick is the first. */
        Burn settled() {
            BlockIgnitionSettings settings = sim.settings.blockIgnition();
            with(OFF);
            steps(3);
            with(settings);
            assertNotNull(sim.zoneAt(1, 65, 1), "the room has no zone");
            return this;
        }

        void step() {
            for (FireIntent i : sim.step().fireIntents()) {
                int x = SectionKeys.blockX(i.block());
                int y = SectionKeys.blockY(i.block());
                int z = SectionKeys.blockZ(i.block());
                int here = sim.world.get(x, y, z);
                switch (i.kind()) {
                    case PLACE -> {
                        if (here == AIR) {
                            sim.world.set(x, y, z, GAS_FIRE);
                        }
                    }
                    case REMOVE -> {
                        if (here == GAS_FIRE) {
                            sim.world.set(x, y, z, AIR);
                        }
                    }
                    case LIGHT -> {
                        if (here == AIR || here == GAS_FIRE) {
                            sim.world.set(x, y, z, FIRE);
                            lit.add(i.block());
                        }
                    }
                }
            }
        }

        void steps(int n) {
            for (int i = 0; i < n; i++) {
                step();
            }
        }

        void stepUntilLit(int limit) {
            for (int i = 0; i < limit && lit.isEmpty(); i++) {
                step();
            }
        }

        int cached() {
            return sim.engine.cachedFlammableZones();
        }
    }

    private static long block(int x, int y, int z) {
        return SectionKeys.block(x, y, z);
    }

    // ---- FIRE-033: hot gas -----------------------------------------------------------------

    /** FIRE-033's cost: a cool zone beside flammable blocks never looks for them. */
    @Test
    void fire_033_a_cool_zone_costs_nothing() {
        Burn b = new Burn(293.15).room(PLANKS);
        b.steps(5);
        assertNotNull(b.sim.zoneAt(1, 65, 1), "the room has no zone");
        assertTrue(b.lit.isEmpty(), "a cool room lit");
        assertEquals(0, b.cached(), "a cool zone found its faces");
    }

    /** FIRE-033: a hot room lights fires in the air cells beside its walls, and keeps its faces. */
    @Test
    void fire_033_a_hot_zone_lights_its_faces() {
        Burn b = new Burn(1100).room(PLANKS);
        b.steps(20);
        assertFalse(b.lit.isEmpty(), "a hot wooden room didn't catch fire");
        assertEquals(1, b.cached(), "the zone keeps its faces");
        for (long at : b.lit) {
            int x = SectionKeys.blockX(at);
            int y = SectionKeys.blockY(at);
            int z = SectionKeys.blockZ(at);
            assertTrue(x >= 0 && x <= 2 && y >= 64 && y <= 66 && z >= 0 && z <= 2, "a fire outside the room");
            // The middle of the room is the one cell with no wall beside it.
            assertNotEquals(block(1, 65, 1), at, "a fire with no block beside it");
        }
    }

    /** FIRE-033, FIRE-019: a fire that was lit is a hotspot once the next tick sees its block. */
    @Test
    void fire_033_a_lit_fire_is_a_hotspot() {
        Burn b = new Burn(1100).room(PLANKS);
        b.stepUntilLit(30);
        assertFalse(b.lit.isEmpty());
        long at = b.lit.get(0);
        b.step();
        assertNotEquals(0L, b.sim.pipeline.hotspots().zoneAt(at), "the fire isn't a hotspot");
    }

    /**
     * FIRE-033: a hot zone looks again when the blocks beside it change. A stone room has no faces,
     * until a wool block goes into its wall; then the one cell beside the wool lights, and no other.
     */
    @Test
    void fire_033_the_faces_follow_the_blocks() {
        Burn b = new Burn(1100).room(SOLID);
        b.steps(3);
        assertTrue(b.lit.isEmpty(), "a stone room lit");
        assertEquals(1, b.cached(), "the hot zone looked");
        b.sim.world.set(-1, 65, 1, WOOL);
        b.stepUntilLit(60);
        assertEquals(List.of(block(0, 65, 1)), b.lit, "the fire is in the cell beside the wool");
        b.steps(10);
        assertEquals(1, b.lit.size(), "that cell holds a fire now, and the wool has no other open face");
    }

    /** FIRE-033: only a plain air cell can hold a new fire, so a lever beside the wool holds it off. */
    @Test
    void fire_033_only_air_cells_light() {
        Burn b = new Burn(1100).room(SOLID);
        b.sim.world.set(-1, 65, 1, WOOL);
        b.sim.world.set(0, 65, 1, LEVER);
        b.steps(30);
        assertTrue(b.lit.isEmpty(), "a fire in the lever's cell");
        b.sim.world.set(0, 65, 1, AIR);
        b.stepUntilLit(60);
        assertEquals(List.of(block(0, 65, 1)), b.lit, "the fire where the lever was");
    }

    /** FIRE-033: each block has its own ignition temperature: wool's is below the gas, planks' above. */
    @Test
    void fire_033_a_block_can_have_its_own_ignition_temperature() {
        Burn b = new Burn(540).room(SOLID);
        b.sim.world.set(-1, 65, 1, WOOL);
        b.sim.world.set(3, 65, 1, PLANKS);
        b.steps(200);
        assertEquals(List.of(block(0, 65, 1)), b.lit, "wool catches at 500 K; planks at 573 K");
    }

    /** FIRE-033: the configured ignition temperature applies to a block with none of its own. */
    @Test
    void fire_033_the_configured_temperature_applies() {
        Burn low = new Burn(540).with(new BlockIgnitionSettings(true, 530, 1)).room(PLANKS);
        low.stepUntilLit(120);
        assertFalse(low.lit.isEmpty(), "planks didn't catch at 540 K with an ignition point of 530 K");
        Burn high =
                new Burn(1100).with(new BlockIgnitionSettings(true, 2000, 1)).room(PLANKS);
        high.steps(20);
        assertTrue(high.lit.isEmpty(), "planks caught below an ignition point of 2,000 K");
    }

    /** FIRE-033: open air is ambient, however hot: nothing is a zone, so nothing lights. */
    @Test
    void fire_033_open_air_lights_nothing() {
        Burn b = new Burn(1100);
        b.sim.world.box(-1, 63, -1, 3, 63, 3, PLANKS);
        b.sim.world.set(1, 64, 1, WOOL);
        b.steps(20);
        assertTrue(b.lit.isEmpty(), "open air lit a block");
        assertEquals(0, b.cached(), "open air found faces");
    }

    /** FIRE-033, FIRE-030: below the fire oxygen threshold, a hot room lights nothing. */
    @Test
    void fire_033_needs_the_fire_oxygen_threshold() {
        Burn b = new Burn(1100, 0.13).room(PLANKS);
        b.steps(20);
        assertTrue(b.lit.isEmpty(), "a fire started at 13 % O2");
        assertEquals(0, b.cached(), "a zone that can't burn found its faces");
    }

    /** FIRE-033: while the game rule is off, a hot room lights nothing and looks for nothing. */
    @Test
    void fire_033_off_lights_nothing() {
        Burn b = new Burn(1100).with(OFF).room(PLANKS);
        b.steps(20);
        assertTrue(b.lit.isEmpty(), "a room lit while off");
        assertEquals(0, b.cached(), "a zone looked while off");
    }

    /** FIRE-033: a zone below every ignition temperature drops the faces it found while it was hot. */
    @Test
    void fire_033_a_zone_that_cools_drops_its_faces() {
        Burn b = new Burn(450).with(new BlockIgnitionSettings(true, 400, 1)).room(PLANKS);
        b.steps(3);
        assertEquals(1, b.cached(), "the zone is above its ignition point");
        b.with(new BlockIgnitionSettings(true, 2000, 1));
        b.step();
        assertEquals(0, b.cached(), "the zone is below every ignition point: 2,000 K, and wool's 500 K");
    }

    /**
     * FIRE-033, §3.5: draws are seeded per dimension: the same seed lights the same cells, and
     * another seed others.
     */
    @Test
    void fire_033_draws_are_seeded() {
        assertEquals(lit(1), lit(1), "one seed, two runs");
        assertNotEquals(lit(1), lit(2), "two seeds");
    }

    private static TreeSet<Long> lit(long seed) {
        Burn b = new Burn(1100);
        b.sim.engine.setSeed(seed);
        b.room(PLANKS);
        b.steps(4);
        return new TreeSet<>(b.lit);
    }

    /**
     * FIRE-033: over many seeds, a face lights at its chance per second. A wool room at 1,000 K, 500 K
     * above wool's ignition point, has all of the heat share, so 0.3 a face.
     */
    @Test
    void fire_033_rate_matches_the_chance() {
        int trials = 200;
        int lit = 0;
        for (int t = 0; t < trials; t++) {
            Burn b = new Burn(1000);
            b.sim.engine.setSeed(t);
            b.room(WOOL).settled();
            b.step();
            lit += b.lit.size();
        }
        // 6 cells have one wall, 12 two and 8 three; a cell lights if any of its faces does.
        double expected = 6 * 0.3 + 12 * (1 - 0.7 * 0.7) + 8 * (1 - 0.7 * 0.7 * 0.7);
        // The variance of one trial is about 6.1, so σ of the mean over 200 is about 0.17.
        assertEquals(expected, (double) lit / trials, 0.7, "cells lit per tick");
    }

    // ---- FIRE-034: gas fires ---------------------------------------------------------------

    /**
     * FIRE-034: a gas fire beside wool lights a vanilla fire in its own cell; a gas fire beside
     * stone goes on burning as it was.
     */
    @Test
    void fire_034_a_gas_fire_lights_wool_not_stone() {
        Burn b = new Burn(293.15).room(SOLID).hydrogen(HYDROGEN).settled();
        b.sim.world.set(-1, 65, 1, WOOL);
        b.sim.world.set(0, 65, 1, GAS_FIRE);
        b.sim.world.set(2, 65, 1, GAS_FIRE);
        b.stepUntilLit(40);
        assertEquals(List.of(block(0, 65, 1)), b.lit, "the gas fire beside the wool");
        assertEquals(FIRE, b.sim.world.get(0, 65, 1));
        assertEquals(GAS_FIRE, b.sim.world.get(2, 65, 1), "the gas fire beside stone");
    }

    /** FIRE-034: a vanilla fire has vanilla's own spread, so the engine asks for nothing beside it. */
    @Test
    void fire_034_only_gas_fires_light() {
        Burn b = new Burn(293.15).room(SOLID).hydrogen(HYDROGEN).settled();
        b.sim.world.set(-1, 65, 1, WOOL);
        b.sim.world.set(0, 65, 1, FIRE);
        b.steps(20);
        assertTrue(b.lit.isEmpty(), "a vanilla fire asked for a fire");
    }

    /** FIRE-034: while the game rule is off, a gas fire lights nothing. */
    @Test
    void fire_034_off_lights_nothing() {
        Burn b = new Burn(293.15).room(SOLID).hydrogen(HYDROGEN).settled().with(OFF);
        b.sim.world.set(-1, 65, 1, WOOL);
        b.sim.world.set(0, 65, 1, GAS_FIRE);
        b.steps(20);
        assertTrue(b.lit.isEmpty(), "a gas fire lit a block while off");
        assertEquals(GAS_FIRE, b.sim.world.get(0, 65, 1));
    }

    /** FIRE-034: a configured chance of 0 lights nothing. */
    @Test
    void fire_034_a_chance_of_zero_lights_nothing() {
        Burn b = new Burn(293.15)
                .with(new BlockIgnitionSettings(true, 573, 0))
                .room(SOLID)
                .hydrogen(HYDROGEN)
                .settled();
        b.sim.world.set(-1, 65, 1, WOOL);
        b.sim.world.set(0, 65, 1, GAS_FIRE);
        b.steps(20);
        assertTrue(b.lit.isEmpty());
    }

    /** FIRE-034: over many seeds, a gas fire lights wool at the configured chance scaled by its odds. */
    @Test
    void fire_034_rate_matches_the_chance() {
        int trials = 400;
        int lit = 0;
        for (int t = 0; t < trials; t++) {
            Burn b = new Burn(293.15);
            b.sim.engine.setSeed(t);
            b.with(new BlockIgnitionSettings(true, 573, 0.5))
                    .room(SOLID)
                    .hydrogen(HYDROGEN)
                    .settled();
            b.sim.world.set(-1, 65, 1, WOOL);
            b.sim.world.set(0, 65, 1, GAS_FIRE);
            b.step();
            lit += b.lit.size();
        }
        // 0.5 × 30 / 100 = 0.15: σ of the rate over 400 trials is about 0.018.
        assertEquals(0.15, (double) lit / trials, 0.06, "the rate");
    }
}
