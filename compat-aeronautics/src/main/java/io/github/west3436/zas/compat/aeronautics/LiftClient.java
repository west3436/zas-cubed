package io.github.west3436.zas.compat.aeronautics;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client's part of the adapter: it forgets the server's lift factors when the client leaves a
 * server, so a server without the adapter isn't read with another's. Loaded only on a client, as
 * the event it listens to is a client's.
 */
final class LiftClient {
    private LiftClient() {}

    /** Registers the listener. Called once, from {@link AeronauticsCompat#init}, on a client. */
    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> LiftFactors.clientLeft());
    }
}
