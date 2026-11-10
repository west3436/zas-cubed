package io.github.west3436.zas.api.client;

import io.github.west3436.zas.api.AnyThread;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * The units this player has chosen to read pressures and temperatures in (CLIENT-006, API-063), for
 * a mod's own GUIs. The API's values are always in kPa and K (A-04): convert only what you show.
 *
 * <p>Without ZAS³, and on a dedicated server, these answer in the defaults, kPa and °C, so a mod can
 * call them unconditionally, from code that also loads on a server (D-17).
 */
public final class DisplayUnits {
    private static final DisplayUnitProvider NONE = new DisplayUnitProvider() {};

    /** A reading above zero and under this is a trace ({@link #reading}). */
    private static final BigDecimal TRACE_BELOW = new BigDecimal("0.1");

    private static final MathContext TWO_FIGURES = new MathContext(2, RoundingMode.HALF_UP);

    private static volatile DisplayUnitProvider provider = NONE;

    private DisplayUnits() {}

    /**
     * A pressure in the player's unit.
     *
     * <p>Thread: any.
     *
     * @param kpa the pressure in kPa
     * @return the pressure in the unit {@link #pressureSymbol} names
     */
    @AnyThread
    public static double pressure(double kpa) {
        return provider.pressure(kpa);
    }

    /**
     * The symbol of the player's pressure unit, such as {@code kPa}.
     *
     * <p>Thread: any.
     *
     * @return the symbol
     */
    @AnyThread
    public static String pressureSymbol() {
        return provider.pressureSymbol();
    }

    /**
     * A temperature in the player's unit.
     *
     * <p>Thread: any.
     *
     * @param kelvin the temperature in K
     * @return the temperature in the unit {@link #temperatureSymbol} names
     */
    @AnyThread
    public static double temperature(double kelvin) {
        return provider.temperature(kelvin);
    }

    /**
     * The symbol of the player's temperature unit, such as {@code °C}.
     *
     * <p>Thread: any.
     *
     * @return the symbol
     */
    @AnyThread
    public static String temperatureSymbol() {
        return provider.temperatureSymbol();
    }

    /**
     * A reading's digits as ZAS³'s HUD and analyzer write a gas's partial pressure or share: with
     * {@code decimals} decimals, except that a trace, a reading above zero and under 0.1, keeps two
     * significant figures, so that a little of a gas doesn't read as none. With one decimal,
     * {@code 0.0019} and {@code 0.00093} are written so, where they would both be {@code 0.0};
     * {@code 0} is {@code 0.0} and {@code 21.28} is {@code 21.3}. A trace that rounds up to 0.1 is
     * written as 0.1 is.
     *
     * <p>The value is the one shown, so convert a pressure first ({@link #pressure}). The digits
     * have a dot and no grouping, whatever the player's language.
     *
     * <p>Thread: any.
     *
     * @param value the reading, in the unit it is shown in
     * @param decimals the decimals of a reading that is no trace, 0 or more
     * @return its digits
     * @throws IllegalArgumentException if {@code decimals} is negative
     */
    @AnyThread
    public static String reading(double value, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must be 0 or more: " + decimals);
        }
        if (value > 0 && value < 0.1) {
            BigDecimal figures = BigDecimal.valueOf(value).round(TWO_FIGURES);
            if (figures.compareTo(TRACE_BELOW) < 0) {
                // The place of the leading figure, then one more: 0.002 is written 0.0020.
                int leading = figures.precision() - figures.scale() - 1;
                return figures.setScale(1 - leading, RoundingMode.UNNECESSARY).toPlainString();
            }
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /**
     * Installs the real provider. Only ZAS³ calls this, once, while its client entry point is
     * constructed.
     *
     * <p>Thread: any; in practice the mod-loading thread.
     *
     * @param impl the provider
     * @throws IllegalStateException if a provider is already installed
     */
    @AnyThread
    public static synchronized void install(DisplayUnitProvider impl) {
        Objects.requireNonNull(impl, "impl");
        if (provider != NONE) {
            throw new IllegalStateException("a ZAS³ display unit provider is already installed: " + provider);
        }
        provider = impl;
    }
}
