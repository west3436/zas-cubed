package io.github.west3436.zas.client;

import io.github.west3436.zas.api.client.DisplayUnits;
import io.github.west3436.zas.config.ZasClientConfig.PressureUnit;
import java.util.Locale;

/**
 * How the atmosphere HUD writes a pressure, in a display unit (CLIENT-006, CLIENT-008). It refers to
 * no client-only class, so a unit test can load it.
 */
final class HudReadout {
    private HudReadout() {}

    /**
     * A pressure to the HUD's resolution: 1 kPa for pressure, 0.1 kPa for a partial pressure ({@code
     * extra} = 1), with as many more decimals as the unit needs.
     */
    static String pressure(double kPa, int extra, PressureUnit unit) {
        return String.format(Locale.ROOT, "%." + decimals(unit, extra) + "f %s", unit.fromKpa(kPa), unit.symbol());
    }

    /**
     * A toxic gas's partial pressure: as {@link #pressure} writes a partial pressure, except that a
     * trace, above zero and under 0.1 in the unit, keeps two significant figures ({@link
     * DisplayUnits#reading}). A gas that harms at a thousandth of a kPa then has a number beside its
     * alert, not "0.0".
     */
    static String toxin(double kPa, PressureUnit unit) {
        return DisplayUnits.reading(unit.fromKpa(kPa), decimals(unit, 1)) + " " + unit.symbol();
    }

    private static int decimals(PressureUnit unit, int extra) {
        return extra
                + switch (unit) {
                    case KPA -> 0;
                    case PSI -> 1;
                    case ATM, BAR -> 2;
                };
    }
}
