package io.github.west3436.zas.atmos.compat;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.compat.PonderGate.Verdict;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * The Ponder scenes' switch with a real Ponder loaded, on a dedicated server beside the release jar
 * (CLIENT-023, COMPAT-002, COMPAT-004, NFR-022). The run says what it asked for, and the table below
 * says what each is expected to load and what the switch should make of it, so the expectation
 * doesn't come from the code under test.
 *
 * <p>The scenes register on a client only, so a server shows what it can: that the game starts with
 * these mods, what a client with them would do, and that none of the scenes' classes is loaded here.
 * These tests are in the switch's package, not the scenes', so that they aren't among those classes.
 */
@GameTestHolder(PonderGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PonderGateTests {
    static final String NAMESPACE = "zas_ponder_gate";
    private static final String BATCH = "zas_ponder_gate";

    /** The scenes' package, which only a client with Ponder loads. */
    private static final String SCENES = "io.github.west3436.zas.atmos.compat.ponder.";

    /** What the one warning says, up to the versions. */
    private static final String WARNING = "The Ponder scenes are off";

    /**
     * What a run loads, and what the switch makes of it with the content on.
     *
     * @param create Create's version as its mod metadata gives it, or null if the run has no Create
     * @param addon whether the ZAS³ Ponder addon is loaded
     * @param ponder the Ponders that may load: FML takes one copy that every mod nesting Ponder
     *     accepts, and not always the newest
     * @param verdict what a client with these mods does
     */
    private record Run(@Nullable String create, boolean addon, Set<String> ponder, Verdict verdict) {}

    private static final Map<String, Run> RUNS = Map.of(
            // Create 6.0.10 nests Ponder 1.0.82, the oldest the scenes support, and is the newest Create.
            "create 6.0.10+mc1.21.1", new Run("6.0.10", false, Set.of("1.0.82+mc1.21.1"), Verdict.ON),
            // Create 6.0.9 nests Ponder 1.0.81.
            "create mc1.21.1-6.0.9", new Run("6.0.9", false, Set.of("1.0.81+mc1.21.1"), Verdict.UNSUPPORTED),
            // The addon as this build makes it holds no scenes. It and Create each accept the other's
            // Ponder, and FML loads either: the scenes are on with both.
            "create+addon 6.0.10+mc1.21.1",
                    new Run("6.0.10", true, Set.of("1.0.82+mc1.21.1", "1.0.87+mc1.21.1"), Verdict.ON),
            // The addon as release 1.0.0 shipped it registers its own copy of the scenes.
            "released", new Run(null, true, Set.of("1.0.87+mc1.21.1"), Verdict.ADDON_HAS_SCENES));

    private PonderGateTests() {}

    private static Run run() {
        String asked = System.getProperty("zas.ponder.run");
        Run run = RUNS.get(asked);
        if (run == null) {
            throw new GameTestAssertException("no expectation for the run '" + asked + "': add it to the table");
        }
        return run;
    }

    /** What a client with this run's mods does: with the content off, nothing, whatever is installed. */
    private static Verdict expected() {
        return ZasAtmosMod.contentEnabled() ? run().verdict() : Verdict.CONTENT_OFF;
    }

    private static @Nullable String installed(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse(null);
    }

    /**
     * The server started with the mods the run asked for, and the switch reads them as the table
     * says: on with Create's Ponder or the addon's, off with an older Ponder, with the released
     * addon's own scenes and with the content off (CONT-155).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_004_the_switch_reads_what_is_installed(GameTestHelper h) {
        Run run = run();
        h.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "not a dedicated server");
        h.assertValueEqual(String.valueOf(installed("create")), String.valueOf(run.create()), "the Create loaded");
        h.assertValueEqual(installed(PonderGate.ADDON) != null, run.addon(), "whether the addon is loaded");
        String ponder = installed(PonderGate.PONDER);
        h.assertTrue(
                ponder != null && run.ponder().contains(ponder),
                "expected the Ponder loaded to be one of " + run.ponder() + ", but it was " + ponder);
        for (String mod : new String[] {"flywheel", "zas_atmos", "zas"}) {
            h.assertTrue(ModList.get().isLoaded(mod), mod + " isn't loaded");
        }
        h.assertValueEqual(PonderGate.verdict(), expected(), "what the switch makes of these mods");
        h.succeed();
    }

    /**
     * The release jar holds the scenes, and a server loads none of their classes (NFR-022): only a
     * client asks the switch, so the entry class has never run, and the JVM's own list of the
     * classes it loaded has none from the scenes' package.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_022_a_server_loads_none_of_the_scenes(GameTestHelper h) {
        // They ship: these are looked up as files, which loads nothing.
        ClassLoader jar = PonderGateTests.class.getClassLoader();
        for (String file : new String[] {
            PonderGate.ENTRY.replace('.', '/') + ".class",
            "assets/zas_ponder/lang/en_us.json",
            "assets/zas_atmos/ponder/stage_5.nbt"
        }) {
            h.assertTrue(jar.getResource(file) != null, "the release jar has no " + file);
        }
        h.assertFalse(PonderGate.started(), "the scenes' entry class ran on a server");

        Path loaded = FMLPaths.GAMEDIR.get().resolve("class-load.log");
        long tests;
        long scenes;
        try (Stream<String> lines = Files.lines(loaded, StandardCharsets.ISO_8859_1)) {
            // The list is live if it has this class, which is running.
            long[] found = lines.collect(
                    () -> new long[2],
                    (counts, line) -> {
                        counts[0] += line.contains(" " + PonderGateTests.class.getName() + " ") ? 1 : 0;
                        counts[1] += line.contains(" " + SCENES) ? 1 : 0;
                    },
                    (a, b) -> {
                        a[0] += b[0];
                        a[1] += b[1];
                    });
            tests = found[0];
            scenes = found[1];
        } catch (IOException | UncheckedIOException e) {
            throw new GameTestAssertException("couldn't read " + loaded + ": " + e);
        }
        h.assertTrue(tests > 0, loaded + " doesn't list this test's own class, so it isn't the JVM's list");
        h.assertValueEqual(scenes, 0L, "the scenes' classes this server loaded");
        h.succeed();
    }

    /**
     * Asked where the scenes can't start, the switch loads nothing, and the log holds its warning once
     * if the Ponder is outside the range and not at all otherwise. Asking again logs nothing. Where
     * they can start, a server never asks: starting them needs a client.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_002_one_warning_and_only_with_an_unsupported_ponder(GameTestHelper h) {
        Verdict expected = expected();
        if (expected != Verdict.ON) {
            for (int i = 0; i < 3; i++) {
                PonderGate.start();
            }
            h.assertFalse(PonderGate.started(), "the scenes started with the switch at " + expected);
        }
        long wanted = expected == Verdict.UNSUPPORTED ? 1L : 0L;
        Path log = FMLPaths.GAMEDIR.get().resolve("logs/latest.log");
        // The log is written behind the game, so the warning may take a tick to be in the file.
        h.succeedWhen(() -> {
            long warnings;
            // Latin-1 reads any byte, whatever the log's encoding, and the warning is ASCII.
            try (Stream<String> lines = Files.lines(log, StandardCharsets.ISO_8859_1)) {
                warnings = lines.filter(line -> line.contains(WARNING)).count();
            } catch (IOException | UncheckedIOException e) {
                throw new GameTestAssertException("couldn't read " + log + ": " + e);
            }
            h.assertValueEqual(warnings, wanted, "the warnings in the log");
        });
    }
}
