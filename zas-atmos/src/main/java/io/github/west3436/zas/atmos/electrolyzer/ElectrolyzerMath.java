package io.github.west3436.zas.atmos.electrolyzer;

/**
 * The electrolyzer's numbers (CONT-055, CONT-056). 2 H₂O → 2 H₂ + O₂: the water it splits each second
 * is as many moles as the H₂ it makes. A bucket is a block of water, 1 m³, so 1 mB is a litre, about
 * 55.5 mol, and a bucket lasts about 7.7 h.
 */
public final class ElectrolyzerMath {
    /** The O₂ it makes, mol/s (CONT-055). */
    public static final double O2_PER_SECOND = 1.0;

    /** The H₂ it makes, mol/s (CONT-055). */
    public static final double H2_PER_SECOND = 2.0;

    /** Water's molar mass, g/mol. */
    public static final double WATER_GRAMS_PER_MOLE = 18.015;

    /** The water in 1 mB, mol: a litre, 1,000 g. */
    public static final double WATER_MOLES_PER_MB = 1000 / WATER_GRAMS_PER_MOLE;

    /** One bucket, mB. */
    public static final int BUCKET_MB = 1000;

    /** The tank, mB: four buckets (CONT-056). */
    public static final int TANK_MB = 4 * BUCKET_MB;

    /**
     * The most an outlet's network is filled to, kPa: the pressure pump's limit, the most any device
     * pushes a network to. It is below a canister's rating and a pipe's burst.
     */
    public static final double MAX_OUTPUT_KPA = 4_500;

    /** Less water than this, mB, counts as none, so rounding never leaves a tank that can't empty. */
    static final double EMPTY_MB = 1e-9;

    private ElectrolyzerMath() {}

    /** The water it splits each second, mB. */
    public static double waterPerSecond() {
        return H2_PER_SECOND / WATER_MOLES_PER_MB;
    }

    /** How long one bucket lasts while it runs, s. */
    public static double secondsPerBucket() {
        return BUCKET_MB / waterPerSecond();
    }

    /**
     * How much of a full step's work the water left pays for.
     *
     * @param waterMb the water in the tank, mB
     * @param seconds the step's length, s
     * @return from 0, with no water, to 1, with enough for the whole step
     */
    public static double fraction(double waterMb, double seconds) {
        double need = waterPerSecond() * seconds;
        if (waterMb <= EMPTY_MB || need <= 0) {
            return 0;
        }
        return Math.min(1, waterMb / need);
    }

    /**
     * The level its block shows, 0 to 4: one for each bucket the tank has begun to fill, so any water
     * shows and only an empty tank shows none.
     *
     * @param waterMb the water in the tank, mB
     */
    public static int level(double waterMb) {
        if (waterMb <= EMPTY_MB) {
            return 0;
        }
        return (int) Math.max(1, Math.min(4, Math.ceil(waterMb / BUCKET_MB - 1e-9)));
    }
}
