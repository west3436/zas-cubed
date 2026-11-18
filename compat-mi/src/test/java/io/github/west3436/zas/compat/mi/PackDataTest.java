package io.github.west3436.zas.compat.mi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The data the adapter ships, read as files: the gases it adds and the fluid tags they map from
 * (COMPAT-092), and the content's mappings of the steams (COMPAT-090, COMPAT-093).
 */
class PackDataTest {
    private static final Path PACK = Path.of(property("zas.mi.resources"), "datapacks", "modern_industrialization");
    private static final Path ZAS = Path.of(property("zas.resources"));
    private static final Path SHARED = ZAS.resolve("datapacks").resolve("shared_gases");
    private static final Path CONTENT = Path.of(property("zas.atmos.resources"), "datapacks", "content");
    private static final Path NUCLEARCRAFT =
            Path.of(property("zas.nuclearcraft.resources"), "datapacks", "nuclearcraft");

    /** The gases the adapter's own pack defines, which NuclearCraft's defines too. */
    private static final Set<String> OWN_GASES = Set.of("argon", "helium", "helium_3");

    /**
     * Modern Industrialization's {@code c:} fluid tags that are gases (its {@code #c:gaseous}, less
     * those left out) and the gas each maps to, from whichever pack.
     */
    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("oxygen", "zas:oxygen"),
            Map.entry("nitrogen", "zas:nitrogen"),
            Map.entry("hydrogen", "zas:hydrogen"),
            Map.entry("methane", "zas:methane"),
            Map.entry("steam", "zas:water_vapor"),
            Map.entry("high_pressure_steam", "zas:water_vapor"),
            Map.entry("argon", "zas:argon"),
            Map.entry("helium", "zas:helium"),
            Map.entry("helium_3", "zas:helium_3"),
            Map.entry("chlorine", "zas:chlorine"),
            Map.entry("deuterium", "zas:deuterium"),
            Map.entry("tritium", "zas:tritium"),
            Map.entry("ethylene", "zas:ethene"));

    private static String property(String name) {
        String value = System.getProperty(name);
        assertTrue(value != null && !value.isBlank(), name + " is not set by the build");
        return value;
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /** The JSON files in a folder, by name without ".json". */
    private static Map<String, JsonObject> files(Path dir) throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> found = Files.list(dir)) {
            for (Path file : (Iterable<Path>) found.filter(p -> p.toString().endsWith(".json"))::iterator) {
                String name = file.getFileName().toString();
                out.put(name.substring(0, name.length() - ".json".length()), read(file));
            }
        }
        return out;
    }

    /**
     * The adapter's gases are NuclearCraft's, file for file, so that with both mods installed the
     * one that wins is the same: a gas has one definition, and one mapping from its tag.
     */
    @Test
    void compat_092_its_gases_are_nuclearcrafts_file_for_file() throws IOException {
        assertEquals(OWN_GASES, files(PACK.resolve("data/zas/zas/gas")).keySet(), "the adapter's gases");
        for (String gas : OWN_GASES) {
            for (String dir : List.of("data/zas/zas/gas", "data/c/zas/fluid_gas")) {
                Path file = Path.of(dir, gas + ".json");
                assertEquals(
                        Files.readString(NUCLEARCRAFT.resolve(file)),
                        Files.readString(PACK.resolve(file)),
                        file + " differs from NuclearCraft's");
            }
        }
        assertFalse(Files.exists(PACK.resolve("data/zas/tags")), "none of its gases is toxic or has a tag");
        assertFalse(Files.exists(PACK.resolve("data/zas/zas/hazard_profile")), "none of its gases has a hazard");
        JsonObject lang = read(ZAS.resolve("assets/zas/lang/en_us.json"));
        assertTrue(lang.has("pack.zas.modern_industrialization"));
        assertTrue(lang.has("pack.zas.modern_industrialization.description"));
        assertEquals(
                "pack.zas.modern_industrialization.description",
                read(PACK.resolve("pack.mcmeta"))
                        .getAsJsonObject("pack")
                        .getAsJsonObject("description")
                        .get("translate")
                        .getAsString());
    }

    /**
     * Each of Modern Industrialization's gas tags maps to its gas, in one pack the adapter has on:
     * its own, the shared gases' or the content's. No tag is mapped in two of them but those the
     * adapter shares with NuclearCraft, and each mapped gas is defined.
     */
    @Test
    void compat_092_each_gas_tag_maps_to_a_defined_gas_once() throws IOException {
        Set<String> defined = new TreeSet<>();
        for (Path dir : List.of(
                PACK.resolve("data/zas/zas/gas"),
                SHARED.resolve("data/zas/zas/gas"),
                ZAS.resolve("data/zas/zas/gas"))) {
            files(dir).keySet().forEach(g -> defined.add("zas:" + g));
        }
        Map<String, JsonObject> mappings = new TreeMap<>();
        for (Path pack : List.of(PACK, SHARED, CONTENT)) {
            for (Map.Entry<String, JsonObject> mapping :
                    files(pack.resolve("data/c/zas/fluid_gas")).entrySet()) {
                assertFalse(mappings.containsKey(mapping.getKey()), "#c:" + mapping.getKey() + " is mapped twice");
                mappings.put(mapping.getKey(), mapping.getValue());
            }
        }
        TAGS.forEach((tag, gas) -> {
            JsonObject mapping = mappings.get(tag);
            assertTrue(mapping != null, "#c:" + tag + " isn't mapped");
            assertEquals(gas, mapping.get("gas").getAsString(), "#c:" + tag);
            assertTrue(defined.contains(gas), "#c:" + tag + " names no defined gas");
        });
        assertEquals(
                Set.of("argon", "helium", "helium_3", "ethylene"),
                files(PACK.resolve("data/c/zas/fluid_gas")).keySet(),
                "the adapter's own mappings");
    }

    /**
     * Steam arrives at boiling at least, and high-pressure steam at 383 K at least, both at the
     * fluid's own temperature if it says more. Modern Industrialization's fluids all say 300 K, so
     * the floor keeps its two steams apart, and high-pressure steam is made of water vapour only
     * once that is hotter. Neither is one way.
     */
    @Test
    void compat_093_high_pressure_steam_arrives_hotter_than_steam() throws IOException {
        Map<String, JsonObject> content = files(CONTENT.resolve("data/c/zas/fluid_gas"));
        Map<String, Double> least = Map.of("steam", 373.15, "high_pressure_steam", 383.0);
        least.forEach((tag, temperature) -> {
            JsonObject mapping = content.get(tag);
            assertEquals("zas:water_vapor", mapping.get("gas").getAsString(), tag);
            assertEquals("fluid", mapping.get("temperature").getAsString(), tag);
            assertEquals(temperature, mapping.get("min_temperature").getAsDouble(), 1e-9, tag);
            assertFalse(mapping.has("one_way") && mapping.get("one_way").getAsBoolean(), tag + ": one way");
        });
        assertFalse(
                Files.exists(NUCLEARCRAFT.resolve("data/c/zas/fluid_gas/high_pressure_steam.json")),
                "NuclearCraft's pack still maps #c:high_pressure_steam");
    }
}
