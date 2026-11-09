package io.github.west3436.zas.reaction;

import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import java.util.List;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The reactions the client received from the server it is connected to (COMPAT-060), for recipe
 * viewers. Kept apart from {@link ServerReactions}, because the integrated server runs in the same
 * JVM. A code type's definition here has {@link ReactionCodecs#NOT_RUN} as its implementation.
 *
 * <p>Holds no client-only classes, so it loads on a dedicated server too.
 */
public final class ClientReactions {
    private static volatile List<ReactionDefinition> current = List.of();

    private ClientReactions() {}

    /** The definitions from the last sync; empty before the first one. Any thread. */
    public static List<ReactionDefinition> current() {
        return current;
    }

    /** Replaces the client's definitions with a payload's. */
    public static void accept(ReactionDefinitionsPayload payload) {
        current = payload.definitions();
    }

    static void handle(ReactionDefinitionsPayload payload, IPayloadContext context) {
        accept(payload);
    }
}
