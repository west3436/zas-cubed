package io.github.west3436.zas.atmos.controller;

/**
 * One reading's danger bounds (CONT-040): {@code min2 ≤ min1 ≤ max1 ≤ max2}. A reading at or below
 * min2, or at or above max2, is danger 2; at or below min1, or at or above max1, danger 1; otherwise
 * 0. A bound of {@link #OFF} is disabled and never trips.
 *
 * <p>Bounds are in the reading's unit: kPa for pressure and partial pressures, K for temperature.
 * The enabled bounds are always in order: {@link #with} keeps them so.
 */
public record Thresholds(double min2, double min1, double max1, double max2) {
    /** A disabled bound. */
    public static final double OFF = -1;

    /** The largest bound a player can set: 5,066 kPa for pressures (CONT-014's range), or 5,066 K. */
    public static final double MAX = 5066;

    /** No bounds at all: the reading never trips. */
    public static final Thresholds NONE = new Thresholds(OFF, OFF, OFF, OFF);

    public Thresholds {
        double last = Double.NEGATIVE_INFINITY;
        for (double b : new double[] {min2, min1, max1, max2}) {
            if (b == OFF) {
                continue;
            }
            if (!Double.isFinite(b) || b < 0 || b > MAX) {
                throw new IllegalArgumentException("a bound must be off or 0 to " + MAX + ": " + b);
            }
            if (b < last) {
                throw new IllegalArgumentException(
                        "bounds out of order: " + min2 + ", " + min1 + ", " + max1 + ", " + max2);
            }
            last = b;
        }
    }

    /** Only maxima: {@code –/–/max1/max2}. */
    public static Thresholds max(double max1, double max2) {
        return new Thresholds(OFF, OFF, max1, max2);
    }

    /** The danger level of a reading: 0, 1 or 2. */
    public int rate(double value) {
        if ((min2 != OFF && value <= min2) || (max2 != OFF && value >= max2)) {
            return 2;
        }
        if ((min1 != OFF && value <= min1) || (max1 != OFF && value >= max1)) {
            return 1;
        }
        return 0;
    }

    /** Bound {@code i}: 0 is min2, 1 min1, 2 max1, 3 max2. */
    public double bound(int i) {
        return switch (i) {
            case 0 -> min2;
            case 1 -> min1;
            case 2 -> max1;
            case 3 -> max2;
            default -> throw new IllegalArgumentException("no bound " + i);
        };
    }

    /**
     * These bounds with bound {@code i} set to {@code value}, clamped to stay in order with the
     * other enabled bounds and within 0 to {@link #MAX}. {@link #OFF} disables it.
     *
     * @param i 0 to 3, as in {@link #bound}
     * @param value the new value, or {@link #OFF}
     * @return the new bounds
     */
    public Thresholds with(int i, double value) {
        double[] b = {min2, min1, max1, max2};
        if (i < 0 || i > 3) {
            throw new IllegalArgumentException("no bound " + i);
        }
        if (value != OFF) {
            double lo = 0;
            double hi = MAX;
            for (int j = 0; j < i; j++) {
                if (b[j] != OFF) {
                    lo = Math.max(lo, b[j]);
                }
            }
            for (int j = i + 1; j < 4; j++) {
                if (b[j] != OFF) {
                    hi = Math.min(hi, b[j]);
                }
            }
            value = Double.isFinite(value) ? Math.max(lo, Math.min(hi, value)) : lo;
        }
        b[i] = value;
        return new Thresholds(b[0], b[1], b[2], b[3]);
    }
}
