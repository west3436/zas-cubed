package io.github.west3436.zas.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.fire.CarbonCombustion;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionRate;
import io.github.west3436.zas.engine.reaction.ReactionResult;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import io.github.west3436.zas.gas.GasDefinitionCodecs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The reaction datapack format (FIRE-001) and the shipped reactions (FIRE-003). */
class ReactionFormatTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "everything");

    @Test
    void fire_001_a_definition_with_every_field_loads() {
        ReactionDefinition d = parse("""
                {
                  "type": "zas:standard",
                  "reactants": {"zas:methane": 1, "zas:oxygen": 2},
                  "products": {"zas:carbon_dioxide": 1, "zas:water_vapor": 2},
                  "enthalpy": -802000,
                  "min_temperature": 250,
                  "max_temperature": 3000,
                  "concentrations": [{"gas": "zas:oxygen", "min": 0.1, "max": 0.9}, {"gas": "zas:nitrogen"}],
                  "catalysts": [{"gas": "zas:carbon_monoxide", "min": 0.001}, {"gas": "zas:hydrogen"}],
                  "requires_ignition": true,
                  "flammability": {"fuel": "zas:methane", "lower": 0.05, "upper": 0.15, "autoignition": 810},
                  "incomplete_combustion": {
                    "reactants": {"zas:methane": 1, "zas:oxygen": 1.5},
                    "products": {"zas:carbon_monoxide": 1, "zas:water_vapor": 2},
                    "enthalpy": -519000
                  },
                  "sources": {"enthalpy": "a citation"}
                }
                """).orElseThrow();
        assertEquals("test:everything", d.id());
        assertEquals(ReactionDefinition.STANDARD, d.type());
        assertEquals(Map.of("zas:methane", 1.0, "zas:oxygen", 2.0), d.reactants());
        assertEquals(Map.of("zas:carbon_dioxide", 1.0, "zas:water_vapor", 2.0), d.products());
        assertEquals(-802000, d.enthalpy());
        ReactionConditions c = d.conditions();
        assertEquals(250, c.minTemperature());
        assertEquals(3000, c.maxTemperature());
        assertEquals(
                List.of(
                        new ReactionConditions.Concentration("zas:oxygen", 0.1, 0.9),
                        new ReactionConditions.Concentration("zas:nitrogen", 0, 1)),
                c.concentrations());
        assertEquals(
                List.of(
                        new ReactionConditions.Concentration("zas:carbon_monoxide", 0.001, 1),
                        new ReactionConditions.Concentration("zas:hydrogen", 0, 1)),
                c.catalysts());
        assertTrue(c.requiresIgnition());
        assertEquals(
                Optional.of(new ReactionConditions.FlammabilityLimits("zas:methane", 0.05, 0.15, 810)),
                c.flammability());
        assertEquals(
                Optional.of(new ReactionDefinition.Stoichiometry(
                        Map.of("zas:methane", 1.0, "zas:oxygen", 1.5),
                        Map.of("zas:carbon_monoxide", 1.0, "zas:water_vapor", 2.0),
                        -519000)),
                d.incomplete());
        assertEquals(Optional.empty(), d.rate());
    }

    @Test
    void fire_001_a_rate_and_its_defaults_load() {
        ReactionDefinition d = parse("""
                {
                  "reactants": {"zas:nitrous_oxide": 1},
                  "products": {"zas:nitrogen": 1, "zas:oxygen": 0.5},
                  "enthalpy": -82050,
                  "rate": {"per_second": 0.012, "activation_energy": 250000, "reference_temperature": 1000}
                }
                """).orElseThrow();
        assertEquals(Optional.of(new ReactionRate(0.012, 250000, 1000)), d.rate());
        assertFalse(d.conditions().requiresIgnition());
        assertEquals(0, d.conditions().minTemperature());
        assertEquals(Double.POSITIVE_INFINITY, d.conditions().maxTemperature());

        ReactionDefinition plain = parse("""
                {"reactants": {"zas:nitrous_oxide": 1}, "enthalpy": 0, "rate": {"per_second": 2}}
                """).orElseThrow();
        assertEquals(Optional.of(new ReactionRate(2, 0, 298.15)), plain.rate());
        assertEquals(
                Double.POSITIVE_INFINITY,
                parse("""
                        {"reactants": {"zas:hydrogen": 1}, "enthalpy": 0, "requires_ignition": true,
                         "flammability": {"fuel": "zas:hydrogen", "lower": 0.1, "upper": 0.2}}
                        """)
                        .orElseThrow()
                        .conditions()
                        .flammability()
                        .orElseThrow()
                        .autoignition());
    }

    @Test
    void fire_001_invalid_definitions_are_skipped_naming_the_field() {
        assertSkipped("{\"reactants\": {\"zas:oxygen\": 1}}", "enthalpy: is required");
        assertSkipped("{\"reactants\": {\"zas:oxygen\": -1}, \"enthalpy\": 0}", "reactants:");
        assertSkipped("{\"enthalpy\": 0}", "reactants:");
        assertSkipped("{\"reactants\": {\"zas:oxygen\": 1}, \"enthalpy\": \"hot\"}", "enthalpy:");
        assertSkipped(
                "{\"reactants\": {\"zas:oxygen\": 1}, \"enthalpy\": 0, \"requires_ignition\": true,"
                        + " \"rate\": {\"per_second\": 1}}",
                "rate:");
        assertSkipped("{\"reactants\": {\"zas:oxygen\": 1}, \"enthalpy\": 0, \"rate\": {\"per_second\": 0}}", "rate:");
        assertSkipped("{\"type\": \"test:nothing\", \"enthalpy\": 0}", "type:");
        assertSkipped(
                "{\"reactants\": {\"zas:oxygen\": 1}, \"enthalpy\": 0,"
                        + " \"flammability\": {\"fuel\": \"zas:oxygen\", \"lower\": 0.5, \"upper\": 0.2}}",
                "flammability:");
        assertSkipped(
                "{\"reactants\": {\"zas:oxygen\": 1}, \"enthalpy\": 0, \"min_temperature\": 500,"
                        + " \"max_temperature\": 400}",
                "max_temperature:");
    }

    @Test
    void fire_003_the_shipped_combustion_burns_each_fuel() throws IOException {
        GasRegistry gases = shippedGases();
        List<String> skipped = new ArrayList<>();
        ReactionSet set = ReactionSet.compile(gases, shippedReactions(), (d, why) -> skipped.add(d.id() + ": " + why));
        assertEquals(List.of(), skipped);
        assertEquals(
                List.of(
                        "zas:carbon_monoxide_combustion",
                        "zas:hydrogen_combustion",
                        "zas:methane_combustion",
                        "zas:nitrous_oxide_decomposition"),
                set.definitions().stream().map(ReactionDefinition::id).toList());

        // fuel, its fraction in air, heat per mol, then the products per mol of fuel
        burns(gases, set, "zas:hydrogen", 0.10, 242_000, 0.5, Map.of("zas:water_vapor", 1.0));
        burns(gases, set, "zas:methane", 0.08, 802_000, 2, Map.of("zas:carbon_dioxide", 1.0, "zas:water_vapor", 2.0));
        burns(gases, set, "zas:carbon_monoxide", 0.20, 283_000, 0.5, Map.of("zas:carbon_dioxide", 1.0));
    }

    @Test
    void fire_003_the_shipped_flammability_limits_and_autoignition_points() throws IOException {
        Map<String, ReactionConditions.FlammabilityLimits> limits = new java.util.TreeMap<>();
        for (ReactionDefinition d : shippedReactions()) {
            d.conditions().flammability().ifPresent(f -> {
                limits.put(f.fuel(), f);
                assertTrue(d.conditions().requiresIgnition(), d.id());
            });
        }
        assertEquals(
                new ReactionConditions.FlammabilityLimits("zas:hydrogen", 0.04, 0.75, 858), limits.get("zas:hydrogen"));
        assertEquals(
                new ReactionConditions.FlammabilityLimits("zas:methane", 0.05, 0.15, 810), limits.get("zas:methane"));
        assertEquals(
                new ReactionConditions.FlammabilityLimits("zas:carbon_monoxide", 0.125, 0.74, 882),
                limits.get("zas:carbon_monoxide"));
    }

    @Test
    void fire_004_nitrous_oxide_decomposes_above_its_threshold() throws IOException {
        GasRegistry gases = shippedGases();
        ReactionSet set = shippedSet(gases);
        int n2o = gases.indexOf("zas:nitrous_oxide");
        int n2 = gases.indexOf("zas:nitrogen");
        int o2 = gases.indexOf("zas:oxygen");

        // Below its 850 K threshold nothing happens, however long.
        for (double t : new double[] {293.15, 800}) {
            GasMixture cold = new GasMixture(gases, 1000, t);
            cold.setMoles(n2o, 10);
            assertFalse(set.react(cold, 60, FireSettings.DEFAULT).reacted(), t + " K");
            assertEquals(10, cold.moles(n2o));
        }

        // Above it: N2O -> N2 + 1/2 O2 at the shipped Arrhenius rate, releasing 82.05 kJ/mol.
        GasMixture hot = new GasMixture(gases, 1000, 1100);
        hot.setMoles(n2o, 10);
        double energy = hot.heatCapacity() * hot.temperature();
        ReactionResult r = set.react(hot, 1, FireSettings.DEFAULT);
        double k = new ReactionRate(0.012, 250_000, 1000).at(1100);
        double reacted = 10 * -Math.expm1(-k);
        assertTrue(reacted > 0.5 && reacted < 10, "reacted " + reacted);
        assertEquals(10 - reacted, hot.moles(n2o), 1e-9);
        assertEquals(reacted, hot.moles(n2), 1e-9);
        assertEquals(reacted / 2, hot.moles(o2), 1e-9);
        assertEquals(82_050 * reacted, r.heat(), 1e-6);
        assertEquals(energy + r.heat(), hot.heatCapacity() * hot.temperature(), 1e-6);
        assertTrue(hot.temperature() > 1100);
    }

    @Test
    void fire_005_half_the_methane_carbon_forms_co_at_an_oxygen_fraction_of_016() throws IOException {
        GasRegistry gases = shippedGases();
        ReactionSet set = shippedSet(gases);
        int ch4 = gases.indexOf("zas:methane");
        int o2 = gases.indexOf("zas:oxygen");
        int n2 = gases.indexOf("zas:nitrogen");
        int co = gases.indexOf("zas:carbon_monoxide");
        int co2 = gases.indexOf("zas:carbon_dioxide");
        double total = 41.6;
        GasMixture m = new GasMixture(gases, 1000, 293.15);
        m.setMoles(ch4, total * 0.07);
        m.setMoles(o2, total * 0.16);
        m.setMoles(n2, total * 0.77);
        double methane = m.moles(ch4);
        double oxygen = m.moles(o2);

        ReactionResult r = set.burn(m, 1000, true, FireSettings.DEFAULT);

        assertEquals(0, m.moles(ch4));
        assertEquals(methane / 2, m.moles(co), 1e-9);
        assertEquals(methane / 2, m.moles(co2), 1e-9);
        // Half a mol less O2 and 283 kJ less heat per mol of carbon that forms CO.
        assertEquals(oxygen - methane * (2 - CarbonCombustion.oxygenSaved(0.5)), m.moles(o2), 1e-9);
        assertEquals(methane * (802_000 - CarbonCombustion.heatLost(0.5)), r.heat(), 1e-6);
    }

    @Test
    void fire_005_methane_burns_completely_at_018_and_above() throws IOException {
        GasRegistry gases = shippedGases();
        ReactionSet set = shippedSet(gases);
        int ch4 = gases.indexOf("zas:methane");
        int o2 = gases.indexOf("zas:oxygen");
        int n2 = gases.indexOf("zas:nitrogen");
        GasMixture m = new GasMixture(gases, 1000, 293.15);
        m.setMoles(ch4, 41.6 * 0.07);
        m.setMoles(o2, 41.6 * 0.18);
        m.setMoles(n2, 41.6 * 0.75);
        set.burn(m, 1000, true, FireSettings.DEFAULT);
        assertEquals(0, m.moles(gases.indexOf("zas:carbon_monoxide")));
    }

    private static void burns(
            GasRegistry gases,
            ReactionSet set,
            String fuelId,
            double fraction,
            double heatPerMol,
            double oxygenPerMol,
            Map<String, Double> productsPerMol) {
        int fuel = gases.indexOf(fuelId);
        int o2 = gases.indexOf("zas:oxygen");
        int n2 = gases.indexOf("zas:nitrogen");
        double total = 101.325 * 1000 / (8.314462618 * 293.15);
        GasMixture m = new GasMixture(gases, 1000, 293.15);
        m.setMoles(fuel, total * fraction);
        m.setMoles(o2, total * (1 - fraction) * 0.21);
        m.setMoles(n2, total * (1 - fraction) * 0.79);
        double burned = m.moles(fuel);
        double oxygen = m.moles(o2);
        double energy = m.heatCapacity() * m.temperature();

        ReactionResult r = set.burn(m, 1000, true, FireSettings.DEFAULT);

        assertEquals(0, m.moles(fuel), fuelId);
        assertEquals(oxygen - oxygenPerMol * burned, m.moles(o2), 1e-9, fuelId);
        productsPerMol.forEach(
                (gas, n) -> assertEquals(n * burned, m.moles(gases.indexOf(gas)), 1e-9, fuelId + " -> " + gas));
        assertEquals(heatPerMol * burned, r.heat(), 1e-6, fuelId);
        assertEquals(energy + r.heat(), m.heatCapacity() * m.temperature(), 1e-6, fuelId);
    }

    private static ReactionSet shippedSet(GasRegistry gases) throws IOException {
        return ReactionSet.compile(gases, shippedReactions(), (d, why) -> {
            throw new AssertionError(d.id() + ": " + why);
        });
    }

    private static Optional<ReactionDefinition> parse(String json) {
        return ReactionLoader.parse(ID, JsonParser.parseString(json));
    }

    private static void assertSkipped(String json, String messageStart) {
        DatapackErrors.beginLoad(ReactionLoader.KIND);
        assertEquals(Optional.empty(), parse(json), json);
        List<DatapackErrors.Problem> problems = DatapackErrors.recent().stream()
                .filter(p -> p.kind().equals(ReactionLoader.KIND))
                .toList();
        assertEquals(1, problems.size(), json);
        assertEquals("data/test/zas/reaction/everything.json", problems.get(0).file());
        assertTrue(
                problems.get(0).message().startsWith(messageStart),
                "expected '" + messageStart + "...', got: " + problems.get(0).message());
    }

    private static Path resources() {
        return Path.of(System.getProperty("zas.resources")).resolve("data/zas/zas");
    }

    private static GasRegistry shippedGases() throws IOException {
        List<GasDefinition> defs = new ArrayList<>();
        for (Path file : jsonFiles(resources().resolve("gas"))) {
            JsonElement json = JsonParser.parseString(Files.readString(file));
            GasDefinitionCodecs.Body body =
                    GasDefinitionCodecs.Body.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            defs.add(body.toDefinition(ResourceLocation.fromNamespaceAndPath("zas", name(file))));
        }
        return GasRegistry.of(defs);
    }

    private static List<ReactionDefinition> shippedReactions() throws IOException {
        List<ReactionDefinition> defs = new ArrayList<>();
        for (Path file : jsonFiles(resources().resolve("reaction"))) {
            JsonElement json = JsonParser.parseString(Files.readString(file));
            defs.add(ReactionLoader.parse(ResourceLocation.fromNamespaceAndPath("zas", name(file)), json)
                    .orElseThrow(() -> new AssertionError("invalid: " + file)));
        }
        return defs;
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static String name(Path file) {
        String n = file.getFileName().toString();
        return n.substring(0, n.length() - ".json".length());
    }
}
