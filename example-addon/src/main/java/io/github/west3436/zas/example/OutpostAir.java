package io.github.west3436.zas.example;

import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The open air of the addon's dimension, {@code zas_example:outpost}, from code (ENV-004): thin, with
 * a temperature that follows the time of day, which a data file couldn't do. A datapack's ambient file
 * for the dimension would win over it (ENV-014). Without ZAS³ it's kept and never called, and the
 * dimension reads as standard air. Go there with {@code /execute in zas_example:outpost run tp ~ 2 ~}.
 */
public final class OutpostAir {
    /** The dimension, which {@code data/zas_example/dimension/outpost.json} defines. */
    public static final ResourceKey<Level> OUTPOST = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(ExampleAddonMod.MOD_ID, "outpost"));

    /** The provider's id. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ExampleAddonMod.MOD_ID, "outpost");

    private OutpostAir() {}

    static void register() {
        AmbientProviders.register(ID, OUTPOST, OutpostAir::ambient);
    }

    /** Once per atmos tick of the dimension, on the server thread (ENV-015), so it's kept cheap. */
    private static AmbientDefinition ambient(ServerLevel level) {
        double temperature = 210 + 20 * Math.sin(level.getTimeOfDay(1) * 2 * Math.PI);
        return new AmbientDefinition(Map.of(StandardAir.NITROGEN, 14.0, StandardAir.OXYGEN, 6.0), temperature);
    }
}
