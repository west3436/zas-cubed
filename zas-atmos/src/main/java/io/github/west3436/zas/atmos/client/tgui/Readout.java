package io.github.west3436.zas.atmos.client.tgui;

import io.github.west3436.zas.api.client.DisplayUnits;
import java.util.Locale;

/**
 * Pressures and temperatures as a GUI or an item's tooltip shows them, in the player's units
 * (CLIENT-006). It refers to no client-only class, so an item, which a dedicated server also loads,
 * can use it.
 */
public final class Readout {
    private Readout() {}

    /** A pressure in kPa, with its unit. */
    public static String pressure(double kpa) {
        return pressureNumber(kpa) + " " + DisplayUnits.pressureSymbol();
    }

    /**
     * A gas's partial pressure in kPa, with its unit: as {@link #pressure}, except that a trace keeps
     * two significant figures ({@link #traceNumber}), so that a little of a gas doesn't read as none.
     */
    public static String partialPressure(double kpa) {
        return traceNumber(DisplayUnits.pressure(kpa)) + " " + DisplayUnits.pressureSymbol();
    }

    /** A pressure in kPa, as a number in the player's unit. */
    public static String pressureNumber(double kpa) {
        return number(DisplayUnits.pressure(kpa));
    }

    /** A temperature in K, with its unit. */
    public static String temperature(double kelvin) {
        return temperatureNumber(kelvin) + " " + DisplayUnits.temperatureSymbol();
    }

    /** A temperature in K, as a number in the player's unit. */
    public static String temperatureNumber(double kelvin) {
        String text = String.format(Locale.ROOT, "%,.1f", DisplayUnits.temperature(kelvin));
        return text.equals("-0.0") ? "0.0" : text;
    }

    /**
     * A share, such as a gas's of a mixture, as a number of percent: 78.1, 0.93 or 0.0050. A trace
     * keeps two significant figures ({@link #traceNumber}).
     */
    public static String percentNumber(double fraction) {
        return traceNumber(fraction * 100);
    }

    /**
     * A gas's reading: as {@link #number}, except that a trace, above zero and under 0.1 in the unit
     * shown, keeps two significant figures as it does on the HUD ({@link DisplayUnits#reading}):
     * 0.0020 and 0.00093, where {@link #number} would write 0.002 and 0.001. It has no more than five
     * decimals, so 0.000019 is 0.00002, and a trace that is zero there reads 0.000, as it did.
     */
    static String traceNumber(double value) {
        return value > 0 && value < 0.1 ? DisplayUnits.reading(value, 3) : number(value);
    }

    /**
     * A value with as many decimals as its size needs: thousands with separators and none, tens with
     * one unless it's whole, down to three for the smallest thresholds, such as CO's 0.005 kPa.
     */
    static String number(double value) {
        double size = Math.abs(value);
        if (size == 0) {
            return "0";
        }
        if (size >= 1000 || (size >= 10 && Math.abs(value - Math.rint(value)) < 1e-9)) {
            return String.format(Locale.ROOT, "%,.0f", value);
        }
        if (size >= 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, size >= 0.1 ? "%.2f" : "%.3f", value);
    }
}
