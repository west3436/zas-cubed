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

    /** A trace of a gas keeps two significant figures in its partial pressure and in its share (CONT-034). */
    @Test
    void cont_034_a_trace_of_a_gas_has_two_significant_figures() {
        assertEquals("0.0020 kPa", Readout.partialPressure(0.001999991));
        assertEquals("0.00093 kPa", Readout.partialPressure(0.00093));
        assertEquals("0.050 kPa", Readout.partialPressure(0.05));
        assertEquals("0.0020", Readout.percentNumber(0.001999991 / 101.325));
        assertEquals("0.00092", Readout.percentNumber(0.00093 / 101.325));
        assertEquals("0.0050", Readout.percentNumber(0.00005));
    }

    /** A trace has no more than seven decimals; one that is zero there reads 0.000, as it did (CONT-034). */
    @Test
    void cont_034_a_trace_has_no_more_than_seven_decimals() {
        assertEquals("0.00091 kPa", Readout.partialPressure(0.00091));
        assertEquals("0.000019 kPa", Readout.partialPressure(0.000019));
        assertEquals("0.000018", Readout.percentNumber(0.00000018));
        assertEquals("0.0000010 kPa", Readout.partialPressure(0.00000099));
        assertEquals("0.0000001 kPa", Readout.partialPressure(0.00000012));
        assertEquals("0.000 kPa", Readout.partialPressure(0.00000004));
        assertEquals("0.000 kPa", Readout.partialPressure(3.2e-11));
        assertEquals("0.000", Readout.percentNumber(3.2e-13));
    }

    @Test
    void cont_034_a_gas_that_is_no_trace_reads_as_before() {
        assertEquals("0 kPa", Readout.partialPressure(0));
        assertEquals("0.100 kPa", Readout.partialPressure(0.0996));
        assertEquals("0.12 kPa", Readout.partialPressure(0.12));
        assertEquals("21.3 kPa", Readout.partialPressure(21.28));
        assertEquals("101.3 kPa", Readout.partialPressure(101.325));
        assertEquals("0", Readout.percentNumber(0));
        assertEquals("0.93", Readout.percentNumber(0.0093));
        assertEquals("78.1", Readout.percentNumber(0.781));
        assertEquals("100", Readout.percentNumber(1));
        // Every other reading of a window keeps the decimals its size needs.
        assertEquals("0.002 kPa", Readout.pressure(0.001999991));
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
