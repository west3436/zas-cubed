package io.github.west3436.zas.compat.jei.gametest;

import io.github.west3436.zas.compat.jei.ZasJeiPlugin;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.gas.GasDefinitionsPayload;
import io.github.west3436.zas.gas.ServerGasRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import mezz.jei.api.IModPlugin;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The JEI plugin on a server whose JEI is older than the plugin supports (COMPAT-002): the game is
 * up with that JEI, the plugin registers nothing, and one warning is logged.
 *
 * <p>JEI starts its plugins only on a client, so the test does what JEI does there. It finds the
 * plugin by its annotation, makes it by its constructor without arguments, and calls every method
 * this JEI's {@code IModPlugin} has, three times over as for three reloads. Whatever it hands the
 * plugin fails the test if the plugin touches it.
 *
 * <p>The test is in a namespace of its own, which the build turns on only for the run it names
 * unsupported ({@code -Pjei.version=unsupported}); that run leaves {@link JeiPluginTests} out.
 */
@GameTestHolder(JeiUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class JeiUnsupportedTests {
    /** The namespace of the tests for an unsupported JEI. */
    public static final String NAMESPACE = "zas_jei_off";

    /** What the one warning says, up to the versions. */
    private static final String WARNING = "The adapter for jei is off";

    private JeiUnsupportedTests() {}

    @GameTest(template = "empty")
    public static void compat_002_an_older_jei_leaves_the_plugin_off_with_one_warning(GameTestHelper h) {
        String installed = ModList.get()
                .getModContainerById(ZasJeiPlugin.JEI)
                .orElseThrow(() -> new GameTestAssertException("JEI isn't installed in this run"))
                .getModInfo()
                .getVersion()
                .toString();
        h.assertValueEqual(installed, System.getProperty("zas.jei.runVersion"), "the JEI this run loaded");

        ClientGasRegistry.accept(GasDefinitionsPayload.of(ServerGasRegistry.current()));
        try {
            h.assertFalse(ViewerData.gases().isEmpty(), "there are gases to register, were the plugin on");
            for (int reload = 0; reload < 3; reload++) {
                // A new plugin each time: the warning is one for the game, not one per plugin.
                IModPlugin plugin = plugin();
                for (Method call : IModPlugin.class.getMethods()) {
                    if (!Modifier.isStatic(call.getModifiers())) {
                        ask(plugin, call);
                    }
                }
            }
        } finally {
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
        }

        // The log is written behind the game, so the warning may take a moment to be in it.
        Path log = FMLPaths.GAMEDIR.get().resolve("logs/latest.log");
        h.succeedWhen(() -> {
            List<String> warnings = lines(log, WARNING);
            h.assertValueEqual(warnings.size(), 1, "the warnings in the log");
            String said = "it supports versions " + ZasJeiPlugin.SUPPORTED + ", and " + installed + " is installed";
            h.assertTrue(warnings.get(0).contains(said), "the warning is: " + warnings.get(0));
        });
    }

    /** The plugin, found and made as JEI does on a client: by its annotation and its constructor. */
    private static IModPlugin plugin() {
        String name = ZasJeiPlugin.class.getName();
        boolean annotated = ModList.get().getAllScanData().stream()
                .flatMap(data -> data.getAnnotations().stream())
                .anyMatch(a -> a.annotationType().getClassName().equals("mezz.jei.api.JeiPlugin")
                        && a.clazz().getClassName().equals(name));
        if (!annotated) {
            throw new GameTestAssertException("no @JeiPlugin on " + name + " in the mods' scan data");
        }
        try {
            return Class.forName(name)
                    .asSubclass(IModPlugin.class)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new GameTestAssertException("this JEI can't make the plugin: " + e);
        }
    }

    /** Calls one of JEI's methods on the plugin, handing it things that fail the test if touched. */
    private static void ask(IModPlugin plugin, Method call) {
        Object[] handed =
                Stream.of(call.getParameterTypes()).map(type -> untouched(type)).toArray();
        try {
            call.invoke(plugin, handed);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof GameTestAssertException touched) {
                throw touched;
            }
            throw new GameTestAssertException("the plugin failed in " + call.getName() + ": " + e.getCause());
        } catch (IllegalAccessException e) {
            throw new GameTestAssertException("couldn't call " + call.getName() + ": " + e);
        }
    }

    /** Something of JEI's that fails the test if anything is asked of it. */
    private static Object untouched(Class<?> type) {
        if (!type.isInterface()) {
            throw new GameTestAssertException("JEI hands a plugin a " + type.getName() + ", which is no interface");
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            throw new GameTestAssertException("the plugin called " + type.getSimpleName() + "." + method.getName());
        });
    }

    /** The lines of a log that hold some text. */
    private static List<String> lines(Path log, String text) {
        // Latin-1 reads any byte, whatever the log's encoding, and the warning is ASCII.
        try (Stream<String> lines = Files.lines(log, StandardCharsets.ISO_8859_1)) {
            return lines.filter(line -> line.contains(text)).toList();
        } catch (IOException | UncheckedIOException e) {
            throw new GameTestAssertException("couldn't read " + log + ": " + e);
        }
    }
}
