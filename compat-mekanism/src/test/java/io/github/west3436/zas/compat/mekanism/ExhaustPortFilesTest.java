package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The exhaust port's files (MEK-022), which no server test can see: its blockstate, models,
 * textures and name are whole and point at each other, and its recipe, drop and tool are in the
 * adapter's data pack, which loads only with Mekanism.
 */
class ExhaustPortFilesTest {
    private static final Path RESOURCES = Path.of(System.getProperty("zas.mekanism.resources"));
    private static final Path ASSETS = RESOURCES.resolve("assets/zas");
    private static final Path PACK = RESOURCES.resolve("datapacks/mekanism/data");
    private static final Path ZAS_RESOURCES = Path.of(System.getProperty("zas.resources"));
    private static final String ID = "zas:" + ExhaustPort.ID;

    private static JsonObject json(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "missing: " + file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** The file of a model named as {@code zas:block/name}. */
    private static Path model(String name) {
        assertTrue(name.startsWith("zas:"), "a model outside the adapter's namespace: " + name);
        return ASSETS.resolve("models/" + name.substring("zas:".length()) + ".json");
    }

    /**
     * The blockstate has a model for each of the six ways the port faces, every model's textures
     * are 16 by 16 pictures of the adapter's own, the item uses the block's model, and the block
     * has a name.
     */
    @Test
    void mek_022_the_ports_blockstate_models_textures_and_name_are_whole() throws IOException {
        JsonObject variants =
                json(ASSETS.resolve("blockstates/" + ExhaustPort.ID + ".json")).getAsJsonObject("variants");
        assertEquals(
                Set.of("facing=north", "facing=east", "facing=south", "facing=west", "facing=up", "facing=down"),
                variants.keySet());
        Set<String> models = new TreeSet<>();
        for (JsonElement variant : variants.asMap().values()) {
            models.add(variant.getAsJsonObject().get("model").getAsString());
        }
        JsonObject item = json(ASSETS.resolve("models/item/" + ExhaustPort.ID + ".json"));
        assertTrue(models.contains(item.get("parent").getAsString()), "the item's model isn't one of the block's");

        Set<String> textures = new TreeSet<>();
        for (String name : models) {
            JsonObject model = json(model(name));
            assertEquals("minecraft:block/cube", model.get("parent").getAsString(), name);
            Map<String, JsonElement> faces = model.getAsJsonObject("textures").asMap();
            assertEquals(Set.of("particle", "north", "south", "east", "west", "up", "down"), faces.keySet(), name);
            faces.values().forEach(texture -> textures.add(texture.getAsString()));
        }
        assertEquals(
                Set.of("zas:block/exhaust_port_front", "zas:block/exhaust_port_side", "zas:block/exhaust_port_back"),
                textures);
        for (String texture : textures) {
            Path file = ASSETS.resolve("textures/" + texture.substring("zas:".length()) + ".png");
            assertTrue(Files.isRegularFile(file), "missing: " + file);
            BufferedImage image = ImageIO.read(file.toFile());
            assertEquals(16, image.getWidth(), texture);
            assertEquals(16, image.getHeight(), texture);
        }

        JsonObject lang = json(ZAS_RESOURCES.resolve("assets/zas/lang/en_us.json"));
        assertEquals("Exhaust Port", lang.get("block.zas." + ExhaustPort.ID).getAsString());
    }

    /**
     * The adapter's assets go into the jar beside the framework's own, so none may have the path of
     * one of those.
     */
    @Test
    void mek_022_no_asset_of_the_adapters_has_the_path_of_one_of_the_frameworks() throws IOException {
        Path assets = RESOURCES.resolve("assets");
        try (Stream<Path> files = Files.walk(assets)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                Path same = ZAS_RESOURCES
                        .resolve("assets")
                        .resolve(assets.relativize(file).toString());
                assertFalse(Files.exists(same), "the framework has this file too: " + same);
            }
        }
    }

    /**
     * The recipe makes one port from iron bars, iron and a pressurized tube; the block drops itself
     * and is mined with a pickaxe. All three are in the adapter's data pack, so none loads without
     * Mekanism, and none is in the mod's own data.
     */
    @Test
    void mek_022_the_ports_recipe_drop_and_tool_are_in_the_adapters_data_pack() throws IOException {
        JsonObject recipe = json(PACK.resolve("zas/recipe/" + ExhaustPort.ID + ".json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals(ID, result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
        Set<String> ingredients = new TreeSet<>();
        for (JsonElement key : recipe.getAsJsonObject("key").asMap().values()) {
            ingredients.add(key.getAsJsonObject().get("item").getAsString());
        }
        assertEquals(
                Set.of("minecraft:iron_bars", "minecraft:iron_ingot", "mekanism:basic_pressurized_tube"), ingredients);

        JsonObject loot = json(PACK.resolve("zas/loot_table/blocks/" + ExhaustPort.ID + ".json"));
        assertTrue(loot.toString().contains("\"name\":\"" + ID + "\""), "the port doesn't drop itself");

        JsonObject tag = json(PACK.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(tag.getAsJsonArray("values").toString().contains("\"" + ID + "\""), "no pickaxe tag");
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(), "the tag replaces the vanilla one");

        assertFalse(Files.exists(RESOURCES.resolve("data")), "the adapter has data outside its data pack");
    }
}
