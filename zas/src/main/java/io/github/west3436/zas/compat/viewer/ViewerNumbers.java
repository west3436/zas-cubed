package io.github.west3436.zas.compat.viewer;

import io.github.west3436.zas.api.client.DisplayUnits;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * How the viewers write numbers: short, with no trailing zeros, and temperatures and pressures in
 * the units the player chose (CLIENT-006). Nothing here has a word in it, so nothing needs a
 * translation.
 */
public final class ViewerNumbers {
    private ViewerNumbers() {}

    /** A number to at most {@code decimals} places, with a true minus sign. */
    public static String number(double value, int decimals) {
        return plain(BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP));
    }

    /** A number to {@code digits} significant figures. */
    public static String significant(double value, int digits) {
        return plain(BigDecimal.valueOf(value).round(new MathContext(digits, RoundingMode.HALF_UP)));
    }

    /**
     * A reaction's coefficient, as short as it can be written, since it is drawn over the gas: 2, ½,
     * 1½, and any other fraction to two places.
     */
    public static String amount(double moles) {
        double whole = Math.floor(moles);
        String part = moles < 0 ? "" : fraction(moles - whole);
        if (part.isEmpty()) {
            return number(moles, 2);
        }
        return whole == 0 ? part : number(whole, 0) + part;
    }

    /** The one character for a quarter, a half or three quarters, or nothing for any other part. */
    private static String fraction(double part) {
        if (part == 0.25) {
            return "¼";
        }
        if (part == 0.5) {
            return "½";
        }
        if (part == 0.75) {
            return "¾";
        }
        return "";
    }

    /** A share of a mixture, 0 to 1, as a percentage. */
    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%s%%", number(fraction * 100, 2));
    }

    /** A range of shares, such as 5–15%. */
    public static String percentRange(double from, double to) {
        return String.format(Locale.ROOT, "%s–%s%%", number(from * 100, 2), number(to * 100, 2));
    }

    /** A temperature in the player's unit, with the unit's symbol. */
    public static String temperature(double kelvin) {
        return String.format(Locale.ROOT, "%s %s", degrees(kelvin), DisplayUnits.temperatureSymbol());
    }

    /** A range of temperatures in the player's unit, with the unit's symbol once. */
    public static String temperatureRange(double fromKelvin, double toKelvin) {
        return String.format(
                Locale.ROOT, "%s–%s %s", degrees(fromKelvin), degrees(toKelvin), DisplayUnits.temperatureSymbol());
    }

    /** A pressure in the player's unit, with the unit's symbol. */
    public static String pressure(double kpa) {
        return String.format(
                Locale.ROOT, "%s %s", significant(DisplayUnits.pressure(kpa), 3), DisplayUnits.pressureSymbol());
    }

    /** An energy for each mole, given in J/mol, as a number of kJ/mol. */
    public static String kilojoules(double joules) {
        return number(Math.abs(joules) / 1000, 2);
    }

    /** A molar mass, given in kg/mol, as a number of g/mol. */
    public static String grams(double kilograms) {
        return number(kilograms * 1000, 3);
    }

    /**
     * The share of the reactants a first-order rate constant converts in one second, as a number of
     * percent: 1 − e^(−k).
     */
    public static String shareInOneSecond(double perSecond) {
        return significant(-Math.expm1(-perSecond) * 100, 2);
    }

    private static String degrees(double kelvin) {
        return number(DisplayUnits.temperature(kelvin), 0);
    }

    private static String plain(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString().replace('-', '−');
    }
}
