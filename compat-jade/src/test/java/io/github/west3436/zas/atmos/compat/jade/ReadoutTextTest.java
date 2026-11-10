package io.github.west3436.zas.atmos.compat.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.api.client.DisplayUnitProvider;
import io.github.west3436.zas.api.client.DisplayUnits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The numbers a reading is drawn with (COMPAT-030). */
class ReadoutTextTest {
    /** Whether the player's units are psi and °F; otherwise the defaults, kPa and °C. */
    private static boolean imperial;

    /** The API takes one provider, once, so this one switches units for the tests. */
    @BeforeAll
    static void installUnits() {
        DisplayUnits.install(new DisplayUnitProvider() {
            @Override
            public double pressure(double kpa) {
                return imperial ? kpa * 0.1450377377 : kpa;
            }

            @Override
            public String pressureSymbol() {
                return imperial ? "psi" : "kPa";
            }

            @Override
            public double temperature(double kelvin) {
                return imperial ? kelvin * 9 / 5 - 459.67 : kelvin - 273.15;
            }

            @Override
            public String temperatureSymbol() {
                return imperial ? "°F" : "°C";
            }
        });
    }

    @AfterEach
    void defaults() {
        imperial = false;
    }

    @Test
    void compat_030_pressure_and_temperature_in_the_default_units() {
        assertEquals("101.3 kPa", ReadoutText.pressure(101.325F));
        assertEquals("20.0 °C", ReadoutText.temperature(293.15F));
    }

    @Test
    void compat_030_pressure_and_temperature_follow_the_players_units() {
        imperial = true;
        assertEquals("14.7 psi", ReadoutText.pressure(101.325F));
        assertEquals("68.0 °F", ReadoutText.temperature(293.15F));
    }

    @Test
    void compat_030_a_number_has_the_decimals_its_size_needs() {
        assertEquals("0 kPa", ReadoutText.pressure(0F));
        assertEquals("0.50 kPa", ReadoutText.pressure(0.5F));
        assertEquals("100 kPa", ReadoutText.pressure(100F));
        assertEquals("4,500 kPa", ReadoutText.pressure(4500.4F));
        assertEquals("-40.0 °C", ReadoutText.temperature(233.15F));
        assertEquals("1,226.9 °C", ReadoutText.temperature(1500F));
    }

    @Test
    void compat_031_a_share_is_a_whole_number_of_percent_and_never_zero() {
        assertEquals("79", ReadoutText.percent(0.79F));
        assertEquals("21", ReadoutText.percent(0.21F));
        assertEquals("100", ReadoutText.percent(1F));
        assertEquals("100", ReadoutText.percent(0.996F));
        assertEquals("1", ReadoutText.percent(0.005F));
        assertEquals("2", ReadoutText.percent(0.015F));
    }
}
