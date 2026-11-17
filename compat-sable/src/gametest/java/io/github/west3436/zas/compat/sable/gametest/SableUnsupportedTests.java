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
 * With a Sable older than the adapter supports, the adapter stays off (COMPAT-002): the game starts,
 * {@code zas} logged its one warning, and the hooks into Sable aren't applied.
 */
@GameTestHolder(SableUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SableUnsupportedTests {
    /** The namespace of the tests for an unsupported Sable. */
    public static final String NAMESPACE = "zas_sable_off";

    private SableUnsupportedTests() {}

    /**
     * The one warning about the version is {@code zas}'s, and its own tests cover its text; this
     * checks what the warning says happened.
     */
    @GameTest(template = "empty")
    public static void compat_002_an_unsupported_sable_leaves_the_adapter_off(GameTestHelper h) {
        String installed = SableGateTests.installed(h);
        Status status = SableGateTests.status(h);
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(Adapters.isActive(SableVersions.MOD_ID), "zas says the adapter is on");
        h.assertFalse(SableCompat.isActive(), "the adapter says it started");
        // Mixin read the config, as Sable is there, and its plugin kept the hooks off.
        h.assertTrue(SableMixinPlugin.loaded(), "Mixin never loaded the adapter's config");
        h.assertFalse(SableMixinPlugin.applies(), "the plugin let the hooks apply");
        h.succeed();
    }
}
