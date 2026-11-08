package io.github.west3436.zas.atmos.airlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The airlock controller's cycle (CONT-139), its sensors' roles (CONT-138), a press (CONT-140) and the air sensor's states (CONT-142). */
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
    void cont_139_the_chamber_side_is_the_zone_both_airlocks_border_else_the_nearer() {
        assertTrue(
                AirlockCycle.frontIsChamber(5, 1, 5, 2, 100, 1), "front shared with the other's front, though farther");
        assertFalse(
                AirlockCycle.frontIsChamber(1, 5, 2, 5, 1, 100), "back shared with the other's back, though farther");
        assertTrue(AirlockCycle.frontIsChamber(5, 1, 2, 5, 100, 1), "front shared with the other's back");
        assertTrue(AirlockCycle.frontIsChamber(5, 7, 5, 7, 1, 4), "both onto one room, both shared: the nearer");
        assertFalse(AirlockCycle.frontIsChamber(5, 7, 5, 7, 4, 1), "both onto one room, both shared: the nearer");
        assertFalse(AirlockCycle.frontIsChamber(1, 2, 3, 4, 4, 1), "none shared: the nearer");
        assertTrue(AirlockCycle.frontIsChamber(-1, -1, -1, -1, 1, 4), "no zones: the nearer");
        assertFalse(AirlockCycle.frontIsChamber(-1, 5, -1, 5, 1, 4), "no zone in front matches no zone");
    }

    private static final long[] CHAMBER = {5, 5};
    private static final long[] FAR = {1, 2};
    private static final double[] IN_CHAMBER = {-8, -8};
    private static final double[] PAST_1 = {8, -24};
    private static final double[] PAST_2 = {-24, 8};

    @Test
    void cont_138_a_sensor_is_the_chambers_or_a_sides_by_its_zone() {
        assertEquals(AirlockCycle.CHAMBER, AirlockCycle.role(5, CHAMBER, FAR, IN_CHAMBER));
        assertEquals(AirlockCycle.SIDE_1, AirlockCycle.role(1, CHAMBER, FAR, PAST_1));
        assertEquals(AirlockCycle.SIDE_2, AirlockCycle.role(2, CHAMBER, FAR, PAST_2));
        assertEquals(AirlockCycle.SIDE_1, AirlockCycle.role(1, CHAMBER, FAR, PAST_2), "its zone, wherever it is");
        assertEquals(AirlockCycle.CHAMBER, AirlockCycle.role(5, CHAMBER, FAR, PAST_1), "its zone, wherever it is");
    }

    @Test
    void cont_138_where_one_zone_fits_two_roles_where_the_sensor_is_decides() {
        // Both airlocks open onto one room, 7.
        long[] far = {7, 7};
        assertEquals(AirlockCycle.SIDE_1, AirlockCycle.role(7, CHAMBER, far, PAST_1));
        assertEquals(AirlockCycle.SIDE_2, AirlockCycle.role(7, CHAMBER, far, PAST_2));
        assertEquals(
                AirlockCycle.CHAMBER, AirlockCycle.role(5, CHAMBER, far, PAST_1), "the chamber's zone, wherever it is");
        assertEquals(
                AirlockCycle.SIDE_1,
                AirlockCycle.role(7, CHAMBER, far, IN_CHAMBER),
                "where it is fits no role its zone has");
    }

    @Test
    void cont_138_with_no_zone_where_the_sensor_is_decides() {
        long[] far = {-1, 2};
        assertEquals(AirlockCycle.SIDE_1, AirlockCycle.role(-1, CHAMBER, far, PAST_1), "past airlock 1, in a vacuum");
        assertEquals(AirlockCycle.CHAMBER, AirlockCycle.role(-1, CHAMBER, far, IN_CHAMBER));
        assertEquals(AirlockCycle.SIDE_2, AirlockCycle.role(9, CHAMBER, far, PAST_2), "a zone no airlock borders");
        assertEquals(
                AirlockCycle.SIDE_2,
                AirlockCycle.role(-1, CHAMBER, far, new double[] {4, 8}),
                "past both: the farther past");
    }

    @Test
    void cont_140_a_press_cycles_away_from_the_side_the_airlock_is_accessing() {
        boolean[] side1Open = {false, true};
        boolean[] side2Open = {true, false};
        boolean[] both = {true, true};
        boolean[] neither = {false, false};
        for (int pressed : new int[] {AirlockCycle.SIDE_1, AirlockCycle.SIDE_2, AirlockCycle.CHAMBER}) {
            assertEquals(1, AirlockCycle.pressTarget(side1Open, -1, pressed), "airlock 1 alone unbolted");
            assertEquals(0, AirlockCycle.pressTarget(side2Open, 0, pressed), "airlock 2 alone unbolted, 1 opened last");
            assertEquals(1, AirlockCycle.pressTarget(both, 0, pressed), "both bolted, airlock 1 opened last");
            assertEquals(0, AirlockCycle.pressTarget(neither, 1, pressed), "neither bolted, airlock 2 opened last");
        }
        for (boolean[] bolted : new boolean[][] {both, neither}) {
            assertEquals(1, AirlockCycle.pressTarget(bolted, -1, AirlockCycle.SIDE_1), "no history, from side 1");
            assertEquals(0, AirlockCycle.pressTarget(bolted, -1, AirlockCycle.SIDE_2), "no history, from side 2");
            assertEquals(0, AirlockCycle.pressTarget(bolted, -1, AirlockCycle.CHAMBER), "no history, from the chamber");
            assertEquals(
                    0, AirlockCycle.pressTarget(bolted, -1, AirlockCycle.NONE), "no history, a sensor with no role");
        }
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
