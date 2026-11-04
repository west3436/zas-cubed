package io.github.west3436.zas.engine.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How fast hot gas and gas fires set blocks alight (FIRE-033, FIRE-034). */
class BlockIgnitionSettingsTest {
    private static final BlockIgnitionSettings DEFAULT = BlockIgnitionSettings.DEFAULT;

    /** FIRE-033: the default ignition point is about wood's autoignition, and it starts on. */
    @Test
    void fire_033_defaults() {
        assertEquals(573, DEFAULT.temperature());
        assertEquals(1, DEFAULT.hotspotChance());
        assertTrue(DEFAULT.enabled());
    }

    /** FIRE-033: no chance below the ignition temperature, and some at it. */
    @Test
    void fire_033_the_ignition_temperature_is_where_it_starts() {
        assertEquals(0, DEFAULT.hotGasChance(100, 572.9, 573));
        assertEquals(0.1, DEFAULT.hotGasChance(100, 573, 573), 1e-12);
    }

    /** FIRE-033: the heat share rises linearly to all of it, 500 K above the ignition temperature. */
    @Test
    void fire_033_the_chance_scales_with_the_heat() {
        assertEquals(0.55, DEFAULT.hotGasChance(100, 823, 573), 1e-12);
        assertEquals(1, DEFAULT.hotGasChance(100, 1073, 573), 1e-12);
        assertEquals(1, DEFAULT.hotGasChance(100, 2700, 573), 1e-12);
    }

    /** FIRE-033: the chance scales with the block's ignite odds, none without any. */
    @Test
    void fire_033_the_chance_scales_with_the_odds() {
        assertEquals(0.05, DEFAULT.hotGasChance(5, 1073, 573), 1e-12);
        assertEquals(0.3, DEFAULT.hotGasChance(30, 1073, 573), 1e-12);
        assertEquals(1, DEFAULT.hotGasChance(300, 1073, 573), 1e-12, "odds above 100 count as 100");
        assertEquals(0, DEFAULT.hotGasChance(0, 1073, 573));
    }

    /** FIRE-034: the chance is the configured one, scaled by the block's ignite odds. */
    @Test
    void fire_034_the_chance_scales_with_the_odds() {
        BlockIgnitionSettings half = new BlockIgnitionSettings(true, 573, 0.5);
        assertEquals(0.15, half.gasFireChance(30), 1e-12);
        assertEquals(0.025, half.gasFireChance(5), 1e-12);
        assertEquals(0.5, half.gasFireChance(300), 1e-12, "odds above 100 count as 100");
        assertEquals(0, half.gasFireChance(0));
    }

    /** A chance per second holds whatever the atmos period, as FIRE-016's does. */
    @Test
    void fire_033_the_chance_is_per_second() {
        assertEquals(0.75, BlockIgnitionSettings.inPeriod(0.5, 2), 1e-12);
        assertEquals(0.5, BlockIgnitionSettings.inPeriod(0.5, 1), 1e-12);
        assertEquals(1, BlockIgnitionSettings.inPeriod(1, 0.5));
        assertEquals(0, BlockIgnitionSettings.inPeriod(0, 1));
    }

    @Test
    void fire_033_with_enabled_keeps_the_rest() {
        BlockIgnitionSettings off = new BlockIgnitionSettings(true, 600, 0.25).withEnabled(false);
        assertFalse(off.enabled());
        assertEquals(600, off.temperature());
        assertEquals(0.25, off.hotspotChance());
    }

    @Test
    void fire_033_rejects_bad_settings() {
        assertThrows(IllegalArgumentException.class, () -> new BlockIgnitionSettings(true, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new BlockIgnitionSettings(true, Double.NaN, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new BlockIgnitionSettings(true, 573, 1.5));
    }
}
