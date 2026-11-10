package io.github.west3436.zas.compat.adastra;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The adapter's own settings, in {@code zas-adastra-server.toml}: a server config, so each world
 * can have its own and a client gets the server's. Every setting is read when it is used, so an
 * edit applies without a restart.
 */
public final class AdAstraConfig {
    /** What the {@code mode} setting can say (SPACE-001, SPACE-004). */
    public enum ModeSetting {
        /** Cooperative under the Normal preset, Authoritative under Hardcore. */
        PRESET,
        /** Ad Astra's oxygenated areas count as breathable. */
        COOPERATIVE,
        /** Only ZAS³'s atmosphere counts. */
        AUTHORITATIVE
    }

    /** The mode, or that it follows the preset. */
    public static final ModConfigSpec.EnumValue<ModeSetting> MODE;

    /** The whole config. */
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MODE = builder.comment(
                        "COOPERATIVE: air from Ad Astra's oxygen distributors is breathable.",
                        "AUTHORITATIVE: only ZAS atmosphere is.",
                        "PRESET: cooperative under the Normal preset, authoritative under Hardcore.")
                .defineEnum("mode", ModeSetting.PRESET);
        SPEC = builder.build();
    }

    private AdAstraConfig() {}

    /** Registers the config with {@code zas}'s container, which the adapter is part of. */
    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "zas-adastra-server.toml");
    }
}
