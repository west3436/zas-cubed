package io.github.west3436.zas.debug;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The debug overlay data the client last received (CLIENT-005), for the renderer in {@code
 * io.github.west3436.zas.client} to draw.
 *
 * <p>Holds no client-only classes, so the payload registration that names its handler loads on a
 * dedicated server too.
 */
public final class ClientDebugOverlay {
    private static volatile DebugOverlayPayload current;

    private ClientDebugOverlay() {}

    /** The data to draw, or null while the overlay is off. */
    public static DebugOverlayPayload current() {
        return current;
    }

    /** Replaces the data; a payload with no layers switches the overlay off. */
    public static void accept(DebugOverlayPayload payload) {
        current = payload.layers() == 0 ? null : payload;
    }

    /** Switches the overlay off, as when leaving a server. */
    public static void clear() {
        current = null;
    }

    static void handle(DebugOverlayPayload payload, IPayloadContext context) {
        accept(payload);
    }
}
