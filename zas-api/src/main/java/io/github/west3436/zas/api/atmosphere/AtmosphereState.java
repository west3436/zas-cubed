package io.github.west3436.zas.api.atmosphere;

/** What kind of gas a queried point holds (API-010, D-43). */
public enum AtmosphereState {
    /** The point is in a zone; the sample is the zone's mixture. */
    ZONED,
    /** The point is open to the dimension's ambient; the sample is the ambient. */
    AMBIENT,
    /**
     * The point is in a paused zone, part of which is in an unloaded chunk; the
     * sample is the zone's last-known mixture (GEO-030).
     */
    PAUSED,
    /**
     * The point has not been classified yet (its chunk is not loaded or not
     * yet resolved); the sample is the ambient, as a stand-in.
     */
    UNRESOLVED
}
