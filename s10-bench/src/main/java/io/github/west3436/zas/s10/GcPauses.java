package io.github.west3436.zas.s10;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

/**
 * The garbage collector's pauses, so the report can say which slow ticks had one in them: a pause
 * that falls inside a timed call counts in that call's time. Every collector's bean is heard, G1's
 * "G1 Concurrent GC" too: what it reports are the Remark and Cleanup pauses, which stop every Java
 * thread, not the concurrent marking between them. Times are {@link System#nanoTime} to the
 * millisecond, as the collector reports them.
 */
final class GcPauses {
    private static final ConcurrentLinkedQueue<long[]> PAUSES = new ConcurrentLinkedQueue<>();
    // Each collector's pauses: {count, nanoseconds}.
    private static final Map<String, long[]> BY_COLLECTOR = new ConcurrentHashMap<>();
    private static long jvmStartNanos;

    private GcPauses() {}

    /** Starts listening. */
    static void install() {
        jvmStartNanos = System.nanoTime() - ManagementFactory.getRuntimeMXBean().getUptime() * 1_000_000L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gc instanceof NotificationEmitter emitter)) {
                continue;
            }
            String collector = gc.getName();
            emitter.addNotificationListener(
                    (n, handback) -> {
                        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) {
                            return;
                        }
                        var info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData())
                                .getGcInfo();
                        record(collector, info.getStartTime(), info.getEndTime());
                    },
                    null,
                    null);
        }
    }

    /** A pause from {@code collector}'s bean, in milliseconds of the JVM's uptime. */
    private static void record(String collector, long startMillis, long endMillis) {
        long start = jvmStartNanos + startMillis * 1_000_000L;
        long end = jvmStartNanos + endMillis * 1_000_000L;
        PAUSES.add(new long[] {start, end});
        BY_COLLECTOR.merge(collector, new long[] {1, end - start}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
    }

    /** How many pauses a collector's bean reported since {@link #install}. */
    static long count(String collector) {
        long[] c = BY_COLLECTOR.get(collector);
        return c == null ? 0 : c[0];
    }

    /** How many pauses were seen, and their time, in all and by collector. */
    static String summary() {
        long ns = 0;
        for (long[] p : PAUSES) {
            ns += p[1] - p[0];
        }
        List<String> by = new ArrayList<>();
        new TreeMap<>(BY_COLLECTOR)
                .forEach((k, v) -> by.add(String.format("%s %,d, %,d ms", k, v[0], v[1] / 1_000_000L)));
        return String.format("%,d, %,d ms", PAUSES.size(), ns / 1_000_000L)
                + (by.isEmpty() ? "" : " (" + String.join("; ", by) + ")");
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
