package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /** A trace keeps two significant figures; every other reading has the decimals asked for. */
    @Test
    void api_063_a_trace_reading_keeps_two_significant_figures() {
        assertEquals("0.0019", DisplayUnits.reading(0.0019, 1));
        assertEquals("0.00093", DisplayUnits.reading(0.00093, 1));
        assertEquals("0.0020", DisplayUnits.reading(0.002, 1));
        assertEquals("0.0020", DisplayUnits.reading(0.001999991f, 1));
        assertEquals("0.050", DisplayUnits.reading(0.05, 1));
        assertEquals("0.099", DisplayUnits.reading(0.0994, 1));
        assertEquals("0.010", DisplayUnits.reading(0.00996, 1));
        assertEquals("0.00000012", DisplayUnits.reading(1.23e-7, 1));
        // The same digits whatever the decimals asked for.
        assertEquals("0.0019", DisplayUnits.reading(0.0019, 0));
        assertEquals("0.0019", DisplayUnits.reading(0.0019, 3));
    }

    @Test
    void api_063_a_reading_that_is_no_trace_has_its_decimals() {
        assertEquals("0.0", DisplayUnits.reading(0, 1));
        assertEquals("0.000", DisplayUnits.reading(0, 3));
        assertEquals("0.1", DisplayUnits.reading(0.1, 1));
        assertEquals("0.3", DisplayUnits.reading(0.25, 1));
        assertEquals("21.3", DisplayUnits.reading(21.28, 1));
        assertEquals("101", DisplayUnits.reading(101.325, 0));
        assertEquals("1013.25", DisplayUnits.reading(1013.25, 2));
        // A trace that rounds up to 0.1 is written as 0.1 is.
        assertEquals("0.1", DisplayUnits.reading(0.0996, 1));
        assertEquals("0.100", DisplayUnits.reading(0.0996, 3));
        // Not a reading: no trace.
        assertEquals("-1.0", DisplayUnits.reading(-1, 1));
        assertEquals("-0.05", DisplayUnits.reading(-0.05, 2));
        assertEquals("NaN", DisplayUnits.reading(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> DisplayUnits.reading(1, -1));
    }
}
