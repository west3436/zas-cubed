package io.github.west3436.zas.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.hud.ClientListenerAir;
import io.github.west3436.zas.hud.ListenerAirPayload;
import org.junit.jupiter.api.Test;

/** The thin-air curve (CLIENT-016), and what a client that hasn't been told yet reads (DATA-019). */
class ThinAirTest {
    @Test
    void client_016_a_vacuum_is_exactly_silent() {
        assertEquals(0, ThinAir.factor(0));
        assertEquals(0, ThinAir.factor(0.5));
        assertEquals(0, ThinAir.factor(ThinAir.SILENT_KPA));
    }

    @Test
    void client_016_normal_air_is_exactly_full_volume() {
        assertEquals(1, ThinAir.factor(ThinAir.FULL_KPA));
        assertEquals(1, ThinAir.factor(101.325));
        assertEquals(1, ThinAir.factor(5_000));
    }

    @Test
    void client_016_the_curve_between() {
        assertEquals(0.08, ThinAir.factor(5), 0.005);
        assertEquals(0.18, ThinAir.factor(10), 0.005);
        assertEquals(0.39, ThinAir.factor(20), 0.005);
        assertEquals(0.59, ThinAir.factor(30), 0.005);
        assertEquals(0.80, ThinAir.factor(40), 0.005);
    }

    @Test
    void client_016_the_curve_only_rises() {
        double last = 0;
        for (double kPa = 0; kPa <= 60; kPa += 0.25) {
            double factor = ThinAir.factor(kPa);
            assertTrue(factor >= last, () -> "it fell at " + factor);
            assertTrue(factor - last <= 0.25 / (ThinAir.FULL_KPA - ThinAir.SILENT_KPA) + 1e-9, "a step");
            last = factor;
        }
    }

    @Test
    void client_016_an_unknown_pressure_is_normal_air() {
        assertEquals(1, ThinAir.factor(ThinAir.UNKNOWN));
    }

    @Test
    void data_019_a_reading_is_whole_kpa_capped_where_the_curve_stops() {
        assertEquals(0, ThinAir.reading(0));
        assertEquals(0, ThinAir.reading(0.99));
        assertEquals(1, ThinAir.reading(1.99));
        assertEquals(49, ThinAir.reading(49.9));
        assertEquals(ThinAir.FULL_KPA, ThinAir.reading(ThinAir.FULL_KPA));
        assertEquals(ThinAir.FULL_KPA, ThinAir.reading(101.325));
        assertEquals(ThinAir.FULL_KPA, ThinAir.reading(9_000));
    }

    @Test
    void data_019_every_reading_below_full_is_quieter_than_full_and_0_is_silent() {
        assertEquals(0, ThinAir.factor(ThinAir.reading(0.4)));
        assertEquals(1, ThinAir.factor(ThinAir.reading(101.325)));
    }

    /**
     * A brand-new client hasn't been told anything, and hears normally until it is. After it leaves a
     * server, what it was told last doesn't stay with it, even if that was a vacuum.
     */
    @Test
    void data_019_a_new_client_reads_as_normal_air_until_it_is_told() {
        ClientListenerAir.clear();
        assertEquals(ThinAir.UNKNOWN, ClientListenerAir.kPa());
        assertEquals(1, ThinAir.factor(ClientListenerAir.kPa()));
        ClientListenerAir.accept(new ListenerAirPayload(0));
        assertEquals(0, ThinAir.factor(ClientListenerAir.kPa()));
        ClientListenerAir.clear();
        assertEquals(1, ThinAir.factor(ClientListenerAir.kPa()));
    }

    @Test
    void data_019_a_payload_clamps_its_reading() {
        assertEquals(0, new ListenerAirPayload(-3).kPa());
        assertEquals(ThinAir.FULL_KPA, new ListenerAirPayload(200).kPa());
    }
}
