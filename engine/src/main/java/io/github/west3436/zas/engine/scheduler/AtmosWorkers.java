package io.github.west3436.zas.engine.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The atmos worker pool: a small dedicated pool of daemon threads named {@code zas-atmos-N}, never
 * a shared executor (architecture §3.1). Dimensions are independent, so with more than one thread
 * they run in parallel; each dimension's ticks still run one at a time ({@link
 * DimensionScheduler}).
 *
 * <p>Its threads run at normal priority. A thread otherwise takes the priority of the thread that
 * starts it, which is the server thread, raised to 8 on a machine with more than four processors;
 * at the server thread's priority, Windows often runs a worker just woken for a tick on the server
 * thread's own processor and holds the server thread off until it yields, so handing over a 10 ms
 * tick stalled the server thread about 10 ms.
 */
public final class AtmosWorkers {
    private AtmosWorkers() {}

    /** The name prefix of worker threads. */
    public static final String THREAD_PREFIX = "zas-atmos-";

    /**
     * A new pool.
     *
     * @param threads at least 1
     */
    public static ExecutorService create(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("at least one worker thread, got " + threads);
        }
        AtomicInteger next = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, THREAD_PREFIX + next.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };
        return Executors.newFixedThreadPool(threads, factory);
    }

    /** Whether the current thread is an atmos worker. */
    public static boolean onWorker() {
        return Thread.currentThread().getName().startsWith(THREAD_PREFIX);
    }
}
