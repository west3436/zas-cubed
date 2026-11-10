package io.github.west3436.zas.compat.mekanism.gametest;

import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.compat.mekanism.MekanismCompat;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import io.github.west3436.zas.compat.mekanism.MekanismVersions;
import io.github.west3436.zas.compat.mekanism.mixin.MekanismMixinPlugin;
import mekanism.api.MekanismAPI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The adapter on a server whose Mekanism is older than it supports (MEK-001): it stays off, no
 * mixin of its config applies, nothing of its data loads, and Mekanism and the server run on.
 *
 * <p>These are in a namespace of their own, which the build turns on only for the run it names
 * unsupported ({@code -Pmekanism.version=unsupported}); that run leaves {@link
 * MekanismFoundationTests} out.
 */
@GameTestHolder(MekanismUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismUnsupportedTests {
    /** The namespace of the tests for an unsupported Mekanism. */
    public static final String NAMESPACE = "zas_mekanism_off";

    private MekanismUnsupportedTests() {}

    /**
     * The one warning about the version is {@code zas}'s, and its own tests cover its text; this
     * checks what the warning says happened.
     */
    @GameTest(template = "empty")
    public static void mek_001_an_unsupported_mekanism_leaves_the_adapter_off(GameTestHelper h) {
        String installed = Adapters.installedVersion(MekanismVersions.MOD_ID).orElse(null);
        h.assertTrue(installed != null, "Mekanism isn't installed on this server");
        String asked = System.getProperty("zas.mekanism.runVersion");
        h.assertTrue(
                asked != null && asked.startsWith("1.21.1-" + installed + "."),
                "the build asked for Mekanism " + asked + ", and " + installed + " is installed");
        h.assertFalse(MekanismVersions.supports(installed), installed + " is inside " + MekanismVersions.SUPPORTED);

        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(MekanismVersions.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Mekanism");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(Adapters.isActive(MekanismVersions.MOD_ID), "zas says the adapter is on");
        h.assertFalse(MekanismCompat.isActive(), "the adapter says it started");

        // Mixin was handed the config, since Mekanism is installed, and the plugin kept it off.
        h.assertTrue(MekanismMixinPlugin.loaded(), "Mixin never loaded zas.mekanism.mixins.json");
        h.assertFalse(MekanismMixinPlugin.applies(), "the adapter's mixins apply to an unsupported Mekanism");

        // Nothing the adapter adds is there.
        String pack = MekanismCompat.dataPackId("zas");
        h.assertFalse(
                h.getLevel().getServer().getPackRepository().getAvailableIds().contains(pack), pack + " is offered");
        h.assertFalse(ModConfigs.getFileMap().containsKey(MekanismSettings.FILE), "its config file is registered");
        h.assertFalse(
                GasProperties.isDefined(ResourceLocation.fromNamespaceAndPath("zas", "chlorine")),
                "the adapter's gases are defined");
        h.assertTrue(ChemicalGases.loadedMap().mappings().isEmpty(), "the chemical map loaded");
        // Not even its chemicals (MEK-011): nothing in Mekanism's registry is ZAS³'s.
        h.assertTrue(
                MekanismAPI.CHEMICAL_REGISTRY.keySet().stream()
                        .noneMatch(chemical -> chemical.getNamespace().equals("zas")),
                "the adapter's chemicals are registered");

        // Mekanism itself is untouched and running.
        h.assertTrue(
                MekanismAPI.CHEMICAL_REGISTRY.containsKey(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen")),
                "Mekanism's chemicals aren't registered");
        h.succeed();
    }
}
