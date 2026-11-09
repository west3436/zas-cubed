package io.github.west3436.zas.s10;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The bench's server tick bookkeeping (NFR-002): wall time, and the server thread's CPU time beside it. */
class TickTimesTest {
    private long wall;
    private long cpu;

    private TickTimes times() {
        return new TickTimes(() -> wall, () -> cpu);
    }

    /** Wall and CPU time both run from the tick's first listener to its last. */
    @Test
    void nfr_002_a_tick_keeps_its_start_its_wall_time_and_its_cpu_time() {
        TickTimes t = times();
        wall = 1_000;
        cpu = 40;
        t.start();
        wall = 4_000;
        cpu = 2_540;
        t.end();
        assertEquals(1_000, t.startNanos());
        assertEquals(3_000, t.wallNanos());
        assertEquals(2_500, t.cpuNanos());
    }

    /** A tick in which the thread was stopped: its wall time counts the stop, its CPU time doesn't. */
    @Test
    void nfr_002_cpu_time_leaves_out_a_stop() {
        TickTimes t = times();
        wall = 0;
        cpu = 0;
        t.start();
        wall = 37_300_000;
        cpu = 15_625_000;
        t.end();
        assertEquals(37_300_000, t.wallNanos());
        assertEquals(15_625_000, t.cpuNanos(), "one Windows step on the CPU in a 37.3 ms tick");

        wall = 50_000_000;
        t.start();
        wall = 51_000_000;
        cpu = 16_625_000;
        t.end();
        assertEquals(50_000_000, t.startNanos(), "the next tick starts its own count");
        assertEquals(1_000_000, t.wallNanos());
        assertEquals(1_000_000, t.cpuNanos());
    }

    /** Where the JVM doesn't measure thread CPU time, the tick says so with -1, not 0. */
    @Test
    void nfr_002_cpu_time_unmeasured_is_minus_one() {
        TickTimes t = times();
        cpu = -1;
        t.start();
        wall = 5;
        t.end();
        assertEquals(5, t.wallNanos());
        assertEquals(-1, t.cpuNanos());
    }
}
