package io.github.west3436.zas.engine.bench;

import java.util.Arrays;

/** Summary statistics of a list of durations in nanoseconds, reported in milliseconds. */
final class Stats {
    private long[] values = new long[64];
    private int size;

    void add(long nanos) {
        if (size == values.length) {
            values = Arrays.copyOf(values, size * 2);
        }
        values[size++] = nanos;
    }

    int count() {
        return size;
    }

    double meanMs() {
        if (size == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += values[i];
        }
        return sum / size / 1e6;
    }

    double sumMs() {
        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += values[i];
        }
        return sum / 1e6;
    }

    /** The {@code p}-th percentile (0 to 100), nearest rank. */
    double percentileMs(double p) {
        if (size == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * size);
        return sorted[Math.max(0, Math.min(size - 1, rank - 1))] / 1e6;
    }

    double medianMs() {
        return percentileMs(50);
    }

    double maxMs() {
        long max = 0;
        for (int i = 0; i < size; i++) {
            max = Math.max(max, values[i]);
        }
        return max / 1e6;
    }

    /** Mean, median, p99 and max, for a table row. */
    String row() {
        return String.format("%s | %s | %s | %s", ms(meanMs()), ms(medianMs()), ms(percentileMs(99)), ms(maxMs()));
    }

    static String ms(double ms) {
        if (ms == 0) {
            return "0";
        }
        if (ms < 0.01) {
            return String.format("%.1f µs", ms * 1000);
        }
        if (ms < 1) {
            return String.format("%.3f ms", ms);
        }
        return String.format("%.2f ms", ms);
    }
}
