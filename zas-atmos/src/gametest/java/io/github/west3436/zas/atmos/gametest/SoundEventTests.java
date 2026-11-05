package io.github.west3436.zas.atmos.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
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
     * A server plays a sound at full volume whatever the air, since only each client knows the air
     * around its own player and fades the sound for itself (CLIENT-016). Someone in the air beside
     * a device in a vacuum still has to get the sound. {@link SoundLog} counts it where it played and
     * reports it once.
     */
    @GameTest(template = "empty")
    public static void client_016_a_server_plays_a_sound_in_full_for_each_client_to_fade(GameTestHelper h) {
        SoundLog.listen();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().playSound(null, pos, AtmosSounds.PIPE_LEAK.get(), SoundSource.BLOCKS, 1f, 1f);
        Vec3 at = Vec3.atCenterOf(pos);
        h.assertValueEqual(SoundLog.take(at.add(0, 5, 0)).size(), 0, "sounds five blocks above it");
        List<SoundLog.Played> played = SoundLog.take(at);
        h.assertValueEqual(played.size(), 1, "sounds played there");
        h.assertTrue(played.get(0).is(AtmosSounds.PIPE_LEAK), "it played " + played.get(0));
        h.assertValueEqual(SoundLog.take(at).size(), 0, "sounds reported again");
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
