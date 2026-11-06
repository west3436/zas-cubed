package io.github.west3436.zas.atmos.client.tgui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How a GUI writes pressures and temperatures, here in the defaults, kPa and °C (CLIENT-006). */
class ReadoutTest {
    @Test
    void client_006_a_number_has_the_decimals_its_size_needs() {
        assertEquals("0", Readout.number(0));
        assertEquals("0.005", Readout.number(0.005));
        assertEquals("0.12", Readout.number(0.12));
        assertEquals("5.00", Readout.number(5));
        assertEquals("81", Readout.number(81));
        assertEquals("101.3", Readout.number(101.325));
        assertEquals("1,013", Readout.number(1013.25));
    }

    @Test
    void client_006_readings_carry_the_default_units() {
        assertEquals("101.3 kPa", Readout.pressure(101.325));
        assertEquals("20.0 °C", Readout.temperature(293.15));
        assertEquals("-30.1", Readout.temperatureNumber(243.0));
    }

    @Test
    void client_006_a_temperature_just_under_freezing_is_not_negative_zero() {
        assertEquals("0.0", Readout.temperatureNumber(273.14));
    }
}
