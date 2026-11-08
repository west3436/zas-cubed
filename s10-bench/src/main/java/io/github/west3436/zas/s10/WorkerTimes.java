package io.github.west3436.zas.s10;

import io.github.west3436.zas.engine.scheduler.Phase;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

/**
 * Each atmos tick's time on the worker (NFR-005), whole and by phase: a phase listener that passes
 * everything on to the profiler's (OPS-004) and keeps its own copy of every finished tick, where
 * the profiler keeps averages. A phase lasts from its {@code enter} to the next one's, as the
 * profiler counts it.
 *
 * <p>Each tick also keeps its worker thread's CPU time, beside its wall time, so a tick slowed by
 * other work on the machine can be told from one that did more work. A tick runs on one thread, from
 * its first {@code enter} to {@code finished}. Windows counts CPU time in steps of about 15.6 ms.
 */
final class WorkerTimes implements Phase.Listener {
    static final Phase[] PHASES = Phase.values();
    /** Index of the whole tick in a row, after the phases. */
    static final int WHOLE = PHASES.length;
    /** Index of the tick's start, {@link System#nanoTime}, after the whole tick. */
    static final int START = WHOLE + 1;
    /** Index of the tick's CPU time on its thread, after its start; -1 if the JVM doesn't measure it. */
    static final int CPU = START + 1;

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final Phase.Listener inner;
    private final LongSupplier wall;
    private final LongSupplier cpu;
    private final long[] scratch = new long[CPU + 1];
    private Phase current;
    private long phaseStart;
    private long tickStart;
    private long cpuStart;
    private final ConcurrentLinkedQueue<long[]> finished = new ConcurrentLinkedQueue<>();

    WorkerTimes(Phase.Listener inner) {
        this(inner, System::nanoTime, WorkerTimes::threadCpu);
    }

    /**
     * @param wall a clock in nanoseconds
     * @param cpu the calling thread's CPU time in nanoseconds, or -1 when it isn't measured
     */
    WorkerTimes(Phase.Listener inner, LongSupplier wall, LongSupplier cpu) {
        this.inner = inner;
        this.wall = wall;
        this.cpu = cpu;
    }

    /** The calling thread's CPU time, in nanoseconds, or -1 if the JVM doesn't measure it. */
    static long threadCpu() {
        return THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : -1;
    }

    @Override
    public void enter(Phase phase) {
        long now = wall.getAsLong();
        if (current == null) {
            java.util.Arrays.fill(scratch, 0);
            tickStart = now;
            scratch[START] = now;
            cpuStart = cpu.getAsLong();
        } else {
            scratch[current.ordinal()] = now - phaseStart;
        }
        current = phase;
        phaseStart = now;
        inner.enter(phase);
    }

    @Override
    public void finished() {
        inner.finished();
        long now = wall.getAsLong();
        long cpuNow = cpu.getAsLong();
        if (current != null) {
            scratch[current.ordinal()] = now - phaseStart;
        }
        scratch[WHOLE] = now - tickStart;
        scratch[CPU] = cpuStart < 0 || cpuNow < 0 ? -1 : cpuNow - cpuStart;
        current = null;
        finished.add(scratch.clone());
    }

    /** The ticks finished since the last call, oldest first. Any thread. */
    List<long[]> drain() {
        List<long[]> out = new ArrayList<>();
        for (long[] row; (row = finished.poll()) != null; ) {
            out.add(row);
        }
        return out;
    }
}
