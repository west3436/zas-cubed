package io.github.west3436.zas.engine.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gas levels that keep a sealed zone the equivalence test would let go (CORE-052). */
class DissolveGuardTest {
    private static final GasRegistry GASES = GasRegistry.of(
            List.of(ZoneFixtures.gas("test:nitrogen", 0.028, 20.8), ZoneFixtures.gas("test:toxin", 0.071, 33.9)));
    private static final int N2 = GASES.indexOf("test:nitrogen");
    private static final int TOXIN = GASES.indexOf("test:toxin");
    private static final double LITRES = 1000;
    private static final double T = 293.15;

    /** A toxic gas's levels: a warning at 0.0001 kPa, harm at 0.001 and 0.01, and half the lowest. */
    private static final DissolveGuard GUARD = DissolveGuard.builder(GASES)
            .level(TOXIN, 0.01, false)
            .level(TOXIN, 0.00005, false)
            .level(TOXIN, 0.001, false)
            .level(TOXIN, 0.0001, false)
            .build();

    /** Nitrogen at one atmosphere with the toxin at a partial pressure, in kPa. */
    private static GasMixture air(double toxinKpa) {
        GasMixture m = new GasMixture(GASES, LITRES, T);
        m.setMoles(N2, 101.325 * LITRES / (GasConstants.R * T));
        m.setMoles(TOXIN, toxinKpa * LITRES / (GasConstants.R * T));
        return m;
    }

    @Test
    void core_052_a_gas_past_a_level_the_ambient_is_under_keeps_the_zone() {
        GasMixture ambient = air(0);
        for (double kPa : new double[] {0.00006, 0.0002, 0.002, 0.02, 1, 5}) {
            GasMixture zone = air(kPa);
            assertTrue(zone.isEquivalent(ambient), kPa + " kPa is the ambient by the tolerances (CORE-006)");
            assertTrue(GUARD.keeps(zone, ambient), kPa + " kPa");
        }
    }

    @Test
    void core_052_a_gas_under_every_level_keeps_nothing() {
        GasMixture ambient = air(0);
        assertFalse(GUARD.keeps(air(0), ambient));
        assertFalse(GUARD.keeps(air(0.00004), ambient), "under half the lowest level");
        // A larger room at the same partial pressure: the amount doesn't matter.
        GasMixture large = air(0.00004);
        large.setVolume(64 * LITRES);
        large.setMoles(TOXIN, 64 * large.moles(TOXIN));
        assertFalse(GUARD.keeps(large, ambient));
    }

    @Test
    void core_052_both_sides_past_the_same_levels_keep_nothing() {
        // An ambient that holds the gas itself: a zone like it is as harmful dissolved as kept.
        GasMixture ambient = air(0.002);
        assertFalse(GUARD.keeps(air(0.002), ambient));
        assertFalse(GUARD.keeps(air(0.003), ambient), "both between the levels at 0.001 and 0.01");
        assertTrue(GUARD.keeps(air(0.02), ambient), "the zone has reached the level at 0.01, the ambient hasn't");
    }

    @Test
    void core_052_a_zone_under_a_level_the_ambient_is_past_is_kept() {
        // A room cleaned of what the open air holds would turn harmful by dissolving.
        GasMixture ambient = air(0.002);
        assertTrue(GUARD.keeps(air(0), ambient));
        assertTrue(GUARD.keeps(air(0.0005), ambient), "under the level at 0.001");
    }

    @Test
    void core_052_a_level_is_reached_at_it_or_only_above_it() {
        GasMixture ambient = air(0.5);
        double at = ambient.partialPressure(TOXIN);
        GasMixture more = air(0.5001);
        DissolveGuard atOrAbove =
                DissolveGuard.builder(GASES).level(TOXIN, at, false).build();
        DissolveGuard onlyAbove =
                DissolveGuard.builder(GASES).level(TOXIN, at, true).build();

        assertFalse(atOrAbove.keeps(more, ambient), "both have reached it");
        assertTrue(onlyAbove.keeps(more, ambient), "the ambient is at it, not above it");
        assertFalse(onlyAbove.keeps(ambient.copy(), ambient));
    }

    @Test
    void core_052_the_partial_pressure_counts_at_the_zones_temperature() {
        GasMixture ambient = air(0);
        GasMixture zone = air(0.00004);
        assertFalse(GUARD.keeps(zone, ambient));
        // The same gas 1.5 times as hot presses 1.5 times as hard: 0.00006 kPa.
        zone.setTemperature(1.5 * T);
        assertTrue(GUARD.keeps(zone, ambient));
    }

    @Test
    void core_052_no_levels_keep_nothing() {
        assertTrue(DissolveGuard.NONE.isEmpty());
        assertFalse(DissolveGuard.NONE.keeps(air(5), air(0)));
        assertSame(DissolveGuard.NONE, DissolveGuard.builder(GASES).build());
        assertFalse(GUARD.isEmpty());
        assertSame(GASES, GUARD.registry());
    }

    @Test
    void core_052_levels_given_in_any_order_or_twice_are_the_same_guard() {
        DissolveGuard again = DissolveGuard.builder(GASES)
                .level(TOXIN, 0.00005, false)
                .level(TOXIN, 0.0001, false)
                .level(TOXIN, 0.001, false)
                .level(TOXIN, 0.001, false)
                .level(TOXIN, 0.01, false)
                .build();
        assertEquals(GUARD, again);
        assertEquals(GUARD.hashCode(), again.hashCode());
        assertNotEquals(
                GUARD, DissolveGuard.builder(GASES).level(TOXIN, 0.001, false).build());
        assertNotEquals(GUARD, DissolveGuard.NONE);
    }

    @Test
    void core_052_a_level_needs_a_gas_of_the_registry_and_a_pressure() {
        DissolveGuard.Builder builder = DissolveGuard.builder(GASES);
        assertThrows(IllegalArgumentException.class, () -> builder.level(-1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> builder.level(GASES.size(), 1, false));
        assertThrows(IllegalArgumentException.class, () -> builder.level(TOXIN, 0, false));
        assertThrows(IllegalArgumentException.class, () -> builder.level(TOXIN, -1, false));
        assertThrows(IllegalArgumentException.class, () -> builder.level(TOXIN, Double.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> builder.level(TOXIN, Double.POSITIVE_INFINITY, false));
    }
}
