package io.github.west3436.zas.s10;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

/**
 * The garbage collector's pauses, so the report can say which slow ticks had one in them: a pause
 * that falls inside a timed call counts in that call's time. Concurrent collection, which doesn't
 * stop the server thread, is left out. Times are {@link System#nanoTime} to the millisecond, as the
 * collector reports them.
 */
final class GcPauses {
    private static final ConcurrentLinkedQueue<long[]> PAUSES = new ConcurrentLinkedQueue<>();
    private static long jvmStartNanos;

    private GcPauses() {}

    /** Starts listening. */
    static void install() {
        jvmStartNanos = System.nanoTime() - ManagementFactory.getRuntimeMXBean().getUptime() * 1_000_000L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gc instanceof NotificationEmitter emitter) || gc.getName().contains("Concurrent")) {
                continue;
            }
            emitter.addNotificationListener(
                    (n, handback) -> {
                        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) {
                            return;
                        }
                        var info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData())
                                .getGcInfo();
                        PAUSES.add(new long[] {
                            jvmStartNanos + info.getStartTime() * 1_000_000L,
                            jvmStartNanos + info.getEndTime() * 1_000_000L
                        });
                    },
                    null,
                    null);
        }
    }

    /** How many pauses were seen, and their time. */
    static String summary() {
        long ns = 0;
        for (long[] p : PAUSES) {
            ns += p[1] - p[0];
        }
        return String.format("%,d, %,d ms", PAUSES.size(), ns / 1_000_000L);
    }

    /** The pause time inside {@code [from, to]}, in ns. For the report, once the run is over. */
    static long within(long from, long to) {
        long sum = 0;
        for (long[] p : PAUSES) {
            long start = Math.max(p[0], from);
            long end = Math.min(p[1], to);
            if (end > start) {
                sum += end - start;
            }
        }
        return sum;
    }
}
