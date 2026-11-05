package io.github.west3436.zas.atmos.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentMode;
import org.junit.jupiter.api.Test;

/** What a client shows for a vent or a scrubber, from its mode and its activity level (CLIENT-019). */
class DeviceJetsTest {
    private static final double EPS = 1e-9;

    @Test
    void client_019_a_vent_blowing_blows_out() {
        assertEquals(DeviceActivity.WORKING, DeviceJets.vent(VentMode.BLOW, DeviceActivity.WORKING), EPS);
        assertEquals(DeviceActivity.HARD, DeviceJets.vent(VentMode.BLOW, DeviceActivity.HARD), EPS);
    }

    @Test
    void client_019_a_vent_siphoning_draws_in() {
        assertEquals(-DeviceActivity.WORKING, DeviceJets.vent(VentMode.SIPHON, DeviceActivity.WORKING), EPS);
        assertEquals(-DeviceActivity.HARD, DeviceJets.vent(VentMode.SIPHON, DeviceActivity.HARD), EPS);
    }

    @Test
    void client_019_a_scrubber_always_draws_in() {
        for (ScrubberMode mode : new ScrubberMode[] {ScrubberMode.SCRUB, ScrubberMode.SIPHON, ScrubberMode.PANIC}) {
            assertEquals(-DeviceActivity.WORKING, DeviceJets.scrubber(mode, DeviceActivity.WORKING), EPS, mode.name());
            assertEquals(-DeviceActivity.HARD, DeviceJets.scrubber(mode, DeviceActivity.HARD), EPS, mode.name());
        }
    }

    @Test
    void client_019_nothing_shows_below_working() {
        for (int level : new int[] {DeviceActivity.IDLE, DeviceActivity.TRICKLE}) {
            for (VentMode mode : VentMode.values()) {
                assertEquals(0, DeviceJets.vent(mode, level), EPS, mode + " at " + level);
            }
            for (ScrubberMode mode : ScrubberMode.values()) {
                assertEquals(0, DeviceJets.scrubber(mode, level), EPS, mode + " at " + level);
            }
        }
    }

    /** The same constant starts a vent's or scrubber's loop, so this is where its sound starts too. */
    @Test
    void client_019_a_jet_starts_at_the_lowest_level_that_plays_a_loop() {
        for (int level = DeviceActivity.IDLE; level <= DeviceActivity.HARD; level++) {
            boolean plays = level >= DeviceJets.MIN_LEVEL;
            assertEquals(plays, DeviceJets.vent(VentMode.BLOW, level) != 0, "a vent at " + level);
            assertEquals(plays, DeviceJets.vent(VentMode.SIPHON, level) != 0, "a siphoning vent at " + level);
            assertEquals(plays, DeviceJets.scrubber(ScrubberMode.SCRUB, level) != 0, "a scrubber at " + level);
        }
    }

    @Test
    void client_019_a_device_switched_off_shows_nothing_whatever_the_table_says() {
        // Its block state and its level come by different packets, so for a moment they can disagree.
        assertEquals(0, DeviceJets.vent(VentMode.OFF, DeviceActivity.HARD), EPS);
        assertEquals(0, DeviceJets.scrubber(ScrubberMode.OFF, DeviceActivity.HARD), EPS);
    }
}
