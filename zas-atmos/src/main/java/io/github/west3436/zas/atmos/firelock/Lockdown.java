package io.github.west3436.zas.atmos.firelock;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Why a closed firelock is locked down (CONT-093): the pressure difference across it is at least
 * 25 kPa, or either side is at least 323.15 K (50 °C) or at most 273.15 K (0 °C). Pure logic.
 *
 * @param reason which threshold was reached
 * @param value the reading that reached it: the difference in kPa, or the side's temperature in K
 */
public record Lockdown(Reason reason, double value) {
    /** The pressure difference that locks a firelock down, in kPa. */
    public static final double PRESSURE_KPA = 25.0;
    /** A side at or above this locks a firelock down, in K (50 °C). */
    public static final double HOT_K = 323.15;
    /** A side at or below this locks a firelock down, in K (0 °C). */
    public static final double COLD_K = 273.15;

    public enum Reason {
        PRESSURE,
        HOT,
        COLD;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The lockdown for the readings on the two sides, the pressure difference first, then heat,
     * then cold.
     *
     * @param pressureA one side's pressure, kPa
     * @param temperatureA that side's temperature, K
     * @param pressureB the other side's pressure, kPa
     * @param temperatureB that side's temperature, K
     * @return the reason it locks down, or empty if it doesn't
     */
    public static Optional<Lockdown> of(double pressureA, double temperatureA, double pressureB, double temperatureB) {
        double dp = Math.abs(pressureA - pressureB);
        if (dp >= PRESSURE_KPA) {
            return Optional.of(new Lockdown(Reason.PRESSURE, dp));
        }
        double hot = Math.max(temperatureA, temperatureB);
        if (hot >= HOT_K) {
            return Optional.of(new Lockdown(Reason.HOT, hot));
        }
        double cold = Math.min(temperatureA, temperatureB);
        if (cold <= COLD_K) {
            return Optional.of(new Lockdown(Reason.COLD, cold));
        }
        return Optional.empty();
    }

    /** The warning a player gets when the firelock refuses to open: its reason, without the reading. */
    public Component message() {
        return FirelockBlock.lockedMessage(reason.key());
    }
}
