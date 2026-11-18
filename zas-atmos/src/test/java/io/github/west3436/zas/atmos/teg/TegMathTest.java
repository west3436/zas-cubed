package io.github.west3436.zas.atmos.teg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import org.junit.jupiter.api.Test;

/** The thermoelectric generator's arithmetic (CONT-157 to CONT-159). */
class TegMathTest {
    private static final double C = 20.8 * 10; // ten moles of a gas like air

    @Test
    void cont_158_heat_out_of_the_hot_stream_is_work_plus_heat_into_the_cold() {
        TegMath.Exchange e = TegMath.exchange(C, 600, C, 300, 0.5, Double.MAX_VALUE);
        assertEquals(e.fromHot(), e.toCold() + e.work(), 1e-9);
        assertEquals(150 * C, e.fromHot(), 1e-9); // half the 300 K difference, two equal streams
        assertTrue(e.work() > 0);
    }

    @Test
    void cont_158_efficiency_is_the_set_share_of_carnot_and_below_it() {
        TegMath.Exchange e = TegMath.exchange(C, 600, C, 300, 0.5, Double.MAX_VALUE);
        double carnot = 1 - 300.0 / 600.0;
        assertEquals(0.5 * carnot, e.work() / e.fromHot(), 1e-9);
        assertTrue(e.work() / e.fromHot() < carnot);
    }

    @Test
    void cont_158_entropy_never_falls_even_at_the_full_carnot_share() {
        for (double f : new double[] {0.5, 0.9, 1.0}) {
            for (double ratio : new double[] {0.25, 1, 4}) {
                double hotC = C, coldC = C * ratio, hotT = 900, coldT = 290;
                TegMath.Exchange e = TegMath.exchange(hotC, hotT, coldC, coldT, f, Double.MAX_VALUE);
                double hotOut = hotT - e.fromHot() / hotC;
                double coldOut = coldT + e.toCold() / coldC;
                double ds = hotC * Math.log(hotOut / hotT) + coldC * Math.log(coldOut / coldT);
                assertTrue(ds >= -1e-9, "entropy fell by " + -ds + " at f " + f + ", ratio " + ratio);
            }
        }
    }

    @Test
    void cont_158_no_work_without_a_difference_or_gas() {
        assertEquals(TegMath.Exchange.NONE, TegMath.exchange(C, 300, C, 300, 0.5, 1e9));
        assertEquals(TegMath.Exchange.NONE, TegMath.exchange(C, 300, C, 400, 0.5, 1e9));
        assertEquals(TegMath.Exchange.NONE, TegMath.exchange(0, 600, C, 300, 0.5, 1e9));
        assertEquals(TegMath.Exchange.NONE, TegMath.exchange(C, 600, 0, 300, 0.5, 1e9));
    }

    @Test
    void cont_158_a_full_store_turns_the_work_back_into_heat_for_the_cold_stream() {
        TegMath.Exchange e = TegMath.exchange(C, 600, C, 300, 0.5, 10);
        assertEquals(10, e.work(), 1e-9);
        assertEquals(e.fromHot() - 10, e.toCold(), 1e-9);
        TegMath.Exchange full = TegMath.exchange(C, 600, C, 300, 0.5, 0);
        assertEquals(0, full.work());
        assertEquals(full.fromHot(), full.toCold(), 1e-9);
    }

    @Test
    void cont_157_a_circulator_evens_its_sides_less_the_friction() {
        double v = 200, t = 293.15;
        double n = TegMath.circulatorMoles(100, t, v, 200, v, 100, 5, 1e9);
        // What moved leaves the inlet 5 kPa above the outlet.
        double inP = 200 - n * GasMixture.GAS_CONSTANT * t / v;
        double outP = 100 + n * GasMixture.GAS_CONSTANT * t / v;
        assertEquals(5, inP - outP, 1e-9);
    }

    @Test
    void cont_157_a_circulator_moves_nothing_uphill_or_within_the_friction() {
        assertEquals(0, TegMath.circulatorMoles(100, 293, 200, 100, 200, 200, 5, 1e9));
        assertEquals(0, TegMath.circulatorMoles(100, 293, 200, 104, 200, 100, 5, 1e9));
    }

    @Test
    void cont_157_a_circulator_passes_at_most_its_flow() {
        double n = TegMath.circulatorMoles(100, 293, 1000, 1000, 1000, 0, 5, 100);
        assertEquals(10, n, 1e-9); // a tenth of the inlet's litres, so a tenth of its moles
    }

    @Test
    void cont_159_segments_light_with_what_it_makes() {
        assertEquals(0, TegMath.segments(0));
        assertEquals(1, TegMath.segments(0.1));
        assertEquals(1, TegMath.segments(24.9));
        assertEquals(2, TegMath.segments(25));
        assertEquals(3, TegMath.segments(100));
        assertEquals(4, TegMath.segments(400));
        assertEquals(5, TegMath.segments(1600));
        assertEquals(TegBlock.MAX_OUTPUT, TegMath.segments(1e9));
    }
}
