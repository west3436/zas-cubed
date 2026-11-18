package io.github.west3436.zas.atmos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.controller.Thermostat.State;
import org.junit.jupiter.api.Test;

/** The thermostat's rule (CONT-165): its band, its range and each device's share. */
class ThermostatTest {
    private static final double TARGET = 293;
    private static final double BAND = 2;

    /** With no target it is off, whatever the room reads. */
    @Test
    void cont_165_no_target_is_off() {
        for (State was : State.values()) {
            assertEquals(State.OFF, Thermostat.next(was, 250, Double.NaN, BAND), "from " + was);
        }
    }

    /**
     * Idle within the band; it heats once the room is more than the band below the target and cools
     * once it is more than the band above it.
     */
    @Test
    void cont_165_it_starts_only_beyond_the_band() {
        assertEquals(State.IDLE, Thermostat.next(State.IDLE, TARGET - BAND, TARGET, BAND), "at the band's foot");
        assertEquals(State.IDLE, Thermostat.next(State.IDLE, TARGET + BAND, TARGET, BAND), "at the band's top");
        assertEquals(State.HEATING, Thermostat.next(State.IDLE, TARGET - BAND - 0.01, TARGET, BAND), "below it");
        assertEquals(State.COOLING, Thermostat.next(State.IDLE, TARGET + BAND + 0.01, TARGET, BAND), "above it");
        assertEquals(State.HEATING, Thermostat.next(State.OFF, 250, TARGET, BAND), "switched on in the cold");
    }

    /** Once heating it goes on to the target, through the band, and stops there; cooling likewise. */
    @Test
    void cont_165_it_runs_to_the_target_then_stops() {
        assertEquals(State.HEATING, Thermostat.next(State.HEATING, TARGET - 0.5, TARGET, BAND), "heating in the band");
        assertEquals(State.IDLE, Thermostat.next(State.HEATING, TARGET, TARGET, BAND), "heating at the target");
        assertEquals(State.COOLING, Thermostat.next(State.COOLING, TARGET + 0.5, TARGET, BAND), "cooling in the band");
        assertEquals(State.IDLE, Thermostat.next(State.COOLING, TARGET, TARGET, BAND), "cooling at the target");
        // Within a tenth of the band is at the target: a wall's leak keeps it from ever quite getting there.
        double near = BAND * Thermostat.STOP_SHARE / 2;
        assertEquals(State.IDLE, Thermostat.next(State.HEATING, TARGET - near, TARGET, BAND), "heating, nearly there");
        assertEquals(State.IDLE, Thermostat.next(State.COOLING, TARGET + near, TARGET, BAND), "cooling, nearly there");
        assertEquals(State.HEATING, Thermostat.next(State.HEATING, TARGET - near, TARGET, 0), "no band");
        // Overshot past the far side of the band, it turns round rather than idling.
        assertEquals(State.COOLING, Thermostat.next(State.HEATING, TARGET + BAND + 1, TARGET, BAND), "overshot up");
        assertEquals(State.HEATING, Thermostat.next(State.COOLING, TARGET - BAND - 1, TARGET, BAND), "overshot down");
    }

    /** No reading is idle, not a guess. */
    @Test
    void cont_165_no_reading_is_idle() {
        assertEquals(State.IDLE, Thermostat.next(State.HEATING, Double.NaN, TARGET, BAND));
    }

    /** A target stays within the heater's and air conditioner's setpoints, 73–573 K, and NaN stays off. */
    @Test
    void cont_165_the_target_is_clamped() {
        assertEquals(73, Thermostat.clampTarget(0));
        assertEquals(573, Thermostat.clampTarget(10_000));
        assertEquals(300, Thermostat.clampTarget(300));
        assertTrue(Double.isNaN(Thermostat.clampTarget(Double.NaN)));
    }

    /** Switched on, it starts at the middle of the preset's safe temperatures: 293 K for Human. */
    @Test
    void cont_165_it_starts_at_the_presets_middle() {
        assertEquals(293, Thermostat.startingTarget(ControllerPreset.HUMAN));
        assertEquals(Thermostat.DEFAULT_TARGET, Thermostat.startingTarget(ControllerPreset.VACUUM), "no bounds");
    }

    /** Devices share the need evenly, each at most what it moves in a step. */
    @Test
    void cont_166_devices_share_the_need() {
        assertEquals(1000, Thermostat.share(3000, 3, 5000));
        assertEquals(5000, Thermostat.share(30_000, 3, 5000), "capped at a device's most");
        assertEquals(0, Thermostat.share(0, 3, 5000));
        assertEquals(0, Thermostat.share(3000, 0, 5000));
    }
}
