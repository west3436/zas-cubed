package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.heat.HeatMath;
import io.github.west3436.zas.engine.heat.HeatSettings;
import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Conduction through zone boundaries (HEAT-001, HEAT-011 to HEAT-013, HEAT-015, HEAT-008,
 * HEAT-006), radiation to a vacuum (HEAT-004) and rejection with heat (architecture §4.8).
 *
 * <p>Each test checks the exact step: a zone coupled to reservoirs of total conductance G at T_eq
 * moves as T' − T_eq = (T − T_eq)·e^(−G·dt/C), and two zones coupled by G close their difference
 * as ΔT' = ΔT·e^(−G·(1/C₁ + 1/C₂)·dt). Rooms are single cells in the middle of a section, so every
 * wall is a straight line of blocks.
 */
class HeatTest {
    private static final double CV_N2 = 20.8;
    private static final double CV_O2 = 21.0;

    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    /** A solid block that barely conducts, so a test's other paths don't matter. */
    private static final int INSULATOR = BUILDER.intern(Geometry.SOLID.withThermal(1e-9, Double.NaN));

    private static final int WOOD = BUILDER.intern(Geometry.SOLID.withThermal(0.15, Double.NaN));
    private static final int LAVA = BUILDER.intern(Geometry.SOLID.withThermal(Double.NaN, 1450));
    /** A glass pane running north to south: sub-cell 0 is its west half, 1 its east half. */
    private static final int PANE = BUILDER.intern(pane().withThermal(5.8, Double.NaN));

    private static final GeometryPalette PALETTE = BUILDER.build();
    private static final int AIR = GeometryPalette.AIR_ID;
    private static final int SOLID = GeometryPalette.SOLID_ID;
    private static final double STONE = HeatSettings.DEFAULT.fallbackTransmittance();

    /** The room cell most tests use, in the middle of a section. */
    private static final int X = 8;

    private static final int Y = 72;
    private static final int Z = 8;

    private static Geometry pane() {
        BitSet s = new BitSet(GeometryDeriver.VOXELS);
        GeometryDeriver.fill(s, 7, 0, 0, 9, 16, 16);
        return GeometryDeriver.derive(s);
    }

    private static Sim sim(Ambient ambient, int background) {
        return new Sim(new TestWorld(ambient, background), PALETTE);
    }

    /** Adds nitrogen at a temperature. */
    private static void add(Sim sim, int x, int y, int z, double n2, double temperature) {
        sim.addSeqs.add(sim.pipeline.add(
                SectionKeys.block(x, y, z), Sim.moles(n2, 0), temperature, LedgerCategory.API, true, "test"));
    }

    private static double heatCapacity(ZoneView z) {
        return z.moles(N2) * CV_N2 + z.moles(O2) * CV_O2;
    }

    private static double dt(Sim sim) {
        return sim.settings.exchange().periodSeconds();
    }

    /**
     * Steps once and checks the zone at the point moved toward {@code teq} at conductance {@code g}.
     */
    private static void assertStep(Sim sim, int x, int y, int z, double g, double teq, String what) {
        ZoneView before = sim.zoneAt(x, y, z);
        assertNotNull(before, what + ": no zone");
        sim.step();
        ZoneView after = sim.zoneAt(x, y, z);
        double expected = teq + (before.temperature() - teq) * Math.exp(-g * dt(sim) / heatCapacity(before));
        assertEquals(expected, after.temperature(), 1e-7, what);
        assertTrue(Math.abs(after.temperature() - before.temperature()) > 1e-3, what + ": nothing moved");
    }

    /** HEAT-001, HEAT-012: a room conducts through one stone layer to the open air on every side. */
    @Test
    void heat_012_single_wall() {
        Sim sim = sim(Ambient.STANDARD_AIR, AIR);
        sim.world.room(X, Y, Z, X, Y, Z);
        add(sim, X, Y, Z, 30, 350);
        sim.step();
        // Six faces of 1 m², each through one block at U 1.7.
        assertStep(sim, X, Y, Z, 6 * STONE, Ambient.STANDARD_AIR.temperature(), "single wall");
    }

    /** HEAT-012: layers in series, 1/U = Σ 1/Uᵢ: stone inside, wood outside. */
    @Test
    void heat_012_layered_wall() {
        Sim sim = sim(Ambient.STANDARD_AIR, AIR);
        sim.world.box(X - 2, Y - 2, Z - 2, X + 2, Y + 2, Z + 2, WOOD);
        sim.world.room(X, Y, Z, X, Y, Z);
        add(sim, X, Y, Z, 30, 350);
        sim.step();
        double u = 1 / (1 / STONE + 1 / 0.15);
        assertEquals(u, HeatMath.series(STONE, 0.15), 1e-15);
        assertStep(sim, X, Y, Z, 6 * u, Ambient.STANDARD_AIR.temperature(), "stone and wood");
    }

    /** HEAT-015: a block with no U uses the configured fallback; a block with one ignores it. */
    @Test
    void heat_015_fallback_transmittance() {
        Sim sim = sim(Ambient.STANDARD_AIR, AIR);
        sim.settings = sim.settings.withHeat(HeatSettings.DEFAULT.withFallbackTransmittance(3.0));
        sim.world.room(X, Y, Z, X, Y, Z);
        sim.world.set(X + 1, Y, Z, WOOD); // one wall with its own U
        add(sim, X, Y, Z, 30, 350);
        sim.step();
        assertStep(sim, X, Y, Z, 5 * 3.0 + 0.15, Ambient.STANDARD_AIR.temperature(), "fallback 3.0");
    }

    /**
     * HEAT-011, far side a cell: two rooms either side of one stone block exchange heat through it,
     * conserving their energy.
     */
    @Test
    void heat_011_far_side_is_a_cell() {
        Sim sim = sim(Ambient.STANDARD_AIR, INSULATOR);
        sim.world.set(X - 1, Y, Z, AIR).set(X, Y, Z, SOLID).set(X + 1, Y, Z, AIR);
        add(sim, X - 1, Y, Z, 30, 400);
        add(sim, X + 1, Y, Z, 10, 250);
        sim.step();
        ZoneView a = sim.zoneAt(X - 1, Y, Z);
        ZoneView b = sim.zoneAt(X + 1, Y, Z);
        double ca = heatCapacity(a);
        double cb = heatCapacity(b);
        sim.step();
        ZoneView a2 = sim.zoneAt(X - 1, Y, Z);
        ZoneView b2 = sim.zoneAt(X + 1, Y, Z);
        double factor = Math.exp(-STONE * (1 / ca + 1 / cb) * dt(sim));
        assertEquals((a.temperature() - b.temperature()) * factor, a2.temperature() - b2.temperature(), 1e-6, "rate");
        assertEquals(
                ca * a.temperature() + cb * b.temperature(),
                ca * a2.temperature() + cb * b2.temperature(),
                1e-6,
                "energy");
    }

    /** HEAT-011, far side a heat source: a room beside a source block moves toward it at its U. */
    @Test
    void heat_011_far_side_is_a_heat_source() {
        Sim sim = sim(Ambient.STANDARD_AIR, INSULATOR);
        sim.world.set(X, Y, Z, AIR).set(X + 1, Y, Z, LAVA);
        add(sim, X, Y, Z, 30, 293.15);
        sim.step();
        // Directly against the source: its own layer only, so U = the fallback.
        assertStep(sim, X, Y, Z, STONE, 1450, "source");
        // Through one stone block: two layers in series.
        Sim walled = sim(Ambient.STANDARD_AIR, INSULATOR);
        walled.world.set(X, Y, Z, AIR).set(X + 1, Y, Z, SOLID).set(X + 2, Y, Z, LAVA);
        add(walled, X, Y, Z, 30, 293.15);
        walled.step();
        assertStep(walled, X, Y, Z, STONE / 2, 1450, "source behind a wall");
    }

    /**
     * HEAT-011, nothing within the wall thickness: each face conducts through the three layers to
     * the ground, at the ambient temperature.
     */
    @Test
    void heat_011_nothing_within_three_blocks_is_the_ground() {
        Ambient cold = Ambient.vacuum(250);
        Sim sim = sim(cold, SOLID);
        sim.world.set(X, Y, Z, AIR);
        add(sim, X, Y, Z, 30, 350);
        sim.step();
        assertStep(sim, X, Y, Z, 6 * STONE / 3, 250, "ground");
        // A thicker search finds nothing either, through more layers.
        Sim deep = sim(cold, SOLID);
        deep.settings = deep.settings.withHeat(HeatSettings.DEFAULT.withMaxThickness(5));
        deep.world.set(X, Y, Z, AIR);
        add(deep, X, Y, Z, 30, 350);
        deep.step();
        assertStep(deep, X, Y, Z, 6 * STONE / 5, 250, "ground at 5");
    }

    /** HEAT-013: the two halves of a pane, each in its own room, exchange heat at the pane's U. */
    @Test
    void heat_013_pane_partition() {
        Sim sim = sim(Ambient.STANDARD_AIR, INSULATOR);
        sim.world.set(X - 1, Y, Z, AIR).set(X, Y, Z, PANE).set(X + 1, Y, Z, AIR);
        add(sim, X - 1, Y, Z, 30, 400);
        add(sim, X + 1, Y, Z, 30, 250);
        sim.step();
        ZoneView a = sim.zoneAt(X - 1, Y, Z);
        ZoneView b = sim.zoneAt(X + 1, Y, Z);
        assertTrue(a.id() != b.id(), "the pane didn't keep the rooms apart");
        double ca = heatCapacity(a);
        double cb = heatCapacity(b);
        sim.step();
        ZoneView a2 = sim.zoneAt(X - 1, Y, Z);
        ZoneView b2 = sim.zoneAt(X + 1, Y, Z);
        // The pane's two cells face each other across its whole 1 m² cross-section.
        double factor = Math.exp(-5.8 * (1 / ca + 1 / cb) * dt(sim));
        assertEquals((a.temperature() - b.temperature()) * factor, a2.temperature() - b2.temperature(), 1e-6, "rate");
    }

    /** HEAT-008: a zone at 0.5 % of standard density beside lava keeps its temperature. */
    @Test
    void heat_008_near_vacuum_does_not_conduct() {
        Sim sim = sim(Ambient.VACUUM, INSULATOR);
        sim.world.set(X, Y, Z, AIR).set(X + 1, Y, Z, LAVA);
        double moles = 0.005 * HeatSettings.STANDARD_AIR_DENSITY * Geometry.FULL_VOLUME;
        add(sim, X, Y, Z, moles, 293.15);
        sim.steps(5);
        assertEquals(293.15, sim.zoneAt(X, Y, Z).temperature(), 1e-12, "a near-vacuum zone conducted");
        // At 2 % it does.
        add(sim, X, Y, Z, 3 * moles, 293.15);
        sim.steps(2);
        assertTrue(sim.zoneAt(X, Y, Z).temperature() > 293.2, "a 2 % zone didn't conduct");
    }

    /**
     * HEAT-004: a room with stone walls in a vacuum radiates from the walls' outer surfaces, each
     * face carrying U·(T − T_s) with T_s where that equals εσ(T_s⁴ − T_bg⁴).
     */
    @Test
    void heat_004_radiates_to_vacuum() {
        Sim sim = sim(Ambient.VACUUM, AIR);
        sim.world.room(X, Y, Z, X, Y, Z);
        add(sim, X, Y, Z, 30, 293.15);
        sim.step();
        ZoneView before = sim.zoneAt(X, Y, Z);
        double t = before.temperature();
        double bg = Ambient.COSMIC_BACKGROUND;
        double flux = HeatMath.radiativeFlux(STONE, t, bg, HeatSettings.DEFAULT.emissivity());
        assertTrue(flux > 0 && flux < STONE * (t - bg), "the flux isn't limited by radiation");
        // Each face's flux, as a conductance to the background over this tick.
        double g = 6 * flux / (t - bg);
        assertStep(sim, X, Y, Z, g, bg, "radiation");
    }

    /**
     * HEAT-006: whatever the period, heat capacities and transmittances, two zones never cross
     * each other's temperature, and a zone beside a source never passes it.
     */
    @Test
    void heat_006_never_overshoots_in_the_engine() {
        Random random = new Random(6);
        for (int run = 0; run < 12; run++) {
            int u = BUILDER.intern(Geometry.SOLID.withThermal(Math.pow(10, -2 + 7 * random.nextDouble()), Double.NaN));
            Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR, INSULATOR), BUILDER.build());
            sim.settings = sim.settings.withPeriodTicks(2 + random.nextInt(199));
            // Two rooms through the wall; a third, apart, through the wall from lava.
            sim.world.set(X - 1, Y, Z, AIR).set(X, Y, Z, u).set(X + 1, Y, Z, AIR);
            sim.world.set(X, Y + 3, Z, AIR).set(X + 1, Y + 3, Z, u).set(X + 2, Y + 3, Z, LAVA);
            add(sim, X - 1, Y, Z, 0.5 + random.nextDouble() * 100, 200 + random.nextDouble() * 200);
            add(sim, X + 1, Y, Z, 0.5 + random.nextDouble() * 100, 200 + random.nextDouble() * 200);
            add(sim, X, Y + 3, Z, 0.5 + random.nextDouble() * 100, 200 + random.nextDouble() * 200);
            sim.step();
            for (int k = 0; k < 6; k++) {
                ZoneView a = sim.zoneAt(X - 1, Y, Z);
                ZoneView b = sim.zoneAt(X + 1, Y, Z);
                ZoneView c = sim.zoneAt(X, Y + 3, Z);
                sim.step();
                ZoneView a2 = sim.zoneAt(X - 1, Y, Z);
                ZoneView b2 = sim.zoneAt(X + 1, Y, Z);
                ZoneView c2 = sim.zoneAt(X, Y + 3, Z);
                assertTrue(
                        Math.signum(b.temperature() - a.temperature()) * (b2.temperature() - a2.temperature()) >= -1e-9,
                        "run " + run + ": the rooms crossed");
                assertTrue(c2.temperature() >= c.temperature() - 1e-9, "run " + run + ": cooled beside lava");
                assertTrue(c2.temperature() <= 1450 + 1e-9, "run " + run + ": past the lava");
            }
        }
    }

    /**
     * Zones that exchanged heat are one component (architecture §4.8): when a chunk under one
     * unloads mid-tick, both are restored, so no energy is made or lost.
     */
    @Test
    void rejection_restores_both_sides_of_a_heat_link() {
        Sim sim = sim(Ambient.STANDARD_AIR, INSULATOR);
        // Room a in chunk (0, 0), room b in chunk (1, 0), one stone block between them.
        sim.world.set(14, Y, Z, AIR).set(15, Y, Z, SOLID).set(16, Y, Z, AIR);
        add(sim, 14, Y, Z, 30, 400);
        add(sim, 16, Y, Z, 30, 250);
        sim.step();
        ZoneView a = sim.zoneAt(14, Y, Z);
        ZoneView b = sim.zoneAt(16, Y, Z);
        sim.betweenCloseAndApply = s -> {
            s.world.unload(SectionKeys.chunk(0, 0));
            s.pipeline.chunkUnloaded(SectionKeys.chunk(0, 0));
            s.world.load(SectionKeys.chunk(0, 0));
            s.pipeline.chunkLoaded(SectionKeys.chunk(0, 0));
        };
        DimensionPipeline.Applied rejected = sim.step();
        sim.betweenCloseAndApply = s -> {};
        assertTrue(rejected.rejected() >= 1, "nothing was rejected");
        assertEquals(a, sim.zoneAt(14, Y, Z), "room a changed");
        assertEquals(b, sim.zoneAt(16, Y, Z), "room b kept heat its partner gave back");
        sim.step();
        assertTrue(sim.zoneAt(14, Y, Z).temperature() < a.temperature(), "heat didn't flow after the redo");
    }

    /** The same world gives the same temperatures, bit for bit (CORE-044). */
    @Test
    void heat_runs_are_identical() {
        double[] first = run();
        double[] second = run();
        assertEquals(first[0], second[0], 0.0);
        assertEquals(first[1], second[1], 0.0);
    }

    private static double[] run() {
        Sim sim = sim(Ambient.VACUUM, AIR);
        sim.world.room(X - 3, Y, Z, X + 3, Y + 1, Z + 1);
        sim.world
                .set(X, Y, Z, SOLID)
                .set(X, Y + 1, Z, SOLID)
                .set(X, Y, Z + 1, PANE)
                .set(X, Y + 1, Z + 1, LAVA);
        add(sim, X - 2, Y, Z, 40, 300);
        add(sim, X + 2, Y, Z, 20, 260);
        sim.steps(20);
        return new double[] {
            sim.zoneAt(X - 2, Y, Z).temperature(), sim.zoneAt(X + 2, Y, Z).temperature()
        };
    }
}
