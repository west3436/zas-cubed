package io.github.west3436.zas.api.client;

/**
 * The service behind {@link ClientSounds}. ZAS³ installs its implementation on the physical client
 * with {@link ClientSounds#install}; until then, on a dedicated server, and when ZAS³ is absent, the
 * defaults here answer: every listener hears normally, and loops play nothing.
 *
 * <p><b>Consumers don't call or implement this.</b> They use {@link ClientSounds}. Every method has
 * a default, so that a provider built against an older API keeps working when a newer one is loaded
 * (architecture §6.2).
 */
public interface ClientSoundProvider {
    /**
     * The volume factor for a listener where it is now; see {@link ClientSounds#thinAir}.
     *
     * @return 0 to 1
     */
    default double thinAir() {
        return 1;
    }

    /**
     * Asks for a looping sound, or renews the request; see {@link ClientSounds#loop}.
     *
     * @param request the loop
     */
    default void loop(LoopRequest request) {}
}
