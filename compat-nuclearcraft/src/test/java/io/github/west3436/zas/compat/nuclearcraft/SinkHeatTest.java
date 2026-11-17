package io.github.west3436.zas.compat.nuclearcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The arithmetic of a fission reactor's heat in its rooms (COMPAT-153). */
class SinkHeatTest {
    private static final double EPSILON = 1e-9;

    @Test
    void compat_153_sinks_with_heat_left_after_them_carry_all_they_can() {
        // 60 H/t of sinks, 100 H/t of fuel: the buffer fills, and the sinks carry their whole 60.
        assertEquals(60.0, SinkHeat.removedPerTick(500, 100, 60, 100), EPSILON);
    }

    @Test
    void compat_153_sinks_that_empty_the_buffer_carry_what_the_fuel_made() {
        // 120 H/t of sinks, 50 H/t of fuel at full reactivity: they carry the 50 made, not 120.
        assertEquals(50.0, SinkHeat.removedPerTick(0, 50, 120, 100), EPSILON);
        // At 80 % reactivity the fuel makes 40.
        assertEquals(40.0, SinkHeat.removedPerTick(0, 50, 120, 80), EPSILON);
        // Never below half, as NuclearCraft counts it.
        assertEquals(25.0, SinkHeat.removedPerTick(0, 50, 120, 10), EPSILON);
        // Never more than the sinks carry.
        assertEquals(30.0, SinkHeat.removedPerTick(0, 50, 30, 100), EPSILON);
    }

    @Test
    void compat_153_an_idle_or_sinkless_reactor_gives_nothing() {
        // Idle: NuclearCraft sets its cooling to 0.
        assertEquals(0.0, SinkHeat.removedPerTick(800, 50, 0, 100), EPSILON);
        // Not burning: the fuel makes nothing, and the buffer is empty.
        assertEquals(0.0, SinkHeat.removedPerTick(0, 0, 120, 100), EPSILON);
        // Nonsense in, nothing out.
        assertEquals(0.0, SinkHeat.removedPerTick(0, Double.NaN, 120, 100), EPSILON);
        assertEquals(0.0, SinkHeat.removedPerTick(10, 50, Double.POSITIVE_INFINITY, 100), EPSILON);
        assertEquals(0.0, SinkHeat.removedPerTick(10, 50, -5, 100), EPSILON);
    }

    @Test
    void compat_153_heat_becomes_joules_by_the_factor_under_the_ceiling() {
        // 20 ticks of 60 H at 1 J/H is 1200 J; the 50 kW ceiling allows 50,000 J in a second.
        assertEquals(1200.0, SinkHeat.joules(1200, 20, 1.0, 50_000), EPSILON);
        assertEquals(12_000.0, SinkHeat.joules(1200, 20, 10.0, 50_000), EPSILON);
        // A ceiling of 500 W allows 500 J in those 20 ticks.
        assertEquals(500.0, SinkHeat.joules(1200, 20, 1.0, 500), EPSILON);
        // 0 is no ceiling.
        assertEquals(1.2e9, SinkHeat.joules(1200, 20, 1.0e6, 0), 1e-3);
        // Nothing, or no time, is no heat.
        assertEquals(0.0, SinkHeat.joules(0, 20, 1.0, 50_000), EPSILON);
        assertEquals(0.0, SinkHeat.joules(1200, 0, 1.0, 50_000), EPSILON);
        assertEquals(0.0, SinkHeat.joules(Double.NaN, 20, 1.0, 50_000), EPSILON);
    }
}
