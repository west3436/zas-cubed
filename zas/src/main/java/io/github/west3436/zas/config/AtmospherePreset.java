package io.github.west3436.zas.config;

import java.util.Locale;

/** The global atmosphere difficulty preset (D-14, OPS-007). */
public enum AtmospherePreset {
    NORMAL,
    HARDCORE;

    /** The lower-case name used in datapacks and config, such as {@code "hardcore"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
