package io.github.west3436.zas.s10;

import java.util.function.LongSupplier;

/**
 * A server tick's time, from the bench's first listener of the tick to its last: wall time, and the
 * server thread's CPU time beside it (NFR-002). A slow tick that took much less CPU time than wall time
 * had the thread stopped for the rest, by the collector, the JVM or the OS, rather than doing more work.
 * Windows counts CPU time in steps of about 15.6 ms. Server thread.
 */
final class TickTimes {
    private final LongSupplier wall;
    private final LongSupplier cpu;
    private long start;
    private long cpuStart;
    private long wallNanos;
    private long cpuNanos;

    TickTimes() {
        this(System::nanoTime, WorkerTimes::threadCpu);
    }

    /**
     * @param wall a clock in nanoseconds
     * @param cpu the calling thread's CPU time in nanoseconds, or -1 when it isn't measured
     */
    TickTimes(LongSupplier wall, LongSupplier cpu) {
        this.wall = wall;
        this.cpu = cpu;
    }

    /** The tick's first listener. */
    void start() {
        start = wall.getAsLong();
        cpuStart = cpu.getAsLong();
    }

    /** The tick's last listener. */
    void end() {
        long cpuNow = cpu.getAsLong();
        wallNanos = wall.getAsLong() - start;
        cpuNanos = cpuStart < 0 || cpuNow < 0 ? -1 : cpuNow - cpuStart;
    }

    /** The tick's start, on the wall clock. */
    long startNanos() {
        return start;
    }

    /** The last tick's wall time, from {@link #start} to {@link #end}. */
    long wallNanos() {
        return wallNanos;
    }

    /** The last tick's CPU time on its thread over the same span, or -1 if the JVM doesn't measure it. */
    long cpuNanos() {
        return cpuNanos;
    }
}
