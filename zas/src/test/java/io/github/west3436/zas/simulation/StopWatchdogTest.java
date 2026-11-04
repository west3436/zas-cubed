package io.github.west3436.zas.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** A stop that takes too long is reported, and one that doesn't is not (NFR-015). */
class StopWatchdogTest {
    private final List<String> reports = new CopyOnWriteArrayList<>();

    private StopWatchdog watchdog(Duration period) {
        return new StopWatchdog(period, 2, () -> "  the levels\n", reports::add);
    }

    private static void awaitEnd(StopWatchdog w) throws InterruptedException {
        w.thread().join(10_000);
        assertFalse(w.thread().isAlive(), "the watchdog's thread should have ended");
    }

    @Test
    void nfr_015_a_stop_that_finishes_in_time_logs_nothing() throws InterruptedException {
        StopWatchdog w = watchdog(Duration.ofHours(1));
        w.start();
        w.finish();
        awaitEnd(w);
        assertTrue(reports.isEmpty(), () -> "reports: " + reports);
    }

    @Test
    void nfr_015_a_stuck_stop_is_reported_a_limited_number_of_times() throws InterruptedException {
        StopWatchdog w = watchdog(Duration.ofMillis(10));
        w.start();
        awaitEnd(w); // never finished, as in a stuck stop: it ends by itself
        assertEquals(2, reports.size());
        assertTrue(reports.get(0).contains("report 1 of 2"), reports.get(0));
        assertTrue(reports.get(1).contains("report 2 of 2"), reports.get(1));
    }

    @Test
    void nfr_015_a_report_says_what_the_stop_is_waiting_for() throws InterruptedException {
        StopWatchdog w = watchdog(Duration.ofMillis(10));
        w.start();
        awaitEnd(w);
        String report = reports.getFirst();
        assertTrue(report.contains("the levels"), report);
        assertTrue(report.contains('"' + Thread.currentThread().getName() + '"'), report);
        assertTrue(report.contains(StopWatchdogTest.class.getName() + ".awaitEnd"), report);
        assertTrue(report.contains("org.junit.platform.launcher"), "every frame, not just the top ones: " + report);
    }

    @Test
    void nfr_015_a_failure_to_read_the_levels_still_dumps_the_threads() throws InterruptedException {
        StopWatchdog w = new StopWatchdog(
                Duration.ofMillis(10),
                1,
                () -> {
                    throw new IllegalStateException("no levels");
                },
                reports::add);
        w.start();
        awaitEnd(w);
        String report = reports.getFirst();
        assertTrue(report.contains("no levels"), report);
        assertTrue(report.contains('"' + Thread.currentThread().getName() + '"'), report);
    }

    @Test
    void nfr_015_the_watchdog_cannot_keep_the_jvm_alive() {
        assertTrue(watchdog(Duration.ofHours(1)).thread().isDaemon());
    }
}
