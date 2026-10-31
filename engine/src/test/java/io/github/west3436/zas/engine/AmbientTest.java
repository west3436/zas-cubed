package io.github.west3436.zas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class AmbientTest {
    private static final double EPS = 1e-9;
    private static final GasRegistry AIR_GASES = GasRegistry.of(List.of(
            new GasDefinition("zas:oxygen", "gas.zas.oxygen", "O₂", 0.031998, 21.06, Optional.empty()),
            new GasDefinition("zas:nitrogen", "gas.zas.nitrogen", "N₂", 0.028014, 20.81, Optional.empty())));

    private static TreeMap<String, Double> pressures(Object... idAndKpa) {
        TreeMap<String, Double> map = new TreeMap<>();
        for (int i = 0; i < idAndKpa.length; i += 2) {
            map.put((String) idAndKpa[i], ((Number) idAndKpa[i + 1]).doubleValue());
        }
        return map;
    }

    @Test
    void env_001_ambient_gives_composition_and_temperature() {
        Ambient a = new Ambient(pressures("zas:oxygen", 30, "zas:nitrogen", 70), 280);
        assertEquals(100, a.pressure(), EPS);
        assertEquals(30, a.partialPressure("zas:oxygen"), EPS);
        assertEquals(0, a.partialPressure("zas:argon"));
        assertEquals(280, a.temperature());
        // Sorted by id, whatever order it was given in.
        assertEquals(
                List.of("zas:nitrogen", "zas:oxygen"),
                List.copyOf(a.partialPressures().keySet()));

        // Moles per 1,000 L: n = pV/RT, so 41.57 mol at 293.15 K is about one atmosphere.
        Ambient byMoles = Ambient.ofMolesPerCubicMetre(Map.of("zas:oxygen", 10.0), 300);
        assertEquals(10 * 8.314462618 * 300 / 1000, byMoles.partialPressure("zas:oxygen"), EPS);
        assertEquals(10, byMoles.moles("zas:oxygen", 1000), EPS);

        // Filling a volume gives the same pressures and temperature.
        GasMixture m = a.fill(AIR_GASES, 2500);
        assertEquals(2500, m.volume());
        assertEquals(280, m.temperature());
        assertEquals(100, m.pressure(), 1e-9);
        assertEquals(30, m.partialPressure(AIR_GASES.indexOf("zas:oxygen")), 1e-9);
    }

    @Test
    void env_001_ambient_rejects_invalid_values() {
        assertThrows(IllegalArgumentException.class, () -> new Ambient(pressures("zas:oxygen", -1), 300));
        assertThrows(IllegalArgumentException.class, () -> new Ambient(pressures("zas:oxygen", Double.NaN), 300));
        assertThrows(IllegalArgumentException.class, () -> new Ambient(pressures(), -1));
        assertThrows(IllegalArgumentException.class, () -> new Ambient(pressures("zas:oxygen", 1), 0));
        Ambient unknown = new Ambient(pressures("zas:argon", 1), 300);
        assertEquals(List.of("zas:argon"), unknown.missingGases(AIR_GASES));
        assertThrows(IllegalArgumentException.class, () -> unknown.fill(AIR_GASES, 1000));
        // Zero entries are dropped, so they compare equal to absent ones.
        assertEquals(
                new Ambient(pressures("zas:oxygen", 1), 300), new Ambient(pressures("zas:oxygen", 1, "x:y", 0), 300));
    }

    @Test
    void env_003_vacuum_is_empty_at_cosmic_background() {
        assertTrue(Ambient.VACUUM.isVacuum());
        assertEquals(0, Ambient.VACUUM.pressure());
        assertEquals(2.7, Ambient.VACUUM.temperature());
        GasMixture m = Ambient.VACUUM.fill(AIR_GASES, 1000);
        assertTrue(m.isEmpty());
        assertEquals(0, m.pressure());
        assertEquals(2.7, m.temperature());

        // A declared temperature replaces the background (D-52).
        Ambient hot = Ambient.vacuum(737);
        assertTrue(hot.isVacuum());
        assertEquals(737, hot.fill(AIR_GASES, 1000).temperature());
        // A vacuum may sit at 0 K.
        assertEquals(0, Ambient.vacuum(0).temperature());
    }

    @Test
    void env_011_fallback_is_standard_air() {
        Ambient air = Ambient.STANDARD_AIR;
        assertFalse(air.isVacuum());
        assertEquals(101.325, air.pressure(), EPS);
        assertEquals(293.15, air.temperature());
        assertEquals(0.79, air.partialPressure("zas:nitrogen") / air.pressure(), EPS);
        assertEquals(0.21, air.partialPressure("zas:oxygen") / air.pressure(), EPS);
        // About 41.57 mol in a 1,000 L cell.
        GasMixture cell = air.fill(AIR_GASES, 1000);
        assertEquals(101.325 * 1000 / (8.314462618 * 293.15), cell.totalMoles(), 1e-9);
    }
}
