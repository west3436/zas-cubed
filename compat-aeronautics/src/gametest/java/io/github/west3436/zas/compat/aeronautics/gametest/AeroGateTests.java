package io.github.west3436.zas.compat.aeronautics.gametest;

import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.aeronautics.AeroSettings;
import io.github.west3436.zas.compat.aeronautics.AeronauticsCompat;
import io.github.west3436.zas.compat.aeronautics.AeronauticsVersions;
import io.github.west3436.zas.compat.aeronautics.SableSeed;
import io.github.west3436.zas.compat.aeronautics.mixin.AeronauticsMixinPlugin;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How the adapter starts with an Aeronautics it supports (COMPAT-002): on, with the Aeronautics and
 * Sable the run asked for, its hook applied, its settings file and its seed there.
 */
@GameTestHolder(AeroGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AeroGateTests {
    /** The namespace of the adapter's GameTests with a supported Aeronautics. */
    public static final String NAMESPACE = "zas_aeronautics";

    private AeroGateTests() {}

    /** The adapter's row in zas's table, as the loader found it. */
    static Status status(GameTestHelper h) {
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(AeronauticsVersions.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Create Aeronautics");
        return status;
    }

    /**
     * An installed mod against the build the run asked for, which Modrinth's maven names {@code
     * <version>+mc<minecraft>}.
     */
    static String installed(GameTestHelper h, String modId, String property) {
        String installed = Adapters.installedVersion(modId).orElse(null);
        h.assertTrue(installed != null, modId + " isn't installed on this server");
        String asked = System.getProperty(property);
        h.assertTrue(
                asked != null && asked.startsWith(installed + "+mc"),
                "the build asked for " + modId + " " + asked + ", and " + installed + " is installed");
        return installed;
    }

    /**
     * With Aeronautics installed in a supported version, {@code zas} starts the adapter: its row is
     * active with the range the mixin plugin reads, and the Aeronautics and Sable on the server are
     * the ones the build asked for. The hook into Sable applies, and the settings file and the seed
     * are registered.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_is_on_with_a_supported_aeronautics(GameTestHelper h) {
        String installed = installed(h, AeronauticsVersions.MOD_ID, "zas.aeronautics.version");
        installed(h, AeronauticsVersions.SABLE_MOD_ID, "zas.aeronautics.sableVersion");
        Status status = status(h);
        h.assertValueEqual(status.row().entryClass(), AeronauticsCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.row().range(), AeronauticsVersions.SUPPORTED, "the row's range");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(Adapters.isActive(AeronauticsVersions.MOD_ID), "zas says the adapter is off");
        h.assertTrue(AeronauticsCompat.isActive(), "the adapter says it never started");
        h.assertTrue(AeronauticsMixinPlugin.loaded(), "Mixin never loaded the adapter's config");
        h.assertTrue(AeronauticsMixinPlugin.applies(), "the plugin keeps the hook from applying");
        h.assertTrue(ModConfigs.getFileMap().containsKey(AeroSettings.FILE), "its config file isn't registered");
        h.assertTrue(
                AmbientSeeds.registered().stream().anyMatch(r -> r.id().equals(SableSeed.ID)),
                "the seed isn't registered: " + AmbientSeeds.registered());
        h.succeed();
    }
}
