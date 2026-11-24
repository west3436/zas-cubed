package io.github.west3436.zas.compat.mi.gametest;

import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.mi.ModernIndustrializationCompat;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * With a Modern Industrialization older than the adapter supports, the adapter stays off
 * (COMPAT-091): the game starts, {@code zas} logged its one warning, and nothing the adapter adds
 * is there.
 */
@GameTestHolder(MiUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MiUnsupportedTests {
    /** The namespace of the tests for an unsupported Modern Industrialization. */
    public static final String NAMESPACE = "zas_mi_off";

    private MiUnsupportedTests() {}

    /**
     * The one warning about the version is {@code zas}'s, and its own tests cover its text; this
     * checks what the warning says happened.
     */
    @GameTest(template = "empty")
    public static void compat_091_an_unsupported_modern_industrialization_leaves_the_adapter_off(GameTestHelper h) {
        String installed = MiGateTests.installed(h);
        Status status = MiGateTests.status(h);
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(
                ZasAdapters.ADAPTERS.isActive(ModernIndustrializationCompat.MOD_ID), "zas says the adapter is on");
        h.assertFalse(ModernIndustrializationCompat.isActive(), "the adapter says it started");

        // Nothing the adapter adds is there, though Modern Industrialization's fluids are.
        for (String pack : List.of(
                ModernIndustrializationCompat.dataPackId("zas"),
                ModernIndustrializationCompat.sharedGasesPackId("zas"))) {
            h.assertFalse(
                    h.getLevel()
                            .getServer()
                            .getPackRepository()
                            .getAvailableIds()
                            .contains(pack),
                    pack + " is offered");
        }
        h.assertFalse(BuiltInRegistries.FLUID.get(MiGateTests.mi("argon")) == Fluids.EMPTY, "it has no argon");
        h.assertFalse(GasProperties.isDefined(MiGateTests.zas("argon")), "the adapter's gases are defined");
        h.assertFalse(GasProperties.isDefined(MiGateTests.zas("chlorine")), "the shared gases are defined");
        h.succeed();
    }
}
