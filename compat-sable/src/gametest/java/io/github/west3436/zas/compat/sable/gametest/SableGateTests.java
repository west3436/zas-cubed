package io.github.west3436.zas.compat.sable.gametest;

import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.sable.SableCompat;
import io.github.west3436.zas.compat.sable.SableVersions;
import io.github.west3436.zas.compat.sable.mixin.SableMixinPlugin;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How the adapter starts with a Sable it supports (COMPAT-002): on, with the Sable the run asked
 * for, and its hooks applied.
 */
@GameTestHolder(SableGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SableGateTests {
    /** The namespace of the adapter's GameTests with a supported Sable. */
    public static final String NAMESPACE = "zas_sable";

    private SableGateTests() {}

    /** The adapter's row in zas's table, as the loader found it. */
    static Status status(GameTestHelper h) {
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(SableVersions.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Sable");
        return status;
    }

    /**
     * Sable against the build the run asked for, which Modrinth's maven names {@code
     * <version>+mc<minecraft>}.
     */
    static String installed(GameTestHelper h) {
        String installed = Adapters.installedVersion(SableVersions.MOD_ID).orElse(null);
        h.assertTrue(installed != null, "Sable isn't installed on this server");
        String asked = System.getProperty("zas.sable.version");
        h.assertTrue(
                asked != null && asked.startsWith(installed + "+mc"),
                "the build asked for Sable " + asked + ", and " + installed + " is installed");
        return installed;
    }

    /**
     * With Sable installed in a supported version, {@code zas} starts the adapter: its row is active
     * with the range the mixin plugin reads, the Sable on the server is the one the build asked for,
     * and the hooks into Sable apply.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_is_on_with_a_supported_sable(GameTestHelper h) {
        String installed = installed(h);
        Status status = status(h);
        h.assertValueEqual(status.row().entryClass(), SableCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.row().range(), SableVersions.SUPPORTED, "the row's range");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(Adapters.isActive(SableVersions.MOD_ID), "zas says the adapter is off");
        h.assertTrue(SableCompat.isActive(), "the adapter says it never started");
        h.assertTrue(SableMixinPlugin.loaded(), "Mixin never loaded the adapter's config");
        h.assertTrue(SableMixinPlugin.applies(), "the plugin keeps the hooks from applying");
        h.succeed();
    }
}
