package io.github.west3436.zas.compat.ae2.gametest;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import io.github.west3436.ss13.compat.Adapters;
import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.ae2.Ae2Compat;
import io.github.west3436.zas.compat.ae2.Ae2Settings;
import io.github.west3436.zas.compat.ae2.GasCells;
import io.github.west3436.zas.compat.ae2.GasKeyType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Whether the adapter starts (COMPAT-002): with a supported AE2 it is on, and gases are a key type of
 * AE2's; with the build before the oldest supported one it stays off and adds nothing.
 */
public final class Ae2GateTests {
    private Ae2GateTests() {}

    /** The AE2 the build asked for is the one installed. */
    private static String installed(GameTestHelper h) {
        String installed = Adapters.installedVersion("ae2").orElse(null);
        h.assertTrue(installed != null, "AE2 isn't installed on this server");
        h.assertValueEqual(installed, System.getProperty("zas.ae2.version"), "the AE2 installed");
        return installed;
    }

    private static Status status(GameTestHelper h) {
        Status status = ZasAdapters.ADAPTERS.status("ae2").orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for AE2");
        return status;
    }

    /** Whether the gas cells are items, and their recipes loaded (COMPAT-075 to COMPAT-077). */
    private static boolean cellsThere(GameTestHelper h, boolean expected) {
        boolean item = BuiltInRegistries.ITEM.containsKey(GasCells.HOUSING);
        boolean recipe = h.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath("zas", "ae2/gas_cell_housing"))
                .isPresent();
        h.assertValueEqual(item, expected, "whether the gas cell housing is an item");
        h.assertValueEqual(recipe, expected, "whether its recipe is loaded");
        return item;
    }

    private static boolean gasTypeRegistered() {
        return AEKeyTypes.getAll().stream().anyMatch(type -> type.getId().equals(GasKeyType.ID));
    }

    /** The tests of a supported AE2. */
    @GameTestHolder(Ae2Tests.NAMESPACE)
    @PrefixGameTestTemplate(false)
    public static final class On {
        private On() {}

        @GameTest(template = "empty")
        public static void compat_002_a_supported_ae2_starts_the_adapter(GameTestHelper h) {
            String installed = installed(h);
            Status status = status(h);
            h.assertValueEqual(status.version(), installed, "the version the loader saw");
            h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
            h.assertTrue(ZasAdapters.ADAPTERS.isActive("ae2"), "zas says the adapter is off");
            h.assertTrue(Ae2Compat.isActive(), "the adapter says it didn't start");
            h.assertTrue(Ae2Compat.registered(), "the buses weren't registered");
            h.assertTrue(ModConfigs.getFileMap().containsKey(Ae2Settings.FILE), "its config file isn't registered");

            // COMPAT-070: gases are a type of stack of AE2's, beside items and fluids.
            h.assertTrue(gasTypeRegistered(), "the gas key type isn't registered");
            AEKeyType type = AEKeyTypes.get(GasKeyType.ID);
            h.assertTrue(type == GasKeyType.TYPE, "AE2 has another type under " + GasKeyType.ID);
            h.assertTrue(type != AEKeyType.items() && type != AEKeyType.fluids(), "gases are items or fluids");
            h.assertValueEqual(type.getUnitSymbol(), "mol", "the unit a terminal shows");
            h.assertValueEqual(type.formatAmount(2500, appeng.api.stacks.AmountFormat.FULL), "2.5 mol", "2,500 units");
            h.assertTrue(AEKey.class.isAssignableFrom(type.getKeyClass()), "the key class");
            cellsThere(h, true);
            h.succeed();
        }
    }

    /** The tests of the AE2 just older than the adapter supports. */
    @GameTestHolder(Ae2Tests.OFF)
    @PrefixGameTestTemplate(false)
    public static final class Off {
        private Off() {}

        @GameTest(template = "empty")
        public static void compat_002_an_unsupported_ae2_leaves_the_adapter_off(GameTestHelper h) {
            String installed = installed(h);
            Status status = status(h);
            h.assertValueEqual(status.version(), installed, "the version the loader saw");
            h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
            h.assertFalse(ZasAdapters.ADAPTERS.isActive("ae2"), "zas says the adapter is on");
            h.assertFalse(Ae2Compat.isActive(), "the adapter says it started");
            h.assertFalse(Ae2Compat.registered(), "the buses were registered");
            h.assertFalse(ModConfigs.getFileMap().containsKey(Ae2Settings.FILE), "its config file is registered");
            h.assertFalse(gasTypeRegistered(), "the gas key type is registered");
            // AE2 itself runs on, with its own types.
            h.assertTrue(AEKeyTypes.getAll().contains(AEKeyType.fluids()), "AE2's fluids aren't a key type");
            // No gas cells, and their recipes stay unloaded without an error.
            cellsThere(h, false);
            h.succeed();
        }
    }
}
