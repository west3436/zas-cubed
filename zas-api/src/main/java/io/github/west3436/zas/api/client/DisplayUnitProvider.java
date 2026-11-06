package io.github.west3436.zas.api.client;

/**
 * The service behind {@link DisplayUnits}. ZAS³ installs its implementation on the physical client
 * with {@link DisplayUnits#install}; until then, on a dedicated server, and when ZAS³ is absent, the
 * defaults here answer: kPa and °C (CLIENT-006).
 *
 * <p><b>Consumers don't call or implement this.</b> They use {@link DisplayUnits}. Every method has
 * a default, so that a provider built against an older API keeps working when a newer one is loaded
 * (architecture §6.2).
 */
public interface DisplayUnitProvider {
    /**
     * A pressure in the player's unit; see {@link DisplayUnits#pressure}.
     *
     * @param kpa the pressure in kPa
     * @return the pressure in the unit
     */
    default double pressure(double kpa) {
        return kpa;
    }

    /**
     * The player's pressure unit's symbol; see {@link DisplayUnits#pressureSymbol}.
     *
     * @return the symbol
     */
    default String pressureSymbol() {
        return "kPa";
    }

    /**
     * A temperature in the player's unit; see {@link DisplayUnits#temperature}.
     *
     * @param kelvin the temperature in K
     * @return the temperature in the unit
     */
    default double temperature(double kelvin) {
        return kelvin - 273.15;
    }

    /**
     * The player's temperature unit's symbol; see {@link DisplayUnits#temperatureSymbol}.
     *
     * @return the symbol
     */
    default String temperatureSymbol() {
        return "°C";
    }
}
