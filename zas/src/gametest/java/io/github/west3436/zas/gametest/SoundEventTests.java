package io.github.west3436.zas.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.zas.ZasMod;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoundEventTests {
    private SoundEventTests() {}

    /**
     * The sound events the game has registered are exactly those {@code sounds.json} defines, so
     * nothing plays silently and nothing in the file is out of reach (CLIENT-020).
     */
    @GameTest(template = "empty")
    public static void client_020_registered_events_are_defined(GameTestHelper h) {
        Set<String> registered = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (id.getNamespace().equals(ZasMod.MOD_ID)) {
                registered.add(id.getPath());
            }
        }
        h.assertTrue(!registered.isEmpty(), "no sound events are registered");
        h.assertValueEqual(registered, new TreeSet<>(definedEvents().keySet()), "the registered sound events");
        h.succeed();
    }

    private static JsonObject definedEvents() {
        String path = "/assets/" + ZasMod.MOD_ID + "/sounds.json";
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
