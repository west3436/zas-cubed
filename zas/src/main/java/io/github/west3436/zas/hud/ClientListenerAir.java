package io.github.west3436.zas.hud;

import io.github.west3436.zas.sound.ThinAir;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The pressure around the local player that the server last sent (DATA-019), for {@link ThinAir}.
 * Until it is told, as on a new client or after leaving a server, it holds {@link ThinAir#UNKNOWN},
 * which counts as normal air.
 *
 * <p>Holds no client-only classes, so the payload registration that names its handler loads on a
 * dedicated server too.
 */
public final class ClientListenerAir {
    private static volatile int kPa = ThinAir.UNKNOWN;

    private ClientListenerAir() {}

    /** The pressure last sent, or {@link ThinAir#UNKNOWN}. */
    public static int kPa() {
        return kPa;
    }

    /** Takes a pressure the server sent. */
    public static void accept(ListenerAirPayload payload) {
        kPa = payload.kPa();
    }

    /** Forgets it, as when leaving a server. */
    public static void clear() {
        kPa = ThinAir.UNKNOWN;
    }

    static void handle(ListenerAirPayload payload, IPayloadContext context) {
        accept(payload);
    }
}
