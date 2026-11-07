package io.github.west3436.zas.atmos.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The controller's breach check (CONT-043): a breach to a low ambient, or a fast fall, alarms at once. */
class BreachAlarmTest {
    private static final Thresholds HUMAN = ControllerPreset.HUMAN.pressure();
    private static final Thresholds VACUUM = ControllerPreset.VACUUM.pressure();
    private static final double NONE = Double.NaN;

    @Test
    void cont_043_a_breach_to_space_alarms_but_one_to_outside_air_does_not() {
        BreachAlarm b = new BreachAlarm();
        assertTrue(b.check(HUMAN, 101, true, 0, 10), "a breach to a vacuum");
        assertTrue(b.check(HUMAN, 101, true, 81, 10), "a breach to an ambient at min2");
        assertFalse(b.check(HUMAN, 101, true, 81.1, 10), "a breach to an ambient above min2");
        assertFalse(b.check(HUMAN, 101, true, 101.325, 10), "a door to the Overworld's air");
        assertFalse(b.check(HUMAN, 101, true, NONE, 10), "an ambient that couldn't be read");
        assertFalse(b.check(HUMAN, 101, false, 0, 10), "an enclosed zone");
    }

    @Test
    void cont_043_a_fast_fall_below_min1_alarms() {
        BreachAlarm b = new BreachAlarm();
        assertFalse(b.check(HUMAN, 101, false, NONE, 10), "the first reading has nothing to fall from");
        assertTrue(b.check(HUMAN, 90, false, NONE, 10), "11 kPa in a reading, to below min1");
        assertFalse(b.check(HUMAN, 85, false, NONE, 10), "5 kPa in a reading");

        b.reset();
        b.check(HUMAN, 120, false, NONE, 10);
        assertFalse(b.check(HUMAN, 100, false, NONE, 10), "a fast fall that stays above min1");
        assertFalse(b.check(HUMAN, 89, false, NONE, 0), "the rule turned off");
        b.check(HUMAN, 100, false, NONE, 10);
        b.reset();
        assertFalse(b.check(HUMAN, 50, false, NONE, 10), "a reading after a reset has nothing to fall from");
    }

    @Test
    void cont_043_a_preset_with_no_pressure_min2_never_alarms_for_a_breach() {
        BreachAlarm b = new BreachAlarm();
        b.check(VACUUM, 101, true, 0, 10);
        assertFalse(b.check(VACUUM, 0, true, 0, 10), "Vacuum: a room open to space, emptied in a reading");
    }

    @Test
    void cont_043_a_pressure_min2_at_0_never_alarms_for_a_breach() {
        Thresholds noFloor = HUMAN.with(0, 0);
        BreachAlarm b = new BreachAlarm();
        assertFalse(b.check(noFloor, 101, true, 0, 10), "a breach to a vacuum");
        assertFalse(b.check(noFloor, 50, true, 0, 10), "a fast fall below min1");
    }
}
