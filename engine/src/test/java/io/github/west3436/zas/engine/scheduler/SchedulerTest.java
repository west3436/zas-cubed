package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The scheduler's timing and phase rules (CORE-040, CORE-041, CORE-046, CORE-048). */
class SchedulerTest {

    /** Every atmos tick runs its phases once each, in CORE-040's order (CORE-040). */
    @Test
    void core_040_phases_run_in_order() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        List<Phase> seen = new ArrayList<>();
        sim.engine.setPhaseListener(seen::add);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 10);
        sim.steps(3);
        Phase[] one = Phase.values();
        assertEquals(3 * one.length, seen.size());
        for (int t = 0; t < 3; t++) {
            assertArrayEquals(
                    one, seen.subList(t * one.length, (t + 1) * one.length).toArray());
        }
        assertEquals(
                List.of(
                        Phase.RESTORE,
                        Phase.GEOMETRY,
                        Phase.BOUNDARIES,
                        Phase.GAS_OPERATIONS,
                        Phase.EXCHANGE,
                        Phase.HEAT,
                        Phase.HOTSPOTS,
                        Phase.ZONE_UPDATES,
                        Phase.GARBAGE_COLLECTION),
                List.of(one));
    }

    /**
     * The default period is 1 s (20 game ticks) with results applied half a period later; other
     * periods are honoured, and an apply delay not shorter than the period is refused (CORE-041,
     * architecture O-7).
     */
    @Test
    void core_041_period_defaults_to_one_second_and_is_configurable() {
        assertEquals(20, SchedulerSettings.DEFAULT.periodTicks());
        assertEquals(1.0, SchedulerSettings.DEFAULT.periodSeconds());
        assertEquals(10, SchedulerSettings.DEFAULT.applyDelayTicks());
        assertEquals(1.0, EngineSettings.DEFAULT.exchange().periodSeconds());

        SchedulerSettings half = new SchedulerSettings(10, 5);
        assertEquals(0.5, half.periodSeconds());
        assertTrue(half.closesAt(30));
        assertTrue(!half.closesAt(35));
        assertEquals(3, half.atmosTick(35));
        assertEquals(0.5, EngineSettings.DEFAULT.withPeriodTicks(10).exchange().periodSeconds());
        assertEquals(2.0, EngineSettings.DEFAULT.withPeriodTicks(40).exchange().periodSeconds());

        assertThrows(IllegalArgumentException.class, () -> new SchedulerSettings(20, 20));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerSettings(20, 0));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerSettings(1, 0));
    }

    /**
     * Two rooms at about 100 and 50 kPa, joined by one opening, are run for 10 s at periods of
     * 0.5, 1 and 2 s: rates are per second, so the states agree (CORE-046).
     */
    @Test
    void core_046_rates_are_per_second() {
        double[] at05 = pressuresAfterTenSeconds(10);
        double[] at1 = pressuresAfterTenSeconds(20);
        double[] at2 = pressuresAfterTenSeconds(40);
        for (int i = 0; i < 2; i++) {
            assertEquals(at1[i], at05[i], 1e-9 * at1[i], "0.5 s vs 1 s, room " + i);
            assertEquals(at1[i], at2[i], 1e-9 * at1[i], "2 s vs 1 s, room " + i);
        }
        assertNotEquals(at1[0], at1[1], 1e-3, "the rooms equalized, so the check proves nothing");
    }

    private static double[] pressuresAfterTenSeconds(int periodTicks) {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.settings = sim.settings.withPeriodTicks(periodTicks);
        // Two 3×3×3 rooms with a shared wall at x = 4.
        sim.world.room(1, 64, 1, 3, 66, 3);
        sim.world.room(5, 64, 1, 7, 66, 3);
        double n = 100 * 27_000 / (io.github.west3436.zas.engine.gas.GasConstants.R * Sim.ROOM_T);
        sim.add(2, 65, 2, 0, n);
        sim.add(6, 65, 2, 0, n / 2);
        sim.step();
        sim.world.set(4, 65, 2, TestWorld.AIR);
        int steps = (int) Math.round(10 / SchedulerSettings.seconds(periodTicks));
        sim.steps(steps);
        ZoneView a = sim.zoneAt(2, 65, 2);
        ZoneView b = sim.zoneAt(6, 65, 2);
        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a.id(), b.id());
        assertEquals(
                1,
                sim.engine
                        .graph()
                        .edgeBetween(
                                sim.engine.graph().zone(a.id()),
                                sim.engine.graph().zone(b.id()))
                        .connections());
        return new double[] {a.moles(O2) / a.volume(), b.moles(O2) / b.volume()};
    }

    /**
     * In each atmos tick every operation is processed exactly once, including operations queued
     * while a tick is in flight, and every awake edge exchanges exactly once (CORE-048).
     */
    @Test
    void core_048_each_item_processed_once_per_tick() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(10, 64, 0, 12, 66, 2);
        List<Long> seqs = new ArrayList<>();
        seqs.add(sim.add(1, 65, 1, 0, 50));
        seqs.add(sim.add(11, 65, 1, 0, 50));
        // Mid-tick: queued after the batch closed, before it is applied.
        sim.betweenCloseAndApply = s -> {
            seqs.add(s.add(1, 65, 1, 1, 0));
            seqs.add(s.add(50, 70, 50, 1, 0));
            seqs.add(s.remove(11, 65, 1, 0, 1));
        };
        sim.step();
        sim.betweenCloseAndApply = s -> {};
        // Open both rooms to the air: two ambient edges.
        sim.world.set(3, 65, 1, TestWorld.AIR);
        sim.world.set(13, 65, 1, TestWorld.AIR);
        sim.step();
        for (int t = 0; t < 5; t++) {
            long before = sim.engine.graph().exchangeCount();
            int awake = 0;
            for (var e : sim.engine.graph().edges()) {
                if (!e.isSleeping()) {
                    awake++;
                }
            }
            sim.step();
            assertEquals(awake, sim.engine.graph().exchangeCount() - before, "exchanges in tick " + sim.tick);
        }
        for (long seq : seqs) {
            assertEquals(1, sim.completions.getOrDefault(seq, 0), "operation " + seq);
        }
        assertEquals(seqs.size(), sim.completions.size());
    }

    /** Ticks must advance, and a batch can't be submitted while one is in flight. */
    @Test
    void ticks_are_ordered_and_one_at_a_time() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.pipeline.close(1, sim.settings, () -> false);
        assertThrows(IllegalStateException.class, () -> sim.pipeline.close(2, sim.settings, () -> false));
        sim.pipeline.apply();
        assertThrows(IllegalStateException.class, () -> sim.pipeline.apply());
        assertThrows(
                IllegalArgumentException.class,
                () -> sim.engine.tick(Batch.of(1, Ambient.STANDARD_AIR, List.of(), List.of())));
    }

    /** An exception in the engine faults the dimension; the last snapshot stays (D-55). */
    @Test
    void engine_exception_faults_the_dimension() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        AtmosSnapshot before = sim.snapshot();
        sim.engine.setPhaseListener(p -> {
            if (p == Phase.EXCHANGE) {
                throw new IllegalStateException("boom");
            }
        });
        sim.pipeline.close(2, sim.settings, () -> false);
        DimensionPipeline.Applied a = sim.pipeline.apply();
        assertNotNull(a.fault());
        assertTrue(sim.scheduler.isFaulted());
        assertEquals(before, sim.snapshot());
        assertThrows(IllegalStateException.class, () -> sim.pipeline.close(3, sim.settings, () -> false));
        assertEquals(ExchangeSettings.DEFAULT, EngineSettings.DEFAULT.exchange());
    }
}
