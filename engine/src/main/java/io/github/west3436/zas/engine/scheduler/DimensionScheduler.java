package io.github.west3436.zas.engine.scheduler;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Runs one {@link DimensionEngine}'s ticks, one at a time: on a worker executor (CORE-045), or
 * inline when the batch is submitted (single-threaded mode, architecture §3.2, CORE-071). Either
 * way the caller {@linkplain #await() collects} the result at a fixed point of its own choosing,
 * so the threading mode can't change any outcome.
 *
 * <p><b>Fault isolation</b> (D-55, NFR-014): an exception inside the engine faults the dimension.
 * {@link #await()} returns null and later submissions are refused; the last published snapshot
 * stays readable.
 *
 * <p>Submit and await from one thread, the server thread in game.
 */
public final class DimensionScheduler {
    private final DimensionEngine engine;
    private final Executor executor;
    private CompletableFuture<Result> inFlight;
    private Throwable fault;

    /**
     * @param engine the engine; this scheduler becomes its only caller
     * @param executor the worker executor, or null to run each tick inline when it is submitted
     */
    public DimensionScheduler(DimensionEngine engine, Executor executor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.executor = executor;
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
        if (executor == null) {
            CompletableFuture<Result> done = new CompletableFuture<>();
            try {
                done.complete(engine.tick(batch));
            } catch (RuntimeException | Error e) {
                done.completeExceptionally(e);
            }
            inFlight = done;
        } else {
            inFlight = CompletableFuture.supplyAsync(() -> engine.tick(batch), executor);
        }
    }

    /**
     * Collects the in-flight tick's result, waiting for the worker if it hasn't finished.
     *
     * @return the result, or null if the engine threw, which faults the dimension
     * @throws IllegalStateException if nothing is in flight
     */
    public Result await() {
        if (inFlight == null) {
            throw new IllegalStateException("no atmos tick in flight");
        }
        CompletableFuture<Result> f = inFlight;
        inFlight = null;
        try {
            return f.join();
        } catch (CompletionException e) {
            fault = e.getCause() != null ? e.getCause() : e;
            return null;
        } catch (RuntimeException e) {
            fault = e;
            return null;
        }
    }
}
