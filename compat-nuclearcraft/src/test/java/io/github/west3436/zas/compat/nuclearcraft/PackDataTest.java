package io.github.west3436.zas.compat.nuclearcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The data the adapter ships, read as files: its gases and those it shares with the Mekanism
 * adapter (COMPAT-151), the fluid tags they map from (COMPAT-152), the HEV suit (COMPAT-154) and
 * what Nuclear Radiation reads of ZAS³'s masks (COMPAT-155).
 */
class PackDataTest {
    private static final Path PACK = Path.of(property("zas.nuclearcraft.resources"), "datapacks", "nuclearcraft");
    private static final Path ZAS = Path.of(property("zas.resources"));
    private static final Path SHARED = ZAS.resolve("datapacks").resolve("shared_gases");
    private static final Path ATMOS = Path.of(property("zas.atmos.resources"));

    /** The gases only NuclearCraft has, which the issue names. */
    private static final Set<String> OWN_GASES = Set.of(
            "argon",
            "neon",
            "helium",
            "helium_3",
            "nitric_oxide",
            "nitrogen_dioxide",
            "fluorine",
            "ammonia",
            "fluoromethane",
            "oxygen_difluoride",
            "diborane",
            "sulfur_trioxide",
            "radon");

    /** Those NuclearCraft shares with Mekanism. */
    private static final Set<String> SHARED_GASES =
            Set.of("chlorine", "sulfur_dioxide", "ethene", "deuterium", "tritium");

    /** The toxic ones of both, which have hazard profiles. */
    private static final Set<String> TOXIC = Set.of(
            "nitric_oxide",
            "nitrogen_dioxide",
            "fluorine",
            "ammonia",
            "oxygen_difluoride",
            "diborane",
            "sulfur_trioxide");

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

    private static Set<String> tag(Path pack) throws IOException {
        JsonObject tag = read(pack.resolve("data/zas/tags/zas/gas/toxic.json"));
        assertFalse(tag.has("replace"), "the tag must add to ZAS³'s, not replace it");
        Set<String> values = new TreeSet<>();
        tag.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        return values;
    }

    @Test
    void compat_151_each_gas_is_defined_once_with_cited_properties_and_a_name() throws IOException {
        Map<String, JsonObject> own = files(PACK.resolve("data/zas/zas/gas"));
        Map<String, JsonObject> shared = files(SHARED.resolve("data/zas/zas/gas"));
        assertEquals(OWN_GASES, own.keySet(), "the adapter's own gases");
        assertEquals(SHARED_GASES, shared.keySet(), "the shared gases");
        for (String builtIn : files(ZAS.resolve("data/zas/zas/gas")).keySet()) {
            assertFalse(own.containsKey(builtIn) || shared.containsKey(builtIn), builtIn + " is ZAS³'s own");
        }

        JsonObject lang = read(ZAS.resolve("assets/zas/lang/en_us.json"));
        Map<String, JsonObject> all = new TreeMap<>(own);
        all.putAll(shared);
        all.forEach((gas, definition) -> {
            assertEquals("gas.zas." + gas, definition.get("name").getAsString(), gas);
            assertTrue(lang.has("gas.zas." + gas), "en_us.json has no gas.zas." + gas);
            assertFalse(definition.get("formula").getAsString().isBlank(), gas + " formula");
            double mass = definition.get("molar_mass").getAsDouble();
            assertTrue(mass > 0.001 && mass < 0.3, gas + " molar_mass is in kg/mol");
            double cv = definition.get("molar_heat_capacity").getAsDouble();
            // At least a monatomic gas's 3/2 R.
            assertTrue(cv >= 12.47 && cv < 100, gas + " molar_heat_capacity");
            JsonObject sources = definition.getAsJsonObject("sources");
            assertTrue(sources.has("molar_mass") && sources.has("molar_heat_capacity"), gas + " cites no sources");
            assertFalse(definition.has("visibility"), gas + " needs no texture");
        });
        for (String noble : List.of("argon", "neon", "helium", "helium_3", "radon")) {
            assertEquals(12.47, own.get(noble).get("molar_heat_capacity").getAsDouble(), 1e-9, noble);
        }
        assertTrue(lang.has("pack.zas.nuclearcraft") && lang.has("pack.zas.nuclearcraft.description"));
        assertTrue(lang.has("pack.zas.shared_gases") && lang.has("pack.zas.shared_gases.description"));
    }

    @Test
    void compat_151_the_toxic_gases_are_tagged_and_have_ascending_hazards() throws IOException {
        Set<String> toxic = new TreeSet<>();
        TOXIC.forEach(gas -> toxic.add("zas:" + gas));
        assertEquals(toxic, tag(PACK));
        assertEquals(Set.of("zas:chlorine", "zas:sulfur_dioxide"), tag(SHARED));

        Map<String, JsonObject> profiles = files(PACK.resolve("data/zas/zas/hazard_profile"));
        assertEquals(TOXIC, profiles.keySet(), "the adapter's hazard profiles");
        Map<String, JsonObject> sharedProfiles = files(SHARED.resolve("data/zas/zas/hazard_profile"));
        assertEquals(Set.of("chlorine", "sulfur_dioxide"), sharedProfiles.keySet(), "the shared hazard profiles");
        Map<String, JsonObject> all = new TreeMap<>(profiles);
        all.putAll(sharedProfiles);
        all.forEach((gas, profile) -> {
            List<Double> pressures = new ArrayList<>();
            List<String> levels = new ArrayList<>();
            for (JsonElement t : profile.getAsJsonArray("thresholds")) {
                JsonObject threshold = t.getAsJsonObject();
                pressures.add(threshold.get("partial_pressure").getAsDouble());
                levels.add(
                        threshold.has("effect_level")
                                ? threshold.get("effect_level").getAsString()
                                : "warning");
            }
            assertEquals(List.of("warning", "impaired", "unconscious"), levels, gas);
            for (int i = 1; i < pressures.size(); i++) {
                assertTrue(pressures.get(i) > pressures.get(i - 1), gas + " thresholds ascend");
            }
            assertTrue(pressures.get(0) > 0, gas);
        });
    }

    @Test
    void compat_152_each_gas_fluid_tag_maps_to_a_defined_gas() throws IOException {
        Set<String> defined = new TreeSet<>();
        for (Path dir : List.of(
                PACK.resolve("data/zas/zas/gas"),
                SHARED.resolve("data/zas/zas/gas"),
                ZAS.resolve("data/zas/zas/gas"))) {
            files(dir).keySet().forEach(g -> defined.add("zas:" + g));
        }
        Map<String, JsonObject> mappings = new TreeMap<>(files(PACK.resolve("data/c/zas/fluid_gas")));
        Map<String, JsonObject> shared = files(SHARED.resolve("data/c/zas/fluid_gas"));
        for (String tag : shared.keySet()) {
            assertFalse(mappings.containsKey(tag), "#c:" + tag + " is mapped in both packs");
        }
        mappings.putAll(shared);
        mappings.forEach((tag, mapping) ->
                assertTrue(defined.contains(mapping.get("gas").getAsString()), "#c:" + tag + " names no defined gas"));
        // Each gas, NuclearCraft's own and shared, by the tag of its own name.
        for (String gas : OWN_GASES) {
            assertEquals("zas:" + gas, mappings.get(gas).get("gas").getAsString(), "#c:" + gas);
        }
        for (String gas : SHARED_GASES) {
            assertEquals("zas:" + gas, mappings.get(gas).get("gas").getAsString(), "#c:" + gas);
        }
    }

    @Test
    void compat_152_steams_become_water_vapour_at_their_own_temperatures() throws IOException {
        Map<String, JsonObject> mappings = files(PACK.resolve("data/c/zas/fluid_gas"));
        // Plain #c:steam and #c:high_pressure_steam are the content's own mappings, since Modern
        // Industrialization has them too (COMPAT-093); NuclearCraft's spent steams are here.
        assertFalse(mappings.containsKey("steam"));
        assertFalse(mappings.containsKey("high_pressure_steam"));
        Map<String, JsonObject> content = files(ATMOS.resolve("datapacks/content/data/c/zas/fluid_gas"));
        JsonObject high = content.get("high_pressure_steam");
        assertEquals("zas:water_vapor", high.get("gas").getAsString());
        assertEquals("fluid", high.get("temperature").getAsString());
        // NuclearCraft's own 383 K, so it arrives as before.
        assertEquals(383, high.get("min_temperature").getAsDouble(), 1e-9);
        assertFalse(high.has("one_way") && high.get("one_way").getAsBoolean(), "high-pressure steam: one way");
        for (String steam : List.of("exhaust_steam", "low_pressure_steam", "low_quality_steam")) {
            JsonObject mapping = mappings.get(steam);
            assertEquals("zas:water_vapor", mapping.get("gas").getAsString(), steam);
            assertEquals("fluid", mapping.get("temperature").getAsString(), steam);
            assertEquals(373.15, mapping.get("min_temperature").getAsDouble(), 1e-9, steam);
            // Only high-pressure steam is made of water vapour going back into a tank; the spent
            // steams would otherwise tie with plain steam at the same temperature.
            assertTrue(mapping.get("one_way").getAsBoolean(), steam + ": one way");
        }
        JsonObject hotHelium = mappings.get("hot_helium");
        assertEquals("zas:helium", hotHelium.get("gas").getAsString());
        assertEquals("fluid", hotHelium.get("temperature").getAsString());
    }

    @Test
    void compat_154_the_hev_suit_is_sealed_against_any_pressure_and_temperature() throws IOException {
        JsonObject hev = read(PACK.resolve("data/nuclearcraft/zas/protection/hev.json"));
        List<String> items = new ArrayList<>();
        hev.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
        assertEquals(
                List.of(
                        "nuclearcraft:hev_helmet",
                        "nuclearcraft:hev_chestplate",
                        "nuclearcraft:hev_leggings",
                        "nuclearcraft:hev_boots"),
                items);
        assertEquals(0.0, hev.get("min_pressure_kpa").getAsDouble());
        assertEquals(1.0e9, hev.get("max_pressure_kpa").getAsDouble());
        assertEquals(0.0, hev.get("min_temperature_k").getAsDouble());
        assertEquals(1.0e9, hev.get("max_temperature_k").getAsDouble());
        assertTrue(hev.get("sealed_breathing").getAsBoolean());
    }

    @Test
    void compat_155_nuclear_radiation_reads_the_masks_and_helmets_as_gas_protection() throws IOException {
        Map<String, JsonObject> armor =
                files(ATMOS.resolve("datapacks/content/data/zas_atmos/nuclear_radiation/armor_protection"));
        assertEquals(Set.of("breath_mask", "gas_mask", "hardsuit_helmet"), armor.keySet());
        armor.forEach((item, json) -> {
            assertEquals("zas_atmos:" + item, json.get("target").getAsString(), item);
            assertTrue(json.get("gas_protection").getAsBoolean(), item);
            assertTrue(
                    Files.isRegularFile(ATMOS.resolve("assets/zas_atmos/models/item/" + item + ".json")),
                    item + " is no item of the content's");
        });
    }
}
