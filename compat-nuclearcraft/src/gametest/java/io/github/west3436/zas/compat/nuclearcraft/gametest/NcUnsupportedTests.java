package io.github.west3436.zas.compat.nuclearcraft.gametest;

import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.nuclearcraft.NuclearCraftCompat;
import io.github.west3436.zas.compat.nuclearcraft.NuclearCraftSettings;
import io.github.west3436.zas.life.Protections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * With a NuclearCraft older than the adapter supports, the adapter stays off (COMPAT-150): the game
 * starts, {@code zas} logged its one warning, and nothing the adapter adds is there.
 */
@GameTestHolder(NcUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NcUnsupportedTests {
    /** The namespace of the tests for an unsupported NuclearCraft. */
    public static final String NAMESPACE = "zas_nuclearcraft_off";

    private NcUnsupportedTests() {}

    /**
     * The one warning about the version is {@code zas}'s, and its own tests cover its text; this
     * checks what the warning says happened.
     */
    @GameTest(template = "empty")
    public static void compat_150_an_unsupported_nuclearcraft_leaves_the_adapter_off(GameTestHelper h) {
        String installed = NcGateTests.installed(h);
        Status status = NcGateTests.status(h);
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(ZasAdapters.ADAPTERS.isActive(NuclearCraftCompat.MOD_ID), "zas says the adapter is on");
        h.assertFalse(NuclearCraftCompat.isActive(), "the adapter says it started");

        // Nothing the adapter adds is there.
        h.assertFalse(ModConfigs.getFileMap().containsKey(NuclearCraftSettings.FILE), "its config file is registered");
        for (String pack : List.of(NuclearCraftCompat.dataPackId("zas"), NuclearCraftCompat.sharedGasesPackId("zas"))) {
            h.assertFalse(
                    h.getLevel()
                            .getServer()
                            .getPackRepository()
                            .getAvailableIds()
                            .contains(pack),
                    pack + " is offered");
        }
        h.assertFalse(GasProperties.isDefined(NcGateTests.zas("argon")), "the adapter's gases are defined");
        h.assertFalse(GasProperties.isDefined(NcGateTests.zas("chlorine")), "the shared gases are defined");
        ItemStack helmet = new ItemStack(BuiltInRegistries.ITEM.get(Area.nc("hev_helmet")));
        h.assertFalse(helmet.is(Items.AIR), "NuclearCraft has no HEV helmet");
        h.assertTrue(Protections.of(helmet) == null, "the HEV helmet has protection");
        h.succeed();
    }
}
