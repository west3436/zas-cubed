package io.github.west3436.zas.atmos.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The device schedule's timing (CONT-006, CONT-072). */
class DeviceScheduleTest {
    private static final int PERIOD = 20;

    private static final class Probe implements DeviceSchedule.Member {
        final List<Long> steps = new ArrayList<>();
        final long[] clock;
        boolean ready = true;
        boolean ticking = true;
        int asked;

        Probe(long[] clock) {
            this.clock = clock;
        }

        @Override
        public boolean readyToStep() {
            asked++;
            return ready;
        }

        @Override
        public boolean ticking() {
            return ticking;
        }

        @Override
        public void step() {
            steps.add(clock[0]);
        }
    }

    private static void run(DeviceSchedule<Probe> s, long[] clock, long until) {
        while (clock[0] < until) {
            clock[0]++;
            s.tick(clock[0]);
        }
    }

    @Test
    void cont_006_each_member_steps_once_per_period() {
        long[] clock = {0};
        DeviceSchedule<Probe> s = new DeviceSchedule<>(PERIOD);
        List<Probe> probes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Probe p = new Probe(clock);
            probes.add(p);
            s.add(p, clock[0]);
        }
        run(s, clock, 10 * PERIOD);
        for (Probe p : probes) {
            assertEquals(10, p.steps.size(), "steps in 10 periods");
            for (int i = 1; i < p.steps.size(); i++) {
                assertEquals(PERIOD, p.steps.get(i) - p.steps.get(i - 1));
            }
        }
    }

    @Test
    void cont_006_members_are_spread_across_the_period() {
        long[] clock = {100};
        DeviceSchedule<Probe> s = new DeviceSchedule<>(PERIOD);
        List<Probe> probes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Probe p = new Probe(clock);
            probes.add(p);
            s.add(p, clock[0]);
        }
        run(s, clock, 100 + PERIOD);
        int[] perTick = new int[PERIOD];
        for (Probe p : probes) {
            assertEquals(1, p.steps.size());
            perTick[(int) (p.steps.get(0) % PERIOD)]++;
        }
        for (int n : perTick) {
            assertTrue(n == 2 || n == 3, "50 members over 20 ticks put 2 or 3 in each: " + n);
        }
    }

    @Test
    void cont_006_removed_members_never_step() {
        long[] clock = {0};
        DeviceSchedule<Probe> s = new DeviceSchedule<>(PERIOD);
        Probe a = new Probe(clock);
        Probe b = new Probe(clock);
        s.add(a, 0);
        s.add(b, 0);
        s.add(a, 0);
        assertEquals(List.of(a, b), s.members(), "adding twice adds once");
        run(s, clock, PERIOD);
        s.remove(a);
        assertFalse(s.contains(a));
        run(s, clock, 5 * PERIOD);
        assertEquals(1, a.steps.size());
        assertEquals(5, b.steps.size());
    }

    @Test
    void cont_072_a_member_that_is_not_ready_waits_and_keeps_its_new_phase() {
        long[] clock = {0};
        DeviceSchedule<Probe> s = new DeviceSchedule<>(PERIOD);
        Probe p = new Probe(clock);
        s.add(p, 0);
        run(s, clock, PERIOD);
        assertEquals(List.of(1L), p.steps);
        p.ready = false;
        run(s, clock, 30);
        assertEquals(1, p.steps.size(), "stepped while not ready");
        p.ready = true;
        s.tick(++clock[0]);
        assertEquals(List.of(1L, 31L), p.steps, "steps on the first game tick it is ready");
        run(s, clock, 31 + 3 * PERIOD);
        assertEquals(List.of(1L, 31L, 51L, 71L, 91L), p.steps, "keeps its new phase");
    }

    /**
     * A member whose chunk doesn't tick is passed over, without being asked whether it is ready, and
     * keeps its phase: it steps at the first of its due ticks after it ticks again.
     */
    @Test
    void cont_006_a_member_that_is_not_ticking_is_passed_over_and_keeps_its_phase() {
        long[] clock = {0};
        DeviceSchedule<Probe> s = new DeviceSchedule<>(PERIOD);
        Probe p = new Probe(clock);
        s.add(p, 0);
        run(s, clock, PERIOD);
        assertEquals(List.of(1L), p.steps);
        p.ticking = false;
        int asked = p.asked;
        run(s, clock, 90);
        assertEquals(List.of(1L), p.steps, "stepped while not ticking");
        assertEquals(asked, p.asked, "asked whether it was ready while not ticking");
        p.ticking = true;
        run(s, clock, 100);
        assertEquals(List.of(1L), p.steps, "stepped before its phase came round");
        run(s, clock, 141);
        assertEquals(List.of(1L, 101L, 121L, 141L), p.steps, "keeps its phase");
        p.ticking = false;
        s.stepAll();
        assertEquals(5, p.steps.size(), "stepAll steps it, ticking or not");
    }

    @Test
    void cont_006_step_all_steps_every_ready_member_once() {
        long[] clock = {0};
        DeviceSchedule<Probe> s = new DeviceSchedule<>(PERIOD);
        Probe a = new Probe(clock);
        Probe b = new Probe(clock);
        b.ready = false;
        s.add(a, 0);
        s.add(b, 0);
        s.stepAll();
        assertEquals(1, a.steps.size());
        assertEquals(0, b.steps.size());
    }

    @Test
    void a_period_below_one_is_refused() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceSchedule<Probe>(0));
    }
}
