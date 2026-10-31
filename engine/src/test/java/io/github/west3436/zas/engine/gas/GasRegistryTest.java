package io.github.west3436.zas.engine.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The engine side of CORE-010. Loading definitions from a datapack is checked
 * by the {@code core_010} GameTest in {@code zas}, which parses with
 * Minecraft's codecs.
 */
class GasRegistryTest {
    @Test
    void core_010_gas_definition_fields() {
        GasDefinition gas = new GasDefinition(
                "zas_test:test_gas",
                "gas.zas_test.test_gas",
                "Tg₂",
                0.05,
                25.5,
                Optional.of(new GasVisibility(0x80FF20, 0.01)));
        GasRegistry registry = GasRegistry.of(List.of(gas));
        GasDefinition held = registry.find("zas_test:test_gas").orElseThrow();
        assertEquals("zas_test:test_gas", held.id());
        assertEquals("gas.zas_test.test_gas", held.nameKey());
        assertEquals("Tg₂", held.formula());
        assertEquals(0.05, held.molarMass());
        assertEquals(25.5, held.molarHeatCapacity());
        assertEquals(0x80FF20, held.visibility().orElseThrow().rgb());
        assertEquals(0.01, held.visibility().orElseThrow().densityThreshold());
        assertEquals(25.5, registry.molarHeatCapacity(registry.indexOf("zas_test:test_gas")));
    }

    @Test
    void core_010_indices_follow_id_order() {
        GasRegistry a = GasRegistry.of(List.of(TestGases.GAMMA, TestGases.ALPHA, TestGases.BETA));
        GasRegistry b = GasRegistry.of(List.of(TestGases.BETA, TestGases.GAMMA, TestGases.ALPHA));
        assertEquals(a, b);
        assertEquals(List.of(TestGases.ALPHA, TestGases.BETA, TestGases.GAMMA), a.definitions());
        assertEquals(0, a.indexOf("test:alpha"));
        assertEquals(2, a.indexOf("test:gamma"));
        assertEquals(-1, a.indexOf("test:missing"));
        assertTrue(a.find("test:missing").isEmpty());
        assertNotEquals(a, GasRegistry.EMPTY);
        assertEquals(0, GasRegistry.EMPTY.size());
    }

    @Test
    void core_010_invalid_definitions_rejected() {
        assertThrows(IllegalArgumentException.class, () -> GasRegistry.of(List.of(TestGases.ALPHA, TestGases.ALPHA)));
        assertThrows(IllegalArgumentException.class, () -> TestGases.gas("no-namespace", 0.03, 20));
        assertThrows(IllegalArgumentException.class, () -> TestGases.gas("test:zero_mass", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> TestGases.gas("test:nan_heat", 0.03, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new GasVisibility(0x1000000, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new GasVisibility(0xFFFFFF, 0));
    }
}
