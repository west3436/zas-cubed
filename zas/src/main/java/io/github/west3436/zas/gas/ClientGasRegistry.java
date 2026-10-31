package io.github.west3436.zas.gas;

import io.github.west3436.zas.engine.gas.GasRegistry;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The gas definitions the client received from the server it is connected to
 * (CORE-014). Kept apart from {@link ServerGasRegistry}, because the
 * integrated server runs in the same JVM.
 *
 * <p>Holds no client-only classes, so it loads on a dedicated server too.
 */
public final class ClientGasRegistry {
    private static volatile GasRegistry current = GasRegistry.EMPTY;

    private ClientGasRegistry() {}

    /** The definitions from the last sync; empty before the first one. */
    public static GasRegistry current() {
        return current;
    }

    /** Replaces the client's definitions with a payload's. */
    public static void accept(GasDefinitionsPayload payload) {
        current = GasRegistry.of(payload.definitions());
    }

    static void handle(GasDefinitionsPayload payload, IPayloadContext context) {
        accept(payload);
    }
}
