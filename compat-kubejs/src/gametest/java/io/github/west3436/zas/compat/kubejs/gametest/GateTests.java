package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import io.github.west3436.zas.compat.kubejs.KubeJsGate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The adapter's version check (COMPAT-002) on a server whose KubeJS it supports: KubeJS found the
 * plugin in the release jar, and the plugin registered.
 */
@GameTestHolder(EventTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class GateTests {
    private GateTests() {}

    /** The KubeJS this server loaded, which is the one the run asked for. */
    static String installed(GameTestHelper h) {
        String installed = ModList.get()
                .getModContainerById(KubeJsGate.MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElseThrow(() -> new GameTestAssertException("KubeJS isn't loaded"));
        h.assertValueEqual(installed, System.getProperty("zas.kubejs.version"), "the KubeJS this run loaded");
        return installed;
    }

    /** How many lines of the server's log hold a text. */
    static long logged(String text) {
        Path log = FMLPaths.GAMEDIR.get().resolve("logs/latest.log");
        // Latin-1 reads any byte, whatever the log's encoding, and the texts looked for are ASCII.
        try (Stream<String> lines = Files.lines(log, StandardCharsets.ISO_8859_1)) {
            return lines.filter(line -> line.contains(text)).count();
        } catch (IOException | UncheckedIOException e) {
            throw new GameTestAssertException("couldn't read " + log + ": " + e);
        }
    }

    /**
     * With a KubeJS in the range the adapter is on, and says so once; nothing warns. Every script
     * of the run ran to its end, so each name it used was there.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_is_on_with_a_supported_kubejs(GameTestHelper h) {
        String installed = installed(h);
        h.assertValueEqual(
                VersionGate.check(installed, KubeJsGate.SUPPORTED), Verdict.SUPPORTED, "the range's verdict");
        h.assertTrue(KubeJsGate.pluginStarted(), "KubeJS never created the plugin");
        h.assertTrue(KubeJsGate.isActive(), "the adapter is off");
        h.assertValueEqual(logged("The adapter for kubejs " + installed + " is on"), 1L, "lines saying it is on");
        h.assertValueEqual(logged("The adapter for kubejs is off"), 0L, "warnings that it is off");
        for (String script :
                List.of("events.js", "calls.js", "hazard.js", "hazard_throws.js", "ambient.js", "ambient_throws.js")) {
            h.assertTrue(Probe.loads(script) >= 1, script + " didn't run to its end");
        }
        h.succeed();
    }

    /**
     * The range starts at the oldest build the adapter is tested with, and has no end: builds
     * below it are out, whatever their line, and newer ones are in.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_range_starts_at_the_oldest_build_tested(GameTestHelper h) {
        for (String version : List.of("2101.7.0-build.130", "2101.7.0-build.126", "2101.6.9-build.999", "2100.7.0")) {
            h.assertValueEqual(VersionGate.check(version, KubeJsGate.SUPPORTED), Verdict.UNSUPPORTED, version);
        }
        for (String version : List.of(
                "2101.7.0-build.131",
                "2101.7.0-build.171",
                "2101.7.1-build.181",
                "2101.7.2-build.381",
                "2101.7.3-build.1",
                "2101.8.0-build.2")) {
            h.assertValueEqual(VersionGate.check(version, KubeJsGate.SUPPORTED), Verdict.SUPPORTED, version);
        }
        h.succeed();
    }
}
