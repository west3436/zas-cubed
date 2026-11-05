package io.github.west3436.zas.api.client;

import io.github.west3436.zas.api.AnyThread;
import java.util.Objects;

/**
 * Sound on the client through ZAS³: a factor for thin air (API-060), and looping sounds that ZAS³
 * plays, fades and stops (API-061).
 *
 * <p>Without ZAS³, and on a dedicated server, {@link #thinAir} is 1 and {@link #loop} does nothing,
 * so a mod can call both unconditionally, from code that also loads on a server (D-17).
 */
public final class ClientSounds {
    /**
     * How long a {@linkplain #loop loop request} lasts, in game ticks. A loop that isn't asked for
     * again within this time stops, whatever happened to whoever asked: this is what keeps a
     * forgotten loop from playing forever.
     */
    public static final int LEASE_TICKS = 10;

    /** The most loops a client plays at once. */
    public static final int MAX_LOOPS = 8;

    private static final ClientSoundProvider NONE = new ClientSoundProvider() {};

    private static volatile ClientSoundProvider provider = NONE;

    private ClientSounds() {}

    /**
     * How much of a sound the local player hears for the air around it (CLIENT-016): 1 in normal air,
     * falling to exactly 0 in a vacuum. It follows the real pressure at the player's eyes, so a sealed
     * suit doesn't change it. A client that hasn't been told yet, and the open air of a breathable
     * dimension, read as normal air.
     *
     * <p>{@link #loop} applies it already. Multiply a one-shot sound's volume by it. Leave vanilla's
     * sounds alone: only this mod's sounds fade in thin air.
     *
     * <p>Thread: any.
     *
     * @return the factor, 0 to 1; always 1 without ZAS³ and on a server
     */
    @AnyThread
    public static double thinAir() {
        return provider.thinAir();
    }

    /**
     * Asks for a looping sound, or renews the request (CLIENT-015). ZAS³ plays only the {@value
     * #MAX_LOOPS} loudest requested loops for this client, which among equal loops are the nearest,
     * and only those within their range. It fades them in and out, applies {@link #thinAir}, and
     * stops each one when its request lapses.
     *
     * <p>Call it again at least every {@value #LEASE_TICKS} game ticks for as long as the source wants
     * the sound, and stop calling when it no longer does. Every game tick is fine, and is how a
     * volume or pitch that changes stays up to date. There is nothing to turn off, and a source that
     * forgets stops anyway. Only ask while the source exists: a block's source checks its block is
     * still there, since a chunk that has left the client is the only removal this catches itself.
     *
     * <p>Thread: the client thread. ZAS³ throws {@link IllegalStateException} from any other, such as
     * an integrated server's.
     *
     * @param request the loop
     * @throws NullPointerException if {@code request} is null
     */
    public static void loop(LoopRequest request) {
        provider.loop(Objects.requireNonNull(request, "request"));
    }

    /**
     * Installs the real provider. Only ZAS³ calls this, once, while its client entry point is
     * constructed.
     *
     * <p>Thread: any; in practice the mod-loading thread.
     *
     * @param impl the provider
     * @throws IllegalStateException if a provider is already installed
     */
    @AnyThread
    public static synchronized void install(ClientSoundProvider impl) {
        Objects.requireNonNull(impl, "impl");
        if (provider != NONE) {
            throw new IllegalStateException("a ZAS³ client sound provider is already installed: " + provider);
        }
        provider = impl;
    }
}
