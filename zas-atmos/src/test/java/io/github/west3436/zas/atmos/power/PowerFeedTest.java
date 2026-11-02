package io.github.west3436.zas.atmos.power;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Sharing a controller's buffer by priority (CONT-064). */
class PowerFeedTest {
    private static PowerFeed.Demand d(PowerPriority p, long cost) {
        return new PowerFeed.Demand(p, cost);
    }

    @Test
    void cont_064_everyone_is_powered_when_there_is_enough() {
        PowerFeed.Plan plan = PowerFeed.plan(
                1000,
                List.of(
                        d(PowerPriority.AUXILIARY, 400),
                        d(PowerPriority.LIFE_SUPPORT, 200),
                        d(PowerPriority.GAS_HANDLING, 400)));
        assertArrayEquals(new boolean[] {true, true, true}, plan.powered());
        assertEquals(1000, plan.spent());
    }

    @Test
    void cont_064_life_support_first_and_lower_groups_stop_when_a_group_misses_out() {
        // 500 FE: both vents (200 each) run; the pump (400) misses out, so the cheap heater (50) gets nothing.
        PowerFeed.Plan plan = PowerFeed.plan(
                500,
                List.of(
                        d(PowerPriority.AUXILIARY, 50),
                        d(PowerPriority.GAS_HANDLING, 400),
                        d(PowerPriority.LIFE_SUPPORT, 200),
                        d(PowerPriority.LIFE_SUPPORT, 200)));
        assertArrayEquals(new boolean[] {false, false, true, true}, plan.powered());
        assertEquals(400, plan.spent());
    }

    @Test
    void cont_064_within_a_group_the_parts_that_fit_run_and_parts_drawing_nothing_always_run() {
        PowerFeed.Plan plan = PowerFeed.plan(
                300,
                List.of(
                        d(PowerPriority.LIFE_SUPPORT, 400),
                        d(PowerPriority.LIFE_SUPPORT, 200),
                        d(PowerPriority.GAS_HANDLING, 100),
                        d(PowerPriority.AUXILIARY, 0)));
        assertArrayEquals(new boolean[] {false, true, false, true}, plan.powered());
        assertEquals(200, plan.spent());
        assertArrayEquals(
                new boolean[] {false},
                PowerFeed.plan(0, List.of(d(PowerPriority.LIFE_SUPPORT, 1))).powered());
    }
}
