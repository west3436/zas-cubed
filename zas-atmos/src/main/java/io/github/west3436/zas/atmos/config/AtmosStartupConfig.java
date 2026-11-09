package io.github.west3436.zas.atmos.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The content mod's startup config, {@code zas_atmos-startup.toml}: whether the content loads at all
 * (CONT-155). FML reads it as soon as it is registered, before anything registers, so it decides what
 * does. It isn't synced, so the server and every client need the same value.
 */
public final class AtmosStartupConfig {
    public static final ModConfigSpec SPEC;

    /** Whether the content loads (CONT-155). */
    public static final ModConfigSpec.BooleanValue CONTENT_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("ZAS³ Atmospherics' content (CONT-155).").push("content");
        CONTENT_ENABLED = builder.comment(
                        "false leaves the atmosphere simulation without the content: no blocks, items, recipes or",
                        "loot from zas_atmos. Read at startup. Set the same value on the server and every client.",
                        "Turning it off in a world removes the content's blocks and items from it.")
                .define("enabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private AtmosStartupConfig() {}

    /** Registers the config with the mod, which loads it at once, and returns whether the content loads. */
    public static boolean register(ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, SPEC);
        return CONTENT_ENABLED.get();
    }
}
