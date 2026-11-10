package io.github.west3436.zas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.config.ZasClientConfig.PressureUnit;
import org.junit.jupiter.api.Test;

/** How the HUD writes a pressure in each display unit (CLIENT-006, CLIENT-008). */
class HudReadoutTest {
    /** Chlorine where the Mekanism adapter's profile makes it a danger, as a float carries it. */
    private static final double DANGER = 0.001999991f;

    /** What is left of it a minute and a half after its room is opened: a warning. */
    private static final double WARNING = 0.00093;

    @Test
    void client_008_a_trace_of_a_toxic_gas_has_two_significant_figures_in_each_unit() {
        assertEquals("0.0020 kPa", HudReadout.toxin(DANGER, PressureUnit.KPA));
        assertEquals("0.0019 kPa", HudReadout.toxin(0.00193, PressureUnit.KPA));
        assertEquals("0.00093 kPa", HudReadout.toxin(WARNING, PressureUnit.KPA));
        assertEquals("0.00029 psi", HudReadout.toxin(DANGER, PressureUnit.PSI));
        assertEquals("0.00013 psi", HudReadout.toxin(WARNING, PressureUnit.PSI));
        assertEquals("0.000020 atm", HudReadout.toxin(DANGER, PressureUnit.ATM));
        assertEquals("0.0000092 atm", HudReadout.toxin(WARNING, PressureUnit.ATM));
        assertEquals("0.000020 bar", HudReadout.toxin(DANGER, PressureUnit.BAR));
        assertEquals("0.0000093 bar", HudReadout.toxin(WARNING, PressureUnit.BAR));
    }

    /** A trace is under a tenth of the unit shown, which is a different pressure in each unit. */
    @Test
    void client_008_a_trace_is_under_a_tenth_of_the_unit_shown() {
        assertEquals("0.5 kPa", HudReadout.toxin(0.5, PressureUnit.KPA));
        assertEquals("0.073 psi", HudReadout.toxin(0.5, PressureUnit.PSI));
        assertEquals("0.0049 atm", HudReadout.toxin(0.5, PressureUnit.ATM));
        assertEquals("0.0050 bar", HudReadout.toxin(0.5, PressureUnit.BAR));
        for (PressureUnit unit : PressureUnit.values()) {
            assertEquals("0.050 " + unit.symbol(), HudReadout.toxin(0.05 / unit.fromKpa(1), unit), unit.toString());
        }
    }

    @Test
    void client_008_zero_and_a_tenth_or_more_are_written_as_before() {
        assertEquals("0.0 kPa", HudReadout.toxin(0, PressureUnit.KPA));
        assertEquals("0.00 psi", HudReadout.toxin(0, PressureUnit.PSI));
        assertEquals("0.000 atm", HudReadout.toxin(0, PressureUnit.ATM));
        assertEquals("0.000 bar", HudReadout.toxin(0, PressureUnit.BAR));
        for (PressureUnit unit : PressureUnit.values()) {
            for (double shown : new double[] {0, 0.1, 0.12, 0.96, 6.04, 21.3}) {
                double kPa = shown / unit.fromKpa(1);
                assertEquals(
                        HudReadout.pressure(kPa, 1, unit), HudReadout.toxin(kPa, unit), shown + " " + unit.symbol());
            }
        }
        // A trace that rounds up to a tenth is written as a tenth is.
        assertEquals("0.1 kPa", HudReadout.toxin(0.0996, PressureUnit.KPA));
        assertEquals("0.10 psi", HudReadout.toxin(0.0996 / PressureUnit.PSI.fromKpa(1), PressureUnit.PSI));
    }

    /** The pressure row and the oxygen row are as they were: a little oxygen still reads to a tenth of a kPa. */
    @Test
    void client_008_the_pressure_and_oxygen_rows_keep_their_decimals() {
        assertEquals("101 kPa", HudReadout.pressure(101.325, 0, PressureUnit.KPA));
        assertEquals("21.3 kPa", HudReadout.pressure(21.28, 1, PressureUnit.KPA));
        assertEquals("0.0 kPa", HudReadout.pressure(0.002, 1, PressureUnit.KPA));
        assertEquals("0 kPa", HudReadout.pressure(0.002, 0, PressureUnit.KPA));
        assertEquals("14.7 psi", HudReadout.pressure(101.325, 0, PressureUnit.PSI));
        assertEquals("3.09 psi", HudReadout.pressure(21.28, 1, PressureUnit.PSI));
        assertEquals("1.00 atm", HudReadout.pressure(101.325, 0, PressureUnit.ATM));
        assertEquals("0.210 atm", HudReadout.pressure(21.28, 1, PressureUnit.ATM));
        assertEquals("1.01 bar", HudReadout.pressure(101.325, 0, PressureUnit.BAR));
        assertEquals("0.213 bar", HudReadout.pressure(21.28, 1, PressureUnit.BAR));
    }
}
