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

    /** A share, such as a gas's of a mixture, as a number of percent: 78.1, 0.93 or 0.005. */
    public static String percentNumber(double fraction) {
        return number(fraction * 100);
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
