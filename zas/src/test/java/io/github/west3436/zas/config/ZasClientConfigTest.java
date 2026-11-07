package io.github.west3436.zas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.config.ZasClientConfig.PressureUnit;
import io.github.west3436.zas.config.ZasClientConfig.TemperatureUnit;
import org.junit.jupiter.api.Test;

/** The display units a player can choose, against values they're known by (CLIENT-006). */
class ZasClientConfigTest {
    private static final double TOLERANCE = 1e-3;

    @Test
    void client_006_pressures_convert_to_each_unit() {
        assertEquals(101.325, PressureUnit.KPA.fromKpa(101.325), TOLERANCE);
        assertEquals(1.0, PressureUnit.ATM.fromKpa(101.325), TOLERANCE);
        assertEquals(1.01325, PressureUnit.BAR.fromKpa(101.325), TOLERANCE);
        assertEquals(14.696, PressureUnit.PSI.fromKpa(101.325), TOLERANCE);
    }

    @Test
    void client_006_temperatures_convert_to_each_unit() {
        assertEquals(373.15, TemperatureUnit.KELVIN.fromKelvin(373.15), TOLERANCE);
        assertEquals(100.0, TemperatureUnit.CELSIUS.fromKelvin(373.15), TOLERANCE);
        assertEquals(212.0, TemperatureUnit.FAHRENHEIT.fromKelvin(373.15), TOLERANCE);
        assertEquals(-273.15, TemperatureUnit.CELSIUS.fromKelvin(0), TOLERANCE);
        assertEquals(-459.67, TemperatureUnit.FAHRENHEIT.fromKelvin(0), TOLERANCE);
    }
}
