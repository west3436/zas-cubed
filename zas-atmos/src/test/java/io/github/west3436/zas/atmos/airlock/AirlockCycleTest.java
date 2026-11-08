package io.github.west3436.zas.atmos.airlock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The airlock controller's cycle (CONT-139), its guess at a link (CONT-138), a press (CONT-140) and the air sensor's states (CONT-142). */
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

    private static final long OPEN = AirlockCycle.OPEN;
    private static final long UNKNOWN = AirlockCycle.UNKNOWN;

    @Test
    void cont_138_a_doors_chamber_side_is_the_zone_by_open_air_else_the_one_both_doors_border() {
        assertEquals(AirlockCycle.FRONT, AirlockCycle.chamberSide(5, OPEN, UNKNOWN, UNKNOWN), "a zone by open air");
        assertEquals(AirlockCycle.BACK, AirlockCycle.chamberSide(OPEN, 5, UNKNOWN, UNKNOWN));
        assertEquals(AirlockCycle.FRONT, AirlockCycle.chamberSide(5, 1, 2, 5), "front shared with the other's back");
        assertEquals(AirlockCycle.BACK, AirlockCycle.chamberSide(1, 5, 5, OPEN), "back shared with the other's front");
        assertEquals(AirlockCycle.NONE, AirlockCycle.chamberSide(5, 1, UNKNOWN, UNKNOWN), "two zones, no other door");
        assertEquals(AirlockCycle.NONE, AirlockCycle.chamberSide(5, 7, 5, 7), "both onto one room: both shared");
        assertEquals(AirlockCycle.NONE, AirlockCycle.chamberSide(1, 2, 3, 4), "none shared");
        assertEquals(AirlockCycle.NONE, AirlockCycle.chamberSide(OPEN, OPEN, 5, OPEN), "open both sides");
        assertEquals(AirlockCycle.BACK, AirlockCycle.chamberSide(UNKNOWN, 5, 5, 2), "one side reads nothing");
        assertEquals(AirlockCycle.NONE, AirlockCycle.chamberSide(UNKNOWN, 5, UNKNOWN, UNKNOWN), "nothing else");
    }

    @Test
    void cont_138_the_door_onto_open_air_or_the_lower_pressure_is_the_outer() {
        assertEquals(
                AirlockCycle.OUTER, AirlockCycle.doorGuess(OPEN, 0, UNKNOWN, Double.NaN), "onto open air or a vacuum");
        assertEquals(AirlockCycle.INNER, AirlockCycle.doorGuess(1, 101.3, OPEN, 0), "the other is onto open air");
        assertEquals(AirlockCycle.OUTER, AirlockCycle.doorGuess(1, 40, 2, 101.3), "the lower side");
        assertEquals(AirlockCycle.INNER, AirlockCycle.doorGuess(1, 101.3, 2, 40), "the higher side");
        assertEquals(AirlockCycle.NONE, AirlockCycle.doorGuess(1, 100, 2, 101.3), "within 5.07 kPa");
        assertEquals(AirlockCycle.NONE, AirlockCycle.doorGuess(1, 40, UNKNOWN, Double.NaN), "no other door");
        assertEquals(AirlockCycle.NONE, AirlockCycle.doorGuess(OPEN, 0, OPEN, 0), "both onto open air");
        assertEquals(AirlockCycle.NONE, AirlockCycle.doorGuess(7, 40, 7, 40), "both onto one room");
        assertEquals(AirlockCycle.NONE, AirlockCycle.doorGuess(UNKNOWN, 0, OPEN, 0), "no chamber side");
    }

    private static final long[] CHAMBER = {5, 5};
    private static final long[] FAR = {1, 2};

    @Test
    void cont_138_a_sensor_is_the_chambers_or_a_sides_by_its_zone() {
        assertEquals(AirlockCycle.CHAMBER, AirlockCycle.sensorGuess(5, CHAMBER, FAR));
        assertEquals(AirlockCycle.INNER, AirlockCycle.sensorGuess(1, CHAMBER, FAR));
        assertEquals(AirlockCycle.OUTER, AirlockCycle.sensorGuess(2, CHAMBER, FAR));
        assertEquals(AirlockCycle.OUTER, AirlockCycle.sensorGuess(OPEN, CHAMBER, new long[] {1, OPEN}), "in a vacuum");
        assertEquals(
                AirlockCycle.CHAMBER,
                AirlockCycle.sensorGuess(5, new long[] {UNKNOWN, 5}, new long[] {UNKNOWN, OPEN}),
                "one door known");
    }

    @Test
    void cont_138_a_sensor_whose_zone_fits_no_role_or_two_has_no_guess() {
        assertEquals(AirlockCycle.NONE, AirlockCycle.sensorGuess(9, CHAMBER, FAR), "a zone no door borders");
        assertEquals(AirlockCycle.NONE, AirlockCycle.sensorGuess(7, CHAMBER, new long[] {7, 7}), "both onto one room");
        assertEquals(AirlockCycle.NONE, AirlockCycle.sensorGuess(OPEN, CHAMBER, new long[] {OPEN, OPEN}));
        assertEquals(AirlockCycle.NONE, AirlockCycle.sensorGuess(UNKNOWN, CHAMBER, FAR), "it reads nothing");
        long[] unknown = {UNKNOWN, UNKNOWN};
        assertEquals(AirlockCycle.NONE, AirlockCycle.sensorGuess(5, unknown, unknown), "no doors");
    }

    @Test
    void cont_140_a_press_cycles_away_from_the_side_the_airlock_is_accessing() {
        boolean[] innerOpen = {false, true};
        boolean[] outerOpen = {true, false};
        boolean[] both = {true, true};
        boolean[] neither = {false, false};
        for (int pressed : new int[] {AirlockCycle.INNER, AirlockCycle.OUTER, AirlockCycle.CHAMBER}) {
            assertEquals(1, AirlockCycle.pressTarget(innerOpen, -1, pressed), "the inner door alone unbolted");
            assertEquals(
                    0,
                    AirlockCycle.pressTarget(outerOpen, 0, pressed),
                    "the outer door alone unbolted, the inner opened last");
            assertEquals(1, AirlockCycle.pressTarget(both, 0, pressed), "both bolted, the inner opened last");
            assertEquals(0, AirlockCycle.pressTarget(neither, 1, pressed), "neither bolted, the outer opened last");
        }
        for (boolean[] bolted : new boolean[][] {both, neither}) {
            assertEquals(
                    1, AirlockCycle.pressTarget(bolted, -1, AirlockCycle.INNER), "no history, from the inner side");
            assertEquals(
                    0, AirlockCycle.pressTarget(bolted, -1, AirlockCycle.OUTER), "no history, from the outer side");
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
