package io.github.west3436.zas.atmos.vent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import org.junit.jupiter.api.Test;

/** A vent's moles per second: its bounds (CONT-014) and its rate (CONT-078). */
class VentFlowTest {
    private static final double R = GasMixture.GAS_CONSTANT;
    private static final double T = 293.15;

    @Test
    void cont_078_a_vent_blows_at_most_half_its_buffer_per_second() {
        // A big network at high pressure into a big, low room: only the rate limits.
        double netVolume = 1_000 + 400 + 70 * 3;
        double netMoles = 3_000;
        double n = VentFlow.blow(50, 100_000, netMoles, netVolume, T, VentSettings.blow(101.325));
        assertEquals(0.5 * netMoles * 400 / netVolume, n, 1e-9);
    }

    @Test
    void cont_078_a_vent_siphons_at_most_half_a_buffer_of_the_room_per_second() {
        double zoneMoles = 5_000;
        double zoneVolume = 100_000;
        double n = VentFlow.siphon(zoneMoles, zoneVolume, T, 0, 470, T, VentSettings.siphon(0));
        assertEquals(0.5 * zoneMoles * 400 / zoneVolume, n, 1e-9);
    }

    @Test
    void cont_014_blowing_stops_at_the_external_bound() {
        double zoneVolume = 27_000;
        // Close to the bound, the vent moves just what reaches it.
        double n = VentFlow.blow(100.0, zoneVolume, 3_000, 1_470, T, VentSettings.blow(101.325));
        assertEquals(1.325 * zoneVolume / (R * T), n, 1e-9);
        assertEquals(0, VentFlow.blow(101.325, zoneVolume, 3_000, 1_470, T, VentSettings.blow(101.325)));
        assertEquals(0, VentFlow.blow(150, zoneVolume, 3_000, 1_470, T, VentSettings.blow(101.325)));
    }

    @Test
    void cont_014_blowing_stops_at_the_internal_bound() {
        double netVolume = 1_470;
        double netPressure = 200;
        double netMoles = netPressure * netVolume / (R * T);
        VentSettings s = new VentSettings(VentMode.BLOW, 5_000, 199);
        double n = VentFlow.blow(0, 1_000_000, netMoles, netVolume, T, s);
        assertEquals(1.0 * netVolume / (R * T), n, 1e-9);
        assertEquals(
                0, VentFlow.blow(0, 1_000_000, netMoles, netVolume, T, new VentSettings(VentMode.BLOW, 5_000, 200)));
    }

    @Test
    void cont_014_siphoning_stops_at_the_external_bound_and_the_internal_bound() {
        double zoneVolume = 27_000;
        double zoneMoles = 101.0 * zoneVolume / (R * T);
        double n = VentFlow.siphon(zoneMoles, zoneVolume, T, 0, 470, T, VentSettings.siphon(100.9));
        assertEquals(0.1 * zoneVolume / (R * T), n, 1e-6);
        assertEquals(0, VentFlow.siphon(zoneMoles, zoneVolume, T, 0, 470, T, VentSettings.siphon(101)));
        // A network already at the internal bound takes nothing.
        double full = 300 * 470 / (R * T);
        assertEquals(
                0, VentFlow.siphon(zoneMoles, zoneVolume, T, full, 470, T, new VentSettings(VentMode.SIPHON, 0, 300)));
    }

    @Test
    void cont_014_bounds_are_clamped_to_0_to_5066_kpa() {
        VentSettings s = new VentSettings(VentMode.BLOW, 9_999, -5);
        assertEquals(VentSettings.MAX_KPA, s.external());
        assertEquals(0, s.internal());
        assertEquals(0, new VentSettings(VentMode.BLOW, Double.NaN, 0).external());
        assertEquals(VentMode.OFF, new VentSettings(null, 1, 1).mode());
        assertTrue(VentSettings.siphon(50).internal() == VentSettings.MAX_KPA);
        assertEquals(VentMode.OFF, VentSettings.OFF.mode());
    }
}
