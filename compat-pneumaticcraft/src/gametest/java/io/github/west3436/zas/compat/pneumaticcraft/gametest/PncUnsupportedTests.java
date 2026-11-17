package io.github.west3436.zas.compat.pneumaticcraft.gametest;

import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.pneumaticcraft.PncSettings;
import io.github.west3436.zas.compat.pneumaticcraft.PneumaticCraftCompat;
import io.github.west3436.zas.life.Protections;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * With a PneumaticCraft older than the adapter supports, the adapter stays off (COMPAT-002): the
 * game starts, {@code zas} logged its one warning, and nothing the adapter adds is there.
 */
@GameTestHolder(PncUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PncUnsupportedTests {
    /** The namespace of the tests for an unsupported PneumaticCraft. */
    public static final String NAMESPACE = "zas_pneumaticcraft_off";

    private PncUnsupportedTests() {}

    /**
     * The one warning about the version is {@code zas}'s, and its own tests cover its text; this
     * checks what the warning says happened.
     */
    @GameTest(template = "empty")
    public static void compat_002_an_unsupported_pneumaticcraft_leaves_the_adapter_off(GameTestHelper h) {
        String installed = PncGateTests.installed(h);
        Status status = PncGateTests.status(h);
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(Adapters.isActive(PneumaticCraftCompat.MOD_ID), "zas says the adapter is on");
        h.assertFalse(PneumaticCraftCompat.isActive(), "the adapter says it started");

        // Nothing the adapter adds is there.
        h.assertFalse(ModConfigs.getFileMap().containsKey(PncSettings.FILE), "its config file is registered");
        String pack = PneumaticCraftCompat.dataPackId("zas");
        h.assertFalse(
                h.getLevel().getServer().getPackRepository().getAvailableIds().contains(pack), pack + " is offered");
        ItemStack chest = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(PneumaticCraftCompat.MOD_ID, "pneumatic_chestplate")));
        h.assertFalse(chest.is(Items.AIR), "PneumaticCraft has no Pneumatic Chestplate");
        h.assertTrue(chest.getCapability(IInternalSupply.ITEM) == null, "the chestplate is a tank for internals");
        h.assertTrue(Protections.of(chest) == null, "the chestplate has protection");
        h.succeed();
    }
}
