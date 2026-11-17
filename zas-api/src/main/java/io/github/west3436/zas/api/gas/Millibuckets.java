package io.github.west3436.zas.api.gas;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ZasApi;

/**
 * Other mods' amounts of gas and ZAS³'s (COMPAT-143, MEK-002). Mods that store gas as a fluid or a
 * chemical count it in whole millibuckets, with no pressure or temperature; ZAS³ counts gas in
 * moles. One factor converts them, the same for every gas and every mod, so a millibucket of oxygen
 * is the same amount whether it comes from a Mekanism tank or another mod's fluid tank, and recipes
 * that are mole-proportional in mB (2 water make 2 hydrogen and 1 oxygen) stay in balance.
 *
 * <p>The factor is set as a volume: 1 mB is so many litres of gas at standard air's pressure and
 * temperature, 101.325 kPa and 293.15 K. It is the server's {@code [units] litres_per_millibucket}
 * in {@code zas-server.toml}. At the default, 1 L, 1,000 mB fill one block at one atmosphere, and
 * 1 mB is about 0.04157 mol.
 *
 * <pre>{@code
 * double factor = Millibuckets.molesPerMillibucket();     // the server's factor, read each time
 * double moles = Millibuckets.moles(250, factor);          // 250 mB coming in
 * long whole = Millibuckets.wholeMillibuckets(12.5, factor); // gas going out, rounded down
 * double taken = Millibuckets.moles(whole, factor);        // take this much, leave the rest
 * }</pre>
 *
 * <p><b>No gas from rounding.</b> A millibucket is whole, a mole isn't. Whoever converts gas into
 * millibuckets rounds down with {@link #wholeMillibuckets}, takes {@link #moles} of the result out
 * of the gas and leaves the rest where it was. Rounding down never makes gas, and leaving the rest
 * never loses any.
 *
 * <p>All but {@link #litresPerMillibucket()} and {@link #molesPerMillibucket()} is pure arithmetic.
 *
 * @since 1.2.0
 */
public final class Millibuckets {
    /** The pressure the factor is set at, in kPa: standard air's. */
    public static final double STANDARD_PRESSURE = StandardAir.PRESSURE;

    /** The temperature the factor is set at, in K: standard air's. */
    public static final double STANDARD_TEMPERATURE = StandardAir.TEMPERATURE;

    /** The default factor: 1 mB is 1 L of gas at the standard pressure and temperature. */
    public static final double DEFAULT_LITRES_PER_MILLIBUCKET = 1.0;

    /** The least the factor can be set to, in L per mB. */
    public static final double MIN_LITRES_PER_MILLIBUCKET = 0.001;

    /** The most the factor can be set to, in L per mB. */
    public static final double MAX_LITRES_PER_MILLIBUCKET = 1000.0;

    private Millibuckets() {}

    /**
     * The server's factor: the litres of gas, at standard air's pressure and temperature, that 1 mB
     * is. Ask each time, since a config edit applies from the next read. Without ZAS³, and on a
     * client, {@link #DEFAULT_LITRES_PER_MILLIBUCKET}.
     *
     * <p>Thread: any.
     *
     * @return litres per mB, from {@link #MIN_LITRES_PER_MILLIBUCKET} to {@link
     *     #MAX_LITRES_PER_MILLIBUCKET}
     */
    @AnyThread
    public static double litresPerMillibucket() {
        double litres = ZasApi.provider().litresPerMillibucket();
        return litres >= MIN_LITRES_PER_MILLIBUCKET && litres <= MAX_LITRES_PER_MILLIBUCKET
                ? litres
                : DEFAULT_LITRES_PER_MILLIBUCKET;
    }

    /**
     * The server's factor as conversions use it, {@link #molesPerMillibucket(double)} of {@link
     * #litresPerMillibucket()}. Ask each time.
     *
     * <p>Thread: any.
     *
     * @return moles per mB, above 0
     */
    @AnyThread
    public static double molesPerMillibucket() {
        return molesPerMillibucket(litresPerMillibucket());
    }

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
        return STANDARD_PRESSURE * litresPerMillibucket / (GasMixture.GAS_CONSTANT * STANDARD_TEMPERATURE);
    }

    /**
     * The moles in an amount. Exact but for floating point, so nothing is gained or lost on the way
     * into ZAS³.
     *
     * @param millibuckets the amount, not negative
     * @param molesPerMillibucket the factor, from {@link #molesPerMillibucket(double)}
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
     * The whole millibuckets an amount of gas makes, rounded down: whoever converts takes {@link
     * #moles} of this many out of the gas and leaves the rest where it is.
     *
     * <p>The answer is the most millibuckets whose {@link #moles} are no more than the gas, by the
     * same multiplication {@code moles} does. So what the result holds is never more than the gas,
     * to the last bit, and millibuckets turned into moles and back come out as they went in.
     *
     * @param moles the gas, in moles; a negative or non-finite amount makes none
     * @param molesPerMillibucket the factor, from {@link #molesPerMillibucket(double)}
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
