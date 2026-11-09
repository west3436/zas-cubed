package io.github.west3436.zas.s10;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** What one stretch of the script recorded, tick by tick. Server thread. */
final class Window {
    final String name;
    final long firstTick;
    final long startNanos = System.nanoTime();
    long endNanos;
    int ticks;

    /** Each server tick's atmos time, all parts together. */
    final Series atmos = new Series();
    /** Each server tick's whole time, from the first listener of its start to the last of its end. */
    final Series wall = new Series();
    /** Each server tick's start, {@link System#nanoTime}. */
    final Series starts = new Series();
    /**
     * Where the window keeps them (the enclosure cap step's), each tick of at least 1 ms's timed
     * calls, as start and end pairs ({@link Clock#lastCalls}), by the tick's index: a collector
     * pause inside them is inside zas's time.
     */
    final Map<Integer, long[]> calls = new java.util.HashMap<>();
    /**
     * Where the window keeps them (the enclosure cap step's), the batch close and apply on the
     * ticks that had either, as {close, apply} nanoseconds, -1 for none, by the tick's index: a slow
     * tick without them, or well beyond them, is a stall, not the batch work.
     */
    final Map<Integer, long[]> batchWork = new java.util.HashMap<>();
    /** Each server tick's time by part, indexed by {@link Clock.Part#index}. */
    final List<Series> parts = new ArrayList<>();
    /** The station's batch closes and applies, on the ticks they happened. */
    final Series close = new Series();

    final Series apply = new Series();
    /** Sections each station batch carried (the dirty ones, the prefetch and requested ones). */
    final Series batchSections = new Series();
    /** Worker ticks of the station that finished in this window: whole, then by phase. */
    final List<long[]> worker = new ArrayList<>();
    /** Worker ticks of the other dimensions (empty, but they share the worker). */
    final Series otherWorker = new Series();
    /** Station batches that carried sections the worker had asked for (an enclosure left the mirror). */
    int requestBatches;

    int requestedSections;

    /** Bytes sent to each player, by what was sent: {count, bytes}. */
    final Map<String, Map<String, long[]>> bytes = new TreeMap<>();

    /** Notes the script made while the window ran, with the tick. */
    final Map<Long, String> events = new LinkedHashMap<>();

    Window(String name, long firstTick) {
        this.name = name;
        this.firstTick = firstTick;
    }

    void tick(long atmosNanos, long startNanos, long wallNanos, long[] byPart, int partCount) {
        ticks++;
        atmos.add(atmosNanos);
        starts.add(startNanos);
        wall.add(wallNanos);
        while (parts.size() < partCount) {
            Series s = new Series();
            for (int i = 0; i < ticks - 1; i++) {
                s.add(0);
            }
            parts.add(s);
        }
        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).add(i < byPart.length ? byPart[i] : 0);
        }
    }

    void sent(String player, String what, int wireBytes) {
        long[] row = bytes.computeIfAbsent(player, k -> new TreeMap<>()).computeIfAbsent(what, k -> new long[2]);
        row[0]++;
        row[1] += wireBytes;
    }

    double seconds() {
        return ((endNanos == 0 ? System.nanoTime() : endNanos) - startNanos) / 1e9;
    }

    /** A growable list of nanosecond (or other) values with the statistics the report gives. */
    static final class Series {
        private long[] values = new long[1024];
        private int size;

        void add(long v) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = v;
        }

        int size() {
            return size;
        }

        long get(int i) {
            return values[i];
        }

        long sum() {
            long s = 0;
            for (int i = 0; i < size; i++) {
                s += values[i];
            }
            return s;
        }

        double average() {
            return size == 0 ? 0 : (double) sum() / size;
        }

        long max() {
            long m = 0;
            for (int i = 0; i < size; i++) {
                m = Math.max(m, values[i]);
            }
            return m;
        }

        /** The value at or below which a fraction {@code q} of them fall (nearest rank). */
        long quantile(double q) {
            if (size == 0) {
                return 0;
            }
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            int rank = (int) Math.ceil(q * size) - 1;
            return sorted[Math.max(0, Math.min(size - 1, rank))];
        }

        /** How many are above {@code limit}. */
        int countAbove(long limit) {
            int n = 0;
            for (int i = 0; i < size; i++) {
                if (values[i] > limit) {
                    n++;
                }
            }
            return n;
        }

        int nonZero() {
            int n = 0;
            for (int i = 0; i < size; i++) {
                if (values[i] != 0) {
                    n++;
                }
            }
            return n;
        }
    }
}
