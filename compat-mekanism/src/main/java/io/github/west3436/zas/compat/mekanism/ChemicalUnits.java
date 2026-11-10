package io.github.west3436.zas.compat.mekanism;

/**
 * Mekanism's amounts and ZAS³'s (MEK-002). Mekanism counts a chemical in whole millibuckets and
 * gives it no pressure or temperature; ZAS³ counts gas in moles. One factor converts them, the same
 * for every chemical, because Mekanism's recipes are mole-proportional in mB (2 water make 2
 * hydrogen and 1 oxygen), so a factor per gas would break their balance.
 *
 * <p>The factor is set as a volume: 1 mB is so many litres of gas at standard air's pressure and
 * temperature, 101.325 kPa and 293.15 K. At the default, 1 L, 1,000 mB fill one block at one
 * atmosphere, and 1 mB is about 0.04157 mol.
 *
 * <p>Pure arithmetic: this class names nothing of the game's, Mekanism's or ZAS³'s.
 */
public final class ChemicalUnits {
    /** The pressure the factor is set at, in kPa: standard air's. */
    public static final double STANDARD_PRESSURE = 101.325;

    /** The temperature the factor is set at, in K: standard air's. */
    public static final double STANDARD_TEMPERATURE = 293.15;

    /** The molar gas constant R, in J/(mol·K). */
    public static final double GAS_CONSTANT = 8.314462618;

    /** The default factor: 1 mB is 1 L of gas at the standard pressure and temperature. */
    public static final double DEFAULT_LITRES_PER_MILLIBUCKET = 1.0;

    /** The least the factor can be set to, in L per mB. */
    public static final double MIN_LITRES_PER_MILLIBUCKET = 0.001;

    /** The most the factor can be set to, in L per mB. */
    public static final double MAX_LITRES_PER_MILLIBUCKET = 1000.0;

    private ChemicalUnits() {}

    /**
     * The moles in one millibucket, n = PV/(RT) at the standard pressure and temperature.
     *
     * @param litresPerMillibucket the factor, in L per mB, finite and positive
     * @return moles per mB
     * @throws IllegalArgumentException if the factor isn't finite and positive
     */
    public static double molesPerMillibucket(double litresPerMillibucket) {
        if (!Double.isFinite(litresPerMillibucket) || litresPerMillibucket <= 0) {
            throw new IllegalArgumentException(
                    "litres per millibucket must be finite and positive: " + litresPerMillibucket);
        }
        return STANDARD_PRESSURE * litresPerMillibucket / (GAS_CONSTANT * STANDARD_TEMPERATURE);
    }

    /**
     * The moles in an amount of a chemical. Exact but for floating point, so nothing is gained or
     * lost on the way into ZAS³.
     *
     * @param millibuckets the amount, not negative
     * @param molesPerMillibucket the factor, from {@link #molesPerMillibucket}
     * @return the moles
     * @throws IllegalArgumentException if the amount is negative
     */
    public static double moles(long millibuckets, double molesPerMillibucket) {
        if (millibuckets < 0) {
            throw new IllegalArgumentException("millibuckets must not be negative: " + millibuckets);
        }
        return millibuckets * molesPerMillibucket;
    }

    /**
     * The whole millibuckets an amount of gas makes, rounded down: Mekanism holds whole
     * millibuckets only, so whoever converts takes {@link #moles} of this many out of the gas and
     * leaves the rest where it is. Rounding down never makes gas, and leaving the rest never loses
     * any.
     *
     * <p>The answer is the most millibuckets whose {@link #moles} are no more than the gas, by the
     * same multiplication {@code moles} does. So what the result holds is never more than the gas,
     * to the last bit, and millibuckets turned into moles and back come out as they went in.
     *
     * @param moles the gas, in moles; a negative or non-finite amount makes none
     * @param molesPerMillibucket the factor, from {@link #molesPerMillibucket}
     * @return the whole millibuckets, 0 or more
     */
    public static long wholeMillibuckets(double moles, double molesPerMillibucket) {
        if (!(moles > 0) || !Double.isFinite(moles) || !(molesPerMillibucket > 0)) {
            return 0;
        }
        double amount = moles / molesPerMillibucket;
        if (amount >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long whole = (long) Math.floor(amount);
        // The division can land a hair to either side of a whole number; the multiplication settles it.
        if ((whole + 1) * molesPerMillibucket <= moles) {
            whole++;
        } else if (whole * molesPerMillibucket > moles) {
            whole--;
        }
        return whole;
    }
}
