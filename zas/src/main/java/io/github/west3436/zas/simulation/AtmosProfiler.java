package io.github.west3436.zas.simulation;

import io.github.west3436.zas.engine.scheduler.Phase;
import java.util.Arrays;

/**
 * One dimension's timings for the profiler (OPS-004): how long each phase of the last atmos ticks
 * took on the worker, and how long closing and applying took on the server thread.
 *
 * <p>The engine calls {@link #listener} on the thread that runs the tick. Each phase lasts from
 * its {@link Phase.Listener#enter} to the next one's, the last one to {@link
 * Phase.Listener#finished}, so {@link Phase#GARBAGE_COLLECTION}'s time includes the invariant
 * checker and building the result. A finished tick's timings are published with one volatile
 * write and picked up on the server thread when the result is applied ({@link #applied}); the
 * rolling window of the last {@value #WINDOW} atmos ticks is the server thread's alone.
 */
public final class AtmosProfiler {
    /** Atmos ticks kept in the rolling window. */
    public static final int WINDOW = 100;

    private static final Phase[] PHASES = Phase.values();
    /** Index of the worker's whole tick in a timing row, after the phases. */
    static final int WORKER = PHASES.length;

    private static final int CLOSE = WORKER + 1;
    private static final int APPLY = WORKER + 2;
    private static final int COLUMNS = WORKER + 3;

    // Worker side: the tick in progress.
    private final long[] scratch = new long[WORKER + 1];
    private Phase current;
    private long phaseStart;
    private long tickStart;
    private volatile long[] finished;
    private volatile String workerThread = "";

    // Server side: the window.
    private final long[][] rows = new long[WINDOW][COLUMNS];
    private int next;
    private int count;
    private long pendingClose;

    /** Average and largest time of one row of the window, in ms. */
    public record Timing(double averageMs, double maxMs) {}

    /** The listener to give the engine. */
    public final Phase.Listener listener = new Phase.Listener() {
        @Override
        public void enter(Phase phase) {
            long now = System.nanoTime();
            if (current == null) {
                Arrays.fill(scratch, 0);
                tickStart = now;
            } else {
                scratch[current.ordinal()] = now - phaseStart;
            }
            if (phase == Phase.GEOMETRY) {
                workerThread = Thread.currentThread().getName();
            }
            current = phase;
            phaseStart = now;
        }

        @Override
        public void finished() {
            long now = System.nanoTime();
            if (current != null) {
                scratch[current.ordinal()] = now - phaseStart;
            }
            scratch[WORKER] = now - tickStart;
            current = null;
            finished = scratch.clone();
        }
    };

    /** The name of the thread that ran the last tick (CORE-045). */
    String workerThread() {
        return workerThread;
    }

    /** Records a batch close's server-thread time. Server thread. */
    void closed(long nanos) {
        pendingClose = nanos;
    }

    /**
     * Records an apply's server-thread time, with the timings of the tick it applied if the engine
     * finished one. Server thread.
     */
    void applied(long nanos, boolean ranTick) {
        long[] worker = ranTick ? finished : null;
        finished = null;
        long[] row = rows[next];
        Arrays.fill(row, 0);
        if (worker != null) {
            System.arraycopy(worker, 0, row, 0, worker.length);
        }
        row[CLOSE] = pendingClose;
        row[APPLY] = nanos;
        pendingClose = 0;
        next = (next + 1) % WINDOW;
        count = Math.min(count + 1, WINDOW);
    }

    /** How many atmos ticks the window holds. Server thread. */
    public int samples() {
        return count;
    }

    /** A phase's time on the worker over the window. Server thread. */
    public Timing phase(Phase phase) {
        return column(phase.ordinal());
    }

    /** The worker's whole tick over the window. Server thread. */
    public Timing worker() {
        return column(WORKER);
    }

    /** Closing batches on the server thread over the window. Server thread. */
    public Timing close() {
        return column(CLOSE);
    }

    /** Applying results on the server thread over the window. Server thread. */
    public Timing apply() {
        return column(APPLY);
    }

    /** Forgets the window. Server thread. */
    public void reset() {
        for (long[] row : rows) {
            Arrays.fill(row, 0);
        }
        next = 0;
        count = 0;
    }

    private Timing column(int c) {
        if (count == 0) {
            return new Timing(0, 0);
        }
        long sum = 0;
        long max = 0;
        for (int i = 0; i < count; i++) {
            long v = rows[i][c];
            sum += v;
            max = Math.max(max, v);
        }
        return new Timing(sum / 1e6 / count, max / 1e6);
    }
}
