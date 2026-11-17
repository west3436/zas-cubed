package io.github.west3436.zas.compat.pneumaticcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The arithmetic of heat between PneumaticCraft's blocks and zones (COMPAT-013, Q-25). */
class HeatExchangeTest {
    @Test
    void compat_013_a_face_is_its_setting_in_series_with_the_blocks_own_resistance() {
        // A Compressed Iron Block's resistance of 1 at 100 J a unit passes 2,000 W/K a face on its
        // own; in series with 10 W/K, a little under 10.
        assertEquals(1 / (1 / 10.0 + 1 / 2000.0), HeatExchange.conductance(10, 100, 1), 1e-12);
        assertEquals(10, HeatExchange.conductance(10, 100, 0), 0);
        assertEquals(0, HeatExchange.conductance(0, 100, 1), 0);
        assertEquals(0, HeatExchange.conductance(10, 0, 1), 0);
        assertEquals(0, HeatExchange.conductance(10, 100, Double.POSITIVE_INFINITY), 0);
        assertEquals(0, HeatExchange.conductance(Double.NaN, 100, 1), 0);
    }

    @Test
    void compat_013_heat_flows_from_hot_to_cold_and_never_past_their_common_temperature() {
        double block = 1000;
        double gas = 2000;
        double common = (block * 1000 + gas * 300) / (block + gas);
        double previous = 0;
        for (double seconds : new double[] {0.05, 1, 10, 100, 1e4}) {
            double joules = HeatExchange.exchange(block, 1000, gas, 300, 50, seconds);
            assertTrue(joules > previous, "the flow grows with time: " + joules + " after " + seconds + " s");
            assertTrue(1000 - joules / block >= common - 1e-9, "the block passed the common temperature");
            assertTrue(300 + joules / gas <= common + 1e-9, "the gas passed the common temperature");
            previous = joules;
        }
        // All the way, they meet.
        assertEquals(block * (1000 - common), HeatExchange.exchange(block, 1000, gas, 300, 50, 1e9), 1e-6);
        // And a cold block takes heat.
        assertTrue(HeatExchange.exchange(block, 200, gas, 300, 50, 1) < 0);
    }

    @Test
    void compat_013_a_short_step_is_conductance_times_difference_times_time() {
        assertEquals(10 * 700 * 0.001, HeatExchange.exchange(1e6, 1000, 1e6, 300, 10, 0.001), 1e-6);
    }

    @Test
    void compat_013_nothing_flows_without_a_path_a_difference_or_time() {
        assertEquals(0, HeatExchange.exchange(1000, 500, 1000, 500, 10, 1), 0);
        assertEquals(0, HeatExchange.exchange(1000, 600, 1000, 500, 0, 1), 0);
        assertEquals(0, HeatExchange.exchange(1000, 600, 1000, 500, 10, 0), 0);
        assertEquals(0, HeatExchange.exchange(0, 600, 1000, 500, 10, 1), 0);
        assertEquals(0, HeatExchange.exchange(1000, Double.NaN, 1000, 500, 10, 1), 0);
    }

    @Test
    void compat_013_one_heat_unit_is_a_hundred_joules_unless_set() {
        // PneumaticCraft makes 100 of Mekanism's units one of its own, and ZAS³ counts one of
        // Mekanism's as 1 J.
        assertEquals(100.0, HeatExchange.DEFAULT_JOULES_PER_HEAT_UNIT, 0);
        assertTrue(HeatExchange.MIN_JOULES_PER_HEAT_UNIT < HeatExchange.DEFAULT_JOULES_PER_HEAT_UNIT);
        assertTrue(HeatExchange.MAX_JOULES_PER_HEAT_UNIT > HeatExchange.DEFAULT_JOULES_PER_HEAT_UNIT);
    }
}
