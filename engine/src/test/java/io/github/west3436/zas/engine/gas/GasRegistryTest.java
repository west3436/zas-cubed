package io.github.west3436.zas.engine.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/**
 * The engine side of CORE-010, CORE-012 and CORE-017. Loading definitions and
 * tags from a datapack is checked by the {@code core_010}, {@code core_012} and
 * {@code core_017} GameTests in {@code zas}, which parse with Minecraft's
 * codecs and tag loader.
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

    /** A tag's members are held by id and by index, in id order (CORE-012). */
    @Test
    void core_012_tag_membership() {
        GasRegistry registry = GasRegistry.of(
                List.of(TestGases.ALPHA, TestGases.BETA, TestGases.GAMMA),
                Map.of("test:fuel", List.of("test:gamma", "test:alpha"), "test:empty", List.of()));
        assertEquals(
                List.of("test:empty", "test:fuel"), List.copyOf(registry.tags().keySet()));
        assertEquals(List.of("test:alpha", "test:gamma"), registry.gasesIn("test:fuel"));
        assertEquals(List.of(), registry.gasesIn("test:empty"));
        assertEquals(List.of(), registry.gasesIn("test:missing"));
        assertTrue(registry.isIn(registry.indexOf("test:alpha"), "test:fuel"));
        assertFalse(registry.isIn(registry.indexOf("test:beta"), "test:fuel"));
        assertFalse(registry.isIn(registry.indexOf("test:alpha"), "test:missing"));
        assertEquals(List.of("test:fuel"), registry.tagsOf(registry.indexOf("test:gamma")));
        assertEquals(List.of(), registry.tagsOf(registry.indexOf("test:beta")));
    }

    /** Tags are part of the gas set: two registries with other tags differ (CORE-012, CORE-014). */
    @Test
    void core_012_tags_are_part_of_the_registry() {
        List<GasDefinition> gases = List.of(TestGases.ALPHA, TestGases.BETA);
        GasRegistry untagged = GasRegistry.of(gases);
        GasRegistry tagged = GasRegistry.of(gases, Map.of("test:fuel", List.of("test:alpha")));
        assertNotEquals(untagged, tagged);
        assertEquals(
                tagged,
                GasRegistry.of(List.of(TestGases.BETA, TestGases.ALPHA), Map.of("test:fuel", List.of("test:alpha"))));
        assertTrue(tagged.toString().contains("#test:fuel"), tagged.toString());
    }

    /** A tag must name defined gases and have a valid id (CORE-012). */
    @Test
    void core_012_invalid_tags_rejected() {
        List<GasDefinition> gases = List.of(TestGases.ALPHA);
        assertThrows(
                IllegalArgumentException.class, () -> GasRegistry.of(gases, Map.of("test:fuel", List.of("test:beta"))));
        assertThrows(
                IllegalArgumentException.class, () -> GasRegistry.of(gases, Map.of("fuel", List.of("test:alpha"))));
    }

    /** Phase data is optional: a definition is valid with it and without it (CORE-017). */
    @Test
    void core_017_phase_data_is_optional() {
        GasDefinition plain = TestGases.gas("test:plain", 0.03, 20);
        assertTrue(plain.phase().isEmpty());
        GasPhaseData water = GasPhaseData.liquidRange(273.16, 373.124, 6010, 40651);
        GasDefinition wet =
                new GasDefinition("test:wet", "gas.test.wet", "H₂O", 0.018, 25.3, Optional.empty(), Optional.of(water));
        GasRegistry registry = GasRegistry.of(List.of(plain, wet));
        assertEquals(water, registry.find("test:wet").orElseThrow().phase().orElseThrow());
        assertTrue(registry.find("test:plain").orElseThrow().phase().isEmpty());
        assertEquals(373.124, water.boilingPoint().getAsDouble());
        assertTrue(water.sublimationPoint().isEmpty());
        GasDefinition plainWithPhase = new GasDefinition(
                "test:plain", plain.nameKey(), plain.formula(), 0.03, 20, Optional.empty(), Optional.of(water));
        assertNotEquals(plain, plainWithPhase);
    }

    /**
     * A gas that sublimes at 101.325 kPa, as CO₂ does, has a sublimation point and no melting or
     * boiling point; values may be left out one by one (CORE-017).
     */
    @Test
    void core_017_sublimation_and_partial_data() {
        GasPhaseData dryIce = GasPhaseData.subliming(194.686, 25200);
        assertTrue(dryIce.meltingPoint().isEmpty());
        assertTrue(dryIce.boilingPoint().isEmpty());
        assertEquals(194.686, dryIce.sublimationPoint().getAsDouble());
        GasPhaseData boilingOnly = new GasPhaseData(
                OptionalDouble.empty(),
                OptionalDouble.of(77.355),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.of(5580),
                OptionalDouble.empty());
        assertTrue(boilingOnly.latentHeatFusion().isEmpty());
    }

    @Test
    void core_017_invalid_phase_data_rejected() {
        OptionalDouble none = OptionalDouble.empty();
        assertThrows(IllegalArgumentException.class, () -> new GasPhaseData(none, none, none, none, none, none));
        assertThrows(IllegalArgumentException.class, () -> GasPhaseData.liquidRange(100, 90, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> GasPhaseData.liquidRange(-1, 90, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> GasPhaseData.subliming(Double.NaN, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GasPhaseData(OptionalDouble.of(50), none, OptionalDouble.of(60), none, none, none));
    }
}
