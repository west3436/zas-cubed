package io.github.west3436.zas.engine.zone;

import java.util.Arrays;

/**
 * How edges exchange gas (CORE-031, CORE-032, CORE-035, CORE-068).
 *
 * <p>Each tick an awake edge closes a fraction of the difference between its two sides, the same
 * for every gas and for thermal energy, and keeps their volume-weighted mean, so moles are
 * conserved. Per second, an edge with k connections closes r_k·w of the difference, where r_k is
 * the sharing ratio for k connections and w ≤ 1 a weight set by the volumes and the {@link
 * Venting} model. Rates are defined per second (D-20, CORE-046): a tick of T seconds closes
 * 1 − (1 − r_k·w)^T, so exchange proceeds at the same real-time rate at any period.
 *
 * @param periodSeconds the atmos tick period T, in s
 * @param sharingPerSecond sharing ratio per second for 1, 2, … connections; the
 *     last entry applies to that many connections or more
 * @param venting how the volumes weight exchange
 * @param sharerVolume V_s, the volume of the temporary mixture an opening shares through, in L;
 *     see {@link Venting}
 */
public record ExchangeSettings(double periodSeconds, double[] sharingPerSecond, Venting venting, double sharerVolume) {

    /** ZAS's sharing ratios per 2 s tick, for 1 to 6 or more connections (zas-core §3.2). */
    private static final double[] ZAS_RATIOS_PER_2S = {0.30, 0.40, 0.48, 0.54, 0.60, 0.66};

    /** A 1 s period, the ZAS table converted to per-second ratios, volume-aware venting with 4,000 L. */
    public static final ExchangeSettings DEFAULT =
            new ExchangeSettings(1.0, fromRatiosPer(ZAS_RATIOS_PER_2S, 2.0), Venting.VOLUME_AWARE, 4000);

    /**
     * How the volumes weight exchange (D-21). A zone shares with the ambient as with a zone, through
     * a temporary mixture of ambient gas that the ambient refills each time, so the ambient never
     * changes (CORE-038).
     */
    public enum Venting {
        /**
         * Exchange passes through a temporary mixture of {@code sharerVolume} at the openings, so the
         * same openings drain a large zone proportionally slower than a small one, into the ambient
         * or into a much larger zone (CORE-035). The weight is w = V_s/(V_s + μ), where μ is the
         * zones' reduced volume V_A·V_B/(V_A + V_B), or the zone's volume for the ambient: a zone
         * drains into a zone of any size no faster than into the ambient, the limit of an infinitely
         * large zone.
         */
        VOLUME_AWARE,
        /**
         * ZAS's model (CORE-068, zas-core §3.2). Two zones move toward their volume-weighted mean at
         * the full ratio, w = 1, so a zone beside a much larger one loses the same fraction per tick
         * whatever its size. With the ambient, the temporary mixture's volume is the zone's volume
         * plus {@code sharerVolume}, w = (V + V_s)/(2V + V_s), so the fraction lost per tick hardly
         * depends on the zone's size: from 0.8·r for a one-cell zone down to r/2 for a large one.
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
        if (!Double.isFinite(sharerVolume) || sharerVolume <= 0) {
            throw new IllegalArgumentException("sharerVolume must be finite and positive, got " + sharerVolume);
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

    /** The sharing ratio for one atmos tick through an edge with this many connections, with w = 1. */
    public double ratio(int connections) {
        return perTick(connections, 1);
    }

    /**
     * The fraction of the difference between two zones that one atmos tick closes across an edge
     * with this many connections (CORE-031, CORE-035): each zone moves that fraction of the way to
     * their volume-weighted mean.
     *
     * @param va one zone's volume, in L
     * @param vb the other's, in L
     */
    public double zoneRatio(int connections, double va, double vb) {
        if (venting == Venting.FAITHFUL) {
            return ratio(connections);
        }
        double reduced = va * vb / (va + vb);
        return perTick(connections, sharerVolume / (sharerVolume + reduced));
    }

    /**
     * The fraction of the difference between a zone and its ambient that one atmos tick closes
     * across an ambient edge with this many connections (CORE-032, CORE-035, CORE-068).
     *
     * @param volume the zone's volume, in L
     */
    public double ambientRatio(int connections, double volume) {
        double sharer = venting == Venting.FAITHFUL ? volume + sharerVolume : sharerVolume;
        return perTick(connections, sharer / (volume + sharer));
    }

    /** 1 − (1 − r_k·w)^T: the fraction one tick closes when one second closes r_k·w. */
    private double perTick(int connections, double weight) {
        if (connections <= 0) {
            return 0;
        }
        double perSecond = sharingPerSecond[Math.min(connections, sharingPerSecond.length) - 1] * weight;
        return 1 - Math.pow(1 - perSecond, periodSeconds);
    }

    @Override
    public double[] sharingPerSecond() {
        return sharingPerSecond.clone();
    }

    public ExchangeSettings withPeriod(double seconds) {
        return new ExchangeSettings(seconds, sharingPerSecond, venting, sharerVolume);
    }

    public ExchangeSettings withVenting(Venting model) {
        return new ExchangeSettings(periodSeconds, sharingPerSecond, model, sharerVolume);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ExchangeSettings s
                && periodSeconds == s.periodSeconds
                && Arrays.equals(sharingPerSecond, s.sharingPerSecond)
                && venting == s.venting
                && sharerVolume == s.sharerVolume;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sharingPerSecond) * 31 + Double.hashCode(periodSeconds) + venting.hashCode();
    }

    @Override
    public String toString() {
        return "ExchangeSettings[period=" + periodSeconds + " s, sharingPerSecond="
                + Arrays.toString(sharingPerSecond) + ", venting=" + venting + ", sharerVolume=" + sharerVolume
                + " L]";
    }
}
