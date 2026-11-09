package io.github.west3436.zas.s10;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/**
 * The collector's pauses the bench hears (NFR-002). Needs G1, with {@code System.gc()} starting a
 * concurrent cycle ({@code -XX:+ExplicitGCInvokesConcurrent}), as the bench's test task runs it.
 */
class GcPausesTest {
    private static final String CONCURRENT = "G1 Concurrent GC";

    /**
     * G1's Remark and Cleanup pauses stop every thread, and come from its "G1 Concurrent GC" bean,
     * which the bench didn't use to hear.
     */
    @Test
    void nfr_002_g1_remark_and_cleanup_pauses_are_heard() throws InterruptedException {
        assumeTrue(
                ManagementFactory.getGarbageCollectorMXBeans().stream()
                                .anyMatch(gc -> gc.getName().equals(CONCURRENT))
                        && ManagementFactory.getRuntimeMXBean()
                                .getInputArguments()
                                .contains("-XX:+ExplicitGCInvokesConcurrent"),
                "needs G1 with -XX:+ExplicitGCInvokesConcurrent");
        GcPauses.install();
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (GcPauses.count(CONCURRENT) < 2 && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(200);
        }
        assertTrue(
                GcPauses.count(CONCURRENT) >= 2,
                "a concurrent cycle's Remark and Cleanup pauses: " + GcPauses.summary());
    }
}
