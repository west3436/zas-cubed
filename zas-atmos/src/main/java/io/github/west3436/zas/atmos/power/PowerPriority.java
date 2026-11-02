package io.github.west3436.zas.atmos.power;

/**
 * Who a provider powers first when it can't power everyone (CONT-064): life support, then gas
 * handling, then the rest. Declared in priority order, so {@link #compareTo} sorts the first group
 * first.
 */
public enum PowerPriority {
    /** Vents and scrubbers. */
    LIFE_SUPPORT,
    /** Pumps, filters and mixers. */
    GAS_HANDLING,
    /** Heaters, freezers, igniters, rechargers and gas production. */
    AUXILIARY
}
