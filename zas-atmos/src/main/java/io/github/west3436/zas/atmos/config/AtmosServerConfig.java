package io.github.west3436.zas.atmos.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The content mod's server config, {@code zas_atmos-server.toml} (per world, like the framework's).
 * Its {@code [power]} section holds the FE values, all starting points for playtesting (D-37,
 * CONT-065).
 */
public final class AtmosServerConfig {
    public static final ModConfigSpec SPEC;

    /** The vent's draw while it runs, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue VENT_DRAW;

    /** The area air controller's own draw, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue CONTROLLER_DRAW;

    /** The area air controller's buffer, in FE (CONT-062). */
    public static final ModConfigSpec.IntValue CONTROLLER_BUFFER;

    /** What the generator makes while it burns, in FE/t (CONT-061). */
    public static final ModConfigSpec.IntValue GENERATOR_OUTPUT;

    /** The generator's own store, in FE. */
    public static final ModConfigSpec.IntValue GENERATOR_BUFFER;

    /** How long one coal or charcoal burns in the generator, in seconds (CONT-066). */
    public static final ModConfigSpec.IntValue GENERATOR_SECONDS_PER_FUEL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Power: what each device draws while it runs, in FE/t (CONT-065), and the power sources.")
                .push("power");
        VENT_DRAW = builder.comment("Vent").defineInRange("vent", 10, 0, 100_000);
        CONTROLLER_DRAW = builder.comment("Area air controller, its own base draw (CONT-065)")
                .defineInRange("area_air_controller", 5, 0, 100_000);
        CONTROLLER_BUFFER = builder.comment(
                        "The area air controller's buffer, in FE (CONT-062). It fills from FE blocks next to it.")
                .defineInRange("area_air_controller_buffer", 10_000, 1_000, 1_000_000);
        GENERATOR_OUTPUT = builder.comment("What the generator makes while it burns, in FE/t (CONT-061)")
                .defineInRange("generator_output", 40, 0, 100_000);
        GENERATOR_BUFFER = builder.comment("The generator's own store, in FE. It stops taking new fuel when full.")
                .defineInRange("generator_buffer", 8_000, 1_000, 30_000);
        GENERATOR_SECONDS_PER_FUEL = builder.comment(
                        "How long one coal or charcoal burns in the generator, in seconds (CONT-066)")
                .defineInRange("generator_seconds_per_fuel", 80, 1, 1_600);
        builder.pop();
        SPEC = builder.build();
    }

    private AtmosServerConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /** A config value, or its default before the config loads. */
    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
