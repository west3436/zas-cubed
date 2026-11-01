package io.github.west3436.zas.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasPhaseData;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.gas.GasDefinitionCodecs;
import io.github.west3436.zas.gas.GasDefinitionsPayload;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * Gas definitions and gas tags from datapacks, using the gases and tags in this source set's {@code
 * data/zas_gametest}, and the built-in gas set.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasRegistryTests {
    private GasRegistryTests() {}

    /** The built-in gases and their formulas (CORE-011). */
    private static final Map<String, String> BUILT_IN = Map.of(
            "zas:carbon_dioxide", "CO₂",
            "zas:carbon_monoxide", "CO",
            "zas:hydrogen", "H₂",
            "zas:methane", "CH₄",
            "zas:nitrogen", "N₂",
            "zas:nitrous_oxide", "N₂O",
            "zas:oxygen", "O₂",
            "zas:water_vapor", "H₂O");

    /** CIAAW 2024 abridged standard atomic weights, in kg/mol, for the built-in gases' elements. */
    private static final Map<String, Double> ATOMIC_WEIGHT =
            Map.of("H", 1.0080e-3, "C", 12.011e-3, "N", 14.007e-3, "O", 15.999e-3);

    /** CODATA 2018, J/(mol·K). */
    private static final double R = 8.314462618;

    /** A datapack gas loads with every field (CORE-010). */
    @GameTest(template = "empty")
    public static void core_010_datapack_gas_loads(GameTestHelper h) {
        GasRegistry registry = ServerGasRegistry.current();
        checkTestGas(h, registry, "server");
        GasDefinition plain = registry.find("zas_gametest:plain_gas").orElse(null);
        h.assertTrue(plain != null, "zas_gametest:plain_gas did not load");
        h.assertTrue(plain.visibility().isEmpty(), "visibility is optional");
        h.assertValueEqual(plain.molarHeatCapacity(), 12.5, "plain_gas molar heat capacity");
        // The standard-air gases ship with zas, under the ids the API's StandardAir uses.
        h.assertTrue(registry.find("zas:nitrogen").isPresent(), "zas:nitrogen did not load");
        h.assertTrue(registry.find("zas:oxygen").isPresent(), "zas:oxygen did not load");
        h.succeed();
    }

    /**
     * ZAS³ ships exactly the eight built-in gases, each with its formula and a name translated in
     * the mod's English language file (CORE-011).
     */
    @GameTest(template = "empty")
    public static void core_011_built_in_gas_set(GameTestHelper h) {
        GasRegistry registry = ServerGasRegistry.current();
        List<String> shipped = registry.definitions().stream()
                .map(GasDefinition::id)
                .filter(id -> id.startsWith(ZasMod.MOD_ID + ":"))
                .toList();
        h.assertValueEqual(shipped, BUILT_IN.keySet().stream().sorted().toList(), "the built-in gases");
        JsonObject lang = classpathJson(h, "/assets/zas/lang/en_us.json").getAsJsonObject();
        for (String id : shipped) {
            GasDefinition gas = registry.find(id).orElseThrow();
            h.assertValueEqual(gas.formula(), BUILT_IN.get(id), id + " formula");
            String key = "gas.zas." + id.substring(4);
            h.assertValueEqual(gas.nameKey(), key, id + " name key");
            h.assertTrue(lang.has(key), "en_us.json has no " + key);
        }
        h.succeed();
    }

    /**
     * Each built-in gas cites a source for its molar mass, molar heat capacity and phase data, and
     * the values match what the citations give (CORE-015, CORE-017): the molar mass is the sum of
     * the cited atomic weights over the formula, and the molar heat capacity, at constant volume as
     * the engine uses it, is the cited C_p less R.
     */
    @GameTest(template = "empty")
    public static void core_015_built_in_gases_cite_sources(GameTestHelper h) {
        GasRegistry registry = ServerGasRegistry.current();
        Pattern cp = Pattern.compile("C_v at 298\\.15 K = C_p − R = ([0-9.]+) − 8\\.314 J/\\(mol·K\\)");
        for (String id : BUILT_IN.keySet()) {
            GasDefinition gas = registry.find(id).orElseThrow();
            GasDefinitionCodecs.Body body = builtInBody(h, id);
            Map<String, String> sources = body.sources();
            for (String field : List.of("molar_mass", "molar_heat_capacity", "phase")) {
                h.assertTrue(
                        sources.containsKey(field) && !sources.get(field).isBlank(),
                        id + " cites no source for " + field);
            }
            h.assertTrue(gas.phase().isPresent(), id + " ships no phase data");
            h.assertTrue(body.unknownSources().isEmpty(), id + " cites unknown fields " + body.unknownSources());
            h.assertTrue(sources.get("molar_mass").contains("CIAAW"), id + " molar mass source");

            double molarMass = formulaMass(h, gas.formula());
            h.assertTrue(
                    Math.abs(gas.molarMass() - molarMass) < 5e-7,
                    id + " molar mass " + gas.molarMass() + " doesn't match the atomic weights' " + molarMass);
            Matcher m = cp.matcher(sources.get("molar_heat_capacity"));
            h.assertTrue(m.find(), id + " molar heat capacity source doesn't give C_p: " + sources);
            double cv = Double.parseDouble(m.group(1)) - R;
            h.assertTrue(
                    Math.abs(gas.molarHeatCapacity() - cv) <= 0.005,
                    id + " molar heat capacity " + gas.molarHeatCapacity() + " isn't the cited C_p - R " + cv);
        }
        h.succeed();
    }

    /**
     * Phase data is optional: a gas loads with it and without it, CO₂ has a sublimation point and
     * no melting or boiling point, and a definition whose phase data contradicts itself is skipped,
     * naming the field (CORE-017, OPS-008).
     */
    @GameTest(template = "empty")
    public static void core_017_phase_data_is_optional(GameTestHelper h) {
        GasRegistry registry = ServerGasRegistry.current();
        GasPhaseData test =
                registry.find("zas_gametest:test_gas").orElseThrow().phase().orElse(null);
        h.assertValueEqual(test, GasPhaseData.liquidRange(150, 250, 3000, 12000), "test_gas phase data");
        h.assertTrue(
                registry.find("zas_gametest:plain_gas").orElseThrow().phase().isEmpty(),
                "plain_gas has phase data it didn't define");

        GasPhaseData co2 =
                registry.find("zas:carbon_dioxide").orElseThrow().phase().orElseThrow();
        h.assertTrue(co2.meltingPoint().isEmpty() && co2.boilingPoint().isEmpty(), "CO₂ melts or boils: " + co2);
        h.assertValueEqual(co2.sublimationPoint().getAsDouble(), 194.686, "CO₂ sublimation point");
        GasPhaseData water =
                registry.find("zas:water_vapor").orElseThrow().phase().orElseThrow();
        h.assertValueEqual(water.boilingPoint().getAsDouble(), 373.124, "H₂O boiling point");

        h.assertFalse(registry.find("zas_gametest:invalid_phase").isPresent(), "invalid_phase loaded");
        DatapackErrors.Problem problem = problem(h, "gas definition", "data/zas_gametest/zas/gas/invalid_phase.json");
        h.assertTrue(problem.skipped(), "invalid_phase wasn't skipped");
        h.assertTrue(problem.message().startsWith("phase: "), "the message doesn't name phase: " + problem);

        // A citation for a field the definition doesn't have is reported; the gas still loads.
        h.assertTrue(registry.find("zas_gametest:miscited_gas").isPresent(), "miscited_gas didn't load");
        DatapackErrors.Problem miscited = problem(h, "gas definition", "data/zas_gametest/zas/gas/miscited_gas.json");
        h.assertFalse(miscited.skipped(), "miscited_gas was skipped");
        h.assertTrue(miscited.message().startsWith("sources: 'boiling_point'"), "message: " + miscited);
        h.succeed();
    }

    /**
     * The shipped gas tags hold their gases, a datapack tag can include other tags and optional
     * entries, a tag naming a gas that doesn't exist is reported and left out, and the API reads
     * the same tags (CORE-012, OPS-008).
     */
    @GameTest(template = "empty")
    public static void core_012_gas_tags(GameTestHelper h) {
        GasRegistry registry = ServerGasRegistry.current();
        h.assertValueEqual(
                registry.gasesIn("zas:oxidizer"), List.of("zas:nitrous_oxide", "zas:oxygen"), "#zas:oxidizer");
        h.assertValueEqual(
                registry.gasesIn("zas:flammable"),
                List.of("zas:carbon_monoxide", "zas:hydrogen", "zas:methane"),
                "#zas:flammable");
        h.assertValueEqual(
                registry.gasesIn("zas:toxic"), List.of("zas:carbon_monoxide", "zas:nitrous_oxide"), "#zas:toxic");
        h.assertValueEqual(
                registry.gasesIn("zas_gametest:hazards"),
                List.of("zas:carbon_monoxide", "zas:hydrogen", "zas:methane", "zas:nitrous_oxide"),
                "#zas_gametest:hazards, from two tags and an optional entry that doesn't exist");
        h.assertTrue(registry.isIn(registry.indexOf("zas:hydrogen"), "zas:flammable"), "H₂ by index");
        h.assertValueEqual(
                registry.tagsOf(registry.indexOf("zas:carbon_monoxide")),
                List.of("zas:flammable", "zas:toxic", "zas_gametest:hazards"),
                "CO's tags");

        h.assertFalse(registry.tags().containsKey("zas_gametest:broken"), "the broken tag loaded");
        DatapackErrors.Problem broken = problem(h, "gas tag", "data/zas_gametest/tags/zas/gas/broken.json");
        h.assertTrue(broken.skipped(), "the broken tag wasn't skipped");
        h.assertValueEqual(
                broken.message(),
                "values: no such gas or gas tag: zas_gametest:no_such_gas; an entry that may be missing needs "
                        + "\"required\": false",
                "the broken tag's message");

        for (GasTag tag : List.of(GasTags.OXIDIZER, GasTags.FLAMMABLE, GasTags.TOXIC)) {
            Set<ResourceLocation> expected = new LinkedHashSet<>();
            registry.gasesIn(tag.id().toString()).forEach(id -> expected.add(ResourceLocation.parse(id)));
            h.assertValueEqual(GasTags.gases(tag), expected, "the API's " + tag);
        }
        h.assertTrue(
                GasTags.contains(GasTags.TOXIC, ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide")),
                "the API says CO isn't toxic");
        h.assertFalse(
                GasTags.contains(GasTags.TOXIC, ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide")),
                "the API says CO₂ is toxic");
        h.assertValueEqual(GasTags.gases(GasTag.of("zas_gametest", "missing")), Set.of(), "a missing tag");
        h.succeed();
    }

    /**
     * The sync payload carries every definition, with its phase data, and every tag to the
     * client-side registry unchanged (CORE-014, CORE-012). The payload goes through its network
     * codec, as it does on the wire; a GameTest server has no client to receive it.
     */
    @GameTest(template = "empty")
    public static void core_014_gas_definitions_sync_to_clients(GameTestHelper h) {
        GasRegistry server = ServerGasRegistry.current();
        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            GasDefinitionsPayload.STREAM_CODEC.encode(buf, GasDefinitionsPayload.of(server));
            GasDefinitionsPayload received = GasDefinitionsPayload.STREAM_CODEC.decode(buf);
            h.assertTrue(buf.readableBytes() == 0, "payload left unread bytes");

            ClientGasRegistry.accept(received);
            GasRegistry client = ClientGasRegistry.current();
            checkTestGas(h, client, "client");
            h.assertValueEqual(client, server, "client registry");
            h.assertValueEqual(
                    client.indexOf("zas_gametest:test_gas"), server.indexOf("zas_gametest:test_gas"), "index");
            h.assertValueEqual(
                    client.find("zas:carbon_dioxide").orElseThrow().phase(),
                    server.find("zas:carbon_dioxide").orElseThrow().phase(),
                    "client CO₂ phase data");
            h.assertValueEqual(client.tags(), server.tags(), "client gas tags");
        } finally {
            buf.release();
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
        }
        h.succeed();
    }

    private static void checkTestGas(GameTestHelper h, GasRegistry registry, String side) {
        GasDefinition gas = registry.find("zas_gametest:test_gas").orElse(null);
        h.assertTrue(gas != null, side + " registry lacks zas_gametest:test_gas: " + registry);
        h.assertValueEqual(gas.nameKey(), "gas.zas_gametest.test_gas", side + " name");
        h.assertValueEqual(gas.formula(), "Tg₂", side + " formula");
        h.assertValueEqual(gas.molarMass(), 0.05, side + " molar mass");
        h.assertValueEqual(gas.molarHeatCapacity(), 25.5, side + " molar heat capacity");
        h.assertTrue(gas.visibility().isPresent(), side + " visibility missing");
        h.assertValueEqual(gas.visibility().get().rgb(), 0x80FF20, side + " colour");
        h.assertValueEqual(gas.visibility().get().densityThreshold(), 0.01, side + " density threshold");
        h.assertTrue(gas.phase().isPresent(), side + " phase data missing");
    }

    /** The most recent load's problem with a file. */
    private static DatapackErrors.Problem problem(GameTestHelper h, String kind, String file) {
        List<DatapackErrors.Problem> problems = DatapackErrors.recent();
        return problems.stream()
                .filter(p -> p.kind().equals(kind) && p.file().equals(file))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no problem reported for " + file + " in " + problems));
    }

    /** A built-in gas's definition file as the server's resources hold it, parsed. */
    private static GasDefinitionCodecs.Body builtInBody(GameTestHelper h, String id) {
        ResourceLocation gas = ResourceLocation.parse(id);
        ResourceLocation file = gas.withPath(GasDefinitionCodecs.DIRECTORY + "/" + gas.getPath() + ".json");
        Resource resource = h.getLevel()
                .getServer()
                .getResourceManager()
                .getResource(file)
                .orElseThrow(() -> new GameTestAssertException("no file " + file));
        try (Reader reader = resource.openAsReader()) {
            return GasDefinitionCodecs.Body.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(message -> new GameTestAssertException(file + ": " + message));
        } catch (IOException e) {
            throw new GameTestAssertException("can't read " + file + ": " + e);
        }
    }

    private static JsonElement classpathJson(GameTestHelper h, String path) {
        try (InputStream in = GasRegistryTests.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new GameTestAssertException("no " + path + " on the classpath");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new GameTestAssertException("can't read " + path + ": " + e);
        }
    }

    /** The molar mass, in kg/mol, of a formula such as {@code CH₄}, from the cited atomic weights. */
    private static double formulaMass(GameTestHelper h, String formula) {
        String plain = formula.chars()
                .map(c -> c >= '₀' && c <= '₉' ? '0' + (c - '₀') : c)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        Matcher m = Pattern.compile("([A-Z][a-z]?)([0-9]*)").matcher(plain);
        double mass = 0;
        int end = 0;
        while (m.find()) {
            h.assertTrue(m.start() == end, "can't read formula " + formula);
            Double weight = ATOMIC_WEIGHT.get(m.group(1));
            h.assertTrue(weight != null, "no atomic weight for " + m.group(1) + " in " + formula);
            mass += weight * (m.group(2).isEmpty() ? 1 : Integer.parseInt(m.group(2)));
            end = m.end();
        }
        h.assertTrue(end == plain.length() && end > 0, "can't read formula " + formula);
        return mass;
    }
}
