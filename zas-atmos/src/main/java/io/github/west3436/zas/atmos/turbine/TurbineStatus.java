package io.github.west3436.zas.atmos.turbine;

import java.util.Locale;

/** What a gas turbine did at its last atmos tick, for its window and a computer (CONT-163). */
public enum TurbineStatus {
    /** Switched off: it lets nothing out and its compressor draws nothing; the shaft runs down. */
    OFF,
    /** It has no compressor paired with it. */
    NO_COMPRESSOR,
    /** Its chamber isn't a sealed room, or its cells can't be read yet. */
    CHAMBER_OPEN,
    /** Its chamber isn't above its exhaust's pressure and its shaft is still. */
    IDLE,
    /** Its shaft turns. */
    RUNNING;

    /** The status with an ordinal, or {@link #OFF} for one out of range. */
    public static TurbineStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** Its window's word's translation key. */
    public String key() {
        return "gui.zas_atmos.turbine.status." + name().toLowerCase(Locale.ROOT);
    }
}
