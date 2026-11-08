package io.github.west3436.zas.s10;

import io.github.west3436.zas.engine.scheduler.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Each atmos tick's time on the worker (NFR-005), whole and by phase: a phase listener that passes
 * everything on to the profiler's (OPS-004) and keeps its own copy of every finished tick, where
 * the profiler keeps averages. A phase lasts from its {@code enter} to the next one's, as the
 * profiler counts it.
 */
final class WorkerTimes implements Phase.Listener {
    static final Phase[] PHASES = Phase.values();
    /** Index of the whole tick in a row, after the phases. */
    static final int WHOLE = PHASES.length;

    private final Phase.Listener inner;
    private final long[] scratch = new long[WHOLE + 1];
    private Phase current;
    private long phaseStart;
    private long tickStart;
    private final ConcurrentLinkedQueue<long[]> finished = new ConcurrentLinkedQueue<>();

    WorkerTimes(Phase.Listener inner) {
        this.inner = inner;
    }

    @Override
    public void enter(Phase phase) {
        long now = System.nanoTime();
        if (current == null) {
            java.util.Arrays.fill(scratch, 0);
            tickStart = now;
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
        long now = System.nanoTime();
        if (current != null) {
            scratch[current.ordinal()] = now - phaseStart;
        }
        scratch[WHOLE] = now - tickStart;
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
