package io.github.west3436.zas.compat.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.reaction.ReactionCodecs;
import io.github.west3436.zas.reaction.ReactionFormatTest;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What the recipe viewers show of the shipped gases and reactions (COMPAT-060, COMPAT-061): every
 * one has an entry and nothing of it is dropped. In the default units, kPa and °C.
 */
class ViewerEntriesTest {
    private static final FireSettings FIRE = FireSettings.DEFAULT;

    @Test
    void compat_061_every_shipped_gas_has_an_entry_with_its_mass_heat_capacity_and_tags() throws IOException {
        GasRegistry registry = shipped();
        List<GasEntry> entries = ViewerEntries.gases(registry);
        assertEquals(8, entries.size(), "the shipped gases");
        for (int i = 0; i < registry.size(); i++) {
            GasDefinition def = registry.definition(i);
            GasEntry entry = entries.get(i);
            assertEquals(def.id(), entry.id());
            assertEquals(def.nameKey(), entry.nameKey());
            assertEquals(def.formula(), entry.formula());
            assertEquals(def.molarMass(), entry.molarMass());
            assertEquals(def.molarHeatCapacity(), entry.molarHeatCapacity());
            assertEquals(registry.tagsOf(i), entry.tags(), def.id());
            assertTrue(entry.known());
            assertFalse(entry.colour() == ViewerEntries.GREY, def.id() + " has a colour of its own");

            List<Chip> chips = ViewerEntries.properties(entry);
            assertEquals(2 + entry.tags().size(), chips.size(), def.id());
            assertEquals(Icon.MASS, chips.get(0).icon());
            assertEquals(Icon.CAPACITY, chips.get(1).icon());
        }

        GasEntry oxygen = entry(entries, "zas:oxygen");
        assertEquals(
                List.of(
                        new Chip(
                                Icon.MASS,
                                Line.of("viewer.zas.g_per_mol", "31.998"),
                                Line.of("viewer.zas.hint.molar_mass")),
                        new Chip(
                                Icon.CAPACITY,
                                Line.of("viewer.zas.j_per_mol_k", "21.06"),
                                Line.of("viewer.zas.hint.heat_capacity")),
                        new Chip(
                                Icon.OXIDIZER,
                                Line.keyOr("gas_tag.zas.oxidizer", "#zas:oxidizer"),
                                Line.plain("#zas:oxidizer"))),
                ViewerEntries.properties(oxygen));
        // A gas in two tags shows both, each with its own icon.
        assertEquals(
                List.of(Icon.MASS, Icon.CAPACITY, Icon.FLAME, Icon.TOXIC),
                ViewerEntries.properties(entry(entries, "zas:carbon_monoxide")).stream()
                        .map(Chip::icon)
                        .toList());
        assertEquals(oxygen, ViewerEntries.emblem(entries), "oxygen stands for the gases");
    }

    @Test
    void compat_061_a_tag_without_an_icon_or_a_name_still_shows() {
        GasRegistry registry = GasRegistry.of(
                List.of(new GasDefinition("pack:argon", "gas.pack.argon", "Ar", 0.039948, 12.5, Optional.empty())),
                Map.of("pack:noble", List.of("pack:argon")));
        GasEntry argon = ViewerEntries.gases(registry).get(0);
        assertEquals(ViewerEntries.GREY, argon.colour());
        assertEquals(
                new Chip(Icon.TAG, Line.keyOr("gas_tag.pack.noble", "#pack:noble"), Line.plain("#pack:noble")),
                ViewerEntries.properties(argon).get(2));
        assertEquals(argon, ViewerEntries.emblem(List.of(argon)), "without oxygen, the first gas");
    }

    @Test
    void compat_060_every_shipped_reaction_has_a_page_with_its_gases_conditions_and_products() throws IOException {
        GasRegistry registry = shipped();
        List<ReactionDefinition> shipped = ReactionFormatTest.shippedReactions();
        List<ReactionEntry> entries = ViewerEntries.reactions(registry, shipped, FIRE);
        assertEquals(
                List.of(
                        "zas:carbon_monoxide_combustion",
                        "zas:hydrogen_combustion",
                        "zas:methane_combustion",
                        "zas:methane_combustion/incomplete",
                        "zas:nitrous_oxide_decomposition"),
                entries.stream().map(e -> e.namespace() + ":" + e.path()).toList());
        for (ReactionDefinition def : shipped) {
            ReactionEntry entry = entry(entries, def.id(), false);
            assertEquals(def.reactants(), amounts(entry.reactants()), def.id());
            assertEquals(def.products(), amounts(entry.products()), def.id());
            def.incomplete().ifPresent(form -> {
                ReactionEntry other = entry(entries, def.id(), true);
                assertEquals(form.reactants(), amounts(other.reactants()), def.id());
                assertEquals(form.products(), amounts(other.products()), def.id());
            });
        }

        List<Chip> methane = List.of(
                new Chip(Icon.SPARK, Line.NONE, hint("ignition")),
                new Chip(Icon.FLAME, Line.plain("537 °C"), hint("autoignition")),
                new Chip(Icon.SHARE, Line.plain("CH₄ 5–15%"), hint("flammable_range")),
                new Chip(Icon.SHARE, Line.plain("O₂ ≥14%"), hint("fire_oxygen")),
                new Chip(Icon.GAUGE, Line.plain("O₂ ≥10 kPa"), hint("fire_oxygen")),
                new Chip(Icon.HEAT_OUT, Line.of("viewer.zas.kj_per_mol", "802"), hint("heat_released")));
        assertEquals(methane, entry(entries, "zas:methane_combustion", false).chips());

        List<Chip> incomplete = new ArrayList<>(methane.subList(0, 5));
        incomplete.add(new Chip(Icon.SHARE, Line.plain("O₂ <18%"), hint("incomplete")));
        incomplete.add(new Chip(Icon.HEAT_OUT, Line.of("viewer.zas.kj_per_mol", "519"), hint("heat_released")));
        assertEquals(incomplete, entry(entries, "zas:methane_combustion", true).chips());

        assertEquals(
                List.of(
                        new Chip(Icon.THERMOMETER, Line.plain("≥577 °C"), hint("temperature")),
                        new Chip(
                                Icon.CLOCK, Line.of("viewer.zas.percent_per_second_at", "1.2", "727 °C"), hint("rate")),
                        new Chip(Icon.BARRIER, Line.of("viewer.zas.kj_per_mol", "250"), hint("activation_energy")),
                        new Chip(Icon.HEAT_OUT, Line.of("viewer.zas.kj_per_mol", "82.05"), hint("heat_released"))),
                entry(entries, "zas:nitrous_oxide_decomposition", false).chips());
    }

    @Test
    void compat_060_no_field_of_the_reaction_format_is_left_out() throws IOException {
        Set<String> fields = new TreeSet<>(ReactionCodecs.fields());
        assertTrue(fields.remove("parameters"), "a code type's parameters, which never leave the server");
        assertEquals(new TreeSet<>(ViewerEntries.SHOWN_FIELDS), fields);

        GasRegistry registry = shipped();
        ReactionDefinition everything = ReactionFormatTest.parse("""
                {
                  "reactants": {"zas:methane": 1, "zas:oxygen": 2},
                  "products": {"zas:carbon_dioxide": 1, "zas:water_vapor": 2},
                  "enthalpy": 802000,
                  "min_temperature": 250,
                  "max_temperature": 3000,
                  "concentrations": [{"gas": "zas:oxygen", "min": 0.1, "max": 0.9}, {"gas": "zas:nitrogen"},
                    {"gas": "zas:hydrogen", "max": 0.5}, {"gas": "zas:nitrous_oxide", "min": 0.2}],
                  "catalysts": [{"gas": "zas:carbon_monoxide", "min": 0.001}, {"gas": "zas:hydrogen"}],
                  "requires_ignition": true,
                  "flammability": {"fuel": "zas:methane", "lower": 0.05, "upper": 0.15, "autoignition": 810},
                  "incomplete_combustion": {
                    "reactants": {"zas:methane": 1, "zas:oxygen": 1.5},
                    "products": {"zas:carbon_monoxide": 1, "zas:water_vapor": 2},
                    "enthalpy": -519000
                  }
                }
                """).orElseThrow();
        ReactionEntry entry = ViewerEntries.reaction(registry, everything, FIRE, false);
        assertEquals(List.of("zas:carbon_monoxide", "zas:hydrogen"), ids(entry.catalysts()));
        assertEquals(
                List.of(
                        new Chip(Icon.SPARK, Line.NONE, hint("ignition")),
                        new Chip(Icon.FLAME, Line.plain("537 °C"), hint("autoignition")),
                        new Chip(Icon.THERMOMETER, Line.plain("−23–2727 °C"), hint("temperature")),
                        new Chip(Icon.SHARE, Line.plain("CH₄ 5–15%"), hint("flammable_range")),
                        new Chip(Icon.SHARE, Line.plain("O₂ ≥14%"), hint("fire_oxygen")),
                        new Chip(Icon.GAUGE, Line.plain("O₂ ≥10 kPa"), hint("fire_oxygen")),
                        new Chip(Icon.SHARE, Line.plain("O₂ 10–90%"), hint("concentration")),
                        new Chip(Icon.SHARE, Line.plain("N₂ 0–100%"), hint("concentration")),
                        new Chip(Icon.SHARE, Line.plain("H₂ ≤50%"), hint("concentration")),
                        new Chip(Icon.SHARE, Line.plain("N₂O ≥20%"), hint("concentration")),
                        new Chip(Icon.SHARE, Line.plain("CO ≥0.1%"), hint("catalyst")),
                        new Chip(Icon.HEAT_IN, Line.of("viewer.zas.kj_per_mol", "802"), hint("heat_absorbed"))),
                entry.chips());

        // A rate that doesn't change with temperature names none, and has no activation energy.
        ReactionDefinition steady = ReactionFormatTest.parse("""
                {
                  "reactants": {"zas:nitrous_oxide": 1},
                  "products": {"zas:nitrogen": 1, "zas:oxygen": 0.5},
                  "enthalpy": 0,
                  "max_temperature": 400,
                  "rate": {"per_second": 0.5}
                }
                """).orElseThrow();
        assertEquals(
                List.of(
                        new Chip(Icon.THERMOMETER, Line.plain("≤127 °C"), hint("temperature")),
                        new Chip(Icon.CLOCK, Line.of("viewer.zas.percent_per_second", "39"), hint("rate"))),
                ViewerEntries.reaction(registry, steady, FIRE, false).chips());

        // A type implemented in code shows its type's id; it may take or give nothing.
        ReactionDefinition coded = new ReactionDefinition(
                "pack:swap",
                "pack:swap_type",
                Map.of(),
                Map.of("zas:nitrogen", 1.0),
                0,
                ReactionConditions.NONE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(ReactionCodecs.NOT_RUN));
        ReactionEntry swap = ViewerEntries.reaction(registry, coded, FIRE, false);
        assertTrue(swap.reactants().isEmpty());
        assertEquals(List.of("zas:nitrogen"), ids(swap.products()));
        assertEquals(List.of(new Chip(Icon.GEAR, Line.plain("pack:swap_type"), hint("code"))), swap.chips());
    }

    @Test
    void compat_060_the_fire_threshold_shown_is_the_servers() throws IOException {
        GasRegistry registry = shipped();
        ReactionDefinition hydrogen = ReactionFormatTest.shippedReactions().stream()
                .filter(def -> def.id().equals("zas:hydrogen_combustion"))
                .findFirst()
                .orElseThrow();
        FireSettings[] now = {FIRE};
        ViewerData.Reaction page =
                ViewerData.reactions(registry, List.of(hydrogen), () -> now[0]).get(0);
        ReactionEntry before = page.now();
        assertTrue(before.chips().contains(new Chip(Icon.SHARE, Line.plain("O₂ ≥14%"), hint("fire_oxygen"))));
        assertTrue(before == page.now(), "a page is made once while nothing changes");

        now[0] = FIRE.withMinOxygenFraction(0.1).withMinOxygenKpa(2.5);
        ReactionEntry after = page.now();
        assertTrue(after.chips().contains(new Chip(Icon.SHARE, Line.plain("O₂ ≥10%"), hint("fire_oxygen"))));
        assertTrue(after.chips().contains(new Chip(Icon.GAUGE, Line.plain("O₂ ≥2.5 kPa"), hint("fire_oxygen"))));
        assertEquals(before.reactants(), after.reactants());
    }

    @Test
    void compat_060_a_gas_the_client_has_no_definition_of_still_shows() {
        ReactionDefinition def = ReactionFormatTest.parse("""
                {"reactants": {"pack:mystery": 2}, "products": {"zas:oxygen": 1}, "enthalpy": 0}
                """).orElseThrow();
        ReactionEntry entry = ViewerEntries.reaction(GasRegistry.EMPTY, def, FIRE, false);
        GasStack mystery = entry.reactants().get(0);
        assertEquals("pack:mystery", mystery.gas().id());
        assertEquals("gas.pack.mystery", mystery.gas().nameKey());
        assertEquals(2, mystery.amount());
        assertFalse(mystery.gas().known());
        assertTrue(ViewerEntries.properties(mystery.gas()).isEmpty());
    }

    @Test
    void compat_060_numbers_are_short_and_keep_their_sign() {
        assertEquals("2", ViewerNumbers.amount(2));
        // A coefficient is drawn over the gas, so the common fractions are one character.
        assertEquals("½", ViewerNumbers.amount(0.5));
        assertEquals("1½", ViewerNumbers.amount(1.5));
        assertEquals("¼", ViewerNumbers.amount(0.25));
        assertEquals("2¾", ViewerNumbers.amount(2.75));
        assertEquals("0.3", ViewerNumbers.amount(0.3));
        assertEquals("1.33", ViewerNumbers.amount(4.0 / 3));
        assertEquals("12", ViewerNumbers.amount(12));
        assertEquals("12.5%", ViewerNumbers.percent(0.125));
        assertEquals("0.1%", ViewerNumbers.percent(0.001));
        assertEquals("4–75%", ViewerNumbers.percentRange(0.04, 0.75));
        assertEquals("0", ViewerNumbers.number(-0.001, 2));
        assertEquals("−23", ViewerNumbers.number(-23.15, 0));
        assertEquals("283", ViewerNumbers.kilojoules(-283000));
        assertEquals("28.014", ViewerNumbers.grams(0.028014));
        assertEquals("1.2", ViewerNumbers.shareInOneSecond(0.012));
        assertEquals("100", ViewerNumbers.shareInOneSecond(50));
        assertEquals("0.0987", ViewerNumbers.significant(0.09869, 3));
        assertEquals("25 °C", ViewerNumbers.temperature(298.15));
        assertEquals("101 kPa", ViewerNumbers.pressure(101.325));
    }

    /** CLIENT-007: every key the viewers can show is in the framework's language file. */
    @Test
    void compat_060_every_key_a_page_can_show_has_text() throws IOException {
        GasRegistry registry = shipped();
        Set<String> keys = new TreeSet<>(Set.of("viewer.zas.gases", "viewer.zas.reactions"));
        keys.add("viewer.zas.percent_per_second");
        for (String hint : List.of("concentration", "catalyst", "code", "heat_absorbed", "temperature")) {
            keys.add("viewer.zas.hint." + hint);
        }
        for (GasEntry gas : ViewerEntries.gases(registry)) {
            keys.add(gas.nameKey());
            ViewerEntries.properties(gas).forEach(chip -> keys(chip, keys));
        }
        ViewerEntries.reactions(registry, ReactionFormatTest.shippedReactions(), FIRE)
                .forEach(entry -> entry.chips().forEach(chip -> keys(chip, keys)));

        Path file = Path.of(System.getProperty("zas.resources")).resolve("assets/zas/lang/en_us.json");
        Set<String> lang;
        try (Reader reader = Files.newBufferedReader(file)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }
        Set<String> missing = new TreeSet<>(keys);
        missing.removeAll(lang);
        assertEquals(Set.of(), missing);
        assertTrue(keys.size() > 20, "the keys were collected");
    }

    private static void keys(Chip chip, Set<String> out) {
        for (Line line : List.of(chip.text(), chip.hint())) {
            if (line.translated()) {
                out.add(line.key());
            }
        }
    }

    private static Line hint(String name) {
        return Line.of("viewer.zas.hint." + name);
    }

    private static GasEntry entry(List<GasEntry> entries, String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static ReactionEntry entry(List<ReactionEntry> entries, String id, boolean incomplete) {
        return entries.stream()
                .filter(e -> e.id().equals(id) && e.incomplete() == incomplete)
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Double> amounts(List<GasStack> stacks) {
        Map<String, Double> out = new TreeMap<>();
        stacks.forEach(stack -> out.put(stack.gas().id(), stack.amount()));
        return out;
    }

    private static List<String> ids(List<GasStack> stacks) {
        return stacks.stream().map(stack -> stack.gas().id()).toList();
    }

    /** The shipped gases with the shipped gas tags, which the reaction tests' registry leaves out. */
    static GasRegistry shipped() throws IOException {
        GasRegistry gases = ReactionFormatTest.shippedGases();
        Path dir = Path.of(System.getProperty("zas.resources")).resolve("data/zas/tags/zas/gas");
        Map<String, List<String>> tags = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                List<String> values = new ArrayList<>();
                JsonParser.parseString(Files.readString(file))
                        .getAsJsonObject()
                        .getAsJsonArray("values")
                        .forEach(value -> values.add(value.getAsString()));
                String name = file.getFileName().toString();
                tags.put("zas:" + name.substring(0, name.length() - ".json".length()), values);
            }
        }
        assertEquals(new HashSet<>(List.of("zas:flammable", "zas:oxidizer", "zas:toxic")), tags.keySet());
        return GasRegistry.of(gases.definitions(), tags);
    }
}
