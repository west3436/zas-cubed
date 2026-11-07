package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The faces of a pipe that show an open mouth (CONT-004), and the pipe's blockstate, which must put its
 * mouth model on just those faces and an arm on each face it connects on.
 */
class PipeMouthsTest {
    /** Faces by the ordinal of Minecraft's {@code Direction}, as {@link PipeMouths} counts them. */
    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");

    private static final int[] OPPOSITE = {1, 0, 3, 2, 5, 4};

    /** The face a north-facing model looks to, turned by a blockstate's x and y. */
    private static final Map<String, String> TURNED =
            Map.of("0 0", "north", "0 90", "east", "0 180", "south", "0 270", "west", "270 0", "up", "90 0", "down");

    @Test
    void cont_004_a_lone_pipe_has_a_mouth_on_every_face() {
        assertEquals(0b111111, PipeMouths.of(0));
    }

    @Test
    void cont_004_a_pipe_with_one_connection_has_a_mouth_opposite_it() {
        for (int i = 0; i < 6; i++) {
            assertEquals(1 << OPPOSITE[i], PipeMouths.of(1 << i), "connected to the " + FACES.get(i));
        }
    }

    @Test
    void cont_004_runs_bends_and_tees_have_no_mouth() {
        for (int arms = 0; arms < 64; arms++) {
            if (Integer.bitCount(arms) >= 2) {
                assertEquals(0, PipeMouths.of(arms), "arms " + Integer.toBinaryString(arms));
            }
        }
    }

    @Test
    void cont_004_the_blockstate_shows_a_mouth_on_each_face_that_leaks() throws IOException {
        JsonObject blockstate = json(assets().resolve("blockstates/pipe.json"));
        for (int arms = 0; arms < 64; arms++) {
            JsonObject state = new JsonObject();
            for (int i = 0; i < 6; i++) {
                state.addProperty(FACES.get(i), (arms & (1 << i)) != 0 ? "true" : "false");
            }
            int shownArms = 0;
            int shownMouths = 0;
            int cores = 0;
            for (JsonElement e : blockstate.getAsJsonArray("multipart")) {
                JsonObject part = e.getAsJsonObject();
                if (part.has("when") && !matches(part.getAsJsonObject("when"), state)) {
                    continue;
                }
                JsonObject apply = part.getAsJsonObject("apply");
                String model = apply.get("model").getAsString();
                int face = 1 << FACES.indexOf(turned(apply));
                switch (model) {
                    case "zas_atmos:block/pipe_core" -> cores++;
                    case "zas_atmos:block/pipe_arm" -> shownArms |= face;
                    case "zas_atmos:block/pipe_mouth" -> shownMouths |= face;
                    default -> throw new AssertionError("the pipe shows " + model);
                }
            }
            String what = "arms " + Integer.toBinaryString(arms);
            assertEquals(1, cores, what + ": cores");
            assertEquals(arms, shownArms, what + ": arms shown");
            assertEquals(PipeMouths.of(arms), shownMouths, what + ": mouths shown");
        }
    }

    @Test
    void cont_004_the_mouth_model_and_its_textures_exist() throws IOException {
        JsonObject model = json(assets().resolve("models/block/pipe_mouth.json"));
        for (Map.Entry<String, JsonElement> texture :
                model.getAsJsonObject("textures").entrySet()) {
            String name = texture.getValue().getAsString().substring("zas_atmos:".length());
            assertTrue(
                    Files.isRegularFile(assets().resolve("textures/" + name + ".png")),
                    "the mouth's " + texture.getKey() + " texture " + name + " doesn't exist");
        }
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
            List<String> values = List.of(p.getValue().getAsString().split("\\|"));
            if (!values.contains(state.get(p.getKey()).getAsString())) {
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
