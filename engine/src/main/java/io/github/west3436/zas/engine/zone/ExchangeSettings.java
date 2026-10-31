package io.github.west3436.zas.engine.zone;

import java.util.Arrays;

/**
 * How edges exchange gas (CORE-031, CORE-032).
 *
 * <p>Sharing ratios are defined per second (D-20, CORE-046): the ratio for an
 * atmos period of T seconds is r_T = 1 − (1 − r₁)^T, so sharing proceeds at the
 * same real-time rate at any period.
 *
 * @param periodSeconds the atmos tick period T, in s
 * @param sharingPerSecond sharing ratio per second for 1, 2, … connections; the
 *     last entry applies to that many connections or more
 * @param venting how a zone exchanges with the ambient
 * @param ambientSharerVolume the volume of ambient gas a zone shares with, in L;
 *     see {@link Venting}
 */
public record ExchangeSettings(
        double periodSeconds, double[] sharingPerSecond, Venting venting, double ambientSharerVolume) {

    /** ZAS's sharing ratios per 2 s tick, for 1 to 6 or more connections (zas-core §3.2). */
    private static final double[] ZAS_RATIOS_PER_2S = {0.30, 0.40, 0.48, 0.54, 0.60, 0.66};

    /** A 1 s period, the ZAS table converted to per-second ratios, volume-aware venting with 4,000 L. */
    public static final ExchangeSettings DEFAULT =
            new ExchangeSettings(1.0, fromRatiosPer(ZAS_RATIOS_PER_2S, 2.0), Venting.VOLUME_AWARE, 4000);

    /**
     * How a zone shares with the ambient. The zone shares, as with a zone, with a
     * temporary mixture of ambient gas that the ambient refills each time, so the
     * ambient never changes (CORE-038).
     */
    public enum Venting {
        /**
         * The temporary mixture's volume is {@code ambientSharerVolume}: a large
         * zone loses a smaller fraction per tick than a small one through the
         * same openings (D-21).
         */
        VOLUME_AWARE,
        /**
         * The temporary mixture's volume is the zone's volume plus {@code
         * ambientSharerVolume}, so the fraction lost per tick hardly depends on
         * the zone's size, as in ZAS (D-21).
         */
        FAITHFUL
    }

    public ExchangeSettings {
        if (!Double.isFinite(periodSeconds) || periodSeconds <= 0) {
            throw new IllegalArgumentException("periodSeconds must be finite and positive, got " + periodSeconds);
        }
        if (sharingPerSecond == null || sharingPerSecond.length == 0) {
            throw new IllegalArgumentException("sharingPerSecond needs at least one ratio");
        }
        sharingPerSecond = sharingPerSecond.clone();
        for (double r : sharingPerSecond) {
            if (!(r >= 0 && r <= 1)) {
                throw new IllegalArgumentException("sharing ratios must be in [0, 1], got " + r);
            }
        }
        if (venting == null) {
            throw new IllegalArgumentException("venting is null");
        }
        if (!Double.isFinite(ambientSharerVolume) || ambientSharerVolume <= 0) {
            throw new IllegalArgumentException(
                    "ambientSharerVolume must be finite and positive, got " + ambientSharerVolume);
        }
    }

    /**
     * Converts ratios defined per {@code seconds} into per-second ratios:
     * r₁ = 1 − (1 − r)^(1 / seconds).
     */
    public static double[] fromRatiosPer(double[] ratios, double seconds) {
        double[] out = new double[ratios.length];
        for (int i = 0; i < ratios.length; i++) {
            out[i] = 1 - Math.pow(1 - ratios[i], 1 / seconds);
        }
        return out;
    }

    /** The sharing ratio for one atmos tick through an edge with this many connections. */
    public double ratio(int connections) {
        if (connections <= 0) {
            return 0;
        }
        double perSecond = sharingPerSecond[Math.min(connections, sharingPerSecond.length) - 1];
        return 1 - Math.pow(1 - perSecond, periodSeconds);
    }

    @Override
    public double[] sharingPerSecond() {
        return sharingPerSecond.clone();
    }

    public ExchangeSettings withPeriod(double seconds) {
        return new ExchangeSettings(seconds, sharingPerSecond, venting, ambientSharerVolume);
    }

    public ExchangeSettings withVenting(Venting model) {
        return new ExchangeSettings(periodSeconds, sharingPerSecond, model, ambientSharerVolume);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ExchangeSettings s
                && periodSeconds == s.periodSeconds
                && Arrays.equals(sharingPerSecond, s.sharingPerSecond)
                && venting == s.venting
                && ambientSharerVolume == s.ambientSharerVolume;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sharingPerSecond) * 31 + Double.hashCode(periodSeconds) + venting.hashCode();
    }

    @Override
    public String toString() {
        return "ExchangeSettings[period=" + periodSeconds + " s, sharingPerSecond="
                + Arrays.toString(sharingPerSecond) + ", venting=" + venting + ", ambientSharerVolume="
                + ambientSharerVolume + " L]";
    }
}
