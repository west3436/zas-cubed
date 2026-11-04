package io.github.west3436.zas.life;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** CONT-057's arithmetic: the defaults supply one breather, and the rate scales with light. */
class PhotosynthesisRatesTest {
    private static final double NORMAL_CROP = 0.125;
    private static final double NORMAL_LEAF = 0.0625;
    private static final double HARDCORE_CROP = 0.0625;
    private static final double HARDCORE_LEAF = 0.03125;
    private static final double HOUR = 3600;

    @Test
    void cont_057_a_breather_needs_8_crops_under_normal_and_16_under_hardcore() {
        assertEquals(1, PhotosynthesisRates.moles(8 * 15, 0, NORMAL_CROP, NORMAL_LEAF, HOUR), 1e-12);
        assertEquals(1, PhotosynthesisRates.moles(16 * 15, 0, HARDCORE_CROP, HARDCORE_LEAF, HOUR), 1e-12);
    }

    @Test
    void cont_057_leaves_make_half_a_crops_rate() {
        assertEquals(NORMAL_LEAF, PhotosynthesisRates.moles(0, 15, NORMAL_CROP, NORMAL_LEAF, HOUR), 1e-12);
        assertEquals(HARDCORE_LEAF, PhotosynthesisRates.moles(0, 15, HARDCORE_CROP, HARDCORE_LEAF, HOUR), 1e-12);
    }

    @Test
    void cont_057_the_rate_is_linear_in_light_from_zero_at_light_zero() {
        assertEquals(0, PhotosynthesisRates.moles(0, 0, NORMAL_CROP, NORMAL_LEAF, HOUR));
        for (int light = 1; light <= 15; light++) {
            assertEquals(
                    NORMAL_CROP * light / 15,
                    PhotosynthesisRates.moles(light, 0, NORMAL_CROP, NORMAL_LEAF, HOUR),
                    1e-12,
                    "light " + light);
        }
    }

    @Test
    void cont_057_a_zones_plants_add_up_over_the_time_given() {
        double oneSecond = (3 * 15 * NORMAL_CROP + 2 * 7 * NORMAL_LEAF) / 15 / HOUR;
        assertEquals(oneSecond, PhotosynthesisRates.moles(3 * 15, 2 * 7, NORMAL_CROP, NORMAL_LEAF, 1), 1e-15);
        assertEquals(oneSecond * 2.5, PhotosynthesisRates.moles(3 * 15, 2 * 7, NORMAL_CROP, NORMAL_LEAF, 2.5), 1e-15);
    }
}
