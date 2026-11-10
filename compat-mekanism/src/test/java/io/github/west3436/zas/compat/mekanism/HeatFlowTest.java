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

    /**
     * A body passes no more than its ceiling, either way. The figures are a Fusion Reactor's: a case
     * of heat capacity 1 behind an insulation of 100,000 and a conduction of 10, at the 9.8 × 10⁹ K
     * Mekanism gives an air-cooled reactor at its highest injection rate, with two port faces on a 7
     * by 7 by 7 room of standard air.
     */
    @Test
    void mek_040_a_body_passes_no_more_than_its_ceiling() {
        double port = HeatFlow.conductance(10, 1, 100_010);
        assertEquals(0.0002, port, 1e-7);
        double room = 216_800;
        double none = Double.POSITIVE_INFINITY;
        // With no ceiling, 1.96 MW a face.
        assertEquals(2 * 1.96e6, HeatFlow.exchange(1, 9.8e9, room, 293.15, 2 * port, 1, none), 2e3);
        // With one of 45 kW, 45 kJ a second for as long as the case is that hot, however many faces.
        assertEquals(45_000, HeatFlow.exchange(1, 9.8e9, room, 293.15, 2 * port, 1, 45_000), 1e-6);
        assertEquals(45_000, HeatFlow.exchange(1, 9.8e9, room, 293.15, 6 * port, 1, 45_000), 1e-6);
        assertEquals(60 * 45_000, HeatFlow.exchange(1, 9.8e9, room, 293.15, 2 * port, 60, 45_000), 1e-3);
        // The other way: a body far colder than its gas gains no faster.
        assertEquals(-45_000, HeatFlow.exchange(1e6, 3, 1e9, 9.8e9, 10, 1, 45_000), 1e-6);
        // A large hot boiler: 1,620 faces of 6.35 W/K, 500 K above its room, would pass 5 MW at first.
        assertEquals(4.85e6, HeatFlow.exchange(86_800, 800, 1e9, 300, 1620 * 6.35, 1, none), 1e4);
        assertEquals(45_000, HeatFlow.exchange(86_800, 800, 1e9, 300, 1620 * 6.35, 1, 45_000), 1e-6);
        // A ceiling of nothing passes nothing.
        assertEquals(0, HeatFlow.exchange(1, 9.8e9, room, 293.15, 2 * port, 1, 0));
        assertEquals(0, HeatFlow.exchange(1, 9.8e9, room, 293.15, 2 * port, 1, Double.NaN));
    }

    /** A flow that the conductance keeps under the ceiling is what it is with no ceiling, to the last bit. */
    @Test
    void mek_040_under_the_ceiling_nothing_changes() {
        // Four Fission Reactor ports at 1,200 K: 34.5 kJ in a second. A Resistive Heater's five faces: 1.2 kJ.
        double fission = HeatFlow.exchange(98_000, 1200, 216_800, 293.15, 4 * 9.514, 1);
        assertEquals(34_500, fission, 100);
        assertEquals(fission, HeatFlow.exchange(98_000, 1200, 216_800, 293.15, 4 * 9.514, 1, 45_000));
        double heater = HeatFlow.exchange(100, 318.3, 86_730, 293.15, 46.51, 1);
        assertEquals(heater, HeatFlow.exchange(100, 318.3, 86_730, 293.15, 46.51, 1, 45_000));
        // Exactly at the ceiling counts as under it.
        assertEquals(
                HeatFlow.exchange(1e9, 1300, 1e9, 300, 10, 1), HeatFlow.exchange(1e9, 1300, 1e9, 300, 10, 1, 10_000));
    }

    /**
     * A flow that starts over the ceiling and falls under it within the time is the two parts put
     * together: at the ceiling until the conductance carries less, and from there as with no ceiling.
     * It agrees with the same course followed in a million small steps, and cutting the time in two
     * changes nothing.
     */
    @Test
    void mek_040_the_ceiling_then_the_conductance() {
        double cb = 100;
        double cg = 23_000;
        double g = 46.5;
        double most = 5_000;
        // 46.5 W/K across 1,700 K would carry 79 kW. At 5 kW the difference closes by 50.2 K a second,
        // and is down to the 107.5 K at which the conductance carries 5 kW after 31.7 s.
        double whole = HeatFlow.exchange(cb, 2000, cg, 300, g, 40, most);
        double tb = 2000;
        double tg = 300;
        double fine = 0;
        int steps = 1_000_000;
        double dt = 40.0 / steps;
        for (int i = 0; i < steps; i++) {
            double q = Math.min(g * (tb - tg), most) * dt;
            tb -= q / cb;
            tg += q / cg;
            fine += q;
        }
        assertEquals(fine, whole, 1e-4 * fine);
        assertTrue(whole < most * 40, "more than the ceiling allows in the time");
        assertTrue(whole < HeatFlow.exchange(cb, 2000, cg, 300, g, 40), "the ceiling held nothing back");

        for (double cut : new double[] {5, 31.7, 35}) {
            double first = HeatFlow.exchange(cb, 2000, cg, 300, g, cut, most);
            double second = HeatFlow.exchange(cb, 2000 - first / cb, cg, 300 + first / cg, g, 40 - cut, most);
            assertEquals(whole, first + second, 1e-9 * whole, "cut at " + cut + " s");
        }
    }

    /**
     * With a ceiling, an exchange still ends at one temperature and no further, never moves more
     * than the ceiling allows in the time, and never more than it moves with no ceiling.
     */
    @Test
    void mek_040_a_ceiling_never_passes_equilibrium_either() {
        // 100 J/K at 1,000 K and 900 J/K at 300 K meet at 370 K: 63,000 J, which 10 W takes 6,300 s over.
        assertEquals(63_000, HeatFlow.exchange(100, 1000, 900, 300, 1e6, 1e6, 10), 1e-6);
        assertEquals(10 * 6_000.0, HeatFlow.exchange(100, 1000, 900, 300, 1e6, 6_000, 10), 1e-6);
        Random random = new Random(41);
        for (int i = 0; i < 100_000; i++) {
            double cb = Math.pow(10, random.nextDouble() * 12 - 3);
            double cg = Math.pow(10, random.nextDouble() * 12 - 3);
            double tb = random.nextDouble() * 5000;
            double tg = random.nextDouble() * 5000;
            double g = Math.pow(10, random.nextDouble() * 10 - 4);
            double s = Math.pow(10, random.nextDouble() * 8 - 2);
            double most = Math.pow(10, random.nextDouble() * 10 - 3);
            double q = HeatFlow.exchange(cb, tb, cg, tg, g, s, most);
            double free = HeatFlow.exchange(cb, tb, cg, tg, g, s);
            double bodyAfter = tb - q / cb;
            double gasAfter = tg + q / cg;
            double slack = 1e-9 * (1 + Math.abs(tb) + Math.abs(tg));
            assertTrue(Math.abs(q) <= most * s * (1 + 1e-12), "over the ceiling: " + q + " J in " + s + " s");
            assertTrue(Math.abs(q) <= Math.abs(free) * (1 + 1e-9), "more than with no ceiling");
            if (tb >= tg) {
                assertTrue(q >= 0, "heat ran uphill");
                assertTrue(bodyAfter >= gasAfter - slack, "passed: " + bodyAfter + " < " + gasAfter);
            } else {
                assertTrue(q <= 0, "heat ran uphill");
                assertTrue(bodyAfter <= gasAfter + slack, "passed: " + bodyAfter + " > " + gasAfter);
            }
        }
    }

    /**
     * A body's ceiling is shared out among the zones it touches in proportion to what each would be
     * passed, whichever way, so that all of them together stay under it.
     */
    @Test
    void mek_040_a_ceiling_is_shared_out_among_a_bodys_zones() {
        // One zone has the whole of it.
        assertEquals(45_000, HeatFlow.share(45_000, 2e6, 2e6));
        // Two zones that would be passed 3 MW and 1 MW have three quarters and one quarter.
        assertEquals(33_750, HeatFlow.share(45_000, 3e6, 4e6), 1e-9);
        assertEquals(11_250, HeatFlow.share(45_000, 1e6, 4e6), 1e-9);
        // Heat out to one zone and in from another both count.
        assertEquals(33_750, HeatFlow.share(45_000, -3e6, 4e6), 1e-9);
        // Under the ceiling in all, each zone's share is above what it would be passed.
        assertTrue(HeatFlow.share(45_000, 3_000, 4_000) > 3_000);
        // A zone that would be passed nothing has none of it, and passes nothing.
        assertEquals(0, HeatFlow.share(45_000, 0, 4e6));
        assertEquals(0, HeatFlow.share(45_000, 0, 0));
        // Never more than the whole, whatever the sum is said to be.
        assertEquals(45_000, HeatFlow.share(45_000, 3e6, 1e6));
        // No ceiling: none for any zone.
        assertEquals(Double.POSITIVE_INFINITY, HeatFlow.share(0, 3e6, 4e6));
        assertEquals(Double.POSITIVE_INFINITY, HeatFlow.share(-1, 3e6, 4e6));
        assertEquals(Double.POSITIVE_INFINITY, HeatFlow.share(0, 0, 0));
    }

    /**
     * The defaults: 1 J to a unit of Mekanism's heat, and 10 W/K a face. The ceiling's default is the
     * owner's to choose, 0 for none, so only its range is held here.
     */
    @Test
    void mek_040_the_defaults() {
        assertEquals(1.0, HeatFlow.DEFAULT_JOULES_PER_HEAT_UNIT);
        assertEquals(10.0, HeatFlow.DEFAULT_FACE_CONDUCTANCE);
        assertTrue(HeatFlow.MIN_JOULES_PER_HEAT_UNIT > 0);
        assertTrue(HeatFlow.DEFAULT_MAX_BODY_WATTS >= 0);
        assertTrue(HeatFlow.DEFAULT_MAX_BODY_WATTS <= HeatFlow.HIGHEST_MAX_BODY_WATTS);
    }
}
