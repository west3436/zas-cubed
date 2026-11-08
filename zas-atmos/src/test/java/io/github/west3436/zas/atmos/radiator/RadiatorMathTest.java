package io.github.west3436.zas.atmos.radiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The heat radiator pipe and heat exchangers move in an atmos tick (HEAT-020 to HEAT-022). */
class RadiatorMathTest {
    private static final double G = RadiatorMath.DEFAULT_CONDUCTANCE;
    private static final double E = RadiatorMath.DEFAULT_EMISSIVITY;
    private static final double SIGMA = RadiatorMath.STEFAN_BOLTZMANN;

    /** The heat a network gives each body in a step, with the network's temperature after it. */
    private record Step(double after, double[] heat) {}

    private static Step step(double c, double t, double[] a, double[] tb) {
        double sum = 0;
        double weighted = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
            weighted += a[i] * tb[i];
        }
        double after = RadiatorMath.settle(c, t, sum, weighted);
        double[] q = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            q[i] = a[i] * (after - tb[i]);
        }
        return new Step(after, q);
    }

    /**
     * HEAT-020: in dense gas, where both the network's and the cell's capacities are large next to
     * the conductance, a segment moves 20 W/K × the difference in temperature, and a configured
     * conductance in proportion.
     */
    @Test
    void heat_020_dense_gas_takes_20_w_per_k_times_the_difference() {
        for (double dt : new double[] {1, 2, 0.5}) {
            double a = RadiatorMath.convection(G, dt, 1e12);
            Step s = step(1e9, 400, new double[] {a}, new double[] {300});
            assertEquals(20 * 100 * dt, s.heat()[0], 20 * 100 * dt * 1e-6, "a step of " + dt + " s");
        }
        double a = RadiatorMath.convection(50, 1, 1e12);
        assertEquals(50 * 100, step(1e9, 400, new double[] {a}, new double[] {300}).heat()[0], 5e-3, "50 W/K");
        assertEquals(0, RadiatorMath.convection(0, 1, 1e12), "no conductance");
        assertEquals(0, RadiatorMath.convection(G, 1, 0), "no gas");
    }

    /**
     * HEAT-020: a segment never moves more than its cell's share of the gas can take, which would
     * bring that share to the network's temperature, however thin the gas or large the conductance.
     */
    @Test
    void heat_020_a_thin_cell_caps_the_heat() {
        for (double cell : new double[] {1e-3, 0.1, 1, 10, 20, 100}) {
            double a = RadiatorMath.convection(G, 1, cell);
            assertTrue(a < cell, "more than the cell can take: " + a + " for " + cell);
            assertTrue(a < G, "more than the conductance: " + a);
            double q = step(1e9, 400, new double[] {a}, new double[] {300}).heat()[0];
            assertTrue(q < cell * 100, "past the network: " + q + " J into " + cell + " J/K");
        }
        assertEquals(1e-3, RadiatorMath.convection(1e6, 1, 1e-3), 1e-9, "a cell's whole capacity at most");
    }

    /** HEAT-020: a cell's share of a body of gas is by volume, and all of a body no bigger than a cell. */
    @Test
    void heat_020_a_cells_share_is_by_volume() {
        assertEquals(10, RadiatorMath.cellCapacity(270, 27_000), 1e-12, "a 27-cell room");
        assertEquals(270, RadiatorMath.cellCapacity(270, 1000), 0, "one cell");
        assertEquals(270, RadiatorMath.cellCapacity(270, 500), 0, "less than a cell");
    }

    /** HEAT-020: a cell thinner than 1 % of standard air's density takes no part. */
    @Test
    void heat_020_near_vacuum_is_below_1_percent_of_standard_air() {
        double standard = 101.325 / (8.314462618 * 293.15);
        assertEquals(standard, RadiatorMath.STANDARD_DENSITY, standard * 1e-6);
        assertTrue(RadiatorMath.nearVacuum(0, 1000), "a vacuum");
        assertTrue(RadiatorMath.nearVacuum(0.009 * standard * 1000, 1000), "0.9 %");
        assertFalse(RadiatorMath.nearVacuum(0.011 * standard * 1000, 1000), "1.1 %");
        assertFalse(RadiatorMath.nearVacuum(standard * 1000, 1000), "standard air");
        assertTrue(RadiatorMath.nearVacuum(1, 0), "no volume");
    }

    /**
     * HEAT-020, HEAT-021: with random capacities, temperatures and runs, the network ends between its
     * own temperature and its bodies', no body is taken past the network's new temperature, and the
     * heat the bodies gain is the heat the network loses.
     */
    @Test
    void heat_020_random_runs_never_cross() {
        Random random = new Random(20);
        for (int i = 0; i < 20_000; i++) {
            double c = Math.pow(10, -3 + 9 * random.nextDouble());
            double t = 2.7 + 2000 * random.nextDouble();
            int n = 1 + random.nextInt(40);
            double[] a = new double[n];
            double[] tb = new double[n];
            double[] cells = new double[n];
            double lo = t;
            double hi = t;
            for (int k = 0; k < n; k++) {
                tb[k] = 2.7 + 2000 * random.nextDouble();
                if (random.nextInt(4) == 0) {
                    cells[k] = Double.POSITIVE_INFINITY; // a vacuum's background
                    a[k] = RadiatorMath.radiation(E * random.nextDouble(), 0.05 + 3 * random.nextDouble(), t, tb[k]);
                } else {
                    cells[k] = Math.pow(10, -3 + 9 * random.nextDouble());
                    a[k] = RadiatorMath.convection(1000 * random.nextDouble(), 0.05 + 3 * random.nextDouble(), cells[k]);
                }
                lo = Math.min(lo, tb[k]);
                hi = Math.max(hi, tb[k]);
            }
            Step s = step(c, t, a, tb);
            double tol = 1e-9 * hi;
            assertTrue(s.after() >= lo - tol && s.after() <= hi + tol, "the network passed every body: " + s.after());
            double gained = 0;
            double sum = c;
            for (int k = 0; k < n; k++) {
                double tk = tb[k] + s.heat()[k] / cells[k];
                assertTrue(
                        tb[k] <= s.after() ? tk <= s.after() + tol : tk >= s.after() - tol,
                        "body " + k + " passed the network: " + tb[k] + " to " + tk + ", network " + s.after());
                gained += s.heat()[k];
                sum += a[k];
            }
            // Rounding: each side is the difference of terms as large as (C + Σ a) × the hottest.
            double lost = c * (t - s.after());
            assertEquals(lost, gained, 1e-12 * sum * hi, "heat not conserved");
        }
    }

    /**
     * HEAT-021: in a vacuum a segment radiates εσA(T⁴ − T_bg⁴) over the step, with ε 0.9 and A
     * 1 m², and a configured emissivity in proportion.
     */
    @Test
    void heat_021_radiates_epsilon_sigma_a_t4() {
        for (double[] tt : new double[][] {{600, 2.7}, {300, 2.7}, {1500, 2.7}, {400, 350}}) {
            double t = tt[0];
            double bg = tt[1];
            double formula = E * SIGMA * 1 * (Math.pow(t, 4) - Math.pow(bg, 4));
            double a = RadiatorMath.radiation(E, 1, t, bg);
            assertEquals(formula, a * (t - bg), formula * 1e-12, "the coupling at " + t + " K");
            double q = step(1e12, t, new double[] {a}, new double[] {bg}).heat()[0];
            assertEquals(formula, q, formula * 1e-6, "a large network at " + t + " K");
        }
        assertEquals(
                0.5 * RadiatorMath.radiation(1, 1, 600, 2.7),
                RadiatorMath.radiation(0.5, 1, 600, 2.7),
                1e-12,
                "ε 0.5");
        assertEquals(0, RadiatorMath.radiation(0, 1, 600, 2.7), "ε 0");
        assertEquals(1, RadiatorMath.AREA, "1 m²");
        assertEquals(0.9, E, "ε 0.9 by default");
    }

    /** HEAT-021: however small the network or long the step, it never drops below the background. */
    @Test
    void heat_021_never_below_the_background() {
        Random random = new Random(21);
        for (int i = 0; i < 10_000; i++) {
            double c = Math.pow(10, -6 + 9 * random.nextDouble());
            double bg = 2.7 + 300 * random.nextDouble();
            double t = bg + 3000 * random.nextDouble();
            double a = RadiatorMath.radiation(E, 0.05 + 100 * random.nextDouble(), t, bg);
            double after = RadiatorMath.settle(c, t, a, a * bg);
            assertTrue(after >= bg - 1e-9 * t && after <= t, "outside the background and the network: " + after);
        }
        assertEquals(400, RadiatorMath.settle(10, 400, 0, 0), "no coupling: no change");
    }

    /**
     * HEAT-022: a pair moves ΔT × C₁C₂ / (C₁ + C₂) from the hotter, which brings both shares to one
     * temperature and conserves their energy.
     */
    @Test
    void heat_022_a_pair_shares_one_temperature() {
        assertEquals(100 * 50 * 50 / 100.0, RadiatorMath.pairHeat(50, 400, 50, 300), 1e-9, "equal shares");
        assertEquals(-100 * 10 * 30 / 40.0, RadiatorMath.pairHeat(10, 300, 30, 400), 1e-9, "the other way");
        Random random = new Random(22);
        for (int i = 0; i < 10_000; i++) {
            double c1 = Math.pow(10, -3 + 9 * random.nextDouble());
            double c2 = Math.pow(10, -3 + 9 * random.nextDouble());
            double t1 = 2.7 + 2000 * random.nextDouble();
            double t2 = 2.7 + 2000 * random.nextDouble();
            double q = RadiatorMath.pairHeat(c1, t1, c2, t2);
            double a = t1 - q / c1;
            double b = t2 + q / c2;
            double tol = 1e-9 * Math.max(t1, t2);
            assertEquals(a, b, tol, "not one temperature");
            assertEquals(c1 * t1 + c2 * t2, c1 * a + c2 * b, 1e-9 * (c1 * t1 + c2 * t2), "energy not conserved");
            assertTrue(a >= Math.min(t1, t2) - tol && a <= Math.max(t1, t2) + tol, "outside the two");
        }
        assertEquals(0, RadiatorMath.pairHeat(0, 400, 50, 300), "one with no gas");
        assertEquals(0, RadiatorMath.pairHeat(50, 400, 0, 300), "the other with no gas");
        assertEquals(0, RadiatorMath.pairHeat(50, 300, 50, 300), "one temperature already");
    }
}
