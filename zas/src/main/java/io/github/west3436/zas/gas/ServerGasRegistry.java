package io.github.west3436.zas.gas;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.engine.gas.GasRegistry;
import org.slf4j.Logger;

/**
 * The gas definitions and gas tags of the running server (CORE-010, CORE-012).
 *
 * <p>The set is fixed when the server starts: zones store moles by gas index,
 * so the indices must not move under them. A {@code /reload} that changes the
 * definitions is logged and takes effect at the next start, as with
 * Minecraft's own datapack registries. Gas tags are resolved against the
 * definitions loaded with them, so they follow the same rule: one set of gases
 * and tags for the whole run, the same on every client.
 */
public final class ServerGasRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The most recent load, which may belong to a world that hasn't started yet. */
    private static volatile GasRegistry loaded = GasRegistry.EMPTY;
    /** The running server's definitions, or null between servers. */
    private static volatile GasRegistry active;

    private ServerGasRegistry() {}

    /** The running server's gas definitions and tags; empty when no server runs. */
    public static GasRegistry current() {
        GasRegistry registry = active;
        return registry != null ? registry : GasRegistry.EMPTY;
    }

    /** Whether a server is running in this JVM, so {@link #current} is its gas set. */
    public static boolean running() {
        return active != null;
    }

    static void loaded(GasRegistry registry) {
        loaded = registry;
        GasRegistry running = active;
        if (running != null && !running.equals(registry)) {
            LOGGER.warn(
                    "Gas definitions or tags changed on reload ({} -> {}); the change takes effect when the "
                            + "server restarts",
                    running,
                    registry);
        }
    }

    static void serverStarting() {
        active = loaded;
    }

    static void serverStopped() {
        active = null;
    }
}
