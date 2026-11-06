package io.github.west3436.zas.api;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * How often a fault in third-party code is logged: at most once per source per minute (API-062,
 * NFR-013). ZAS³ and its content mod catch what another mod's code throws, skip that code for the
 * tick, and ask here before logging it, so code that throws every tick can't fill the log.
 *
 * <p>A source is any text that names the code at fault, such as {@code "Ambient provider
 * mymod:moon"}. The record of sources forgets each one once its minute is up, and never holds more
 * than 1,024. If more than that fail within one minute, the extra ones go unlogged until there is
 * room.
 *
 * <p>This only decides what gets logged, so it can't affect a result. It needs nothing from ZAS³ and
 * works the same without it.
 */
public final class ThirdPartyFaults {
    private static final long MINUTE_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final int MOST_SOURCES = 1024;

    private static final AtomicLong ADVANCED_NANOS = new AtomicLong();
    private static final Limit LIMIT =
            new Limit(() -> System.nanoTime() + ADVANCED_NANOS.get(), MINUTE_NANOS, MOST_SOURCES);

    private ThirdPartyFaults() {}

    /**
     * Whether to log a fault now, which also counts as logging it: this returns true for a source at
     * most once a minute.
     *
     * <p>Thread: any; a code reaction's fault is reported from the atmos worker.
     *
     * @param source names the code at fault
     * @return true if the caller should log this fault
     */
    @AnyThread
    public static boolean shouldLog(String source) {
        return LIMIT.allows(Objects.requireNonNull(source, "source"));
    }

    /**
     * Moves this class's clock forward, so a GameTest can check what happens a minute later without
     * waiting for it. Nothing else reads this clock. Not for use outside tests.
     *
     * <p>Thread: any.
     *
     * @param by how far to move it
     */
    @AnyThread
    public static void advanceClockForTest(Duration by) {
        ADVANCED_NANOS.addAndGet(by.toNanos());
    }

    /** The limit itself, on a clock of its own so a unit test can run it. */
    static final class Limit {
        private final LongSupplier clock;
        private final long intervalNanos;
        private final int mostSources;
        /** When each source was last logged, oldest first, since an entry is only ever added at the end. */
        private final Map<String, Long> logged = new LinkedHashMap<>();

        Limit(LongSupplier clock, long intervalNanos, int mostSources) {
            this.clock = clock;
            this.intervalNanos = intervalNanos;
            this.mostSources = mostSources;
        }

        synchronized boolean allows(String source) {
            long now = clock.getAsLong();
            for (Iterator<Long> oldest = logged.values().iterator(); oldest.hasNext(); ) {
                if (now - oldest.next() < intervalNanos) {
                    break;
                }
                oldest.remove();
            }
            if (logged.containsKey(source) || logged.size() >= mostSources) {
                return false;
            }
            logged.put(source, now);
            return true;
        }

        synchronized int sources() {
            return logged.size();
        }
    }
}
