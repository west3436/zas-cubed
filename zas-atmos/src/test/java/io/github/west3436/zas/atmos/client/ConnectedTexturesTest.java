package io.github.west3436.zas.atmos.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The connected-texture pack for Fusion (CONT-132): what it holds, and that without Fusion nothing
 * changes. How the blocks look joined is the owner's check in game.
 */
class ConnectedTexturesTest {
    private static final List<String> BLOCKS =
            List.of("station_wall", "reinforced_station_wall", "reinforced_glass_block");

    /** The walls a pipe goes into (#487): each joins itself with a pipe in it as well. */
    private static final List<String> WALLS = List.of("station_wall", "reinforced_station_wall");

    /** Fusion's pieced layout: five tiles side by side. */
    private static final int TILES = 5;

    /** Minecraft 1.21.1's resource pack format. */
    private static final int PACK_FORMAT = 34;

    @Test
    void cont_132_the_pack_is_a_resource_pack_for_fusion() throws IOException {
        JsonObject meta = json(pack().resolve("pack.mcmeta"));
        assertEquals(
                PACK_FORMAT, meta.getAsJsonObject("pack").get("pack_format").getAsInt());
        String description = meta.getAsJsonObject("pack")
                .getAsJsonObject("description")
                .get("translate")
                .getAsString();
        assertTrue(json(lang()).has(description), description + " isn't in en_us.json");
        // 1.2.0 has the pieced layout, and 1.2.9 is the first to cut a face that comes in parts right:
        // the faces of a wall with a pipe in it do (#487).
        assertEquals("1.2.9", meta.getAsJsonObject("fusion").get("min_version").getAsString());
    }

    @Test
    void cont_132_the_pack_holds_just_the_three_strips_and_their_metadata() throws IOException {
        Set<String> expected = new TreeSet<>(Set.of("pack.mcmeta"));
        for (String block : BLOCKS) {
            expected.add("assets/zas_atmos/textures/block/" + block + ".png");
            expected.add("assets/zas_atmos/textures/block/" + block + ".png.mcmeta");
        }
        try (Stream<Path> files = Files.walk(pack())) {
            Set<String> found = files.filter(Files::isRegularFile)
                    .map(p -> pack().relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(expected, found);
        }
    }

    /**
     * The glass joins glass. A wall joins the same wall, with a pipe in it or not (#487): its texture is
     * on both blocks, so it names the two rather than asking for the same block.
     */
    @Test
    void cont_132_each_strip_is_pieced_and_joins_its_own_block() throws IOException {
        for (String block : BLOCKS) {
            BufferedImage strip = strip(block);
            assertEquals(16 * TILES, strip.getWidth(), block);
            assertEquals(16, strip.getHeight(), block);
            JsonObject connections = pieced(block).getAsJsonObject("connections");
            if (!WALLS.contains(block)) {
                assertEquals("is_same_block", connections.get("type").getAsString(), block);
                continue;
            }
            assertEquals("or", connections.get("type").getAsString(), block);
            List<String> joined = new ArrayList<>();
            for (JsonElement predicate : connections.getAsJsonArray("predicates")) {
                assertEquals(
                        "match_block", predicate.getAsJsonObject().get("type").getAsString(), block);
                joined.add(predicate.getAsJsonObject().get("block").getAsString());
            }
            assertEquals(List.of("zas_atmos:" + block, "zas_atmos:" + block + "_pipe"), joined, block);
        }
    }

    /** A strip's Fusion metadata, checked to be a connecting texture in the pieced layout. */
    private static JsonObject pieced(String texture) throws IOException {
        JsonObject fusion = json(pack().resolve("assets/zas_atmos/textures/block/" + texture + ".png.mcmeta"))
                .getAsJsonObject("fusion");
        assertEquals("connecting", fusion.get("type").getAsString(), texture);
        assertEquals("pieced", fusion.get("layout").getAsString(), texture);
        return fusion;
    }

    @Test
    void cont_132_a_lone_block_looks_as_it_does_without_fusion() throws IOException {
        for (String block : BLOCKS) {
            BufferedImage own = ImageIO.read(
                    assets().resolve("textures/block/" + block + ".png").toFile());
            BufferedImage strip = strip(block);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals(own.getRGB(x, y), strip.getRGB(x, y), block + " at " + x + ", " + y);
                }
            }
        }
    }

    /** The joined tiles are drawn from the block's own texture, so the glass keeps its see-through pane. */
    @Test
    void cont_132_the_joined_tiles_use_only_the_blocks_own_colours() throws IOException {
        for (String block : BLOCKS) {
            BufferedImage own = ImageIO.read(
                    assets().resolve("textures/block/" + block + ".png").toFile());
            Set<Integer> colours = new HashSet<>();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    colours.add(own.getRGB(x, y));
                }
            }
            BufferedImage strip = strip(block);
            for (int y = 0; y < 16; y++) {
                for (int x = 16; x < 16 * TILES; x++) {
                    assertTrue(
                            colours.contains(strip.getRGB(x, y)),
                            block + "'s tile " + x / 16 + " at " + x % 16 + ", " + y);
                }
            }
        }
    }

    /**
     * Without Fusion the blocks must load and look as they always have: their models are vanilla's
     * {@code cube_all} on their own textures, and nothing outside the pack names Fusion.
     */
    @Test
    void cont_132_nothing_outside_the_pack_needs_fusion() throws IOException {
        for (String block : BLOCKS) {
            JsonObject model = json(assets().resolve("models/block/" + block + ".json"));
            assertEquals("minecraft:block/cube_all", model.get("parent").getAsString(), block);
            assertEquals(
                    "zas_atmos:block/" + block,
                    model.getAsJsonObject("textures").get("all").getAsString(),
                    block);
        }
        try (Stream<Path> files = Files.walk(assets())) {
            for (Path file : files.filter(
                            p -> p.toString().endsWith(".json") || p.toString().endsWith(".mcmeta"))
                    .toList()) {
                JsonObject json = json(file);
                assertFalse(json.has("fusion"), file + " has Fusion metadata");
                assertFalse(
                        json.has("loader") && json.get("loader").getAsString().startsWith("fusion:"),
                        file + " uses Fusion's model loader");
            }
        }
    }

    private static BufferedImage strip(String block) throws IOException {
        return ImageIO.read(pack().resolve("assets/zas_atmos/textures/block/" + block + ".png")
                .toFile());
    }

    private static JsonObject json(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path resources() {
        return Path.of(System.getProperty("zas_atmos.resources"));
    }

    private static Path pack() {
        return resources().resolve(ConnectedTextures.PACK);
    }

    private static Path assets() {
        return resources().resolve("assets").resolve("zas_atmos");
    }

    private static Path lang() {
        return assets().resolve("lang").resolve("en_us.json");
    }
}
