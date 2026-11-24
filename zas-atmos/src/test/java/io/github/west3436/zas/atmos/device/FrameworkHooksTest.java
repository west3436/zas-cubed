package io.github.west3436.zas.atmos.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.west3436.ss13.client.tgui.Readout;
import io.github.west3436.zas.api.client.DisplayUnitProvider;
import io.github.west3436.zas.api.client.DisplayUnits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The windows' and tooltips' readings come from SS13 Framework's {@link Readout}, in the units ZAS³
 * installs (CLIENT-006): each of the client's unit settings reads as it did when the readout was
 * ZAS³'s own.
 */
class FrameworkHooksTest {
    /** A stand-in for the client's config: the units each setting gives, as zas's client config does. */
    private static final class Settings implements DisplayUnitProvider {
        volatile String pressure = "kPa";
        volatile String temperature = "°C";

        @Override
        public double pressure(double kpa) {
            return switch (pressure) {
                case "atm" -> kpa / 101.325;
                case "bar" -> kpa * 0.01;
                case "psi" -> kpa / 6.894_757;
                default -> kpa;
            };
        }

        @Override
        public String pressureSymbol() {
            return pressure;
        }

        @Override
        public double temperature(double kelvin) {
            return switch (temperature) {
                case "K" -> kelvin;
                case "°F" -> (kelvin - 273.15) * 9 / 5 + 32;
                default -> kelvin - 273.15;
            };
        }

        @Override
        public String temperatureSymbol() {
            return temperature;
        }
    }

    private static final Settings SETTINGS = new Settings();

    private static boolean installed;

    @BeforeAll
    static void install() {
        try {
            DisplayUnits.install(SETTINGS); // reads as the defaults until a test changes it
            installed = true;
        } catch (IllegalStateException alreadyInstalled) {
            installed = false;
        }
        Readout.useUnits(FrameworkHooks.playerUnits());
    }

    private static void with(String pressure, String temperature, Runnable check) {
        assumeTrue(installed, "another test installed the display units first");
        SETTINGS.pressure = pressure;
        SETTINGS.temperature = temperature;
        try {
            check.run();
        } finally {
            SETTINGS.pressure = "kPa";
            SETTINGS.temperature = "°C";
        }
    }

    @Test
    void client_006_the_defaults_read_as_before() {
        assertEquals("101.3 kPa", Readout.pressure(101.325));
        assertEquals("1,013 kPa", Readout.pressure(1013.25));
        assertEquals("0.0020 kPa", Readout.partialPressure(0.001999991));
        assertEquals("20.0 °C", Readout.temperature(293.15));
        assertEquals("0.0", Readout.temperatureNumber(273.14));
    }

    @Test
    void client_006_each_pressure_unit_reads_as_before() {
        with("atm", "°C", () -> {
            assertEquals("1.00 atm", Readout.pressure(101.325));
            assertEquals("90 atm", Readout.pressure(9119.25)); // whole tens have no decimals
            assertEquals("0.0010 atm", Readout.partialPressure(0.101325));
        });
        with("bar", "°C", () -> {
            assertEquals("1.01 bar", Readout.pressure(101.325));
            assertEquals("50.7 bar", Readout.pressure(5066.25));
        });
        with("psi", "°C", () -> {
            assertEquals("14.7 psi", Readout.pressure(101.325));
            assertEquals("1,323 psi", Readout.pressure(9120));
        });
    }

    @Test
    void client_006_each_temperature_unit_reads_as_before() {
        with("kPa", "K", () -> assertEquals("300.0 K", Readout.temperature(300)));
        with("kPa", "°F", () -> {
            assertEquals("212.0 °F", Readout.temperature(373.15));
            assertEquals("-459.7", Readout.temperatureNumber(0));
        });
        with("kPa", "°C", () -> assertEquals("-273.1", Readout.temperatureNumber(0.01)));
    }
}
