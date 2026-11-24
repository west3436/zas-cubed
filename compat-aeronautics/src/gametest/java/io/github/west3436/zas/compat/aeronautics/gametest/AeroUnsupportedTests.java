package io.github.west3436.zas.compat.aeronautics.gametest;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.aeronautics.AeroSettings;
import io.github.west3436.zas.compat.aeronautics.AeronauticsCompat;
import io.github.west3436.zas.compat.aeronautics.AeronauticsVersions;
import io.github.west3436.zas.compat.aeronautics.SableSeed;
import io.github.west3436.zas.compat.aeronautics.mixin.AeronauticsMixinPlugin;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/**
 * With an Aeronautics older than the adapter supports, the adapter stays off (COMPAT-002): the game
 * starts, {@code zas} logged its one warning, the hook into Sable isn't applied, and nothing the
 * adapter adds is there.
 */
@GameTestHolder(AeroUnsupportedTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AeroUnsupportedTests {
    /** The namespace of the tests for an unsupported Aeronautics. */
    public static final String NAMESPACE = "zas_aeronautics_off";

    private AeroUnsupportedTests() {}

    /**
     * The one warning about the version is {@code zas}'s, and its own tests cover its text; this
     * checks what the warning says happened. In a vacuum, Sable's pressure is still its own.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void compat_002_an_unsupported_aeronautics_leaves_the_adapter_off(GameTestHelper h) {
        String installed = AeroGateTests.installed(h, AeronauticsVersions.MOD_ID, "zas.aeronautics.version");
        AeroGateTests.installed(h, AeronauticsVersions.SABLE_MOD_ID, "zas.aeronautics.sableVersion");
        Status status = AeroGateTests.status(h);
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(ZasAdapters.ADAPTERS.isActive(AeronauticsVersions.MOD_ID), "zas says the adapter is on");
        h.assertFalse(AeronauticsCompat.isActive(), "the adapter says it started");

        // Mixin read the config, as Aeronautics and Sable are there, and its plugin kept the hook off.
        h.assertTrue(AeronauticsMixinPlugin.loaded(), "Mixin never loaded the adapter's config");
        h.assertFalse(AeronauticsMixinPlugin.applies(), "the plugin let the hook apply");

        // Nothing the adapter adds is there.
        h.assertFalse(ModConfigs.getFileMap().containsKey(AeroSettings.FILE), "its config file is registered");
        h.assertFalse(
                AmbientSeeds.registered().stream().anyMatch(r -> r.id().equals(SableSeed.ID)),
                "the seed is registered");

        ServerLevel overworld = h.getLevel().getServer().overworld();
        Vector3d point = new Vector3d(0.5, 70, 0.5);
        double own = DimensionPhysicsData.getAirPressure(overworld, point);
        h.assertTrue(own > 0, "Sable gives no pressure in the Overworld");
        Ambients.overrideForTest(Level.OVERWORLD, Ambient.VACUUM);
        h.runAfterDelay(3, () -> {
            try {
                h.assertValueEqual(
                        DimensionPhysicsData.getAirPressure(overworld, point), own, "Sable's pressure in a vacuum");
            } finally {
                Ambients.overrideForTest(Level.OVERWORLD, null);
            }
            h.succeed();
        });
    }
}
