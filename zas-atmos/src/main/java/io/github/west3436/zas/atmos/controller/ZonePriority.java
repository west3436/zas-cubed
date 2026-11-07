package io.github.west3436.zas.atmos.controller;

/**
 * Which area air controllers a central atmospherics controller pays first when it can't pay them all
 * (CONT-121): Critical, then Normal, then Low. Declared in that order, so {@link #compareTo} sorts
 * Critical first. Each area air controller then powers its own parts by {@link
 * io.github.west3436.zas.atmos.power.PowerPriority} (CONT-064).
 */
public enum ZonePriority {
    CRITICAL("critical"),
    NORMAL("normal"),
    LOW("low");

    private final String key;

    ZonePriority(String key) {
        this.key = key;
    }

    /** Its name in saves and translation keys. */
    public String key() {
        return key;
    }

    /** The priority with a key; Normal, the default, for any other. */
    public static ZonePriority byKey(String key) {
        for (ZonePriority p : values()) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        return NORMAL;
    }
}
