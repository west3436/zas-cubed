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

    /** CONT-087: a large network well below its target takes the full power; a small one only what it needs. */
    @Test
    void cont_087_limited_or_exact() {
        assertEquals(5000, HeaterMath.heat(1e6, 293.15, 303, 1, W), 0, "a large network");
        assertEquals(20.8 * 10, HeaterMath.heat(20.8, 293, 303, 1, W), 1e-9, "1 mol of N2, 10 K below");
        assertEquals(10_000, HeaterMath.heat(1e6, 293.15, 303, 2, W), 0, "two seconds");
        assertEquals(2000, HeaterMath.heat(1e6, 293.15, 303, 1, 2000), 0, "a configured 2 kW");
        assertEquals(0, HeaterMath.heat(1e6, 293.15, 303, 1, 0), 0, "a configured 0 W");
        assertEquals(0, HeaterMath.heat(0, 293, 303, 1, W), 0, "an empty network");
        assertEquals(0, HeaterMath.heat(100, 310, 303, 1, W), 0, "above the target: a heater never cools");
    }

    /**
     * CONT-019: in a room, the target is the network temperature at which the room and the
     * network, mixed, reach the setpoint, capped at 573 K; there is none once the room is there.
     */
    @Test
    void cont_019_room_target() {
        // A room of 20 kJ/K, 2 K short, over a network of 1 kJ/K: 40 K above the setpoint.
        assertEquals(343, HeaterMath.roomTarget(20_000, 301, 1000, 303), 1e-9, "the target");
        // Mixed, they come to the setpoint exactly.
        double mixed = (20_000 * 301 + 1000 * 343) / 21_000.0;
        assertEquals(303, mixed, 1e-9, "the mix");
        assertEquals(HeaterMath.MAX_GAS, HeaterMath.roomTarget(20_000, 250, 1000, 303), 0, "capped at 573 K");
        assertEquals(0, HeaterMath.roomTarget(20_000, 303, 1000, 303), 0, "a room at the setpoint");
        assertEquals(0, HeaterMath.roomTarget(20_000, 310, 1000, 303), 0, "a room above it");
        assertEquals(0, HeaterMath.roomTarget(20_000, 290, 0, 303), 0, "an empty network");
        assertEquals(303, HeaterMath.roomTarget(0, 290, 1000, 303), 0, "an empty room");
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
