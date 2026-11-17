package io.github.west3436.zas.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

/** The lift factor's arithmetic (COMPAT-080), with ZAS³'s molar masses for N₂, O₂ and CO₂. */
class LiftFactorTest {
    private static final Map<String, Double> MOLAR_MASSES =
            Map.of("zas:nitrogen", 0.0280134, "zas:oxygen", 0.0319988, "zas:carbon_dioxide", 0.0440095);
    private static final ToDoubleFunction<String> M = gas -> MOLAR_MASSES.getOrDefault(gas, Double.NaN);

    /** Standard air as ZAS³'s fallback and the Overworld's definition write it. */
    private static final Map<String, Double> AIR = Map.of("zas:nitrogen", 80.04675, "zas:oxygen", 21.27825);

    private static double factor(Map<String, Double> ambient, double kelvin) {
        return LiftFactor.factor(ambient, kelvin, AIR, 293.15, M);
    }

    @Test
    void compat_080_standard_air_is_exactly_one() {
        assertEquals(1.0, factor(AIR, 293.15));
    }

    @Test
    void compat_080_a_vacuum_is_zero() {
        assertEquals(0.0, factor(Map.of(), 2.7));
    }

    /** The spec's example: 0.6 kPa of CO₂ at 210 K is about 0.013. */
    @Test
    void compat_080_thin_cold_co2_is_about_0_013() {
        double f = factor(Map.of("zas:carbon_dioxide", 0.6), 210);
        assertEquals(0.013, f, 0.0005);
    }

    /** Density scales with pressure and inversely with temperature. */
    @Test
    void compat_080_half_the_pressure_or_twice_the_temperature_halves_it() {
        Map<String, Double> half = Map.of("zas:nitrogen", 80.04675 / 2, "zas:oxygen", 21.27825 / 2);
        assertEquals(0.5, factor(half, 293.15), 1e-12);
        assertEquals(0.5, factor(AIR, 586.3), 1e-12);
        // ZAS³'s Nether under Normal: air at 313.15 K.
        assertEquals(293.15 / 313.15, factor(AIR, 313.15), 1e-12);
    }

    /** A gas with no molar mass leaves Sable's pressure as it is. */
    @Test
    void compat_080_an_unknown_gas_leaves_the_factor_at_one() {
        assertEquals(LiftFactor.NONE, factor(Map.of("zas:unobtainium", 50.0), 293.15));
        assertTrue(Double.isNaN(LiftFactor.density(Map.of("zas:unobtainium", 50.0), 293.15, M)));
    }
}
