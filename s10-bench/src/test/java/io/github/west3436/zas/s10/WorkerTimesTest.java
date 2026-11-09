package io.github.west3436.zas.s10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.scheduler.Phase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The bench's worker tick bookkeeping (NFR-005): wall time by phase, and CPU time beside it. */
class WorkerTimesTest {
    private long wall;
    private long cpu;
    private final List<String> passed = new ArrayList<>();

    private final Phase.Listener inner = new Phase.Listener() {
        @Override
        public void enter(Phase phase) {
            passed.add(phase.name());
        }

        @Override
        public void finished() {
            passed.add("finished");
        }
    };

    private WorkerTimes times() {
        return new WorkerTimes(inner, () -> wall, () -> cpu);
    }

    /** Each phase lasts until the next starts; the whole tick and its CPU time span all of them. */
    @Test
    void nfr_005_a_tick_keeps_its_phases_its_wall_time_and_its_cpu_time() {
        WorkerTimes t = times();
        wall = 1_000;
        cpu = 50;
        t.enter(Phase.RESTORE);
        wall = 1_300;
        cpu = 250;
        t.enter(Phase.GAS_OPERATIONS);
        wall = 2_000;
        cpu = 600;
        t.enter(Phase.EXCHANGE);
        wall = 2_100;
        cpu = 650;
        t.finished();

        List<long[]> rows = t.drain();
        assertEquals(1, rows.size());
        long[] r = rows.get(0);
        assertEquals(300, r[Phase.RESTORE.ordinal()]);
        assertEquals(700, r[Phase.GAS_OPERATIONS.ordinal()]);
        assertEquals(100, r[Phase.EXCHANGE.ordinal()]);
        assertEquals(0, r[Phase.HEAT.ordinal()], "a phase the tick didn't enter");
        assertEquals(1_100, r[WorkerTimes.WHOLE]);
        assertEquals(1_000, r[WorkerTimes.START]);
        assertEquals(600, r[WorkerTimes.CPU], "CPU time from the first phase to the end");
        assertEquals(List.of("RESTORE", "GAS_OPERATIONS", "EXCHANGE", "finished"), passed, "passed on");
        assertTrue(t.drain().isEmpty(), "each tick is drained once");
    }

    /** A thread descheduled for part of a tick: its wall time counts it, its CPU time doesn't. */
    @Test
    void nfr_005_cpu_time_leaves_out_time_off_the_cpu() {
        WorkerTimes t = times();
        wall = 0;
        cpu = 0;
        t.enter(Phase.RESTORE);
        wall = 331_000_000;
        cpu = 160_000_000;
        t.finished();
        wall = 400_000_000;
        cpu = 170_000_000;
        t.enter(Phase.RESTORE);
        wall = 410_000_000;
        cpu = 180_000_000;
        t.finished();

        List<long[]> rows = t.drain();
        assertEquals(2, rows.size(), "oldest first");
        assertEquals(331_000_000, rows.get(0)[WorkerTimes.WHOLE]);
        assertEquals(160_000_000, rows.get(0)[WorkerTimes.CPU]);
        assertEquals(10_000_000, rows.get(1)[WorkerTimes.WHOLE]);
        assertEquals(10_000_000, rows.get(1)[WorkerTimes.CPU], "the second tick starts its own count");
    }

    /** Where the JVM doesn't measure thread CPU time, the row says so with -1, not 0. */
    @Test
    void nfr_005_cpu_time_unmeasured_is_minus_one() {
        WorkerTimes t = times();
        cpu = -1;
        t.enter(Phase.RESTORE);
        wall = 5;
        t.finished();
        assertEquals(-1, t.drain().get(0)[WorkerTimes.CPU]);
    }
}
