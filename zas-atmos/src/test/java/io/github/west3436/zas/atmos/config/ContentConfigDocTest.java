package io.github.west3436.zas.atmos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/**
 * The content mod's FE draws (CONT-065): each device's default is the requirement's, and {@code
 * docs/config.md} lists every key of {@code zas_atmos-server.toml} with its default.
 */
class ContentConfigDocTest {
    /** CONT-065's table, by config key, in FE/t. A firelock draws nothing, so has no key. */
    private static final Map<String, Integer> DRAWS = Map.ofEntries(
            Map.entry("power.vent", 10),
            Map.entry("power.scrubber", 15),
            Map.entry("power.scrubber_panic", 30),
            Map.entry("power.pressure_pump", 20),
            Map.entry("power.volume_pump", 20),
            Map.entry("power.filter", 20),
            Map.entry("power.mixer", 20),
            Map.entry("power.heater", 60),
            Map.entry("power.air_conditioner", 60),
            Map.entry("power.igniter", 20),
            Map.entry("power.recharger", 40),
            Map.entry("power.electrolyzer", 80),
            Map.entry("power.area_air_controller", 5),
            Map.entry("power.central_controller", 5),
            Map.entry("power.fire_alarm", 1),
            Map.entry("power.airlock_controller", 1));

    /** A row of the page's tables: its key in backticks, then its default in backticks. */
    private static final Pattern ROW = Pattern.compile("^\\| `\\[([a-z_.]+)\\] ([a-z_]+)` \\| `([^`]*)` \\|");

    @Test
    void cont_065_each_device_draws_the_requirements_default() {
        Map<String, Object> defaults = defaults();
        DRAWS.forEach((key, fe) -> assertEquals(fe, defaults.get(key), key));
    }

    @Test
    void cont_065_config_md_lists_every_content_key_with_its_default() throws IOException {
        assertDocumented(AtmosServerConfig.SPEC, "zas_atmos-server.toml");
    }

    /** The content switch is on unless a pack turns it off (CONT-155). */
    @Test
    void cont_155_the_content_is_on_by_default() {
        assertEquals(Map.of("content.enabled", true), defaults(AtmosStartupConfig.SPEC));
    }

    @Test
    void cont_155_config_md_lists_the_startup_key_with_its_default() throws IOException {
        assertDocumented(AtmosStartupConfig.SPEC, "zas_atmos-startup.toml");
    }

    /** docs/config.md's table under "## Content mod: `<file>`" lists every key of the spec with its default. */
    private static void assertDocumented(ModConfigSpec spec, String file) throws IOException {
        Map<String, String> expected = new TreeMap<>();
        defaults(spec).forEach((key, value) -> expected.put(key, String.valueOf(value)));
        List<String> lines = Files.readAllLines(Path.of(System.getProperty("zas.configDoc")));
        int start = lines.indexOf("## Content mod: `" + file + "`");
        assertTrue(start >= 0, "docs/config.md has no section for " + file);
        Map<String, String> documented = new TreeMap<>();
        for (String line : lines.subList(start + 1, lines.size())) {
            if (line.startsWith("## ")) {
                break;
            }
            Matcher m = ROW.matcher(line);
            if (m.find()) {
                documented.put(m.group(1) + "." + m.group(2), m.group(3));
            }
        }
        assertEquals(expected, documented, "docs/config.md's " + file + " table");
    }

    private static Map<String, Object> defaults() {
        return defaults(AtmosServerConfig.SPEC);
    }

    /** Every key of a config, as {@code section.key}, with its default. */
    private static Map<String, Object> defaults(ModConfigSpec spec) {
        Map<String, Object> out = new TreeMap<>();
        collect(spec.getValues(), out);
        return out;
    }

    private static void collect(UnmodifiableConfig config, Map<String, Object> out) {
        for (UnmodifiableConfig.Entry e : config.entrySet()) {
            Object value = e.getValue();
            if (value instanceof UnmodifiableConfig sub) {
                collect(sub, out);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> v) {
                out.put(String.join(".", v.getPath()), v.getDefault());
            }
        }
    }
}
