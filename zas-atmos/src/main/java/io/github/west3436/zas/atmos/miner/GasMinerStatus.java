package io.github.west3436.zas.atmos.miner;

import java.util.Locale;

/**
 * What a gas miner did at its last atmos tick, for its window (CONT-180, CONT-181): off, filling, or
 * idle and why. Decided on the server each atmos tick and synced by its ordinal.
 */
public enum GasMinerStatus {
    /** Switched off, or held by its redstone setting. */
    OFF,
    /** Added gas to the room in front. */
    FILLING,
    /** The room in front is at or above its target. */
    AT_TARGET,
    /** What it added went to the open air: it waits before it tries again (CONT-181). */
    OPEN_AIR,
    /** Waiting for the cell in front to read as a zone or the ambient, or for a paused zone. */
    WAITING;

    /** The status with an ordinal, or {@link #OFF} for one out of range. */
    public static GasMinerStatus of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }

    /** The window line's translation key. */
    public String key() {
        return "gui.zas_atmos.gas_miner.status." + name().toLowerCase(Locale.ROOT);
    }
}
