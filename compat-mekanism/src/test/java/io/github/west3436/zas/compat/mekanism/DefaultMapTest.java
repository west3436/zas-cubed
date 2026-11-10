package io.github.west3436.zas.compat.mekanism;

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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The map the adapter ships (MEK-010), read from its data pack as files: what is mapped by default,
 * what never is, steam's temperature and fusion fuel's split.
 */
class DefaultMapTest {
    private static final Path PACK = Path.of(System.getProperty("zas.mekanism.resources"), "datapacks", "mekanism");
    private static final Path ZAS_RESOURCES = Path.of(System.getProperty("zas.resources"));

    /** ZAS³'s gases that Mekanism has no chemical for, which the adapter registers as chemicals (MEK-011). */
    private static final Set<String> REGISTERED =
            Set.of("zas:nitrogen", "zas:carbon_dioxide", "zas:nitrous_oxide", "zas:carbon_monoxide", "zas:methane");

    /** The gases ZAS³ itself defines that the defaults map onto. */
    private static final Set<String> BUILT_IN;

    static {
        Set<String> builtIn = new TreeSet<>(Set.of("zas:oxygen", "zas:hydrogen", "zas:water_vapor"));
        builtIn.addAll(REGISTERED);
        BUILT_IN = builtIn;
    }

    private static ChemicalMap map;

    @BeforeAll
    static void load() throws IOException {
        List<ChemicalMapping> mappings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> file :
                files(ChemicalMapFormat.DIRECTORY).entrySet()) {
            try {
                mappings.add(ChemicalMapFormat.parse(file.getKey(), file.getValue(), warnings::add));
            } catch (ChemicalMapFormat.InvalidMappingException e) {
                throw new AssertionError(ChemicalMapFormat.file(file.getKey()) + ": " + e.getMessage(), e);
            }
        }
        assertEquals(List.of(), warnings);
        map = ChemicalMap.of(mappings);
    }

    /** The pack's files in a folder of a format, by the id their path gives them. */
    private static Map<ResourceLocation, JsonElement> files(String directory) throws IOException {
        Map<ResourceLocation, JsonElement> out = new TreeMap<>();
        Path data = PACK.resolve("data");
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespace : (Iterable<Path>) namespaces::iterator) {
                Path folder = namespace.resolve(directory);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> found = Files.walk(folder)) {
                    for (Path file :
                            (Iterable<Path>) found.filter(p -> p.toString().endsWith(".json"))::iterator) {
                        String path = folder.relativize(file).toString().replace('\\', '/');
                        out.put(
                                ResourceLocation.fromNamespaceAndPath(
                                        namespace.getFileName().toString(), path.substring(0, path.length() - 5)),
                                JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        return out;
    }

    private static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }

    private static String onlyGas(String chemical) {
        ChemicalMapping mapping = map.ofChemical(id(chemical)).orElseThrow(() -> new AssertionError(chemical));
        assertEquals(1, mapping.gases().size(), chemical);
        return mapping.gases().keySet().iterator().next().toString();
    }

    /**
     * The requirement's list, O₂, H₂, water vapour, steam, Cl₂, HCl, SO₂, C₂H₄, D₂, T₂ and fuel, the
     * chemicals the adapter itself registers (MEK-011), and nothing else.
     */
    @Test
    void mek_010_the_default_map_is_the_requirements_list() {
        Set<String> expected = new TreeSet<>(Set.of(
                "mekanism:oxygen",
                "mekanism:hydrogen",
                "mekanism:water_vapor",
                "mekanism:steam",
                "mekanism:chlorine",
                "mekanism:hydrogen_chloride",
                "mekanism:sulfur_dioxide",
                "mekanism:ethene",
                "mekanismgenerators:deuterium",
                "mekanismgenerators:tritium",
                "mekanismgenerators:fusion_fuel"));
        expected.addAll(REGISTERED);
        assertEquals(
                expected,
                new TreeSet<>(
                        map.chemicals().stream().map(ResourceLocation::toString).toList()));
        assertEquals("zas:oxygen", onlyGas("mekanism:oxygen"));
        assertEquals("zas:hydrogen", onlyGas("mekanism:hydrogen"));
        assertEquals("zas:water_vapor", onlyGas("mekanism:water_vapor"));
        assertEquals("zas:water_vapor", onlyGas("mekanism:steam"));
        assertEquals("zas:chlorine", onlyGas("mekanism:chlorine"));
        assertEquals("zas:hydrogen_chloride", onlyGas("mekanism:hydrogen_chloride"));
        assertEquals("zas:sulfur_dioxide", onlyGas("mekanism:sulfur_dioxide"));
        assertEquals("zas:ethene", onlyGas("mekanism:ethene"));
        assertEquals("zas:deuterium", onlyGas("mekanismgenerators:deuterium"));
        assertEquals("zas:tritium", onlyGas("mekanismgenerators:tritium"));
        assertEquals(Map.of(), map.ties());
    }

    /**
     * Each gas the adapter registers as a chemical is mapped to that chemical, which has the gas's
     * own id, both ways and with no least temperature; and the pack tags the five as gaseous, which
     * is how Mekanism knows to draw them as gas, adding to Mekanism's tag.
     */
    @Test
    void mek_011_each_registered_gas_is_mapped_to_the_chemical_of_its_own_id() throws IOException {
        for (String gas : REGISTERED) {
            ChemicalMapping mapping = map.ofChemical(id(gas)).orElseThrow(() -> new AssertionError(gas));
            assertEquals(Map.of(id(gas), 1.0), mapping.gases(), gas);
            assertTrue(mapping.bothWays(), gas);
            assertEquals(0, mapping.minTemperature(), gas);
            assertEquals(Optional.of(id(gas)), map.forGas(id(gas), 293.15).map(ChemicalMapping::chemical), gas);
            assertTrue(
                    Files.isRegularFile(ZAS_RESOURCES.resolve("data/zas/zas/gas/" + id(gas).getPath() + ".json")),
                    gas + " isn't one of ZAS³'s gases");
        }
        // No chemical in ZAS³'s namespace is mapped but those.
        assertEquals(
                new TreeSet<>(REGISTERED),
                new TreeSet<>(map.chemicals().stream()
                        .filter(chemical -> chemical.getNamespace().equals("zas"))
                        .map(ResourceLocation::toString)
                        .toList()));

        JsonObject tag = JsonParser.parseString(Files.readString(
                        PACK.resolve("data/mekanism/tags/mekanism/chemical/gaseous.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertFalse(tag.has("replace"), "the tag must add to Mekanism's, not replace it");
        Set<String> values = new TreeSet<>();
        tag.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        assertEquals(new TreeSet<>(REGISTERED), values);
    }

    /** Steam enters at 373.15 K or more; water vapour and everything else at the source's temperature. */
    @Test
    void mek_010_steam_is_the_only_default_with_a_least_temperature() {
        for (ChemicalMapping mapping : map.mappings()) {
            boolean steam = mapping.chemical().equals(id("mekanism:steam"));
            assertEquals(
                    steam ? 373.15 : 0,
                    mapping.minTemperature(),
                    mapping.chemical().toString());
            assertFalse(mapping.oneWay(), mapping.chemical().toString());
        }
        assertEquals(
                Optional.of(id("mekanism:steam")),
                map.forGas(id("zas:water_vapor"), 373.15).map(ChemicalMapping::chemical));
        assertEquals(
                Optional.of(id("mekanism:water_vapor")),
                map.forGas(id("zas:water_vapor"), 293.15).map(ChemicalMapping::chemical));
    }

    /** Fusion fuel is half deuterium and half tritium, and neither gas comes back as fuel. */
    @Test
    void mek_010_fusion_fuel_is_half_deuterium_and_half_tritium() {
        ChemicalMapping fuel =
                map.ofChemical(id("mekanismgenerators:fusion_fuel")).orElseThrow();
        assertEquals(Map.of(id("zas:deuterium"), 0.5, id("zas:tritium"), 0.5), fuel.gases());
        assertFalse(fuel.bothWays());
        assertEquals(
                Optional.of(id("mekanismgenerators:deuterium")),
                map.forGas(id("zas:deuterium"), 300).map(ChemicalMapping::chemical));
        assertEquals(
                Optional.of(id("mekanismgenerators:tritium")),
                map.forGas(id("zas:tritium"), 300).map(ChemicalMapping::chemical));
    }

    /**
     * Never mapped: process liquids, metals, radioactive chemicals, infuse types, pigments and
     * slurries, by the ids Mekanism 10.7.19 registers them under.
     */
    @Test
    void mek_010_process_liquids_metals_radioactives_infusions_pigments_and_slurries_are_never_mapped() {
        List<String> never = new ArrayList<>(List.of(
                // process liquids and metals, and what isn't a gas at room conditions
                "brine",
                "sodium",
                "superheated_sodium",
                "lithium",
                "sulfuric_acid",
                "hydrofluoric_acid",
                "osmium",
                "uranium_oxide",
                "uranium_hexafluoride",
                "fissile_fuel",
                "sulfur_trioxide",
                // radioactive
                "nuclear_waste",
                "spent_nuclear_waste",
                "plutonium",
                "polonium",
                // exotic, and the placeholder
                "antimatter",
                "empty",
                // infuse types
                "carbon",
                "redstone",
                "diamond",
                "refined_obsidian",
                "gold",
                "tin",
                "bio",
                "fungi"));
        for (String colour : List.of(
                "black",
                "dark_blue",
                "dark_green",
                "dark_aqua",
                "dark_red",
                "purple",
                "orange",
                "gray",
                "dark_gray",
                "indigo",
                "bright_green",
                "aqua",
                "red",
                "pink",
                "yellow",
                "white",
                "brown",
                "bright_pink")) {
            never.add(colour);
        }
        for (String ore : List.of("iron", "gold", "osmium", "copper", "tin", "lead", "uranium")) {
            never.add("dirty_" + ore);
            never.add("clean_" + ore);
        }
        for (String name : never) {
            assertEquals(Optional.empty(), map.ofChemical(id("mekanism:" + name)), name);
        }
    }

    /**
     * Every gas the defaults name is one of ZAS³'s own or is defined by the adapter's pack, with a
     * name players can read; the pack defines no gas the map doesn't use.
     */
    @Test
    void mek_010_every_mapped_gas_is_defined_and_named() throws IOException {
        Map<ResourceLocation, JsonElement> definitions = files("zas/gas");
        Set<String> named = new TreeSet<>();
        for (ChemicalMapping mapping : map.mappings()) {
            mapping.gases().keySet().forEach(gas -> named.add(gas.toString()));
        }
        Set<String> defined = new TreeSet<>(
                definitions.keySet().stream().map(ResourceLocation::toString).toList());
        Set<String> expected = new TreeSet<>(named);
        expected.removeAll(BUILT_IN);
        assertEquals(expected, defined, "the pack's gas definitions");
        for (String gas : BUILT_IN) {
            assertTrue(
                    Files.isRegularFile(ZAS_RESOURCES.resolve("data/zas/zas/gas/" + id(gas).getPath() + ".json")),
                    gas + " isn't one of ZAS³'s gases");
        }

        JsonObject lang = JsonParser.parseString(
                        Files.readString(ZAS_RESOURCES.resolve("assets/zas/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        definitions.forEach((gas, json) -> {
            JsonObject definition = json.getAsJsonObject();
            String name = definition.get("name").getAsString();
            assertEquals("gas." + gas.getNamespace() + "." + gas.getPath(), name);
            assertTrue(lang.has(name), "en_us.json has no " + name);
            assertTrue(definition.get("molar_mass").getAsDouble() > 0, gas + " molar_mass");
            assertTrue(definition.get("molar_heat_capacity").getAsDouble() > 0, gas + " molar_heat_capacity");
            assertFalse(definition.get("formula").getAsString().isBlank(), gas + " formula");
            JsonObject sources = definition.getAsJsonObject("sources");
            assertTrue(sources.has("molar_mass") && sources.has("molar_heat_capacity"), gas + " cites no sources");
        });
        assertTrue(lang.has("pack.zas.mekanism") && lang.has("pack.zas.mekanism.description"));
    }

    /** The toxic chemicals' gases join ZAS³'s toxic gases, so masks filter them and scrubbers remove them. */
    @Test
    void mek_010_chlorine_hydrogen_chloride_and_sulfur_dioxide_are_toxic() throws IOException {
        JsonObject tag = JsonParser.parseString(
                        Files.readString(PACK.resolve("data/zas/tags/zas/gas/toxic.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertFalse(tag.has("replace"), "the tag must add to ZAS³'s, not replace it");
        Set<String> values = new TreeSet<>();
        tag.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        assertEquals(Set.of("zas:chlorine", "zas:hydrogen_chloride", "zas:sulfur_dioxide"), values);
    }
}
