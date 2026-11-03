package io.github.west3436.zas.atmos.heater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The heater's step (CONT-019, CONT-087). */
class HeaterMathTest {
    private static final double W = HeaterMath.DEFAULT_MAX_WATTS;

    /**
     * CONT-087: whatever the gas, a step never moves more than the configured power, 5 kW by
     * default, nor passes its target.
     */
    @Test
    void cont_087_never_more_than_5_kw() {
        Random random = new Random(87);
        for (int i = 0; i < 10_000; i++) {
            double c = Math.pow(10, -3 + 9 * random.nextDouble()); // 1 mJ/K to 1 MJ/K
            double t = 2.7 + 700 * random.nextDouble();
            double target = HeaterMath.clampSetpoint(250 + 400 * random.nextDouble());
            double seconds = 0.1 + 5 * random.nextDouble();
            double watts = i % 2 == 0 ? W : 20_000 * random.nextDouble();
            double joules = HeaterMath.heat(c, t, target, seconds, watts);
            assertTrue(joules >= 0, "negative heat");
            assertTrue(joules / seconds <= watts * (1 + 1e-12), "more than " + watts + " W: " + joules / seconds);
            assertTrue(t + joules / c <= target + 1e-9 || t >= target, "past the target");
            if (t >= target) {
                assertEquals(0, joules, "heat at or above the target");
            }
        }
    }

    /** CONT-087: a large zone well below its setpoint takes the full power; a small one only what it needs. */
    @Test
    void cont_087_limited_or_exact() {
        assertEquals(5000, HeaterMath.heat(1e6, 293.15, 303, 1, W), 0, "a large zone");
        assertEquals(20.8 * 10, HeaterMath.heat(20.8, 293, 303, 1, W), 1e-9, "1 mol of N2, 10 K below");
        assertEquals(10_000, HeaterMath.heat(1e6, 293.15, 303, 2, W), 0, "two seconds");
        assertEquals(2000, HeaterMath.heat(1e6, 293.15, 303, 1, 2000), 0, "a configured 2 kW");
        assertEquals(0, HeaterMath.heat(1e6, 293.15, 303, 1, 0), 0, "a configured 0 W");
        assertEquals(0, HeaterMath.heat(0, 293, 303, 1, W), 0, "an empty zone");
        assertEquals(0, HeaterMath.heat(100, 310, 303, 1, W), 0, "above the target: a heater never cools");
    }

    /**
     * CONT-087: the battery pays the full draw, 60 FE/t over the step, at full power, and in
     * proportion below it, rounded up; nothing for no heat.
     */
    @Test
    void cont_087_draw_in_proportion() {
        assertEquals(1200, HeaterMath.energy(5000, 1, W, 60, 20), "full power over 20 ticks");
        assertEquals(600, HeaterMath.energy(2500, 1, W, 60, 20), "half power");
        assertEquals(1, HeaterMath.energy(1e-6, 1, W, 60, 20), "a trace of heat costs 1 FE");
        assertEquals(0, HeaterMath.energy(0, 1, W, 60, 20), "no heat");
        assertEquals(1200, HeaterMath.energy(9000, 1, W, 60, 20), "capped at the full draw");
        assertEquals(0, HeaterMath.energy(5000, 1, W, 0, 20), "a configured draw of 0");
        assertEquals(0, HeaterMath.energy(5000, 1, 0, 60, 20), "a configured 0 W");
        Random random = new Random(19);
        for (int i = 0; i < 10_000; i++) {
            double joules = W * random.nextDouble();
            int fe = HeaterMath.energy(joules, 1, W, 60, 20);
            assertTrue(fe >= 1200 * joules / W - 1e-6 && fe <= 1200 * joules / W + 1, "not in proportion: " + fe);
        }
    }

    /** CONT-019: the setpoint is clamped to 293–573 K. */
    @Test
    void cont_019_setpoint_range() {
        assertEquals(293, HeaterMath.clampSetpoint(100), 0);
        assertEquals(573, HeaterMath.clampSetpoint(1000), 0);
        assertEquals(400, HeaterMath.clampSetpoint(400), 0);
        assertEquals(293, HeaterMath.clampSetpoint(Double.NaN), 0);
        assertEquals(HeaterMath.DEFAULT_SETPOINT, HeaterMath.clampSetpoint(HeaterMath.DEFAULT_SETPOINT), 0);
    }
}
