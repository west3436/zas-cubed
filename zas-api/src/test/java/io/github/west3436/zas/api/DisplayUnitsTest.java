package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.api.client.DisplayUnits;
import org.junit.jupiter.api.Test;

/** Without ZAS³ the display units are the defaults, kPa and °C (API-063, CLIENT-006, D-17). */
class DisplayUnitsTest {
    @Test
    void api_063_units_default_to_kpa_and_celsius_without_zas() {
        assertEquals(101.325, DisplayUnits.pressure(101.325), 1e-9);
        assertEquals("kPa", DisplayUnits.pressureSymbol());
        assertEquals(20.0, DisplayUnits.temperature(293.15), 1e-9);
        assertEquals("°C", DisplayUnits.temperatureSymbol());
    }
}
