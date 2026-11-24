package io.github.west3436.zas.atmos.lang;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.west3436.ss13.testkit.PlayerTextScan;
import io.github.west3436.ss13.testkit.PlayerTextScan.Found;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No player-facing text in {@code zas} or {@code zas_atmos} is written in the code, and every key the
 * code spells out is in {@code en_us.json} (CLIENT-007). It lives here because {@code zas_atmos} may
 * look at {@code zas}, and {@code zas} never names {@code zas_atmos}. The Jade and CC: Tweaked adapters
 * and the Ponder scenes, which are part of {@code zas_atmos} in the release jar, are read from their
 * builds. The scenes' text is in a language file of its own, in the {@code zas_ponder} resource folder.
 *
 * <p>Text with no letters, such as "●", "—" or a number, is never flagged. Everything else that's
 * allowed is in the three lists below, and each entry is an exact match.
 */
class PlayerTextTest {
    /** A unit's symbol reads the same in any language's text around it. */
    private static final Set<String> UNIT_SYMBOLS =
            Set.of("kPa", "atm", "bar", "psi", "K", "°C", "°F", "L/s", "mol/s", "s");

    /**
     * Who paused a dimension and why (OPS-005). They're logged and saved in the world's data, so they
     * can't follow each reader's language, and an operator's own reason is shown the same way.
     */
    private static final Set<String> PAUSE_TEXT =
            Set.of("zas:engine", "no reason given", "the engine failed:", "the engine couldn't be rebuilt:");

    /**
     * The mod id Ponder puts first in the scenes' keys: the plugin's, which is {@code zas_atmos} for
     * the scenes in the release jar. The scan reads Ponder's key calls with it (CLIENT-023).
     */
    private static final String PONDER_NAMESPACE = "zas_atmos";

    /**
     * Methods whose text stays in English for operators. A datapack problem is the line in the log,
     * {@code /zas datapacks} repeats it, and much of it is the codecs' own English.
     */
    private static final Set<String> OPERATOR_LINES =
            Set.of("io/github/west3436/zas/data/DatapackErrors$Problem.toString");

    @Test
    void client_007_no_hard_coded_player_text() {
        List<String> problems = new ArrayList<>();
        for (Path classes : List.of(
                classes("zas.mainClasses"),
                classes("zas_atmos.mainClasses"),
                classes("zas_atmos.jadeClasses"),
                classes("zas_atmos.ccClasses"),
                classes("zas_atmos.ponderClasses"))) {
            hardCoded(PlayerTextScan.of(classes, OPERATOR_LINES, PONDER_NAMESPACE))
                    .forEach(found -> problems.add(found.toString()));
        }
        assertTrue(
                problems.isEmpty(),
                "Hard-coded player text (CLIENT-007). Add it to en_us.json and use Component.translatable:\n  "
                        + String.join("\n  ", problems));
    }

    @Test
    void client_007_every_key_the_code_spells_out_is_in_en_us() throws IOException {
        Set<String> zas = lang("zas.resources", "zas");
        // zas_atmos requires zas, so it can name zas's keys too. zas can't name zas_atmos's.
        Set<String> atmos = new HashSet<>(zas);
        atmos.addAll(lang("zas_atmos.resources", "zas_atmos"));
        // The Ponder scenes are part of zas_atmos, so they can name its keys and zas's, and their own.
        Set<String> ponder = new HashSet<>(atmos);
        ponder.addAll(lang("zas_atmos.ponderResources", "zas_ponder"));
        List<String> problems = new ArrayList<>();
        problems.addAll(missing(classes("zas.mainClasses"), zas));
        problems.addAll(missing(classes("zas_atmos.mainClasses"), atmos));
        problems.addAll(missing(classes("zas_atmos.jadeClasses"), atmos));
        // The CC: Tweaked adapter names no keys: what it says is a Lua error, which a program may match.
        problems.addAll(missing(classes("zas_atmos.ponderClasses"), ponder));
        assertTrue(problems.isEmpty(), "Translation keys missing from en_us.json:\n  " + String.join("\n  ", problems));
    }

    /** The mods list shows this key's text, and a mod's own description only without it. */
    @Test
    void client_007_each_mods_description_has_a_key() throws IOException {
        assertTrue(lang("zas.resources", "zas").contains("fml.menu.mods.info.description.zas"));
        assertTrue(lang("zas_atmos.resources", "zas_atmos").contains("fml.menu.mods.info.description.zas_atmos"));
        assertTrue(lang("zas_ponder.resources", "zas_ponder").contains("fml.menu.mods.info.description.zas_ponder"));
    }

    /**
     * Each item, block and fluid tag of ours has a name in its own mod's {@code en_us.json}, under
     * NeoForge's key {@code tag.<registry>.<namespace>.<path>}, a slash in the path as a dot: recipe
     * viewers such as EMI show it, and log the tags without one. Tags of ours are those in the
     * {@code zas} and {@code zas_atmos} namespaces, wherever the two mods' resources declare them,
     * their built-in content pack included.
     */
    @Test
    void client_007_each_of_our_item_block_and_fluid_tags_has_a_name() throws IOException {
        Map<String, Set<String>> lang =
                Map.of("zas", lang("zas.resources", "zas"), "zas_atmos", lang("zas_atmos.resources", "zas_atmos"));
        Pattern tagFile = Pattern.compile("(?:^|/)data/(zas|zas_atmos)/tags/(item|block|fluid)/(.+)\\.json$");
        Set<String> problems = new TreeSet<>();
        int tags = 0;
        for (String property : List.of("zas.resources", "zas_atmos.resources")) {
            Path root = Path.of(System.getProperty(property));
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                Matcher m = tagFile.matcher(root.relativize(file).toString().replace('\\', '/'));
                if (!m.find()) {
                    continue;
                }
                tags++;
                String key = "tag." + m.group(2) + "." + m.group(1) + "."
                        + m.group(3).replace('/', '.');
                if (!lang.get(m.group(1)).contains(key)) {
                    problems.add(key + " (" + root.relativize(file) + ")");
                }
            }
        }
        assertTrue(tags > 0, "no tags found");
        assertTrue(problems.isEmpty(), "Tags with no name in en_us.json:\n  " + String.join("\n  ", problems));
    }

    private static Set<Found> hardCoded(PlayerTextScan scan) {
        Set<Found> out = new TreeSet<>();
        for (Found found : scan.withLetters()) {
            String text = found.text().strip();
            if (!UNIT_SYMBOLS.contains(text) && !PAUSE_TEXT.contains(text)) {
                out.add(found);
            }
        }
        return out;
    }

    private static List<String> missing(Path classes, Set<String> lang) {
        Set<Found> keys =
                PlayerTextScan.of(classes, OPERATOR_LINES, PONDER_NAMESPACE).keys();
        assertFalse(keys.isEmpty(), "no translation keys found in " + classes);
        List<String> out = new ArrayList<>();
        for (Found key : keys) {
            if (!lang.contains(key.text())) {
                out.add(key.toString());
            }
        }
        return out;
    }

    private static Path classes(String property) {
        return Path.of(System.getProperty(property));
    }

    private static Set<String> lang(String property, String namespace) throws IOException {
        Path file = Path.of(System.getProperty(property)).resolve("assets/" + namespace + "/lang/en_us.json");
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }
    }
}
