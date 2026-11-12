package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The look of a wall with a pipe in it (#487). The wall is drawn a face at a time: a face the pipe
 * shows on is the wall's face with the shape of the pipe's flange left out, and the flange set into the
 * gap. No two pieces share a place, so nothing fights for depth, whatever cuts a face up (Fusion cuts a
 * joined face into its corners).
 */
class EncasedPipeFacesTest {
    private static final List<String> WALLS = List.of("station_wall", "reinforced_station_wall");

    /** Faces by the ordinal of Minecraft's {@code Direction}, as {@link PipeMouths} counts them. */
    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");

    /** The face a north-facing model looks to, turned by a blockstate's x and y. */
    private static final Map<String, String> TURNED =
            Map.of("0 0", "north", "0 90", "east", "0 180", "south", "0 270", "west", "270 0", "up", "90 0", "down");

    /** The pipe shows where it connects, and where a bare pipe would have a mouth (CONT-004). */
    @Test
    void each_face_is_drawn_once_with_the_pipes_end_where_the_pipe_shows() throws IOException {
        for (String wall : WALLS) {
            JsonObject blockstate = json(assets().resolve("blockstates/" + wall + "_pipe.json"));
            for (int arms = 0; arms < 64; arms++) {
                JsonObject state = new JsonObject();
                for (int i = 0; i < 6; i++) {
                    state.addProperty(FACES.get(i), (arms & (1 << i)) != 0 ? "true" : "false");
                }
                int[] whole = new int[6];
                int[] holed = new int[6];
                int[] ends = new int[6];
                for (JsonElement e : blockstate.getAsJsonArray("multipart")) {
                    JsonObject part = e.getAsJsonObject();
                    if (!matches(part.getAsJsonObject("when"), state)) {
                        continue;
                    }
                    JsonObject apply = part.getAsJsonObject("apply");
                    String model = apply.get("model").getAsString();
                    int face = FACES.indexOf(turned(apply));
                    boolean locked = apply.has("uvlock") && apply.get("uvlock").getAsBoolean();
                    if (model.equals("zas_atmos:block/" + wall + "_face")) {
                        whole[face]++;
                        assertTrue(locked, wall + ": a face of the wall lies as a whole wall's does");
                    } else if (model.equals("zas_atmos:block/" + wall + "_face_pipe")) {
                        holed[face]++;
                        assertTrue(locked, wall + ": a face of the wall lies as a whole wall's does");
                    } else if (model.equals("zas_atmos:block/pipe_end")) {
                        ends[face]++;
                        assertFalse(locked, wall + ": the end keeps its own place on the pipe's texture");
                    } else {
                        throw new AssertionError(wall + " shows " + model);
                    }
                }
                int shows = arms | PipeMouths.of(arms);
                for (int face = 0; face < 6; face++) {
                    String what = wall + ", arms " + Integer.toBinaryString(arms) + ", " + FACES.get(face);
                    boolean end = (shows & (1 << face)) != 0;
                    assertEquals(end ? 0 : 1, whole[face], what + ": whole faces");
                    assertEquals(end ? 1 : 0, holed[face], what + ": faces with the gap");
                    assertEquals(end ? 1 : 0, ends[face], what + ": ends");
                }
            }
        }
    }

    @Test
    void the_end_fills_the_gap_in_the_face_and_nothing_overlaps() throws IOException {
        int[][] wall = new int[16][16];
        int[][] end = new int[16][16];
        cover(wall, "encased_face_pipe", "#casing");
        cover(end, "pipe_end", "#pipe");
        int flange = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                assertEquals(1, wall[x][y] + end[x][y], "pieces at " + x + ", " + y);
                flange += end[x][y];
            }
        }
        // The flange of pipe_arm: 6 by 4 with a 4 by 1 strip above and below.
        assertEquals(6 * 4 + 2 * 4, flange);
        int[][] whole = new int[16][16];
        cover(whole, "encased_face", "#casing");
        for (int[] column : whole) {
            for (int count : column) {
                assertEquals(1, count);
            }
        }
    }

    /** The end is the end face of the pipe's own arm: the same pixels of the same texture, dyed. */
    @Test
    void the_end_is_the_arms_end_face() throws IOException {
        List<String> arm = new ArrayList<>();
        for (JsonElement e :
                json(assets().resolve("models/block/pipe_arm.json")).getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            JsonObject north = element.getAsJsonObject("faces").getAsJsonObject("north");
            if (north != null && element.getAsJsonArray("from").get(2).getAsDouble() == 0) {
                arm.addAll(texels(element, north));
            }
        }
        JsonObject model = json(assets().resolve("models/block/pipe_end.json"));
        assertEquals(
                "zas_atmos:block/pipe",
                model.getAsJsonObject("textures").get("pipe").getAsString());
        List<String> end = new ArrayList<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            JsonObject north = element.getAsJsonObject("faces").getAsJsonObject("north");
            assertEquals(0, north.get("tintindex").getAsInt());
            end.addAll(texels(element, north));
        }
        assertEquals(
                arm.stream().distinct().sorted().toList(), end.stream().sorted().toList());
    }

    @Test
    void each_walls_faces_are_in_its_own_texture() throws IOException {
        for (String wall : WALLS) {
            for (String face : List.of("encased_face", "encased_face_pipe")) {
                JsonObject model =
                        json(assets().resolve("models/block/" + wall + face.substring("encased".length()) + ".json"));
                assertEquals("zas_atmos:block/" + face, model.get("parent").getAsString());
                assertEquals(
                        "zas_atmos:block/" + wall,
                        model.getAsJsonObject("textures").get("casing").getAsString());
            }
            assertEquals(
                    "zas_atmos:block/" + wall,
                    json(assets().resolve("models/block/" + wall + ".json"))
                            .getAsJsonObject("textures")
                            .get("all")
                            .getAsString(),
                    "the wall's own texture");
        }
    }

    /**
     * Counts the pixels of the north face each element of a model covers. Every element is on the grid,
     * shows its north face only, on the block's face, and hides it behind a block to the north.
     */
    private static void cover(int[][] count, String name, String texture) throws IOException {
        JsonObject model = json(assets().resolve("models/block/" + name + ".json"));
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            JsonObject faces = element.getAsJsonObject("faces");
            assertEquals(List.of("north"), List.copyOf(faces.keySet()), name);
            JsonObject north = faces.getAsJsonObject("north");
            assertEquals(texture, north.get("texture").getAsString(), name);
            assertEquals("north", north.get("cullface").getAsString(), name);
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            assertEquals(0, from.get(2).getAsInt(), name + ": on the block's north face");
            for (int x = from.get(0).getAsInt(); x < to.get(0).getAsInt(); x++) {
                for (int y = from.get(1).getAsInt(); y < to.get(1).getAsInt(); y++) {
                    count[x][y]++;
                }
            }
        }
    }

    /** The texture's pixel under each pixel of an element's north face: "x y of the block: u v". */
    private static List<String> texels(JsonObject element, JsonObject north) {
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        JsonArray uv = north.getAsJsonArray("uv");
        List<String> texels = new ArrayList<>();
        int x1 = to.get(0).getAsInt();
        int y1 = to.get(1).getAsInt();
        // A north face runs from the element's east and top to its west and bottom.
        for (int x = from.get(0).getAsInt(); x < x1; x++) {
            for (int y = from.get(1).getAsInt(); y < y1; y++) {
                int u = uv.get(0).getAsInt() + (x1 - 1 - x);
                int v = uv.get(1).getAsInt() + (y1 - 1 - y);
                assertTrue(u < uv.get(2).getAsInt() && v < uv.get(3).getAsInt(), "the uv is the face's size");
                texels.add(x + " " + y + ": " + u + " " + v);
            }
        }
        return texels;
    }

    /** Whether a multipart condition holds: all of its properties, or any of an OR's conditions. */
    private static boolean matches(JsonObject when, JsonObject state) {
        if (when.has("OR")) {
            for (JsonElement e : when.getAsJsonArray("OR")) {
                if (matches(e.getAsJsonObject(), state)) {
                    return true;
                }
            }
            return false;
        }
        for (Map.Entry<String, JsonElement> p : when.entrySet()) {
            if (!p.getValue().getAsString().equals(state.get(p.getKey()).getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static String turned(JsonObject apply) {
        int x = apply.has("x") ? apply.get("x").getAsInt() : 0;
        int y = apply.has("y") ? apply.get("y").getAsInt() : 0;
        String face = TURNED.get(x + " " + y);
        assertTrue(face != null, "a part turned x " + x + ", y " + y);
        return face;
    }

    private static JsonObject json(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path assets() {
        return Path.of(System.getProperty("zas_atmos.resources"))
                .resolve("assets")
                .resolve("zas_atmos");
    }
}
