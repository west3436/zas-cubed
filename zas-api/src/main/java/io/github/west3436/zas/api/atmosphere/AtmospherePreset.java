package io.github.west3436.zas.api.atmosphere;

/**
 * The server's atmosphere difficulty preset (OPS-007, D-14): {@link Atmospheres#preset()}. The
 * preset sets the defaults of a group of ZAS³'s options, each of which a server can still set by
 * itself.
 *
 * <p>Later minor versions may add presets. Treat one you don't know as {@link #NORMAL}.
 */
public enum AtmospherePreset {
    /** The default: the gentler rules. */
    NORMAL,
    /** The harsher rules. */
    HARDCORE
}
