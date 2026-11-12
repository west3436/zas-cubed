package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import io.github.west3436.zas.compat.kubejs.KubeJsGate;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The adapter on a server whose KubeJS is older than it supports (COMPAT-002): it stays off, one
 * warning says so, and the server and KubeJS run on.
 *
 * <p>These are in a namespace of their own, which the build turns on only for the run that loads
 * such a KubeJS. That run has its own script, {@code off.js}, since a script that names what the
 * adapter gives fails where the adapter is off.
 */
@GameTestHolder(OffTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class OffTests {
    /** The namespace of the tests for an unsupported KubeJS. */
    public static final String NAMESPACE = "zas_kubejs_off";

    private OffTests() {}

    /**
     * KubeJS created the plugin, as it does whatever its version, and the plugin registered
     * nothing: a script finds no event group and none of the calls. The log holds the one warning,
     * with the range and the version.
     */
    @GameTest(template = "empty")
    public static void compat_002_an_unsupported_kubejs_leaves_the_adapter_off(GameTestHelper h) {
        String installed = GateTests.installed(h);
        h.assertValueEqual(
                VersionGate.check(installed, KubeJsGate.SUPPORTED), Verdict.UNSUPPORTED, "the range's verdict");
        h.assertTrue(KubeJsGate.pluginStarted(), "KubeJS never created the plugin");
        h.assertFalse(KubeJsGate.isActive(), "the adapter is on");

        h.assertValueEqual(Probe.loads("off.js"), 1, "times KubeJS ran the pack's script to its end");
        for (String name : List.of("ZasEvents", "Atmospheres", "GasMutations", "HazardHandlers", "AmbientProviders")) {
            h.assertValueEqual(Probe.noted("off." + name), "undefined", "what a script finds " + name + " to be");
        }

        h.assertValueEqual(
                GateTests.logged("The adapter for kubejs is off: it supports versions " + KubeJsGate.SUPPORTED
                        + ", and " + installed + " is installed"),
                1L,
                "the warnings in the log");
        h.assertValueEqual(GateTests.logged("The adapter for kubejs is off"), 1L, "warnings of any wording");
        h.assertValueEqual(GateTests.logged("The adapter for kubejs " + installed + " is on"), 0L, "lines saying on");
        h.succeed();
    }
}
