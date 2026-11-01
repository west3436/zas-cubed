package io.github.west3436.zas.engine.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Runs one {@link DimensionEngine}'s ticks, one at a time: on a worker executor (CORE-045), or
 * inline when the batch is submitted (single-threaded mode, architecture §3.2, CORE-071). Either
 * way the caller {@linkplain #await() collects} the result at a fixed point of its own choosing,
 * so the threading mode can't change any outcome.
 *
 * <p><b>Fault isolation</b> (D-55, NFR-014): an exception inside the engine faults the dimension.
 * {@link #await()} returns null and later submissions are refused; the last published snapshot
 * stays readable. Collecting a result never waits without bound: a tick that hasn't finished
 * within the timeout faults the dimension too, so a stuck worker can't hang the server thread.
 *
 * <p><b>Inspections</b> ({@link #inspect}) read the engine on the thread that owns it, between
 * ticks: they queue behind the tick in flight, and the next tick queues behind them, so commands
 * and the profiler can look inside the engine without racing the worker (OPS-004, GEO-032).
 *
 * <p>Submit, await and inspect from one thread, the server thread in game.
 */
public final class DimensionScheduler {
    /** The default longest wait for a tick in {@link #await()}: 60 s, far beyond NFR-005's 250 ms. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 60_000;

    private final DimensionEngine engine;
    private final Executor executor;
    private final long timeoutMillis;
    private CompletableFuture<Result> inFlight;
    /** The last work handed to the executor: a tick or an inspection. The next one waits for it. */
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

    private volatile boolean abandoned;
    private Throwable fault;
    private List<DebugRegion> debugRegions = List.of();
    /** Written by the tick that exports it, read once its future completes (happens-before). */
    private volatile DebugView exported = DebugView.EMPTY;

    private DebugView debugView = DebugView.EMPTY;

    /**
     * @param engine the engine; this scheduler becomes its only caller
     * @param executor the worker executor, or null to run each tick inline when it is submitted
     */
    public DimensionScheduler(DimensionEngine engine, Executor executor) {
        this(engine, executor, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * @param engine the engine; this scheduler becomes its only caller
     * @param executor the worker executor, or null to run each tick inline when it is submitted
     * @param timeoutMillis the longest {@link #await()} waits before faulting the dimension
     */
    public DimensionScheduler(DimensionEngine engine, Executor executor, long timeoutMillis) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.executor = executor;
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + timeoutMillis);
        }
        this.timeoutMillis = timeoutMillis;
    }

    public DimensionEngine engine() {
        return engine;
    }

    /** Whether ticks run inline. */
    public boolean isSingleThreaded() {
        return executor == null;
    }

    /** Whether a submitted batch hasn't been collected yet. */
    public boolean isInFlight() {
        return inFlight != null;
    }

    /** Whether the in-flight tick has finished, so {@link #await()} won't block. */
    public boolean isDone() {
        return inFlight != null && inFlight.isDone();
    }

    /** The exception that faulted the dimension, or null. */
    public Throwable fault() {
        return fault;
    }

    public boolean isFaulted() {
        return fault != null;
    }

    /**
     * Sets the regions the operator debug overlay wants exported after each tick submitted from now
     * on (CLIENT-005, CLIENT-011); empty for none, which costs nothing.
     */
    public void setDebugRegions(List<DebugRegion> regions) {
        debugRegions = List.copyOf(regions);
    }

    /**
     * The debug export of the last collected tick: {@link DebugView#EMPTY} if it was submitted with
     * no debug regions, or the engine threw.
     */
    public DebugView debugView() {
        return debugView;
    }

    /**
     * Abandons the tick in flight, if any, without waiting for it: for shutdown. The engine must
     * not be used afterwards.
     */
    public void abandon() {
        abandoned = true;
        CompletableFuture<Result> f = inFlight;
        inFlight = null;
        if (f != null) {
            f.cancel(true);
        }
        tail.cancel(true);
    }

    /**
     * Runs a read-only query against the engine on the thread that owns it, between ticks: after
     * the tick in flight, if any, and before the next one. In single-threaded mode it runs now, on
     * the caller's thread, which owns the engine.
     *
     * <p>The query must not change the engine. Its result completes the future on the worker, so
     * a caller on the server thread hands the result back to itself (in game, {@code
     * server.execute}). A query that throws fails its future and nothing else. A slow query delays
     * the next tick, whose {@link #await()} still waits no longer than the timeout.
     *
     * @return the query's result; failed if the dimension is faulted or the scheduler abandoned
     */
    public <T> CompletableFuture<T> inspect(Function<? super DimensionEngine, ? extends T> query) {
        Objects.requireNonNull(query, "query");
        if (fault != null || abandoned) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("the dimension's engine is faulted or stopped", fault));
        }
        if (executor == null) {
            try {
                return CompletableFuture.completedFuture(query.apply(engine));
            } catch (RuntimeException | Error e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<T> f = tail.handleAsync(
                (ignored, error) -> {
                    if (abandoned) {
                        throw new java.util.concurrent.CancellationException("the scheduler was abandoned");
                    }
                    return query.apply(engine);
                },
                executor);
        tail = f;
        // A copy, so a caller that cancels or times out its future can't mark the query done
        // while it still runs, which would let the next tick start beside it.
        return f.copy();
    }

    /**
     * Starts a tick.
     *
     * @throws IllegalStateException if a tick is in flight or the dimension is faulted
     */
    public void submit(Batch batch) {
        Objects.requireNonNull(batch, "batch");
        if (inFlight != null) {
            throw new IllegalStateException("atmos tick " + batch.tick() + " submitted while another is in flight");
        }
        if (fault != null) {
            throw new IllegalStateException("the dimension's engine is faulted", fault);
        }
        List<DebugRegion> regions = debugRegions;
        if (executor == null) {
            CompletableFuture<Result> done = new CompletableFuture<>();
            try {
                done.complete(tick(batch, regions));
            } catch (RuntimeException | Error e) {
                done.completeExceptionally(e);
            }
            inFlight = done;
        } else if (tail.isDone()) {
            inFlight = CompletableFuture.supplyAsync(() -> tick(batch, regions), executor);
            tail = inFlight;
        } else {
            // An inspection is still running: the tick waits for it, so they never overlap.
            inFlight = tail.handleAsync((ignored, error) -> tick(batch, regions), executor);
            tail = inFlight;
        }
    }

    /** One tick, then the debug export if regions were asked for, on the thread that owns the engine. */
    private Result tick(Batch batch, List<DebugRegion> regions) {
        Result result = engine.tick(batch);
        exported = regions.isEmpty() ? DebugView.EMPTY : engine.debugView(regions);
        return result;
    }

    /**
     * Collects the in-flight tick's result, waiting for the worker if it hasn't finished, but no
     * longer than the timeout.
     *
     * @return the result, or null if the engine threw or timed out, which faults the dimension
     * @throws IllegalStateException if nothing is in flight
     */
    public Result await() {
        if (inFlight == null) {
            throw new IllegalStateException("no atmos tick in flight");
        }
        CompletableFuture<Result> f = inFlight;
        inFlight = null;
        debugView = DebugView.EMPTY;
        try {
            Result result = f.get(timeoutMillis, TimeUnit.MILLISECONDS);
            debugView = exported;
            return result;
        } catch (TimeoutException e) {
            f.cancel(true);
            fault = new TimeoutException("atmos tick didn't finish within " + timeoutMillis + " ms");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fault = e;
            return null;
        } catch (java.util.concurrent.ExecutionException | CompletionException e) {
            fault = e.getCause() != null ? e.getCause() : e;
            return null;
        } catch (RuntimeException e) {
            fault = e;
            return null;
        }
    }
}
