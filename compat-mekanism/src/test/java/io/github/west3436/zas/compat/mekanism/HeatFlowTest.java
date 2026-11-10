package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The arithmetic of heat between a Mekanism capacitor and a zone's gas (MEK-040). */
class HeatFlowTest {
    /** A small exchange is the conductance times the difference times the time. */
    @Test
    void mek_040_a_short_exchange_is_conductance_times_difference_times_time() {
        // 10 W/K across 100 K for 1 s between two large bodies: 1,000 J, less a hair for the drift.
        double q = HeatFlow.exchange(1e9, 400, 1e9, 300, 10, 1);
        assertEquals(1000, q, 1e-3);
        assertEquals(-1000, HeatFlow.exchange(1e9, 300, 1e9, 400, 10, 1), 1e-3, "the other way");
    }

    /** However long or strong, an exchange brings the two to one temperature and no further. */
    @Test
    void mek_040_an_exchange_never_passes_equilibrium() {
        // 100 J/K at 1,000 K and 900 J/K at 300 K meet at 370 K: 63,000 J.
        assertEquals(63_000, HeatFlow.exchange(100, 1000, 900, 300, 1e6, 1e6), 1e-6);
        Random random = new Random(40);
        for (int i = 0; i < 100_000; i++) {
            double cb = Math.pow(10, random.nextDouble() * 12 - 3);
            double cg = Math.pow(10, random.nextDouble() * 12 - 3);
            double tb = random.nextDouble() * 5000;
            double tg = random.nextDouble() * 5000;
            double g = Math.pow(10, random.nextDouble() * 10 - 4);
            double s = Math.pow(10, random.nextDouble() * 8 - 2);
            double q = HeatFlow.exchange(cb, tb, cg, tg, g, s);
            double bodyAfter = tb - q / cb;
            double gasAfter = tg + q / cg;
            double slack = 1e-9 * (1 + Math.abs(tb) + Math.abs(tg));
            if (tb >= tg) {
                assertTrue(q >= 0, "heat ran uphill");
                assertTrue(bodyAfter >= gasAfter - slack, "passed: " + bodyAfter + " < " + gasAfter);
            } else {
                assertTrue(q <= 0, "heat ran uphill");
                assertTrue(bodyAfter <= gasAfter + slack, "passed: " + bodyAfter + " > " + gasAfter);
            }
        }
    }

    /** Two halves of a time move what the whole time moves: the rate is per second, whatever the step. */
    @Test
    void mek_040_the_step_length_changes_nothing() {
        double cb = 100;
        double cg = 23_000;
        double whole = HeatFlow.exchange(cb, 800, cg, 300, 46.5, 2);
        double first = HeatFlow.exchange(cb, 800, cg, 300, 46.5, 1);
        double second = HeatFlow.exchange(cb, 800 - first / cb, cg, 300 + first / cg, 46.5, 1);
        assertEquals(whole, first + second, 1e-9 * whole);
    }

    /** Nothing flows without a capacity, a conductance, a time or a difference, or from values that aren't numbers. */
    @Test
    void mek_040_nothing_flows_from_nothing() {
        assertEquals(0, HeatFlow.exchange(0, 400, 100, 300, 10, 1));
        assertEquals(0, HeatFlow.exchange(100, 400, 0, 300, 10, 1));
        assertEquals(0, HeatFlow.exchange(100, 400, 100, 300, 0, 1));
        assertEquals(0, HeatFlow.exchange(100, 400, 100, 300, 10, 0));
        assertEquals(0, HeatFlow.exchange(100, 300, 100, 300, 10, 1));
        assertEquals(0, HeatFlow.exchange(100, Double.NaN, 100, 300, 10, 1));
        assertEquals(0, HeatFlow.exchange(Double.POSITIVE_INFINITY, 400, 100, 300, 10, 1));
        assertEquals(0, HeatFlow.exchange(100, 400, 100, 300, -1, 1));
    }

    /**
     * A face conducts in series with the block's own resistance as Mekanism counts it: a capacitor
     * of C whose inverse coefficients add to R passes 20 × C / R W/K.
     */
    @Test
    void mek_040_a_face_conducts_in_series_with_the_blocks_own_resistance() {
        // A Resistive Heater: C 100, inverse conduction 5 and insulation 10. 20 × 100 / 15 = 133.3 W/K,
        // in series with 10 W/K: 9.302 W/K.
        assertEquals(10 * (2000.0 / 15) / (10 + 2000.0 / 15), HeatFlow.conductance(10, 100, 15), 1e-12);
        assertEquals(9.302, HeatFlow.conductance(10, 100, 15), 1e-3);
        // No resistance of its own: the face's.
        assertEquals(10, HeatFlow.conductance(10, 100, 0));
        // An Ultimate Thermodynamic Conductor's insulation leaves almost nothing.
        assertTrue(HeatFlow.conductance(10, 1, 100_005) < 0.001);
        // Never more than the face's, and nothing through no face or no capacitor.
        assertTrue(HeatFlow.conductance(10, 1e12, 1) <= 10);
        assertEquals(0, HeatFlow.conductance(0, 100, 15));
        assertEquals(0, HeatFlow.conductance(10, 0, 15));
        assertEquals(0, HeatFlow.conductance(Double.NaN, 100, 15));
    }

    /** Gas below the near-vacuum density, or none at all, carries no heat (HEAT-008). */
    @Test
    void mek_040_thin_gas_takes_no_part() {
        double nearVacuum = 0.000416; // 1 % of standard air's density, mol/L
        assertTrue(HeatFlow.thin(0, 27_000, nearVacuum));
        assertTrue(HeatFlow.thin(11, 27_000, nearVacuum));
        assertFalse(HeatFlow.thin(12, 27_000, nearVacuum));
        assertTrue(HeatFlow.thin(12, 0, nearVacuum));
        assertFalse(HeatFlow.thin(1, 27_000, 0), "with no line, any gas carries heat");
    }

    /** The defaults: 1 J to a unit of Mekanism's heat, and 10 W/K a face. */
    @Test
    void mek_040_the_defaults() {
        assertEquals(1.0, HeatFlow.DEFAULT_JOULES_PER_HEAT_UNIT);
        assertEquals(10.0, HeatFlow.DEFAULT_FACE_CONDUCTANCE);
        assertTrue(HeatFlow.MIN_JOULES_PER_HEAT_UNIT > 0);
    }
}
