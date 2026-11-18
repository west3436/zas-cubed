package io.github.west3436.zas.atmos.turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import org.junit.jupiter.api.Test;

/** The gas turbine's arithmetic (CONT-160 to CONT-164). */
class TurbineMathTest {
    private static final double N = 10;
    private static final double C = 20.8 * N;

    @Test
    void cont_160_compressing_costs_more_than_ideal_and_nothing_downhill() {
        double ideal = TurbineMath.compressionWork(C, N, 293, 100, 300, 1);
        double real = TurbineMath.compressionWork(C, N, 293, 100, 300, 0.8);
        assertTrue(ideal > 0);
        assertEquals(ideal / 0.8, real, 1e-9);
        assertEquals(0, TurbineMath.compressionWork(C, N, 293, 300, 100, 0.8));
    }

    @Test
    void cont_161_expanding_gives_less_than_ideal_and_less_than_the_gas_holds() {
        double ideal = TurbineMath.expansionWork(C, N, 1500, 300, 100, 1);
        double real = TurbineMath.expansionWork(C, N, 1500, 300, 100, 0.8);
        assertEquals(0.8 * ideal, real, 1e-9);
        assertTrue(ideal < C * 1500);
        assertEquals(0, TurbineMath.expansionWork(C, N, 1500, 100, 300, 0.8));
        // Into vacuum it stops at a tenth of the chamber's pressure.
        assertEquals(
                TurbineMath.expansionWork(C, N, 1500, 300, 30, 1),
                TurbineMath.expansionWork(C, N, 1500, 300, 0, 1),
                1e-9);
    }

    @Test
    void cont_161_compressing_then_expanding_ideally_gives_back_the_work() {
        double t = 293;
        double in = TurbineMath.compressionWork(C, N, t, 100, 300, 1);
        double hot = t + in / C;
        double out = TurbineMath.expansionWork(C, N, hot, 300, 100, 1);
        assertEquals(in, out, 1e-6 * in);
    }

    @Test
    void cont_161_the_turbine_lets_out_toward_even_within_its_caps() {
        double t = 1000, v = 1000;
        double even = TurbineMath.turbineMoles(1000, t, v, 300, v, 100, 1, 1e9);
        assertEquals(200 / (GasMixture.GAS_CONSTANT * t * (2 / v)), even, 1e-9);
        assertEquals(even / 2, TurbineMath.turbineMoles(1000, t, v, 300, v, 100, 0.5, 1e9), 1e-9);
        // Into open air it would let out 36 mol here, but never more than half what the chamber holds.
        assertEquals(25, TurbineMath.turbineMoles(50, t, v, 300, Double.POSITIVE_INFINITY, 0, 1, 1e9), 1e-9);
        assertEquals(0, TurbineMath.turbineMoles(1000, t, v, 100, v, 300, 1, 1e9));
    }

    @Test
    void cont_160_a_lean_chamber_gets_fuel_and_no_oxidizer() {
        // 100 mol, a quarter of it oxidizer, no fuel: lean.
        double[] r = TurbineMath.release(0.5, 0.5, 0, 25, 100, 0.2, false, 10, 10);
        assertEquals(0.5, r[0], 1e-12);
        assertEquals(0, r[1], 1e-12);
    }

    @Test
    void cont_160_a_rich_chamber_gets_oxidizer_and_no_fuel() {
        // 10 mol of fuel and 1 of oxidizer: rich, so oxidizer to burn it, capped at four times the fuel's.
        double[] r = TurbineMath.release(0.5, 0.5, 10, 1, 100, 0.2, false, 10, 10);
        assertEquals(0, r[0], 1e-12);
        assertEquals(TurbineMath.OXIDIZER_CATCH_UP * 0.5 * 0.5, r[1], 1e-12);
        // Uncapped when the shortfall is small.
        double[] s = TurbineMath.release(0.5, 0.5, 2.2, 1, 5, 0.2, false, 10, 10);
        assertEquals(0, s[0], 1e-12);
        assertEquals(0.1, s[1], 1e-9);
    }

    @Test
    void cont_160_a_primed_chamber_waits_and_the_buffer_bounds_each_gas() {
        double[] primed = TurbineMath.release(0.5, 0.5, 1, 30, 100, 0.2, true, 10, 10);
        assertEquals(0, primed[0], 1e-12);
        assertEquals(0, primed[1], 1e-12);
        // An empty chamber wants both; the buffer holds a little fuel and no oxidizer.
        double[] r = TurbineMath.release(0.5, 0.5, 0, 0, 100, 0.2, false, 0.1, 0);
        assertEquals(0.1, r[0], 1e-12);
        assertEquals(0, r[1], 1e-12);
        double[] none = TurbineMath.release(0, 0.5, 0, 0, 100, 0.2, false, 10, 10);
        assertEquals(0, none[0] + none[1], 1e-12);
        double[] noReaction = TurbineMath.release(0.5, Double.NaN, 0, 0, 100, 0.2, false, 10, 10);
        assertEquals(0, noReaction[0] + noReaction[1], 1e-12);
    }

    @Test
    void cont_162_output_goes_as_rpm_to_the_0_8() {
        double top = TurbineMath.generatorJoules(1e9, 1, 1000, 1e9);
        double half = TurbineMath.generatorJoules(1e9, 0.5, 1000, 1e9);
        assertEquals(1000, top, 1e-9);
        assertEquals(1000 * Math.pow(0.5, 0.8), half, 1e-9);
        assertEquals(5, TurbineMath.generatorJoules(5, 1, 1000, 1e9), 1e-9);
        assertEquals(7, TurbineMath.generatorJoules(1e9, 1, 1000, 7), 1e-9);
        assertEquals(0, TurbineMath.generatorJoules(1e9, 0, 1000, 1e9));
    }

    @Test
    void cont_161_rpm_goes_as_the_root_of_the_shaft_energy() {
        assertEquals(10_000, TurbineMath.rpm(500, 500, 10_000), 1e-9);
        assertEquals(5_000, TurbineMath.rpm(125, 500, 10_000), 1e-9);
        assertEquals(10_000, TurbineMath.rpm(5_000, 500, 10_000), 1e-9);
        assertEquals(0, TurbineMath.rpm(0, 500, 10_000));
    }

    @Test
    void cont_164_blades_show_three_speeds() {
        assertEquals(0, TurbineMath.spin(0));
        assertEquals(0, TurbineMath.spin(0.01));
        assertEquals(1, TurbineMath.spin(0.1));
        assertEquals(2, TurbineMath.spin(0.5));
        assertEquals(TurbinePartBlock.MAX_SPIN, TurbineMath.spin(1));
    }
}
