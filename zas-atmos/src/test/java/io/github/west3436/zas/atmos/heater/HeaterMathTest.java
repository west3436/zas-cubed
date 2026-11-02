package io.github.west3436.zas.atmos.heater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The heater's step (CONT-019, CONT-087). */
class HeaterMathTest {
    /** CONT-087: whatever the gas, a step never moves more than 5 kW, nor passes the setpoint. */
    @Test
    void cont_087_never_more_than_5_kw() {
        Random random = new Random(87);
        for (int i = 0; i < 10_000; i++) {
            double c = Math.pow(10, -3 + 9 * random.nextDouble()); // 1 mJ/K to 1 MJ/K
            double t = 2.7 + 700 * random.nextDouble();
            double setpoint = HeaterMath.clampSetpoint(250 + 400 * random.nextDouble());
            double seconds = 0.1 + 5 * random.nextDouble();
            double joules = HeaterMath.heat(c, t, setpoint, seconds);
            assertTrue(joules >= 0, "negative heat");
            assertTrue(joules / seconds <= HeaterMath.MAX_WATTS * (1 + 1e-12), "more than 5 kW: " + joules / seconds);
            assertTrue(t + joules / c <= setpoint + 1e-9 || t >= setpoint, "past the setpoint");
            if (t >= setpoint) {
                assertEquals(0, joules, "heat at or above the setpoint");
            }
        }
    }

    /** CONT-087: a large network well below its setpoint takes the full 5 kW; a small one only what it needs. */
    @Test
    void cont_087_limited_or_exact() {
        assertEquals(5000, HeaterMath.heat(1e6, 293.15, 303, 1), 0, "a large network");
        assertEquals(20.8 * 10, HeaterMath.heat(20.8, 293, 303, 1), 1e-9, "1 mol of N2, 10 K below");
        assertEquals(10_000, HeaterMath.heat(1e6, 293.15, 303, 2), 0, "two seconds");
        assertEquals(0, HeaterMath.heat(0, 293, 303, 1), 0, "an empty network");
        assertEquals(0, HeaterMath.heat(100, 310, 303, 1), 0, "above the setpoint: a heater never cools");
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
