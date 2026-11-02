package io.github.west3436.zas.atmos.firelock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The firelock's lockdown thresholds (CONT-093). */
class LockdownTest {
    private static final double T = 293.15;

    @Test
    void cont_093_lockdown_at_25_kpa_difference() {
        assertTrue(Lockdown.of(101, T, 76.01, T).isEmpty(), "24.99 kPa");
        assertEquals(Optional.of(new Lockdown(Lockdown.Reason.PRESSURE, 25.0)), Lockdown.of(101, T, 76, T));
        assertEquals(
                Lockdown.Reason.PRESSURE,
                Lockdown.of(0, T, 101, T).orElseThrow().reason(),
                "either way");
    }

    @Test
    void cont_093_lockdown_at_50_c_or_0_c() {
        assertTrue(Lockdown.of(101, 323.14, 101, 273.16).isEmpty(), "just inside both");
        assertEquals(
                Lockdown.Reason.HOT,
                Lockdown.of(101, T, 101, 323.15).orElseThrow().reason(),
                "at 50 °C");
        assertEquals(
                Lockdown.Reason.COLD,
                Lockdown.of(101, 273.15, 101, T).orElseThrow().reason(),
                "at 0 °C");
        assertEquals(400.0, Lockdown.of(101, 400, 101, 200).orElseThrow().value(), "heat is named before cold");
        assertEquals(
                Lockdown.Reason.PRESSURE,
                Lockdown.of(0, 2.7, 101, T).orElseThrow().reason(),
                "the pressure difference is named first");
    }
}
