package io.github.west3436.zas.atmos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/** The content mod's config defaults: each device's FE draw (CONT-065) and the content switch (CONT-155). */
class ContentConfigDefaultsTest {
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

    @Test
    void cont_065_each_device_draws_the_requirements_default() {
        Map<String, Object> defaults = defaults(AtmosServerConfig.SPEC);
        DRAWS.forEach((key, fe) -> assertEquals(fe, defaults.get(key), key));
    }

    /** The content switch is on unless a pack turns it off (CONT-155). */
    @Test
    void cont_155_the_content_is_on_by_default() {
        assertEquals(Map.of("content.enabled", true), defaults(AtmosStartupConfig.SPEC));
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
