package io.github.west3436.zas.example.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.ss13.Ss13Framework;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.zas.example.ExampleAddonMod;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * This dedicated server runs the built release jar, whose {@code zas} nests SS13 Framework's mod and
 * API, beside a second consumer, {@code zas_example_framework}, that nests a copy one patch version
 * newer. Nothing else puts the framework on the classpath, so FML's jar-in-jar locator chooses
 * between the copies, as in production, and must load one copy of each, the newest.
 */
@GameTestHolder(ExampleAddonMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrameworkOneCopyTests {
    private static final String API_PACKAGE = "io.github.west3436.ss13.api";
    private static final String API_CLASS = API_PACKAGE.replace('.', '/') + "/FrameworkApi.class";
    private static final String MOD_CLASS = "io/github/west3436/ss13/Ss13Framework.class";
    private static final String MOD_TOML = "META-INF/neoforge.mods.toml";
    private static final String SECOND_CONSUMER = "zas_example_framework";
    private static final List<String> NESTED = List.of("ss13-framework", "ss13-framework-api");

    private FrameworkOneCopyTests() {}

    /** The release jar and the second consumer each nest the framework's mod and API. */
    @GameTest(template = "empty")
    public static void the_release_jar_and_a_second_consumer_nest_the_framework(GameTestHelper h) {
        for (String mod : List.of("zas", SECOND_CONSUMER)) {
            h.assertTrue(ModList.get().isLoaded(mod), mod + " isn't loaded");
            IModFile file = ModList.get().getModFileById(mod).getFile();
            for (String artifact : NESTED) {
                h.assertTrue(nests(file, artifact), mod + "'s jar doesn't nest io.github.west3436:" + artifact);
            }
        }
        h.succeed();
    }

    /** The game finds one copy of the framework mod's classes and one of the API's, in one module. */
    @GameTest(template = "empty")
    public static void one_copy_of_the_framework_loads(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded(FrameworkApi.MOD_ID), "SS13 Framework isn't loaded");
        ClassLoader loader = FrameworkApi.class.getClassLoader();
        h.assertTrue(
                resources(loader, MOD_TOML).size() >= 3,
                "the class loader's search doesn't see across mod files, so a count of copies means nothing");
        List<URL> api = resources(loader, API_CLASS);
        h.assertValueEqual(api.size(), 1, "copies of the API's FrameworkApi class: " + api);
        List<URL> mod = resources(loader, MOD_CLASS);
        h.assertValueEqual(mod.size(), 1, "copies of the mod's Ss13Framework class: " + mod);
        List<Module> modules = FrameworkApi.class.getModule().getLayer().modules().stream()
                .filter(m -> m.getPackages().contains(API_PACKAGE))
                .toList();
        h.assertValueEqual(modules.size(), 1, "modules holding the API's package: " + modules);
        h.assertTrue(
                FrameworkApi.class.getModule() != Ss13Framework.class.getModule(),
                "the API loaded inside the mod's module");
        h.succeed();
    }

    /** The copy that loaded is the second consumer's, the newer, for the mod and for the API. */
    @GameTest(template = "empty")
    public static void the_newest_framework_wins(GameTestHelper h) {
        String newest = System.getProperty("zas.example.newestFramework");
        h.assertTrue(newest != null, "the build didn't say which copy is newest");
        String loaded = ModList.get()
                .getModContainerById(FrameworkApi.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("none");
        h.assertValueEqual(loaded, newest, "the framework mod's loaded version");
        h.assertValueEqual(FrameworkApi.version(), Optional.of(newest), "the API's loaded version");
        h.succeed();
    }

    private static boolean nests(IModFile file, String artifact) {
        Path metadata = file.findResource("META-INF", "jarjar", "metadata.json");
        if (!Files.exists(metadata)) {
            return false;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("jars")) {
                JsonObject jar = element.getAsJsonObject();
                JsonObject id = jar.getAsJsonObject("identifier");
                if ("io.github.west3436".equals(id.get("group").getAsString())
                        && artifact.equals(id.get("artifact").getAsString())) {
                    return Files.exists(
                            file.findResource(jar.get("path").getAsString().split("/")));
                }
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<URL> resources(ClassLoader loader, String name) {
        try {
            return Collections.list(loader.getResources(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
