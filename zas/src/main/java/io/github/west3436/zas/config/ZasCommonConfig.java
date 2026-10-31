package io.github.west3436.zas.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The game-wide config, {@code config/zas-common.toml}: settings of the installation rather than
 * of a world.
 */
public final class ZasCommonConfig {
    public static final ModConfigSpec SPEC;

    /** The number of atmos worker threads (architecture §3.1). */
    public static final ModConfigSpec.IntValue WORKER_THREADS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        WORKER_THREADS = builder.comment(
                        "Atmos worker threads (zas-atmos-N). Dimensions simulate in parallel with more",
                        "than one; each dimension uses one at a time. Read when a server starts.")
                .defineInRange("worker_threads", 1, 1, 16);
        SPEC = builder.build();
    }

    private ZasCommonConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** The configured worker count; 1 before the config loads. */
    public static int workerThreads() {
        return SPEC.isLoaded() ? WORKER_THREADS.get() : 1;
    }
}
