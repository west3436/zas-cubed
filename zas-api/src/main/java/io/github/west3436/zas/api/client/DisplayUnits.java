package io.github.west3436.zas.api.client;

import io.github.west3436.zas.api.AnyThread;
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
