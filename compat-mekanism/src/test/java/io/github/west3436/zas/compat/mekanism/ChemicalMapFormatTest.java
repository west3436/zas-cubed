package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.compat.mekanism.ChemicalMapFormat.InvalidMappingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The chemical map's datapack format (MEK-010). */
class ChemicalMapFormatTest {
    private static final ResourceLocation STEAM = ResourceLocation.parse("mekanism:steam");
    private static final ResourceLocation H2O = ResourceLocation.parse("zas:water_vapor");
    private static final ResourceLocation D2 = ResourceLocation.parse("zas:deuterium");
    private static final ResourceLocation T2 = ResourceLocation.parse("zas:tritium");

    private static ChemicalMapping parse(String json) throws InvalidMappingException {
        List<String> warnings = new ArrayList<>();
        ChemicalMapping mapping = ChemicalMapFormat.parse(STEAM, JsonParser.parseString(json), warnings::add);
        assertEquals(List.of(), warnings, json);
        return mapping;
    }

    private static String error(String json) {
        return assertThrows(InvalidMappingException.class, () -> parse(json), json)
                .getMessage();
    }

    /** A file's path names its chemical, as in ZAS³'s other formats. */
    @Test
    void mek_010_a_file_maps_the_chemical_its_path_names() {
        assertEquals("zas/mekanism_chemical", ChemicalMapFormat.DIRECTORY);
        assertEquals("data/mekanism/zas/mekanism_chemical/steam.json", ChemicalMapFormat.file(STEAM));
    }

    /** One gas, both ways, with no least temperature, is the plain case. */
    @Test
    void mek_010_a_chemical_that_is_one_gas() throws InvalidMappingException {
        ChemicalMapping mapping = parse("{\"gas\": \"zas:water_vapor\"}");
        assertEquals(STEAM, mapping.chemical());
        assertEquals(Map.of(H2O, 1.0), mapping.gases());
        assertEquals(0, mapping.minTemperature());
        assertFalse(mapping.oneWay());
        assertTrue(mapping.bothWays());
        assertEquals(mapping, ChemicalMapping.of(STEAM, H2O));
    }

    /** A least temperature is given on the way in: steam arrives at 373.15 K or more. */
    @Test
    void mek_010_steam_arrives_at_its_least_temperature_or_hotter() throws InvalidMappingException {
        ChemicalMapping steam = parse("{\"gas\": \"zas:water_vapor\", \"min_temperature\": 373.15}");
        assertEquals(373.15, steam.minTemperature());
        assertEquals(373.15, steam.entryTemperature(293.15));
        assertEquals(373.15, steam.entryTemperature(0));
        assertEquals(500, steam.entryTemperature(500));
        GasParcel gas = steam.gas(1000, 0.04, 293.15);
        assertEquals(373.15, gas.temperature());
        assertEquals(40.0, gas.moles(H2O), 1e-12);
        assertEquals(GasParcel.EMPTY, steam.gas(0, 0.04, 293.15));
    }

    /** A chemical that is several gases splits by mole fraction, and never comes back. */
    @Test
    void mek_010_fusion_fuel_splits_into_its_gases() throws InvalidMappingException {
        ChemicalMapping fuel = parse("{\"gases\": {\"zas:deuterium\": 0.5, \"zas:tritium\": 0.5}}");
        assertEquals(Map.of(D2, 0.5, T2, 0.5), fuel.gases());
        assertFalse(fuel.bothWays());
        GasParcel gas = fuel.gas(10, 0.04, 300);
        assertEquals(0.2, gas.moles(D2), 1e-12);
        assertEquals(0.2, gas.moles(T2), 1e-12);
        assertEquals(0.4, gas.totalMoles(), 1e-12);
        assertEquals(300, gas.temperature());
    }

    /** One-way: the chemical becomes the gas, but the gas never becomes this chemical. */
    @Test
    void mek_010_a_one_way_mapping_never_leads_back() throws InvalidMappingException {
        ChemicalMapping mapping = parse("{\"gas\": \"zas:water_vapor\", \"one_way\": true}");
        assertTrue(mapping.oneWay());
        assertFalse(mapping.bothWays());
    }

    /** An invalid file is refused with a message that starts with the field at fault (OPS-008). */
    @Test
    void mek_010_an_invalid_file_names_the_field_at_fault() {
        assertEquals("must be a JSON object", error("[]"));
        assertTrue(error("{}").startsWith("gas: is required"), error("{}"));
        assertTrue(error("{\"gas\": 5}").startsWith("gas: not a gas id"));
        assertTrue(error("{\"gas\": \"Not An Id\"}").startsWith("gas: not a gas id"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"gases\": {\"zas:oxygen\": 1}}")
                .startsWith("gases: can't be given together with gas"));
        assertTrue(error("{\"gases\": []}").startsWith("gases: must be an object"));
        assertTrue(error("{\"gases\": {}}").startsWith("gases: must name at least one gas"));
        assertTrue(error("{\"gases\": {\"Bad Id\": 1}}").startsWith("gases: not a gas id"));
        assertTrue(
                error("{\"gases\": {\"zas:deuterium\": \"half\"}}").startsWith("gases: zas:deuterium: not a number"));
        assertTrue(error("{\"gases\": {\"zas:deuterium\": 0, \"zas:tritium\": 1}}")
                .startsWith("gases: zas:deuterium: must be finite and above 0"));
        assertTrue(error("{\"gases\": {\"zas:deuterium\": 0.5, \"zas:tritium\": 0.4}}")
                .startsWith("gases: the fractions must add up to 1"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"min_temperature\": -1}")
                .startsWith("min_temperature: must be finite and not negative"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"min_temperature\": \"hot\"}")
                .startsWith("min_temperature: not a number"));
        assertTrue(
                error("{\"gas\": \"zas:oxygen\", \"one_way\": \"yes\"}").startsWith("one_way: must be true or false"));
    }

    /** An unknown field is warned about and the rest loads; NeoForge's conditions key isn't unknown. */
    @Test
    void mek_010_an_unknown_field_is_ignored_with_a_warning() throws InvalidMappingException {
        List<String> warnings = new ArrayList<>();
        ChemicalMapping mapping = ChemicalMapFormat.parse(
                STEAM,
                JsonParser.parseString(
                        "{\"gas\": \"zas:water_vapor\", \"colour\": \"white\", \"neoforge:conditions\": []}"),
                warnings::add);
        assertEquals(Map.of(H2O, 1.0), mapping.gases());
        assertEquals(List.of("'colour' names no field of a chemical mapping"), warnings);
    }
}
