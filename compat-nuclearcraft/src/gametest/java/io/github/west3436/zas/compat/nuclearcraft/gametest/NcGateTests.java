package io.github.west3436.zas.compat.nuclearcraft.gametest;

import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.nuclearcraft.NuclearCraftCompat;
import io.github.west3436.zas.compat.nuclearcraft.NuclearCraftSettings;
import io.github.west3436.zas.life.Protections;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How the adapter starts with a NuclearCraft it supports (COMPAT-150), and what its data adds:
 * NuclearCraft's gases (COMPAT-151) and the HEV suit (COMPAT-154).
 */
@GameTestHolder(NcGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NcGateTests {
    /** The namespace of the adapter's GameTests with a supported NuclearCraft. */
    public static final String NAMESPACE = "zas_nuclearcraft";

    /** The gases only NuclearCraft has. */
    static final List<String> OWN_GASES = List.of(
            "argon",
            "neon",
            "helium",
            "helium_3",
            "nitric_oxide",
            "nitrogen_dioxide",
            "fluorine",
            "ammonia",
            "fluoromethane",
            "oxygen_difluoride",
            "diborane",
            "sulfur_trioxide",
            "radon");

    /** Those it shares with Mekanism, in zas's pack of shared gases. */
    static final List<String> SHARED_GASES = List.of("chlorine", "sulfur_dioxide", "ethene", "deuterium", "tritium");

    /** The toxic ones of both. */
    static final Set<String> TOXIC = Set.of(
            "nitric_oxide",
            "nitrogen_dioxide",
            "fluorine",
            "ammonia",
            "oxygen_difluoride",
            "diborane",
            "sulfur_trioxide",
            "chlorine",
            "sulfur_dioxide");

    private NcGateTests() {}

    static ResourceLocation zas(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    /** The adapter's row in zas's table, as the loader found it. */
    static Status status(GameTestHelper h) {
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(NuclearCraftCompat.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for NuclearCraft");
        return status;
    }

    /** The installed NuclearCraft, which must be the one the build asked for. */
    static String installed(GameTestHelper h) {
        String installed = Adapters.installedVersion(NuclearCraftCompat.MOD_ID).orElse(null);
        h.assertTrue(installed != null, "NuclearCraft isn't installed on this server");
        String asked = System.getProperty("zas.nuclearcraft.version");
        h.assertValueEqual(installed, asked, "the NuclearCraft installed against the one the build asked for");
        return installed;
    }

    /**
     * With NuclearCraft installed in a supported version, {@code zas} starts the adapter: its row is
     * active, and the NuclearCraft on the server is the one the build asked for. Its settings file
     * is registered, and its data pack and the shared gases' pack are on.
     */
    @GameTest(template = "empty")
    public static void compat_150_the_adapter_is_on_with_a_supported_nuclearcraft(GameTestHelper h) {
        String installed = installed(h);
        Status status = status(h);
        h.assertValueEqual(status.row().entryClass(), NuclearCraftCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(Adapters.isActive(NuclearCraftCompat.MOD_ID), "zas says the adapter is off");
        h.assertTrue(NuclearCraftCompat.isActive(), "the adapter says it never started");
        h.assertTrue(
                ModConfigs.getFileMap().containsKey(NuclearCraftSettings.FILE), "its config file isn't registered");
        h.assertTrue(NuclearCraftSettings.SPEC.isLoaded(), "the server config isn't loaded on a running server");
        for (String pack : List.of(NuclearCraftCompat.dataPackId("zas"), NuclearCraftCompat.sharedGasesPackId("zas"))) {
            h.assertTrue(
                    h.getLevel()
                            .getServer()
                            .getPackRepository()
                            .getSelectedIds()
                            .contains(pack),
                    pack + " isn't on");
        }
        h.succeed();
    }

    /**
     * NuclearCraft's gases are ZAS³ gases: those only it has from its own pack, those it shares with
     * Mekanism from zas's, and the toxic ones join {@code #zas:toxic}, which still holds ZAS³'s own.
     */
    @GameTest(template = "empty")
    public static void compat_151_nuclearcrafts_gases_are_defined_and_the_toxic_ones_tagged(GameTestHelper h) {
        for (String gas : OWN_GASES) {
            h.assertTrue(GasProperties.isDefined(zas(gas)), "the server defines no gas zas:" + gas);
        }
        for (String gas : SHARED_GASES) {
            h.assertTrue(GasProperties.isDefined(zas(gas)), "the server defines no gas zas:" + gas);
        }
        for (String gas : OWN_GASES) {
            h.assertValueEqual(GasTags.contains(GasTags.TOXIC, zas(gas)), TOXIC.contains(gas), "zas:" + gas + " toxic");
        }
        for (String gas : SHARED_GASES) {
            h.assertValueEqual(GasTags.contains(GasTags.TOXIC, zas(gas)), TOXIC.contains(gas), "zas:" + gas + " toxic");
        }
        h.assertTrue(GasTags.contains(GasTags.TOXIC, zas("carbon_monoxide")), "the packs replaced #zas:toxic");
        h.assertTrue(GasProperties.isDefined(zas("oxygen")), "zas's own gases aren't defined");
        h.succeed();
    }

    /**
     * The HEV suit is a suit (COMPAT-154): each piece is rated for any pressure and temperature, as
     * the MekaSuit is, and its helmet seals the breathing.
     */
    @GameTest(template = "empty")
    public static void compat_154_the_hev_suit_is_a_sealed_suit(GameTestHelper h) {
        for (String path : List.of("hev_helmet", "hev_chestplate", "hev_leggings", "hev_boots")) {
            ItemStack piece = new Area(h, 0).item(path);
            ILifeSupport rated = Protections.of(piece);
            h.assertTrue(rated != null, path + " has no protection");
            h.assertValueEqual(rated.minPressure(), 0.0, path + ": least pressure");
            h.assertValueEqual(rated.maxPressure(), 1.0e9, path + ": most pressure");
            h.assertValueEqual(rated.minTemperature(), 0.0, path + ": least temperature");
            h.assertValueEqual(rated.maxTemperature(), 1.0e9, path + ": most temperature");
            h.assertTrue(rated.sealedBreathing(), path + ": doesn't seal the breathing");
        }
        h.succeed();
    }
}
