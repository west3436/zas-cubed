package io.github.west3436.zas.compat.pneumaticcraft.gametest;

import io.github.west3436.ss13.compat.Adapters;
import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.pneumaticcraft.PncSettings;
import io.github.west3436.zas.compat.pneumaticcraft.PneumaticCraftCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How the adapter starts with a PneumaticCraft it supports (COMPAT-002): on, with the version the
 * run asked for, its settings file and its data pack there.
 */
@GameTestHolder(PncGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PncGateTests {
    /** The namespace of the adapter's GameTests with a supported PneumaticCraft. */
    public static final String NAMESPACE = "zas_pneumaticcraft";

    private PncGateTests() {}

    /** The adapter's row in zas's table, as the loader found it. */
    static Status status(GameTestHelper h) {
        Status status = ZasAdapters.ADAPTERS.status(PneumaticCraftCompat.MOD_ID).orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for PneumaticCraft");
        return status;
    }

    /**
     * The installed PneumaticCraft against the build the run asked for, which its maven names
     * {@code <version>+mc<minecraft>}.
     */
    static String installed(GameTestHelper h) {
        String installed =
                Adapters.installedVersion(PneumaticCraftCompat.MOD_ID).orElse(null);
        h.assertTrue(installed != null, "PneumaticCraft isn't installed on this server");
        String asked = System.getProperty("zas.pneumaticcraft.version");
        h.assertTrue(
                asked != null && asked.startsWith(installed + "+mc"),
                "the build asked for PneumaticCraft " + asked + ", and " + installed + " is installed");
        return installed;
    }

    /**
     * With PneumaticCraft installed in a supported version, {@code zas} starts the adapter: its row
     * is active, and the PneumaticCraft on the server is the one the build asked for. Its settings
     * file is registered and its data pack is on.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_is_on_with_a_supported_pneumaticcraft(GameTestHelper h) {
        String installed = installed(h);
        Status status = status(h);
        h.assertValueEqual(status.row().entryClass(), PneumaticCraftCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(ZasAdapters.ADAPTERS.isActive(PneumaticCraftCompat.MOD_ID), "zas says the adapter is off");
        h.assertTrue(PneumaticCraftCompat.isActive(), "the adapter says it never started");
        h.assertTrue(ModConfigs.getFileMap().containsKey(PncSettings.FILE), "its config file isn't registered");
        String pack = PneumaticCraftCompat.dataPackId("zas");
        h.assertTrue(
                h.getLevel().getServer().getPackRepository().getSelectedIds().contains(pack), pack + " isn't on");
        h.succeed();
    }
}
