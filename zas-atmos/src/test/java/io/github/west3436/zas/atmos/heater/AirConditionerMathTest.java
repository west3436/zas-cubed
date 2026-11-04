package io.github.west3436.zas.atmos.heater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The air conditioner's step (CONT-019, CONT-087). */
class AirConditionerMathTest {
    private static final double W = AirConditionerMath.DEFAULT_MAX_WATTS;

    /**
     * CONT-087: whatever the gas, a step never takes more than the configured power, 5 kW by default;
     * CONT-019: nor does it take the zone below its setpoint.
     */
    @Test
    void cont_087_never_more_than_5_kw_and_never_below_the_setpoint() {
        Random random = new Random(87);
        for (int i = 0; i < 10_000; i++) {
            double c = Math.pow(10, -3 + 9 * random.nextDouble()); // 1 mJ/K to 1 MJ/K
            double t = 2.7 + 700 * random.nextDouble();
            double setpoint = AirConditionerMath.clampSetpoint(50 + 300 * random.nextDouble());
            double seconds = 0.1 + 5 * random.nextDouble();
            double watts = i % 2 == 0 ? W : 20_000 * random.nextDouble();
            double joules = AirConditionerMath.cool(c, t, setpoint, seconds, watts);
            assertTrue(joules >= 0, "negative heat");
            assertTrue(joules / seconds <= watts * (1 + 1e-12), "more than " + watts + " W: " + joules / seconds);
            assertTrue(t - joules / c >= setpoint - 1e-9 || t <= setpoint, "below the setpoint");
            if (t <= setpoint) {
                assertEquals(0, joules, "cooling at or below the setpoint");
            }
        }
    }

    /** CONT-087: a large zone well above its setpoint gives up the full power; a small one only what it must. */
    @Test
    void cont_087_limited_or_exact() {
        assertEquals(5000, AirConditionerMath.cool(1e6, 293.15, 283, 1, W), 0, "a large zone");
        assertEquals(20.8 * 10, AirConditionerMath.cool(20.8, 293, 283, 1, W), 1e-9, "1 mol of N2, 10 K above");
        assertEquals(10_000, AirConditionerMath.cool(1e6, 293.15, 283, 2, W), 0, "two seconds");
        assertEquals(2000, AirConditionerMath.cool(1e6, 293.15, 283, 1, 2000), 0, "a configured 2 kW");
        assertEquals(0, AirConditionerMath.cool(1e6, 293.15, 283, 1, 0), 0, "a configured 0 W");
        assertEquals(0, AirConditionerMath.cool(0, 293, 283, 1, W), 0, "an empty zone");
        assertEquals(0, AirConditionerMath.cool(100, 270, 283, 1, W), 0, "below the setpoint: it never heats");
        assertEquals(0, AirConditionerMath.cool(100, 283, 283, 1, W), 0, "at the setpoint");
    }

    /** CONT-019: the setpoint is clamped to 73–293 K. */
    @Test
    void cont_019_setpoint_range() {
        assertEquals(73, AirConditionerMath.clampSetpoint(10), 0);
        assertEquals(293, AirConditionerMath.clampSetpoint(1000), 0);
        assertEquals(200, AirConditionerMath.clampSetpoint(200), 0);
        assertEquals(293, AirConditionerMath.clampSetpoint(Double.NaN), 0);
        assertEquals(
                AirConditionerMath.DEFAULT_SETPOINT,
                AirConditionerMath.clampSetpoint(AirConditionerMath.DEFAULT_SETPOINT),
                0);
    }
}
