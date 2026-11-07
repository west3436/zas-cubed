package io.github.west3436.zas.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The heat data format's transmittance (HEAT-002, HEAT-019). */
class ThermalDataTest {
    private static DataResult<ThermalData> parse(String json) {
        return ThermalData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    /** HEAT-019: a transmittance of 0 loads as 0, not as the fallback. */
    @Test
    void heat_019_a_transmittance_of_0_loads() {
        ThermalData d = parse("""
                        {"blocks": ["zas_atmos:station_wall"], "transmittance": 0}
                        """).getOrThrow();
        assertEquals(Optional.of(0.0), d.transmittance());
        assertEquals(Optional.empty(), d.temperature());
    }

    /** HEAT-002: a positive U loads; a negative one, or one past the maximum, is refused naming its field. */
    @Test
    void heat_002_transmittance_range() {
        assertEquals(Optional.of(1.7), parse("""
                                {"blocks": ["minecraft:stone"], "transmittance": 1.7}
                                """).getOrThrow().transmittance());
        for (String u : new String[] {"-1", "-0.001", "1000001"}) {
            DataResult<ThermalData> r = parse("{\"blocks\": [\"minecraft:stone\"], \"transmittance\": " + u + "}");
            assertTrue(r.isError(), u + " loaded");
            String message = r.error().orElseThrow().message();
            assertTrue(message.startsWith("transmittance: "), u + ": " + message);
        }
    }
}
