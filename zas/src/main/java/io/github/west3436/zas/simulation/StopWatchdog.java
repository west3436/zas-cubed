package io.github.west3436.zas.simulation;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Says what a server stop is waiting for when it doesn't finish (NFR-015). The game's final chunk
 * loop logs nothing while it runs, so a stop stuck there leaves no trace of why.
 *
 * <p>From {@link ServerStoppingEvent} to {@link ServerStoppedEvent} a daemon thread waits. If the
 * stop outlasts {@link #PERIOD}, it logs each level's remaining chunk work and a dump of every
 * thread, up to {@link #MAX_REPORTS} times, and then ends. It only reads and logs: it never ends
 * the stop or the process, and it can't keep the JVM alive. A stop that finishes in time logs
 * nothing.
 */
public final class StopWatchdog {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long a stop runs before the first report, and the time between reports. */
    static final Duration PERIOD = Duration.ofMinutes(2);

    /** How many times one stop is reported. */
    static final int MAX_REPORTS = 3;

    private static volatile StopWatchdog current;

    private final Duration period;
    private final int maxReports;
    private final Supplier<String> levels;
    private final Consumer<String> log;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Thread thread;

    /**
     * @param period how long to wait before the first report and between reports
     * @param maxReports how many reports to make at most
     * @param levels describes the levels' remaining work; read on the watchdog's thread
     * @param log receives each report
     */
    StopWatchdog(Duration period, int maxReports, Supplier<String> levels, Consumer<String> log) {
        this.period = period;
        this.maxReports = maxReports;
        this.levels = levels;
        this.log = log;
        this.thread = new Thread(this::run, "zas-stop-watchdog");
        this.thread.setDaemon(true);
    }

    /** Registers the lifecycle listeners. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        // First and last, so that the other listeners' time counts too.
        gameBus.addListener(EventPriority.HIGHEST, (ServerStoppingEvent e) -> {
            finishCurrent();
            MinecraftServer server = e.getServer();
            StopWatchdog w = new StopWatchdog(PERIOD, MAX_REPORTS, () -> describeLevels(server), LOGGER::warn);
            current = w;
            w.start();
        });
        gameBus.addListener(EventPriority.LOWEST, (ServerStoppedEvent e) -> finishCurrent());
    }

    private static void finishCurrent() {
        StopWatchdog w = current;
        current = null;
        if (w != null) {
            w.finish();
        }
    }

    void start() {
        thread.start();
    }

    /** The stop has finished: ends the wait, with no report. Any thread. */
    void finish() {
        finished.countDown();
    }

    /** The watchdog's thread. For tests. */
    Thread thread() {
        return thread;
    }

    private void run() {
        long started = System.nanoTime();
        for (int report = 1; report <= maxReports; report++) {
            try {
                if (finished.await(period.toNanos(), TimeUnit.NANOSECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                return; // nothing interrupts this thread but the JVM going down
            }
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
            log.accept("The server stop has been running for " + seconds + " s and hasn't finished (report " + report
                    + " of " + maxReports + ").\nLevels:\n" + read(levels) + "\nThreads:\n"
                    + read(StopWatchdog::threadDump));
        }
    }

    /** A failure to read must not cost the rest of the report. */
    private static String read(Supplier<String> source) {
        try {
            return source.get();
        } catch (RuntimeException e) {
            return "  couldn't be read: " + e;
        }
    }

    /**
     * Each level's remaining chunk work. These counters aren't thread-safe, so what they say is
     * approximate; stuck work keeps saying the same thing from one report to the next.
     */
    private static String describeLevels(MinecraftServer server) {
        StringBuilder out = new StringBuilder();
        for (ServerLevel level : server.getAllLevels()) {
            ServerChunkCache chunks = level.getChunkSource();
            out.append("  ")
                    .append(level.dimension().location())
                    .append(": chunk work left ")
                    .append(chunks.chunkMap.hasWork())
                    .append(", loaded chunks ")
                    .append(chunks.getLoadedChunksCount())
                    .append(", chunk tasks waiting for the server thread ")
                    .append(chunks.getPendingTasksCount())
                    .append('\n');
        }
        return out.toString();
    }

    /** Every thread, with all its frames: {@link ThreadInfo#toString} stops at eight. */
    static String threadDump() {
        StringBuilder out = new StringBuilder();
        for (ThreadInfo t : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            out.append('"').append(t.getThreadName()).append("\" ").append(t.getThreadState());
            if (t.getLockName() != null) {
                out.append(" on ").append(t.getLockName());
            }
            if (t.getLockOwnerName() != null) {
                out.append(" held by \"").append(t.getLockOwnerName()).append('"');
            }
            out.append('\n');
            for (StackTraceElement frame : t.getStackTrace()) {
                out.append("    at ").append(frame).append('\n');
            }
        }
        return out.toString();
    }
}
