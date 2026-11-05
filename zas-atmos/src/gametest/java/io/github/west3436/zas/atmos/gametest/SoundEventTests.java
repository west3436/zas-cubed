package io.github.west3436.zas.atmos.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoundEventTests {
    private SoundEventTests() {}

    /**
     * The sound events the game has registered are exactly those {@code sounds.json} defines, so
     * nothing plays silently and nothing in the file is out of reach (CLIENT-020).
     */
    @GameTest(template = "empty")
    public static void client_020_atmos_registered_events_are_defined(GameTestHelper h) {
        Set<String> registered = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (id.getNamespace().equals(ZasAtmosMod.MOD_ID)) {
                registered.add(id.getPath());
            }
        }
        h.assertTrue(!registered.isEmpty(), "no sound events are registered");
        h.assertValueEqual(registered, new TreeSet<>(definedEvents().keySet()), "the registered sound events");
        h.succeed();
    }

    /**
     * An event reaches as far as {@code sounds.json} says. A server only sends a sound out to its
     * event's own range, so a longer {@code attenuation_distance} alone would never be heard out
     * there, and nothing else would show it (CLIENT-020).
     */
    @GameTest(template = "empty")
    public static void client_020_atmos_event_ranges_match_sounds_json(GameTestHelper h) {
        JsonObject events = definedEvents();
        for (String name : events.keySet()) {
            float range = 16;
            for (JsonElement sound : events.getAsJsonObject(name).getAsJsonArray("sounds")) {
                if (sound.isJsonObject() && sound.getAsJsonObject().has("attenuation_distance")) {
                    range = Math.max(
                            range,
                            sound.getAsJsonObject().get("attenuation_distance").getAsFloat());
                }
            }
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(ZasAtmosMod.id(name));
            h.assertTrue(event != null, name + " isn't registered");
            h.assertValueEqual(event.getRange(1.0f), range, name + "'s range");
        }
        h.succeed();
    }

    private static JsonObject definedEvents() {
        String path = "/assets/" + ZasAtmosMod.MOD_ID + "/sounds.json";
        try (InputStream in = SoundEventTests.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new GameTestAssertException("no " + path + " on the classpath");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new GameTestAssertException("can't read " + path + ": " + e);
        }
    }
}
