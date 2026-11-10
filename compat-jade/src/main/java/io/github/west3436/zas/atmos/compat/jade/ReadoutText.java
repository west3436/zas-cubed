package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.atmos.client.tgui.Readout;

/**
 * The numbers a reading is drawn with, as the device windows write them: in the player's units
 * (COMPAT-030, API-063), with as many decimals as each value's size needs. No words.
 */
final class ReadoutText {
    private ReadoutText() {}

    /** A pressure in kPa, in the player's unit with its symbol: {@code 101.3 kPa}. */
    static String pressure(float kpa) {
        return Readout.pressure(kpa);
    }

    /** A temperature in K, in the player's unit with its symbol: {@code 20.0 °C}. */
    static String temperature(float kelvin) {
        return Readout.temperature(kelvin);
    }

    /**
     * A listed gas's share as a whole number of percent, without the sign. A gas is only listed
     * from half a percent up, so it never reads 0.
     */
    static String percent(float fraction) {
        return String.valueOf(Math.max(1, Math.round(fraction * 100)));
    }
}
