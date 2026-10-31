package io.github.west3436.zas.api;

import java.util.Objects;

/**
 * The entry point of the ZAS³ API.
 *
 * <p>It holds the active {@link ZasApiProvider}. Until ZAS³ installs its own,
 * and always when ZAS³ is not installed, a built-in fallback answers every call
 * with a vanilla, breathable world: standard air everywhere, no zones, and
 * mutations that move nothing (D-17). A mod that bundles the API can therefore
 * call it unconditionally.
 *
 * <p>ZAS³ installs its provider while it is constructed. Call the API from
 * common setup or later; to use it during your own mod's construction, declare
 * an optional dependency on {@code zas} with {@code ordering = "AFTER"}.
 *
 * <p>The queries are in {@link io.github.west3436.zas.api.atmosphere.Atmospheres}
 * and the mutations in {@link io.github.west3436.zas.api.mutation.GasMutations}.
 */
public final class ZasApi {
    private static volatile ZasApiProvider provider = FallbackProvider.INSTANCE;

    private ZasApi() {}

    /**
     * Whether ZAS³ is installed and has provided a real implementation
     * (API-040). Use it to turn atmosphere features, such as a HUD, on or off.
     *
     * <p>Thread: any.
     *
     * @return true if ZAS³'s provider is installed
     */
    @AnyThread
    public static boolean isPresent() {
        return provider.isReal();
    }

    /**
     * The active provider: ZAS³'s, or the fallback.
     *
     * <p>Thread: any.
     *
     * @return the provider, never null
     */
    @AnyThread
    public static ZasApiProvider provider() {
        return provider;
    }

    /**
     * Installs the real provider. Only ZAS³ calls this, once, while it is
     * constructed.
     *
     * <p>Thread: any; in practice the mod-loading thread.
     *
     * @param impl the provider
     * @throws IllegalStateException if a provider is already installed
     * @throws IllegalArgumentException if {@code impl} is not a real provider
     */
    @AnyThread
    public static synchronized void install(ZasApiProvider impl) {
        Objects.requireNonNull(impl, "impl");
        if (!impl.isReal()) {
            throw new IllegalArgumentException("only a real provider can be installed");
        }
        if (provider != FallbackProvider.INSTANCE) {
            throw new IllegalStateException("a ZAS³ API provider is already installed: " + provider);
        }
        provider = impl;
    }
}
