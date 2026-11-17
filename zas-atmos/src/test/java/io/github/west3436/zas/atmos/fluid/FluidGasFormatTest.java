package io.github.west3436.zas.atmos.fluid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.Millibuckets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The datapack format of the fluid tags' gases (COMPAT-140) and their temperatures (COMPAT-145). */
class FluidGasFormatTest {
    private static final ResourceLocation OXYGEN_TAG = ResourceLocation.parse("c:oxygen");
    private static final ResourceLocation O2 = ResourceLocation.parse("zas:oxygen");

    private static FluidGasMapping parse(String json) throws FluidGasFormat.InvalidMappingException {
        List<String> warnings = new ArrayList<>();
        FluidGasMapping mapping = FluidGasFormat.parse(OXYGEN_TAG, JsonParser.parseString(json), warnings::add);
        assertEquals(List.of(), warnings);
        return mapping;
    }

    private static String error(String json) {
        return assertThrows(
                        FluidGasFormat.InvalidMappingException.class,
                        () -> FluidGasFormat.parse(OXYGEN_TAG, JsonParser.parseString(json), w -> {}))
                .getMessage();
    }

    /** A file's path names the tag it maps, so one tag has one file, and two packs can't add up. */
    @Test
    void compat_140_a_file_maps_the_tag_its_path_names() throws Exception {
        assertEquals("data/c/zas/fluid_gas/oxygen.json", FluidGasFormat.file(OXYGEN_TAG));
        FluidGasMapping mapping = parse("{\"gas\": \"zas:oxygen\"}");
        assertEquals(FluidGasMapping.of(OXYGEN_TAG, O2), mapping);
        assertFalse(mapping.oneWay());
        assertTrue(parse("{\"gas\": \"zas:oxygen\", \"one_way\": true}").oneWay());
    }

    /** An invalid file names the field at fault; an unknown field is a warning and the rest loads. */
    @Test
    void compat_140_an_invalid_file_names_the_field_at_fault() {
        assertTrue(error("[]").startsWith("must be"));
        assertTrue(error("{}").startsWith("gas:"));
        assertTrue(error("{\"gas\": 3}").startsWith("gas:"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"temperature\": \"hot\"}").startsWith("temperature:"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"temperature\": -1}").startsWith("temperature:"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"min_temperature\": -1}").startsWith("min_temperature:"));
        assertTrue(error("{\"gas\": \"zas:oxygen\", \"one_way\": 1}").startsWith("one_way:"));
        List<String> warnings = new ArrayList<>();
        assertDoesNotThrow(() -> FluidGasFormat.parse(
                OXYGEN_TAG, JsonParser.parseString("{\"gas\": \"zas:oxygen\", \"colour\": 1}"), warnings::add));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("colour"));
    }

    /**
     * A mapping with no temperature arrives at 293.15 K; a set one at that; {@code "fluid"} at the
     * fluid type's; each raised to the least temperature. A gas leaves as the fluid only once it is
     * as hot as that.
     */
    @Test
    void compat_145_the_temperature_a_fluid_arrives_at() throws Exception {
        FluidGasMapping plain = parse("{\"gas\": \"zas:oxygen\"}");
        assertEquals(Millibuckets.STANDARD_TEMPERATURE, plain.entryTemperature(300));
        assertEquals(0, plain.leastTemperature(300));
        FluidGasMapping set = parse("{\"gas\": \"zas:oxygen\", \"temperature\": 90}");
        assertEquals(90, set.entryTemperature(300));
        assertEquals(90, set.leastTemperature(300));
        FluidGasMapping steam =
                parse("{\"gas\": \"zas:water_vapor\", \"temperature\": \"fluid\", \"min_temperature\": 373.15}");
        assertEquals(383, steam.entryTemperature(383));
        assertEquals(373.15, steam.entryTemperature(300));
        assertEquals(383, steam.leastTemperature(383));
        FluidGasMapping hot = parse("{\"gas\": \"zas:water_vapor\", \"min_temperature\": 373.15}");
        assertEquals(373.15, hot.entryTemperature(300));
        assertEquals(373.15, hot.leastTemperature(300));
    }

    /** Some millibuckets are their moles at the unit factor, exactly, as one gas. */
    @Test
    void compat_143_millibuckets_are_moles_at_the_unit_factor() throws Exception {
        double factor = Millibuckets.molesPerMillibucket(1.0);
        GasParcel gas = parse("{\"gas\": \"zas:oxygen\"}").gas(250, factor, 300);
        assertEquals(250 * factor, gas.moles(O2));
        assertEquals(Millibuckets.STANDARD_TEMPERATURE, gas.temperature());
        assertTrue(parse("{\"gas\": \"zas:oxygen\"}").gas(0, factor, 300).isEmpty());
    }

    /** Every shipped file loads with no warning, and maps a {@code c:} tag to one of ZAS³'s gases. */
    @Test
    void compat_140_the_shipped_mappings_load() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/resources/datapacks/content/data/c/zas/fluid_gas");
        List<Path> files;
        try (Stream<Path> found = Files.list(root)) {
            files = found.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(8, files.size(), "shipped mappings: " + files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            ResourceLocation tag = ResourceLocation.fromNamespaceAndPath("c", name.substring(0, name.length() - 5));
            JsonElement json = JsonParser.parseString(Files.readString(file));
            List<String> warnings = new ArrayList<>();
            FluidGasMapping mapping = assertLoads(tag, json, warnings);
            assertEquals(List.of(), warnings, name);
            assertEquals("zas", mapping.gas().getNamespace(), name);
        }
    }

    private static FluidGasMapping assertLoads(ResourceLocation tag, JsonElement json, List<String> warnings) {
        try {
            return FluidGasFormat.parse(tag, json, warnings::add);
        } catch (FluidGasFormat.InvalidMappingException e) {
            throw new AssertionError(tag + ": " + e.getMessage(), e);
        }
    }
}
