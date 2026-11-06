package io.github.west3436.zas.example.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.example.ExampleAddonMod;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * API-007: this server runs the built {@code zas} jar and a built copy of the example addon, each
 * nesting the API, with no other API on the classpath. FML's jar-in-jar locator chooses between the
 * nested copies, as in production (D-17), and the mods must end up sharing one.
 */
@GameTestHolder(ExampleAddonMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OneCopyTests {
    private static final String API_PACKAGE = "io.github.west3436.zas.api";
    private static final String API_CLASS = API_PACKAGE.replace('.', '/') + "/ZasApi.class";
    private static final String MOD_TOML = "META-INF/neoforge.mods.toml";

    private OneCopyTests() {}

    /** Both jars hold the API, under the identity FML compares them by, at the path they name. */
    @GameTest(template = "empty")
    public static void api_007_zas_and_a_consumer_both_nest_the_api(GameTestHelper h) {
        for (String mod : List.of("zas", ExampleAddonMod.MOD_ID)) {
            h.assertTrue(ModList.get().isLoaded(mod), mod + " isn't loaded");
            IModFile file = ModList.get().getModFileById(mod).getFile();
            h.assertTrue(nestsTheApi(file), mod + "'s jar doesn't nest io.github.west3436:zas-api");
        }
        h.succeed();
    }

    /**
     * The game's class loader finds one {@code ZasApi}, in one module, and it's the one zas installed
     * its provider through: a second copy would leave this mod's copy with the fallback.
     */
    @GameTest(template = "empty")
    public static void api_007_only_one_copy_of_the_api_is_loaded(GameTestHelper h) {
        ClassLoader loader = ZasApi.class.getClassLoader();
        h.assertTrue(
                resources(loader, MOD_TOML).size() >= 2,
                "the class loader's search doesn't see across mod files, so a count of copies means nothing");
        List<URL> copies = resources(loader, API_CLASS);
        h.assertValueEqual(copies.size(), 1, "copies of the API's ZasApi class: " + copies);
        List<Module> modules = ZasApi.class.getModule().getLayer().modules().stream()
                .filter(m -> m.getPackages().contains(API_PACKAGE))
                .toList();
        h.assertValueEqual(modules.size(), 1, "modules holding the API's package: " + modules);
        h.assertTrue(ZasApi.isPresent(), "this mod's copy of the API can't see zas");
        h.assertTrue(ZasApi.provider().isReal(), "this mod's copy of the API has only the fallback");
        h.succeed();
    }

    private static boolean nestsTheApi(IModFile file) {
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
                        && "zas-api".equals(id.get("artifact").getAsString())) {
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
