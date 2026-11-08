package io.github.west3436.zas.s10;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server thread's time in {@code zas} and {@code zas_atmos} code (NFR-001), by part.
 *
 * <p>Every way into their code that the server thread takes is wrapped to call {@link #enter} and
 * {@link #exit}: their game-bus listeners ({@link BusTiming}), their block entities' tickers and
 * the M1 change hook (the bench's mixins), and the bench's own calls to the API. A call made from
 * inside another, such as a zone event fired while a result is applied, counts once, in the
 * outer part. The one exception is a device's step ({@link #enterDevice}): its time is its
 * device type's part, taken out of the device ticker's that runs it, so the report can say which
 * devices cost what. Calls on other threads count nothing. {@link #endTick} closes a server tick.
 */
final class Clock {
    /** What {@link #enter} returns off the server thread. */
    static final long OFF = Long.MIN_VALUE;
    /** What {@link #enter} returns inside another timed call. */
    private static final long NESTED = Long.MIN_VALUE + 1;

    private static volatile Thread server;
    private static int depth;
    // Time charged to device parts inside the outer call in progress, which that call leaves out.
    private static long inner;
    private static boolean inDevice;
    private static final Map<String, Part> PARTS = new ConcurrentHashMap<>();
    private static final List<Part> ORDER = new ArrayList<>();

    /**
     * Whether each tick's outer calls are kept, start and end, for {@link #lastCalls}: the enclosure
     * cap step's, so its report can tell a collector pause inside zas's calls from one outside them.
     * Off for the scenario. Server thread.
     */
    static boolean keepCalls;

    private static long[] calls = new long[64];
    private static int callEnds;
    private static final long[] NO_CALLS = new long[0];
    private static long[] lastCalls = NO_CALLS;

    /** The M1 change hook, {@code ChangeTracker.onBlockWrite}. */
    static final Part CHANGE_HOOK = part("ChangeTracker · block write (M1)", "io.github.west3436.zas.change.");

    /** The bench's own API calls: the stand-in supply and fuel, and the fill. */
    static final Part BENCH_API = part("Bench's own API calls", "bench");

    private static final ClassValue<Part> BLOCK_ENTITIES = new ClassValue<>() {
        @Override
        protected Part computeValue(Class<?> type) {
            String n = type.getName();
            return n.startsWith(BusTiming.ZAS) && !n.startsWith(BusTiming.BENCH)
                    ? part("Block entity ticker · " + type.getSimpleName(), n)
                    : null;
        }
    };

    private static final ClassValue<Part> DEVICES = new ClassValue<>() {
        @Override
        protected Part computeValue(Class<?> type) {
            return part("Device step · " + type.getSimpleName(), type.getName());
        }
    };

    private Clock() {}

    /** One part's time: what it took in the server tick in progress. */
    static final class Part {
        final String name;
        final String category;
        final int index;
        long now;

        private Part(String name, String category, int index) {
            this.name = name;
            this.category = category;
            this.index = index;
        }
    }

    /** Starts counting on this thread, the server's. */
    static void start(Thread thread) {
        server = thread;
    }

    /** A part by name, created if new; {@code owner} names the class it belongs to. */
    static Part part(String name, String owner) {
        Part p = PARTS.get(name);
        if (p == null) {
            synchronized (ORDER) {
                p = PARTS.get(name);
                if (p == null) {
                    p = new Part(name, Categories.of(owner), ORDER.size());
                    ORDER.add(p);
                    PARTS.put(name, p);
                }
            }
        }
        return p;
    }

    /** The parts, in the order they first ran. */
    static List<Part> parts() {
        synchronized (ORDER) {
            return List.copyOf(ORDER);
        }
    }

    /** The part of a block entity's ticker, or null if it isn't one of the mods'. */
    static Part blockEntity(Object be) {
        return BLOCK_ENTITIES.get(be.getClass());
    }

    /** The part of a device type's step. */
    static Part device(Object device) {
        return DEVICES.get(device.getClass());
    }

    /** Starts a timed call; pass what it returns to {@link #exit}. */
    static long enter() {
        if (Thread.currentThread() != server) {
            return OFF;
        }
        if (depth++ > 0) {
            return NESTED;
        }
        inner = 0;
        return System.nanoTime();
    }

    /** Ends a timed call, charging it to {@code part} unless it was nested. */
    static void exit(Part part, long started) {
        if (started == OFF) {
            return;
        }
        depth--;
        if (started == NESTED) {
            return;
        }
        long end = System.nanoTime();
        part.now += end - started - inner;
        inner = 0;
        if (keepCalls) {
            if (callEnds + 2 > calls.length) {
                calls = java.util.Arrays.copyOf(calls, calls.length * 2);
            }
            calls[callEnds++] = started;
            calls[callEnds++] = end;
        }
    }

    /** The last tick's outer calls while {@link #keepCalls} is on: start and end, in pairs. */
    static long[] lastCalls() {
        return lastCalls;
    }

    /**
     * Starts a device's step, which counts in its own part even inside the device ticker's call;
     * pass what it returns to {@link #exitDevice}. A call inside the step counts in the step.
     */
    static long enterDevice() {
        if (Thread.currentThread() != server) {
            return OFF;
        }
        if (depth == 0 || inDevice) {
            return enter();
        }
        depth++;
        inDevice = true;
        return System.nanoTime();
    }

    /** Ends a device's step, charging it to {@code part}. */
    static void exitDevice(Part part, long started) {
        if (started == OFF || started == NESTED || depth == 1) {
            exit(part, started);
            return;
        }
        depth--;
        inDevice = false;
        long took = System.nanoTime() - started;
        part.now += took;
        inner += took;
    }

    /**
     * Closes a server tick: hands each part's time to {@code sink} (indexed by {@link Part#index}),
     * then starts the next at zero.
     *
     * @return the tick's total
     */
    static long endTick(long[] sink) {
        long total = 0;
        synchronized (ORDER) {
            for (Part p : ORDER) {
                if (sink != null && p.index < sink.length) {
                    sink[p.index] = p.now;
                }
                total += p.now;
                p.now = 0;
            }
        }
        lastCalls = keepCalls ? java.util.Arrays.copyOf(calls, callEnds) : NO_CALLS;
        callEnds = 0;
        return total;
    }

    /** How many parts there are now. */
    static int partCount() {
        synchronized (ORDER) {
            return ORDER.size();
        }
    }
}
