package io.github.west3436.zas.atmos.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.west3436.zas.atmos.lang.PlayerTextScan.Found;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * No player-facing text in {@code zas}, {@code zas_atmos} or {@code zas_ponder} is written in the code,
 * and every key the code spells out is in {@code en_us.json} (CLIENT-007). It lives here because
 * {@code zas_atmos} may look at {@code zas}, and {@code zas} never names {@code zas_atmos}. The Ponder
 * addon, which requires {@code zas_atmos}, is read from its build, and so is the Jade adapter, which is
 * part of {@code zas_atmos} in the release jar.
 *
 * <p>Text with no letters, such as "●", "—" or a number, is never flagged. Everything else that's
 * allowed is in the three lists below, and each entry is an exact match.
 */
class PlayerTextTest {
    /** A unit's symbol reads the same in any language's text around it. */
    private static final Set<String> UNIT_SYMBOLS = Set.of("kPa", "atm", "bar", "psi", "K", "°C", "°F", "L/s", "s");

    /**
     * Who paused a dimension and why (OPS-005). They're logged and saved in the world's data, so they
     * can't follow each reader's language, and an operator's own reason is shown the same way.
     */
    private static final Set<String> PAUSE_TEXT =
            Set.of("zas:engine", "no reason given", "the engine failed:", "the engine couldn't be rebuilt:");

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
                classes("zas_ponder.mainClasses"))) {
            hardCoded(PlayerTextScan.of(classes, OPERATOR_LINES)).forEach(found -> problems.add(found.toString()));
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
        // The Ponder addon requires zas_atmos, so it can name the keys of both.
        Set<String> ponder = new HashSet<>(atmos);
        ponder.addAll(lang("zas_ponder.resources", "zas_ponder"));
        List<String> problems = new ArrayList<>();
        problems.addAll(missing(classes("zas.mainClasses"), zas));
        problems.addAll(missing(classes("zas_atmos.mainClasses"), atmos));
        problems.addAll(missing(classes("zas_atmos.jadeClasses"), atmos));
        problems.addAll(missing(classes("zas_ponder.mainClasses"), ponder));
        assertTrue(problems.isEmpty(), "Translation keys missing from en_us.json:\n  " + String.join("\n  ", problems));
    }

    /** The mods list shows this key's text, and a mod's own description only without it. */
    @Test
    void client_007_each_mods_description_has_a_key() throws IOException {
        assertTrue(lang("zas.resources", "zas").contains("fml.menu.mods.info.description.zas"));
        assertTrue(lang("zas_atmos.resources", "zas_atmos").contains("fml.menu.mods.info.description.zas_atmos"));
        assertTrue(lang("zas_ponder.resources", "zas_ponder").contains("fml.menu.mods.info.description.zas_ponder"));
    }

    @Test
    void client_007_the_scan_finds_text_however_it_gets_there() throws IOException {
        Set<String> expected = Set.of(
                "Direct",
                "Concatenated ",
                "Local",
                "Either",
                "Or",
                "Constant",
                "Array",
                "Passed",
                "Returned",
                "Enum field",
                "Record field",
                "Appended",
                "Drawn",
                "Floating",
                "Argument",
                "Client argument",
                "Fallback",
                "Format ",
                "Argument of a format",
                "Captured",
                "Command error",
                "Substring");
        Set<String> found = hardCoded(scan(PlayerTextSamples.FLAGGED)).stream()
                .map(Found::text)
                .collect(Collectors.toSet());
        assertEquals(new TreeSet<>(expected), new TreeSet<>(found));
    }

    @Test
    void client_007_the_scan_allows_symbols_numbers_units_and_composed_values() throws IOException {
        assertEquals(Set.of(), hardCoded(scan(PlayerTextSamples.ALLOWED)));
    }

    @Test
    void client_007_the_scan_finds_keys_through_helpers() throws IOException {
        Set<String> keys =
                scan(PlayerTextSamples.KEYS).keys().stream().map(Found::text).collect(Collectors.toSet());
        assertEquals(
                new TreeSet<>(Set.of(
                        "sample.plain",
                        "sample.client",
                        "sample.either",
                        "sample.or",
                        "sample.prefix.helper",
                        "sample.prefix.enum")),
                new TreeSet<>(keys));
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
        Set<Found> keys = PlayerTextScan.of(classes, OPERATOR_LINES).keys();
        assertFalse(keys.isEmpty(), "no translation keys found in " + classes);
        List<String> out = new ArrayList<>();
        for (Found key : keys) {
            if (!lang.contains(key.text())) {
                out.add(key.toString());
            }
        }
        return out;
    }

    private static PlayerTextScan scan(List<Class<?>> samples) throws IOException {
        List<byte[]> files = new ArrayList<>();
        for (Class<?> sample : samples) {
            String file = sample.getName().substring(sample.getPackageName().length() + 1) + ".class";
            try (InputStream in = sample.getResourceAsStream(file)) {
                files.add(in.readAllBytes());
            }
        }
        return new PlayerTextScan(
                files, Set.of(PlayerTextSamples.Diagnostic.class.getName().replace('.', '/') + ".toString"));
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
