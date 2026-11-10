package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The version check with a real Jade loaded (COMPAT-002). The run says which Jade it asked for, and
 * the table below says what each is expected to do, so the expectation doesn't come from the code
 * under test. They are in a namespace of their own because they are all that runs with a Jade
 * outside the range, where the readouts are off.
 */
@GameTestHolder(JadeGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class JadeGateTests {
    static final String NAMESPACE = "zas_jade_gate";
    private static final String BATCH = "zas_jade_gate";

    /** Whether the adapter is on with each Jade the tests are run with. */
    private static final Map<String, Boolean> ON = Map.of(
            // The newest for 1.21.1.
            "15.10.6+neoforge", true,
            // The oldest for 1.21.1, and the range's first.
            "15.1.6+neoforge", true,
            // The last for 1.21, which NeoForge 21.1 still loads.
            "15.1.5+neoforge", false);

    /** What the one warning says, up to the versions. */
    private static final String WARNING = "The adapter for jade is off";

    private JadeGateTests() {}

    /** The Jade this server loaded, and whether the adapter should be on with it. */
    private static boolean expected(GameTestHelper h) {
        String asked = System.getProperty("zas.jade.version");
        String loaded = ModList.get()
                .getModContainerById(JadeGate.MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElseThrow(() -> new GameTestAssertException("Jade isn't loaded"));
        h.assertValueEqual(loaded, asked, "the Jade this run loaded");
        Boolean on = ON.get(loaded);
        if (on == null) {
            throw new GameTestAssertException("no expectation for Jade " + loaded + ": add it to the table");
        }
        return on;
    }

    /**
     * With a supported Jade the readout is registered and a pipe gets its reading; with one outside
     * the range Jade holds nothing of the adapter's, and the server is up all the same.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_002_the_readout_is_on_only_with_a_supported_jade(GameTestHelper h) {
        boolean on = expected(h);
        // Jade creates the plugin whatever its version; the check only stops it registering.
        h.assertTrue(JadeGate.pluginStarted(), "Jade never created the plugin");
        h.assertValueEqual(JadeGate.open(), on, "whether the version check lets the adapter in");
        Spot s = new Spot(h, 40);
        s.clear();
        PipeBlockEntity pipe = s.place(1, 1, 1, AtmosBlocks.PIPE.get().defaultBlockState(), PipeBlockEntity.class);
        pipe.network().gas().add(new GasParcel(Map.of(Spot.O2, 2.0), Spot.T));
        h.assertValueEqual(
                JadeProbe.registered(pipe.getBlockState(), pipe), on, "whether Jade holds the readout for a pipe");
        h.assertValueEqual(JadeProbe.silent(s.level, pipe.getBlockPos()), !on, "whether a pipe gets no reading");
        s.clear();
        h.succeed();
    }

    /**
     * The check is made once, when Jade loads the plugin, and the log holds its warning once if the
     * Jade is outside the range and not at all otherwise. Asking again logs nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_002_one_warning_and_only_outside_the_range(GameTestHelper h) {
        boolean on = expected(h);
        for (int i = 0; i < 3; i++) {
            h.assertValueEqual(JadeGate.open(), on, "the version check's answer when asked again");
        }
        Path log = FMLPaths.GAMEDIR.get().resolve("logs/latest.log");
        long warnings;
        // Latin-1 reads any byte, whatever the log's encoding, and the warning is ASCII.
        try (Stream<String> lines = Files.lines(log, StandardCharsets.ISO_8859_1)) {
            warnings = lines.filter(line -> line.contains(WARNING)).count();
        } catch (IOException | UncheckedIOException e) {
            throw new GameTestAssertException("couldn't read " + log + ": " + e);
        }
        h.assertValueEqual(warnings, on ? 0L : 1L, "the warnings in the log");
        h.succeed();
    }
}
