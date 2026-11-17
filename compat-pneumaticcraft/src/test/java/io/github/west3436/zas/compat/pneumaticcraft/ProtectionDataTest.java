package io.github.west3436.zas.compat.pneumaticcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The protection data the adapter ships for Pneumatic Armor (COMPAT-010), read as files: the four
 * pieces, each named once, rated as the softsuit, the helmet alone sealing the breath.
 */
class ProtectionDataTest {
    private static JsonObject read(String file) throws IOException {
        String root = System.getProperty("zas.pneumaticcraft.resources");
        assertTrue(root != null && !root.isBlank(), "zas.pneumaticcraft.resources is not set by the build");
        Path path = Path.of(root, "datapacks", "pneumaticcraft", "data", "pneumaticcraft", "zas", "protection", file);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void compat_010_the_four_pieces_are_rated_as_the_softsuit() throws IOException {
        List<String> named = new ArrayList<>();
        for (String file : List.of("pneumatic_helmet.json", "pneumatic_armor.json")) {
            JsonObject json = read(file);
            json.getAsJsonArray("items").forEach(e -> named.add(e.getAsString()));
            assertEquals(0.0, json.get("min_pressure_kpa").getAsDouble(), file);
            assertEquals(0.0, json.get("min_temperature_k").getAsDouble(), file);
            assertEquals(500.0, json.get("max_temperature_k").getAsDouble(), file);
            boolean sealed =
                    json.has("sealed_breathing") && json.get("sealed_breathing").getAsBoolean();
            assertEquals(file.equals("pneumatic_helmet.json"), sealed, file + ": seals the breathing");
        }
        assertEquals(
                List.of(
                        "pneumaticcraft:pneumatic_helmet",
                        "pneumaticcraft:pneumatic_chestplate",
                        "pneumaticcraft:pneumatic_leggings",
                        "pneumaticcraft:pneumatic_boots"),
                named);
    }
}
