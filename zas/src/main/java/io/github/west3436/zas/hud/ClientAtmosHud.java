package io.github.west3436.zas.hud;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The HUD state the client last received (CLIENT-008), for the renderer in {@code
 * io.github.west3436.zas.client} to draw.
 *
 * <p>Holds no client-only classes, so the payload registration that names its handler loads on a
 * dedicated server too.
 */
public final class ClientAtmosHud {
    private static volatile AtmosHudPayload current = AtmosHudPayload.NONE;

    private ClientAtmosHud() {}

    /** The state to draw; {@link AtmosHudPayload#NONE} when nothing is shown. */
    public static AtmosHudPayload current() {
        return current;
    }

    /** Replaces the state. */
    public static void accept(AtmosHudPayload payload) {
        current = payload;
    }

    /** Hides the HUD, as when leaving a server. */
    public static void clear() {
        current = AtmosHudPayload.NONE;
    }

    static void handle(AtmosHudPayload payload, IPayloadContext context) {
        accept(payload);
    }
}
