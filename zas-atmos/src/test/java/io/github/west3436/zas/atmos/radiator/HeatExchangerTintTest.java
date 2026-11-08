package io.github.west3436.zas.atmos.radiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** A heat exchanger's colour by the temperature of its gas (CONT-148). */
class HeatExchangerTintTest {
    /** CONT-148: each stop's temperature shows its colour exactly. */
    @Test
    void cont_148_each_stop_shows_its_colour() {
        double[] celsius = {-60, 20, 200, 600, 1000, 1500};
        int[] colours = {0x8ec5ff, 0xc07a45, 0x9c4a32, 0xe8341c, 0xff8c1a, 0xffe8b0};
        for (int i = 0; i < celsius.length; i++) {
            int band = HeatExchangerTint.band(celsius[i] + 273.15);
            assertEquals(i * HeatExchangerTint.STEPS, band, celsius[i] + " °C's band");
            assertEquals(colours[i], HeatExchangerTint.colour(band), celsius[i] + " °C's colour");
        }
        assertEquals(HeatExchangerTint.band(293.15), HeatExchangerTint.COPPER, "room temperature is copper");
        assertEquals(0xc07a45, HeatExchangerTint.colour(HeatExchangerTint.COPPER), "copper, for the item");
    }

    /** CONT-148: below the first stop is frost blue, above the last yellow-white. */
    @Test
    void cont_148_past_the_ends_holds_the_end_colours() {
        assertEquals(0x8ec5ff, HeatExchangerTint.colour(HeatExchangerTint.band(2.7)), "space");
        assertEquals(0x8ec5ff, HeatExchangerTint.colour(HeatExchangerTint.band(0)), "0 K");
        assertEquals(0xffe8b0, HeatExchangerTint.colour(HeatExchangerTint.band(5000)), "5,000 K");
        assertEquals(0, HeatExchangerTint.band(Double.NaN), "no temperature");
        assertEquals(0x8ec5ff, HeatExchangerTint.colour(-3), "a band below the first");
        assertEquals(0xffe8b0, HeatExchangerTint.colour(99), "a band past the last");
    }

    /**
     * CONT-148: between stops the colour blends, by the band nearest the temperature, and hotter is
     * never a lower band.
     */
    @Test
    void cont_148_between_stops_the_colour_blends() {
        assertEquals(0xae623c, HeatExchangerTint.colour(HeatExchangerTint.COPPER + 3), "half way to dark red");
        assertEquals(0xa5a3ab, HeatExchangerTint.blend(0x8ec5ff, 0xc07a45, 0.45), "each channel rounded");
        Random random = new Random(148);
        int last = 0;
        double t = 150;
        for (int i = 0; i < 10_000; i++) {
            t += 0.2 * random.nextDouble();
            int band = HeatExchangerTint.band(t);
            assertTrue(band >= last, "a lower band for a hotter gas at " + t + " K");
            assertTrue(band >= 0 && band < HeatExchangerTint.BANDS, "no such band: " + band);
            last = band;
        }
        for (int i = 0; i < 10_000; i++) {
            double k = 213.15 + (1773.15 - 213.15) * random.nextDouble();
            int stop = 0;
            while (k > HeatExchangerTint.STOPS[stop + 1]) {
                stop++;
            }
            double along = (k - HeatExchangerTint.STOPS[stop])
                    / (HeatExchangerTint.STOPS[stop + 1] - HeatExchangerTint.STOPS[stop]);
            double exact = HeatExchangerTint.STEPS * (stop + along);
            assertTrue(Math.abs(HeatExchangerTint.band(k) - exact) <= 0.5 + 1e-9, "not the nearest band at " + k);
        }
    }
}
