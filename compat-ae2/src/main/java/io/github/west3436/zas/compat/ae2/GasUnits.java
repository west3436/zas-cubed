package io.github.west3436.zas.compat.ae2;

/**
 * AE2's amounts of gas and ZAS³'s (COMPAT-074). AE2 counts every stack in whole units, and ZAS³
 * counts gas in moles, so one unit of a gas in ME storage is a millimole: a terminal shows 1,000 of
 * them as 1 mol.
 *
 * <p>Gas goes into ME storage only in whole millimoles. Whoever converts takes {@link #moles} of
 * {@link #whole} units out of the gas and leaves the rest where it is, so rounding never makes gas
 * and never loses any.
 *
 * <p>Pure arithmetic: this class names nothing of the game's, AE2's or ZAS³'s.
 */
public final class GasUnits {
    /** The units in a mole. */
    public static final int PER_MOLE = 1000;

    private GasUnits() {}

    /**
     * The moles in an amount of units. Correctly rounded, so units turned into moles and back come
     * out as they went in.
     *
     * @param units the amount, not negative
     * @return the moles
     * @throws IllegalArgumentException if the amount is negative
     */
    public static double moles(long units) {
        if (units < 0) {
            throw new IllegalArgumentException("units must not be negative: " + units);
        }
        return units / (double) PER_MOLE;
    }

    /**
     * The whole units an amount of gas makes, rounded down: the most units whose {@link #moles} are
     * no more than the gas, by the same division {@code moles} does. So what the result holds is
     * never more than the gas, to the last bit.
     *
     * @param moles the gas, in moles; a negative or non-finite amount makes none
     * @return the whole units, 0 or more
     */
    public static long whole(double moles) {
        if (!(moles > 0) || !Double.isFinite(moles)) {
            return 0;
        }
        double amount = moles * PER_MOLE;
        if (amount >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long whole = (long) Math.floor(amount);
        // The multiplication can land a hair to either side of a whole number; the division settles it.
        while (moles(whole + 1) <= moles) {
            whole++;
        }
        while (whole > 0 && moles(whole) > moles) {
            whole--;
        }
        return whole;
    }

    /**
     * The fewest whole units that hold at least an amount of gas, rounded up: what ME storage gives
     * up when a block takes that much of it, so the block never holds more than ME storage lost.
     *
     * @param moles the gas, in moles; a negative or non-finite amount needs none
     * @return the whole units, 0 or more
     */
    public static long covering(double moles) {
        long whole = whole(moles);
        return whole == Long.MAX_VALUE || !(moles > 0) || moles(whole) >= moles ? whole : whole + 1;
    }
}
