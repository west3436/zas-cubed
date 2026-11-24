package io.github.west3436.zas.compat.mi.gametest;

import io.github.west3436.ss13.compat.Adapters;
import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.mi.ModernIndustrializationCompat;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How the adapter starts with a Modern Industrialization it supports (COMPAT-091), and the gases
 * its data adds (COMPAT-092).
 */
@GameTestHolder(MiGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MiGateTests {
    /** The namespace of the adapter's GameTests with a supported Modern Industrialization. */
    public static final String NAMESPACE = "zas_mi";

    /** Its gas fluids that are ZAS³'s own gases, by the fluid's path and the gas's (COMPAT-090). */
    static final Map<String, String> OWN_GASES = Map.of(
            "oxygen", "oxygen",
            "nitrogen", "nitrogen",
            "hydrogen", "hydrogen",
            "methane", "methane");

    /**
     * Its gas fluids that the adapter's packs add the gases of (COMPAT-092), by the fluid's path and
     * the gas's: ethylene is ZAS³'s ethene.
     */
    static final Map<String, String> ADDED_GASES = Map.of(
            "argon", "argon",
            "helium", "helium",
            "helium_3", "helium_3",
            "chlorine", "chlorine",
            "deuterium", "deuterium",
            "tritium", "tritium",
            "ethylene", "ethene");

    private MiGateTests() {}

    static ResourceLocation zas(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    static ResourceLocation mi(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModernIndustrializationCompat.MOD_ID, path);
    }

    /** The adapter's row in zas's table, as the loader found it. */
    static Status status(GameTestHelper h) {
        Status status = ZasAdapters.ADAPTERS
                .status(ModernIndustrializationCompat.MOD_ID)
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Modern Industrialization");
        return status;
    }

    /** The installed Modern Industrialization, which must be the one the build asked for. */
    static String installed(GameTestHelper h) {
        String installed =
                Adapters.installedVersion(ModernIndustrializationCompat.MOD_ID).orElse(null);
        h.assertTrue(installed != null, "Modern Industrialization isn't installed on this server");
        String asked = System.getProperty("zas.mi.version");
        h.assertValueEqual(
                installed, asked, "the Modern Industrialization installed against the one the build asked for");
        return installed;
    }

    static boolean selected(GameTestHelper h, String pack) {
        return h.getLevel().getServer().getPackRepository().getSelectedIds().contains(pack);
    }

    /**
     * With Modern Industrialization installed in a supported version, {@code zas} starts the
     * adapter: its row is active, and the Modern Industrialization on the server is the one the
     * build asked for. Its data pack and the shared gases' pack are on.
     */
    @GameTest(template = "empty")
    public static void compat_091_the_adapter_is_on_with_a_supported_modern_industrialization(GameTestHelper h) {
        String installed = installed(h);
        Status status = status(h);
        h.assertValueEqual(
                status.row().entryClass(), ModernIndustrializationCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(
                ZasAdapters.ADAPTERS.isActive(ModernIndustrializationCompat.MOD_ID), "zas says the adapter is off");
        h.assertTrue(ModernIndustrializationCompat.isActive(), "the adapter says it never started");
        for (String pack : List.of(
                ModernIndustrializationCompat.dataPackId("zas"),
                ModernIndustrializationCompat.sharedGasesPackId("zas"))) {
            h.assertTrue(selected(h, pack), pack + " isn't on");
        }
        h.succeed();
    }

    /**
     * The gases of Modern Industrialization's that ZAS³ doesn't have are defined, from its pack and
     * the shared one, and chlorine is toxic; the noble gases and the hydrogen isotopes aren't.
     */
    @GameTest(template = "empty")
    public static void compat_092_its_other_gases_are_defined(GameTestHelper h) {
        for (String gas : ADDED_GASES.values()) {
            h.assertTrue(GasProperties.isDefined(zas(gas)), "the server defines no gas zas:" + gas);
            h.assertValueEqual(
                    GasTags.contains(GasTags.TOXIC, zas(gas)), gas.equals("chlorine"), "zas:" + gas + " toxic");
        }
        h.assertTrue(GasTags.contains(GasTags.TOXIC, zas("carbon_monoxide")), "the packs replaced #zas:toxic");
        h.succeed();
    }
}
