package io.github.west3436.zas.atmos.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.junit.jupiter.api.Test;

/**
 * The shipped sounds as files: every event names files that exist, with a translated subtitle
 * (CLIENT-020), and every file is mono Vorbis (CLIENT-021). A sibling of this test checks the same
 * of {@code zas}'s files; that the events are registered is a GameTest, as it needs the game.
 */
class SoundAssetsTest {
    private static final String NAMESPACE = "zas_atmos";

    @Test
    void client_020_every_event_names_files_that_exist() throws IOException {
        for (String name : namedFiles()) {
            assertTrue(Files.isRegularFile(file(name)), "sounds.json names " + name + ", which doesn't exist");
        }
    }

    @Test
    void client_020_every_file_is_named_by_an_event() throws IOException {
        Set<String> named = namedFiles();
        for (Path ogg : shippedFiles()) {
            String relative = sounds().relativize(ogg).toString().replace('\\', '/');
            String name = NAMESPACE + ":" + relative.substring(0, relative.length() - ".ogg".length());
            assertTrue(named.contains(name), ogg + " is in the jar, but no event plays it");
        }
    }

    @Test
    void client_020_every_event_has_a_translated_subtitle() throws IOException {
        JsonObject lang = json(assets().resolve("lang/en_us.json"));
        JsonObject events = json(assets().resolve("sounds.json"));
        assertFalse(events.isEmpty(), "sounds.json has no events");
        for (String event : events.keySet()) {
            JsonElement subtitle = events.getAsJsonObject(event).get("subtitle");
            assertTrue(subtitle != null, event + " has no subtitle");
            assertTrue(lang.has(subtitle.getAsString()), "en_us.json has no " + subtitle.getAsString());
        }
    }

    @Test
    void client_021_every_file_is_mono_vorbis() throws IOException {
        List<Path> files = shippedFiles();
        assertFalse(files.isEmpty(), "no sound files");
        for (Path ogg : files) {
            assertEquals(1, vorbisChannels(Files.readAllBytes(ogg)), ogg + " channels");
        }
    }

    /** The game's own decoder reads each file, as far as its last sample (CLIENT-021). */
    @Test
    void client_021_every_file_decodes_with_the_games_decoder() throws IOException {
        for (Path ogg : shippedFiles()) {
            try (InputStream in = Files.newInputStream(ogg);
                    JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
                assertEquals(1, stream.getFormat().getChannels(), ogg + " decoded channels");
                assertTrue(stream.readAll().remaining() > 0, ogg + " decoded to nothing");
            }
        }
    }

    /**
     * The channel count from the first Ogg page's Vorbis identification header: "OggS", 26 bytes of
     * page header, the segment table, then packet type 1, "vorbis", a 4-byte version and the count.
     */
    private static int vorbisChannels(byte[] file) {
        assertEquals("OggS", new String(file, 0, 4, StandardCharsets.US_ASCII), "not an Ogg file");
        int packet = 27 + (file[26] & 0xFF);
        assertEquals(1, file[packet], "the first packet isn't an identification header");
        assertEquals("vorbis", new String(file, packet + 1, 6, StandardCharsets.US_ASCII));
        return file[packet + 11] & 0xFF;
    }

    /** Every sound file sounds.json names, as {@code namespace:path}. */
    private static Set<String> namedFiles() throws IOException {
        Set<String> names = new TreeSet<>();
        JsonObject events = json(assets().resolve("sounds.json"));
        for (String event : events.keySet()) {
            for (JsonElement sound : events.getAsJsonObject(event).getAsJsonArray("sounds")) {
                names.add(
                        sound.isJsonObject()
                                ? sound.getAsJsonObject().get("name").getAsString()
                                : sound.getAsString());
            }
        }
        return names;
    }

    private static Path file(String name) {
        int colon = name.indexOf(':');
        assertEquals(NAMESPACE, name.substring(0, colon), name + " is in another mod's namespace");
        return sounds().resolve(name.substring(colon + 1) + ".ogg");
    }

    private static List<Path> shippedFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(sounds())) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                assertTrue(file.toString().endsWith(".ogg"), file + " isn't an ogg");
            }
            return files;
        }
    }

    private static JsonObject json(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path sounds() {
        return assets().resolve("sounds");
    }

    private static Path assets() {
        return Path.of(System.getProperty("zas_atmos.resources"))
                .resolve("assets")
                .resolve(NAMESPACE);
    }
}
