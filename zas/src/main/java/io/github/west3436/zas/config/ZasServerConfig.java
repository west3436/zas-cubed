package io.github.west3436.zas.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The per-world server config, {@code <world>/serverconfig/zas-server.toml} (OPS-001).
 *
 * <p>For now it holds only what R0 needs: the atmosphere preset, which selects ambient variants
 * (ENV-010), and the dimensions excluded from simulation (ENV-007). The full preset system,
 * with every preset-controlled option and per-option overrides, is OPS-007 and OPS-014 in R1.
 */
public final class ZasServerConfig {
    public static final ModConfigSpec SPEC;

    /** The atmosphere difficulty preset (D-14). */
    public static final ModConfigSpec.EnumValue<AtmospherePreset> PRESET;

    /** Dimension ids excluded from simulation (ENV-007), in addition to those datapacks exclude. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_DIMENSIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PRESET = builder.comment(
                        "Atmosphere difficulty preset: NORMAL or HARDCORE.",
                        "It selects the preset variant of each dimension's ambient atmosphere.")
                .defineEnum("preset", AtmospherePreset.NORMAL);
        EXCLUDED_DIMENSIONS = builder.comment(
                        "Dimension ids excluded from simulation, such as \"minecraft:the_end\".",
                        "No zones form there; queries still return the dimension's ambient.")
                .defineListAllowEmpty(
                        "excluded_dimensions",
                        List.of(),
                        () -> "",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        SPEC = builder.build();
    }

    private ZasServerConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /** The active preset; {@link AtmospherePreset#NORMAL} while no world's config is loaded. */
    public static AtmospherePreset preset() {
        return SPEC.isLoaded() ? PRESET.get() : AtmospherePreset.NORMAL;
    }

    /** Whether the config excludes a dimension; false while no world's config is loaded. */
    public static boolean excludes(ResourceLocation dimension) {
        return SPEC.isLoaded() && EXCLUDED_DIMENSIONS.get().contains(dimension.toString());
    }
}
