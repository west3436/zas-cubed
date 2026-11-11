package io.github.west3436.zas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.west3436.zas.config.ZasClientConfig.PressureUnit;
import org.junit.jupiter.api.Test;

/** How the HUD writes a pressure in each display unit (CLIENT-006, CLIENT-008). */
class HudReadoutTest {
    /** Chlorine where the Mekanism adapter's profile makes it a danger, as a float carries it. */
    private static final double DANGER = 0.001999991f;

    /** What is left of it a minute and a half after its room is opened: a warning. */
    private static final double WARNING = 0.00093;

    /** The lowest threshold of a gas we ship: the Mekanism adapter's chlorine, at its warning. */
    private static final double LOWEST_THRESHOLD = 0.0001;

    /** The carbon dioxide of a room that was breathed in for two minutes: safe. */
    private static final double BREATHED_OUT = 0.00062;

    @Test
    void client_008_a_trace_of_a_gas_at_warning_or_worse_has_two_significant_figures_in_each_unit() {
        assertEquals("0.0020 kPa", HudReadout.toxin(DANGER, true, PressureUnit.KPA));
        assertEquals("0.0019 kPa", HudReadout.toxin(0.00193, true, PressureUnit.KPA));
        assertEquals("0.00093 kPa", HudReadout.toxin(WARNING, true, PressureUnit.KPA));
        assertEquals("0.00029 psi", HudReadout.toxin(DANGER, true, PressureUnit.PSI));
        assertEquals("0.00013 psi", HudReadout.toxin(WARNING, true, PressureUnit.PSI));
        assertEquals("0.000020 atm", HudReadout.toxin(DANGER, true, PressureUnit.ATM));
        assertEquals("0.0000092 atm", HudReadout.toxin(WARNING, true, PressureUnit.ATM));
        assertEquals("0.000020 bar", HudReadout.toxin(DANGER, true, PressureUnit.BAR));
        assertEquals("0.0000093 bar", HudReadout.toxin(WARNING, true, PressureUnit.BAR));
    }

    /**
     * A trace has at most four decimals more than its unit's own: five in kPa, six in psi, seven in
     * atm and bar. So the lowest threshold of a shipped gas, chlorine's warning at 0.0001 kPa, is a
     * number in every unit, and so is all a yellow or red row can read.
     */
    @Test
    void client_008_the_lowest_shipped_threshold_is_no_zero_in_any_unit() {
        assertEquals("0.00010 kPa", HudReadout.toxin(LOWEST_THRESHOLD, true, PressureUnit.KPA));
        assertEquals("0.000015 psi", HudReadout.toxin(LOWEST_THRESHOLD, true, PressureUnit.PSI));
        assertEquals("0.0000010 atm", HudReadout.toxin(LOWEST_THRESHOLD, true, PressureUnit.ATM));
        assertEquals("0.0000010 bar", HudReadout.toxin(LOWEST_THRESHOLD, true, PressureUnit.BAR));
        // A step above it, the second figure is there in every unit.
        assertEquals("0.00012 kPa", HudReadout.toxin(0.00012, true, PressureUnit.KPA));
        assertEquals("0.000017 psi", HudReadout.toxin(0.00012, true, PressureUnit.PSI));
        assertEquals("0.0000012 atm", HudReadout.toxin(0.00012, true, PressureUnit.ATM));
        assertEquals("0.0000012 bar", HudReadout.toxin(0.00012, true, PressureUnit.BAR));
        for (PressureUnit unit : PressureUnit.values()) {
            String zero = HudReadout.pressure(0, 1, unit);
            for (double kPa = LOWEST_THRESHOLD; kPa < 0.2; kPa *= 1.07) {
                assertNotEquals(zero, HudReadout.toxin(kPa, true, unit), kPa + " kPa in " + unit);
            }
        }
        // Far under any threshold, a trace is zero at those decimals and is written as zero is.
        assertEquals("0.00001 kPa", HudReadout.toxin(0.000012, true, PressureUnit.KPA));
        assertEquals("0.0 kPa", HudReadout.toxin(0.0000049, true, PressureUnit.KPA));
        for (PressureUnit unit : PressureUnit.values()) {
            assertEquals(HudReadout.pressure(0, 1, unit), HudReadout.toxin(3.2e-11, true, unit), unit.toString());
        }
    }

    /** A trace is under a tenth of the unit shown, which is a different pressure in each unit. */
    @Test
    void client_008_a_trace_is_under_a_tenth_of_the_unit_shown() {
        assertEquals("0.5 kPa", HudReadout.toxin(0.5, true, PressureUnit.KPA));
        assertEquals("0.073 psi", HudReadout.toxin(0.5, true, PressureUnit.PSI));
        assertEquals("0.0049 atm", HudReadout.toxin(0.5, true, PressureUnit.ATM));
        assertEquals("0.0050 bar", HudReadout.toxin(0.5, true, PressureUnit.BAR));
        for (PressureUnit unit : PressureUnit.values()) {
            assertEquals(
                    "0.050 " + unit.symbol(), HudReadout.toxin(0.05 / unit.fromKpa(1), true, unit), unit.toString());
        }
    }

    @Test
    void client_008_zero_and_a_tenth_or_more_are_written_as_before() {
        assertEquals("0.0 kPa", HudReadout.toxin(0, true, PressureUnit.KPA));
        assertEquals("0.00 psi", HudReadout.toxin(0, true, PressureUnit.PSI));
        assertEquals("0.000 atm", HudReadout.toxin(0, true, PressureUnit.ATM));
        assertEquals("0.000 bar", HudReadout.toxin(0, true, PressureUnit.BAR));
        for (PressureUnit unit : PressureUnit.values()) {
            for (double shown : new double[] {0, 0.1, 0.12, 0.96, 6.04, 21.3}) {
                double kPa = shown / unit.fromKpa(1);
                assertEquals(
                        HudReadout.pressure(kPa, 1, unit),
                        HudReadout.toxin(kPa, true, unit),
                        shown + " " + unit.symbol());
            }
        }
        // A trace that rounds up to a tenth is written as a tenth is.
        assertEquals("0.1 kPa", HudReadout.toxin(0.0996, true, PressureUnit.KPA));
        assertEquals("0.10 psi", HudReadout.toxin(0.0996 / PressureUnit.PSI.fromKpa(1), true, PressureUnit.PSI));
    }

    /** A safe row, which shows only with the analyzer in hand, is written as it always was. */
    @Test
    void client_008_a_safe_row_keeps_its_one_decimal() {
        assertEquals("0.0 kPa", HudReadout.toxin(BREATHED_OUT, false, PressureUnit.KPA));
        assertEquals("0.00 psi", HudReadout.toxin(BREATHED_OUT, false, PressureUnit.PSI));
        assertEquals("0.000 atm", HudReadout.toxin(BREATHED_OUT, false, PressureUnit.ATM));
        assertEquals("0.000 bar", HudReadout.toxin(BREATHED_OUT, false, PressureUnit.BAR));
        assertEquals("0.0 kPa", HudReadout.toxin(0, false, PressureUnit.KPA));
        for (PressureUnit unit : PressureUnit.values()) {
            for (double kPa : new double[] {0, 0.0000049, BREATHED_OUT, DANGER, 0.05, 0.5, 4.9}) {
                assertEquals(
                        HudReadout.pressure(kPa, 1, unit), HudReadout.toxin(kPa, false, unit), kPa + " kPa in " + unit);
            }
        }
        // The same trace at warning has its figures.
        assertEquals("0.00062 kPa", HudReadout.toxin(BREATHED_OUT, true, PressureUnit.KPA));
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
