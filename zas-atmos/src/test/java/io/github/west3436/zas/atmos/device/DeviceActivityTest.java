package io.github.west3436.zas.atmos.device;

import static io.github.west3436.zas.atmos.device.DeviceActivity.HARD;
import static io.github.west3436.zas.atmos.device.DeviceActivity.IDLE;
import static io.github.west3436.zas.atmos.device.DeviceActivity.TRICKLE;
import static io.github.west3436.zas.atmos.device.DeviceActivity.WORKING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** How hard a device reads as working (DATA-017): the scale's thresholds and its hysteresis. */
class DeviceActivityTest {
    private static int step(DeviceActivity a, double moles) {
        a.moved(moles);
        return a.finishStep();
    }

    @Test
    void data_017_a_new_device_is_idle() {
        assertEquals(IDLE, new DeviceActivity().level());
        assertEquals(IDLE, new DeviceActivity().finishStep());
    }

    @Test
    void data_017_each_threshold_either_side() {
        double[][] cases = {
            {0, IDLE},
            {1e-4, IDLE}, // the least a device moves at all
            {0.049, IDLE},
            {0.05, TRICKLE},
            {0.99, TRICKLE},
            {1, WORKING},
            {4.99, WORKING},
            {5, HARD},
            {1_000, HARD},
        };
        for (double[] c : cases) {
            assertEquals((int) c[1], step(new DeviceActivity(), c[0]), () -> c[0] + " mol");
        }
    }

    /** What the round's owner asked for, with the rates the devices have at 1 atm and 293 K. */
    @Test
    void data_017_big_changes_read_high_and_a_trickle_doesnt() {
        double oneKpaIn500Litres = 1 * 500 / (8.314 * 293);
        assertEquals(TRICKLE, step(new DeviceActivity(), oneKpaIn500Litres), "a scrubber on 1 kPa of CO2");
        assertEquals(IDLE, step(new DeviceActivity(), 0.04 * 500 / (8.314 * 293)), "a scrubber on trace CO2");
        assertEquals(HARD, step(new DeviceActivity(), 101.325 * 200 / (8.314 * 293)), "a vent filling a vacuum");
        assertEquals(HARD, step(new DeviceActivity(), 101.325 * 500 / (8.314 * 293)), "a scrubber siphoning");
        assertEquals(HARD, step(new DeviceActivity(), 101.325 * 1_000 / (8.314 * 293)), "a scrubber in panic");
    }

    @Test
    void data_017_a_step_adds_up_what_it_moved() {
        DeviceActivity a = new DeviceActivity();
        a.moved(0.6);
        a.moved(0.6);
        assertEquals(WORKING, a.finishStep());
    }

    @Test
    void data_017_a_negative_or_nan_amount_counts_for_nothing() {
        DeviceActivity a = new DeviceActivity();
        a.moved(-5);
        a.moved(Double.NaN);
        assertEquals(IDLE, a.finishStep());
    }

    @Test
    void data_017_a_step_doesnt_carry_into_the_next() {
        DeviceActivity a = new DeviceActivity();
        assertEquals(TRICKLE, step(a, 0.03 + 0.03));
        assertEquals(TRICKLE, step(a, 0));
        assertEquals(IDLE, step(a, 0));
    }

    @Test
    void data_017_a_level_rises_at_once() {
        DeviceActivity a = new DeviceActivity();
        assertEquals(HARD, step(a, 8));
    }

    @Test
    void data_017_a_level_holds_between_its_bounds_however_long() {
        DeviceActivity a = new DeviceActivity();
        step(a, 5);
        for (int t = 0; t < 100; t++) {
            assertEquals(HARD, step(a, 2.5), "just above where hard falls, step " + t);
        }
        step(a, 0.5);
        step(a, 0.5);
        assertEquals(WORKING, a.level());
        for (int t = 0; t < 100; t++) {
            assertEquals(WORKING, step(a, 0.5), "just above where working falls, step " + t);
        }
    }

    @Test
    void data_017_a_level_falls_only_after_steps_running_below_its_bound() {
        DeviceActivity a = new DeviceActivity();
        step(a, 8);
        assertEquals(HARD, step(a, 2), "one low step");
        assertEquals(WORKING, step(a, 2), "two low steps running");
        assertEquals(WORKING, step(a, 0.4), "one low step");
        assertEquals(TRICKLE, step(a, 0.4), "two low steps running");
        assertEquals(TRICKLE, step(a, 0.02), "one low step");
        assertEquals(IDLE, step(a, 0.02), "two low steps running");
    }

    @Test
    void data_017_a_device_that_stops_falls_straight_to_idle_after_the_hold() {
        DeviceActivity a = new DeviceActivity();
        step(a, 8);
        assertEquals(HARD, step(a, 0));
        assertEquals(IDLE, step(a, 0));
    }

    @Test
    void data_017_a_device_that_moves_gas_every_other_step_doesnt_flicker() {
        DeviceActivity a = new DeviceActivity();
        step(a, 8);
        for (int t = 0; t < 100; t++) {
            assertEquals(HARD, step(a, t % 2 == 0 ? 0 : 8), "step " + t);
        }
        DeviceActivity b = new DeviceActivity();
        step(b, 0.5);
        for (int t = 0; t < 100; t++) {
            assertEquals(TRICKLE, step(b, t % 2 == 0 ? 0 : 0.5), "step " + t);
        }
    }

    @Test
    void data_017_a_rate_around_a_threshold_doesnt_flicker() {
        DeviceActivity a = new DeviceActivity();
        Random random = new Random(1);
        int changes = 0;
        int last = step(a, 5);
        for (int t = 0; t < 1_000; t++) {
            int level = step(a, 5 * (0.8 + random.nextDouble() * 0.4)); // 4 to 6 mol
            if (level != last) {
                changes++;
            }
            last = level;
        }
        assertEquals(0, changes, "the level changed");
    }

    /**
     * Whatever it reads, a level that falls waits out its hold, so it can't change at every step. The
     * worst case is a climb from idle to hard, one level a step, then a fall all the way: 4 changes in
     * 5 steps.
     */
    @Test
    void data_017_no_reading_makes_the_level_change_at_every_step() {
        DeviceActivity a = new DeviceActivity();
        double[] climbAndFall = {0.06, 1.5, 6, 0, 0};
        int changes = 0;
        int last = a.level();
        for (int t = 0; t < 1_000; t++) {
            int level = step(a, climbAndFall[t % climbAndFall.length]);
            changes += level != last ? 1 : 0;
            last = level;
        }
        assertEquals(800, changes, "the worst case");

        Random random = new Random(2);
        DeviceActivity b = new DeviceActivity();
        double[] amounts = {0, 0.01, 0.06, 0.6, 1.5, 3, 6, 40};
        changes = 0;
        last = b.level();
        int steps = 10_000;
        for (int t = 0; t < steps; t++) {
            int level = step(b, amounts[random.nextInt(amounts.length)]);
            changes += level != last ? 1 : 0;
            last = level;
        }
        assertTrue(changes <= steps * 4 / 5, "changes: " + changes + " in " + steps);
    }

    @Test
    void data_017_a_device_that_moves_no_gas_reads_as_working_while_it_runs() {
        DeviceActivity a = new DeviceActivity();
        assertEquals(WORKING, step(a, DeviceActivity.RUNNING_MOLES));
        for (int t = 0; t < 10; t++) {
            assertEquals(WORKING, step(a, DeviceActivity.RUNNING_MOLES), "step " + t);
        }
    }

    @Test
    void data_017_a_loop_is_louder_at_each_level_and_silent_when_idle() {
        assertEquals(0f, DeviceActivity.volume(IDLE));
        assertTrue(DeviceActivity.volume(TRICKLE) > 0);
        assertTrue(DeviceActivity.volume(WORKING) > DeviceActivity.volume(TRICKLE));
        assertTrue(DeviceActivity.volume(HARD) > DeviceActivity.volume(WORKING));
        assertEquals(1f, DeviceActivity.volume(HARD));
    }

    @Test
    void data_017_stopping_drops_to_idle_at_once_and_ends_the_step() {
        DeviceActivity a = new DeviceActivity();
        step(a, 8);
        a.moved(8);
        a.stopNow();
        assertEquals(IDLE, a.finishStep());
        assertEquals(IDLE, a.level());
        assertEquals(IDLE, a.finishStep(), "what it moved before it stopped isn't carried");
        assertEquals(HARD, step(a, 8), "it can start again");
    }
}
