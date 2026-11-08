package io.github.west3436.zas.atmos.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code docs/crafting.md} against the recipe files (CONT-058). Each row of its recipe tables is a
 * recipe file, with the names the game shows, its stage, its grid and its ingredients, and each
 * recipe file has a row. Rows under "Not in the mod yet" are the recipes whose result has no name
 * in {@code en_us.json}, as every block and item the mod registers has one. The components table's
 * stages and uses follow from the recipes too. The page's prose isn't checked.
 */
class CraftingDocTest {
    private static final String NOT_YET = "Not in the mod yet";

    /** Vanilla items whose names in the game aren't their ids' words. */
    private static final Map<String, String> VANILLA_NAMES =
            Map.of("minecraft:redstone", "redstone dust", "minecraft:redstone_block", "block of redstone");

    /** The stages, in the order a player reaches them. */
    private enum Stage {
        WORKSHOP,
        HARDENED,
        BLUESPACE;

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The vanilla materials that set a later stage: obsidian takes a diamond pickaxe, and ender pearls endermen. */
    private static final Map<String, Stage> VANILLA_STAGES =
            Map.of("minecraft:obsidian", Stage.HARDENED, "minecraft:ender_pearl", Stage.BLUESPACE);

    /** A recipe file: a shaped one has a pattern and key, a shapeless one a list of ingredients. */
    private record Recipe(
            String file,
            String result,
            int count,
            List<String> pattern,
            Map<Character, String> key,
            List<String> list) {
        List<String> ingredients() {
            return pattern != null ? List.copyOf(key.values()) : list;
        }
    }

    private static List<Recipe> recipes;
    private static JsonObject lang;
    private static List<String> doc;

    /** Each item's stage: the earliest of its recipes'. */
    private static Map<String, Stage> stages;

    @BeforeAll
    static void load() throws IOException {
        Path resources = Path.of(System.getProperty("zas_atmos.resources"));
        lang = json(resources.resolve("assets/zas_atmos/lang/en_us.json"));
        doc = Files.readAllLines(Path.of(System.getProperty("zas.craftingDoc")));
        recipes = new ArrayList<>();
        try (Stream<Path> files = Files.list(resources.resolve("data/zas_atmos/recipe"))) {
            for (Path file : files.sorted().toList()) {
                recipes.add(recipe(file));
            }
        }
        stages = new HashMap<>();
        for (boolean changed = true; changed; ) {
            changed = false;
            for (Recipe r : recipes) {
                Stage s = stage(r);
                Stage was = stages.get(r.result());
                if (s != null && (was == null || s.compareTo(was) < 0)) {
                    stages.put(r.result(), s);
                    changed = true;
                }
            }
        }
    }

    @Test
    void cont_058_the_recipe_tables_match_the_recipe_files() {
        List<String> expected = new ArrayList<>();
        for (Recipe r : recipes) {
            Stage stage = stage(r);
            assertTrue(stage != null, r.file() + " has an ingredient no recipe chain makes from vanilla materials");
            String makes = (r.count() > 1 ? r.count() + " × " : "") + name(r.result());
            String grid;
            List<String> ingredients = new ArrayList<>();
            if (r.pattern() != null) {
                grid = String.join(
                        " / ",
                        r.pattern().stream()
                                .map(row -> "`" + row.replace(' ', '.') + "`")
                                .toList());
                r.key().forEach((k, id) -> ingredients.add("`" + k + "` " + name(id)));
            } else {
                grid = "shapeless";
                Map<String, Integer> counts = new LinkedHashMap<>();
                r.list().forEach(id -> counts.merge(id, 1, Integer::sum));
                counts.forEach((id, n) -> ingredients.add((n > 1 ? n + " × " : "") + name(id)));
            }
            expected.add(row(inTheMod(r.result()) ? "" : NOT_YET, makes, stage.label(), grid, ingredients));
        }

        List<String> documented = new ArrayList<>();
        String section = null;
        for (String line : generated("recipes")) {
            if (line.startsWith("### ")) {
                section = line.substring(4);
            } else if (line.startsWith("| ") && !line.startsWith("| Makes |")) {
                List<String> c = cells(line);
                assertTrue(c.size() == 4, "a row of four cells: " + line);
                documented.add(row(
                        NOT_YET.equals(section) ? NOT_YET : "",
                        c.get(0),
                        c.get(1),
                        c.get(2),
                        List.of(c.get(3).split(", "))));
            }
        }
        same(expected, documented, "the recipe files", "docs/crafting.md's recipe tables");
    }

    @Test
    void cont_058_the_components_table_matches_the_recipe_files() {
        Map<String, String> byName = new HashMap<>();
        recipes.forEach(r -> byName.put(name(r.result()), r.result()));
        List<String> wrong = new ArrayList<>();
        int rows = 0;
        for (String line : generated("components")) {
            if (!line.startsWith("| ") || line.startsWith("| Component |")) {
                continue;
            }
            rows++;
            List<String> c = cells(line);
            String id = byName.get(c.get(0).toLowerCase(Locale.ROOT));
            if (id == null) {
                wrong.add("no recipe makes " + c.get(0));
                continue;
            }
            String stage = stages.get(id).label();
            if (!stage.equals(c.get(1).toLowerCase(Locale.ROOT))) {
                wrong.add(c.get(0) + " is " + stage + ", not " + c.get(1));
            }
            List<String> uses = recipes.stream()
                    .filter(r -> r.ingredients().contains(id))
                    .map(r -> name(r.result()))
                    .distinct()
                    .sorted()
                    .toList();
            List<String> listed = Stream.of(c.get(2).split(", "))
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .sorted()
                    .toList();
            if (!uses.equals(listed)) {
                wrong.add(c.get(0) + " goes into " + uses + ", not " + listed);
            }
        }
        assertTrue(rows > 0, "no rows in the components table");
        assertTrue(wrong.isEmpty(), "docs/crafting.md's components table:\n  " + String.join("\n  ", wrong));
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /** A row as a comparable line: lower case, its ingredients sorted. */
    private static String row(String section, String makes, String stage, String grid, List<String> ingredients) {
        List<String> sorted = ingredients.stream().sorted().toList();
        return String.join(" | ", section, makes, stage, grid, String.join(", ", sorted))
                .toLowerCase(Locale.ROOT);
    }

    /** Fails with what each side has that the other lacks. */
    private static void same(List<String> a, List<String> b, String aName, String bName) {
        List<String> onlyA = new ArrayList<>(a);
        b.forEach(onlyA::remove);
        List<String> onlyB = new ArrayList<>(b);
        a.forEach(onlyB::remove);
        assertTrue(
                onlyA.isEmpty() && onlyB.isEmpty(),
                "Only in " + aName + ":\n  " + String.join("\n  ", onlyA) + "\nOnly in " + bName + ":\n  "
                        + String.join("\n  ", onlyB));
    }

    /** The lines between a {@code GENERATED} marker of the page and its end. */
    private static List<String> generated(String part) {
        int start = doc.indexOf("<!-- GENERATED: " + part + " -->");
        int end = doc.indexOf("<!-- END GENERATED: " + part + " -->");
        assertTrue(start >= 0 && end > start, "docs/crafting.md has no " + part + " tables");
        return doc.subList(start + 1, end);
    }

    private static List<String> cells(String line) {
        String inner = line.substring(2, line.length() - 2);
        return List.of(inner.split(" \\| "));
    }

    /** A recipe's stage: its latest ingredient's; null while one of them has no stage yet. */
    private static Stage stage(Recipe r) {
        Stage max = Stage.WORKSHOP;
        for (String id : r.ingredients()) {
            Stage s = id.startsWith("minecraft:") ? VANILLA_STAGES.getOrDefault(id, Stage.WORKSHOP) : stages.get(id);
            if (s == null) {
                return null;
            }
            max = s.compareTo(max) > 0 ? s : max;
        }
        return max;
    }

    /** The name the game shows, in lower case: from en_us.json, or the id's words. */
    private static String name(String id) {
        if (VANILLA_NAMES.containsKey(id)) {
            return VANILLA_NAMES.get(id);
        }
        String[] parts = id.split(":");
        for (String kind : List.of("item", "block")) {
            String key = kind + "." + parts[0] + "." + parts[1];
            if (lang.has(key)) {
                return lang.get(key).getAsString().toLowerCase(Locale.ROOT);
            }
        }
        return parts[1].replace('_', ' ');
    }

    /** Whether the mod has the item: it has a name in en_us.json. */
    private static boolean inTheMod(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        return lang.has("item.zas_atmos." + path) || lang.has("block.zas_atmos." + path);
    }

    private static Recipe recipe(Path file) throws IOException {
        JsonObject json = json(file);
        String name = file.getFileName().toString();
        JsonObject result = json.getAsJsonObject("result");
        String id = result.get("id").getAsString();
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        String type = json.get("type").getAsString();
        if (type.equals("minecraft:crafting_shaped")) {
            List<String> pattern = new ArrayList<>();
            json.getAsJsonArray("pattern").forEach(e -> pattern.add(e.getAsString()));
            Map<Character, String> key = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("key").entrySet()) {
                key.put(e.getKey().charAt(0), item(name, e.getValue()));
            }
            return new Recipe(name, id, count, pattern, key, null);
        }
        assertTrue(type.equals("minecraft:crafting_shapeless"), name + " is a " + type);
        List<String> list = new ArrayList<>();
        JsonArray ingredients = json.getAsJsonArray("ingredients");
        ingredients.forEach(e -> list.add(item(name, e)));
        return new Recipe(name, id, count, null, null, list);
    }

    /** An ingredient's one item; the recipes use no tags or alternatives. */
    private static String item(String file, JsonElement ingredient) {
        assertTrue(
                ingredient.isJsonObject() && ingredient.getAsJsonObject().has("item"),
                file + " has an ingredient that isn't one item: " + ingredient);
        return ingredient.getAsJsonObject().get("item").getAsString();
    }

    private static JsonObject json(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
}
