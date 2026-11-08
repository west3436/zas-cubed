package io.github.west3436.zas.atmos.airlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The airlock controller's cycle (CONT-139) and the air sensor's states (CONT-142). */
class AirlockCycleTest {
    @Test
    void cont_139_siphons_above_blows_below_and_is_done_within_5_07_kpa() {
        assertEquals(5.06625, AirlockCycle.TOLERANCE_KPA, 1e-9);
        assertEquals(AirlockCycle.Step.SIPHON, AirlockCycle.next(101.325, 0));
        assertEquals(AirlockCycle.Step.SIPHON, AirlockCycle.next(5.07, 0), "just over 5 %");
        assertEquals(AirlockCycle.Step.DONE, AirlockCycle.next(5.06, 0), "within 5 %");
        assertEquals(AirlockCycle.Step.BLOW, AirlockCycle.next(0, 101.325));
        assertEquals(AirlockCycle.Step.BLOW, AirlockCycle.next(96.25, 101.325), "just over 5 % below");
        assertEquals(AirlockCycle.Step.DONE, AirlockCycle.next(96.27, 101.325));
        assertEquals(AirlockCycle.Step.DONE, AirlockCycle.next(106.38, 101.325), "within 5 % above");
    }

    @Test
    void cont_139_the_chamber_side_is_the_sensors_zone_else_the_nearer() {
        assertTrue(AirlockCycle.frontIsChamber(7, 7, 8, 100, 1), "front in the sensor's zone, though farther");
        assertFalse(AirlockCycle.frontIsChamber(7, 8, 7, 1, 100), "back in the sensor's zone, though farther");
        assertTrue(AirlockCycle.frontIsChamber(7, 7, 7, 1, 4), "both in its zone: the nearer");
        assertFalse(AirlockCycle.frontIsChamber(7, 8, 9, 4, 1), "neither: the nearer");
        assertTrue(AirlockCycle.frontIsChamber(-1, -1, -1, 1, 4), "no zones: the nearer");
    }

    @Test
    void cont_142_cycling_then_alert_below_81_or_above_122_kpa_then_standby() {
        assertEquals(SensorStatus.STANDBY, SensorStatus.of(false, 101.325));
        assertEquals(SensorStatus.STANDBY, SensorStatus.of(false, 81));
        assertEquals(SensorStatus.STANDBY, SensorStatus.of(false, 122));
        assertEquals(SensorStatus.ALERT, SensorStatus.of(false, 80.99));
        assertEquals(SensorStatus.ALERT, SensorStatus.of(false, 122.01));
        assertEquals(SensorStatus.ALERT, SensorStatus.of(false, 0));
        assertEquals(SensorStatus.CYCLING, SensorStatus.of(true, 0), "cycling shows over alert");
        assertEquals(SensorStatus.CYCLING, SensorStatus.of(true, 101.325));
    }
}
