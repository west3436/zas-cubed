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
     * A toxic gas's partial pressure. While its row is at warning or worse, a trace, above zero and
     * under 0.1 in the unit, keeps two significant figures, with at most four decimals more than
     * the unit's own ({@link DisplayUnits#reading}): five in kPa, six in psi, seven in atm and bar.
     * A gas that harms at a ten-thousandth of a kPa then has a number beside its alert in every
     * unit, not "0.0". A safe row is written as {@link #pressure} writes a partial pressure, so the
     * little carbon dioxide of a room that was breathed in reads as none.
     *
     * @param alert whether the row is at warning or worse
     */
    static String toxin(double kPa, boolean alert, PressureUnit unit) {
        if (!alert) {
            return pressure(kPa, 1, unit);
        }
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
