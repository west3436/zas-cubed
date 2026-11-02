package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.zone.Pins;
import java.util.Arrays;
import java.util.List;

/**
 * One chunk's share of one zone, as saved with the chunk (DATA-002, D-54): the zone's cells in that
 * chunk and the gas those cells hold, the zone's mixture scaled by their share of its volume.
 * Every share of a zone has the zone's pressure, composition and temperature, so any one of them is
 * the zone's last-known state (GEO-035), and a zone rebuilt from its chunks holds the sum of their
 * shares (DATA-008).
 *
 * <p>Immutable. Moles are indexed by the gas registry of the engine that reads or wrote it.
 *
 * @param zoneId the zone's id (CORE-026)
 * @param cells the zone's cells in this chunk, ascending, sub-cells included (D-44)
 * @param moles the share's moles per gas
 * @param temperature the zone's temperature in K
 * @param zoneChunks every chunk the zone spans, this one included, ascending: the zone is complete
 *     once all of them have loaded
 * @param pins the owners of the zone's pins (D-64, API-017, API-018), sorted; every share of a
 *     zone has its pins
 */
public record ZoneShare(
        long zoneId, long[] cells, double[] moles, double temperature, long[] zoneChunks, List<String> pins) {

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if the id isn't positive, an amount or the temperature
     *     isn't finite and non-negative
     */
    public ZoneShare {
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zone id must be positive, got " + zoneId);
        }
        cells = Membership.sortedDistinct(cells);
        moles = moles.clone();
        for (double n : moles) {
            if (!Double.isFinite(n) || n < 0) {
                throw new IllegalArgumentException("moles must be finite and >= 0: " + n);
            }
        }
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
        zoneChunks = Membership.sortedDistinct(zoneChunks);
        pins = Pins.of(pins);
    }

    /** A share of an unpinned zone. */
    public ZoneShare(long zoneId, long[] cells, double[] moles, double temperature, long[] zoneChunks) {
        this(zoneId, cells, moles, temperature, zoneChunks, Pins.NONE);
    }

    /** A share of a zone with the anonymous pin ({@link Pins#ANONYMOUS}) or none. */
    public ZoneShare(long zoneId, long[] cells, double[] moles, double temperature, long[] zoneChunks, boolean pinned) {
        this(zoneId, cells, moles, temperature, zoneChunks, pinned ? Pins.ANONYMOUS_ONLY : Pins.NONE);
    }

    /** Whether the zone is pinned (D-64): it has a pin. */
    public boolean pinned() {
        return !pins.isEmpty();
    }

    /** This share with the given owners' pins taken off, or itself if it has none of them. */
    public ZoneShare withoutPins(java.util.Set<String> owners) {
        List<String> kept = pins;
        for (String o : pins) {
            if (owners.contains(o)) {
                kept = Pins.without(kept, o);
            }
        }
        return kept == pins ? this : new ZoneShare(zoneId, cells, moles, temperature, zoneChunks, kept);
    }

    @Override
    public long[] cells() {
        return cells.clone();
    }

    @Override
    public double[] moles() {
        return moles.clone();
    }

    /** The moles of one gas, without copying. */
    public double moles(int gas) {
        return moles[gas];
    }

    @Override
    public long[] zoneChunks() {
        return zoneChunks.clone();
    }

    /** The number of cells, without copying. */
    public int cellCount() {
        return cells.length;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ZoneShare z
                && zoneId == z.zoneId
                && Arrays.equals(cells, z.cells)
                && Arrays.equals(moles, z.moles)
                && Double.compare(temperature, z.temperature) == 0
                && Arrays.equals(zoneChunks, z.zoneChunks)
                && pins.equals(z.pins);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(zoneId) * 31 + Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        return "ZoneShare#" + zoneId + "[" + cells.length + " cells, " + Arrays.toString(moles) + " mol, " + temperature
                + " K, " + zoneChunks.length + " chunks" + (pinned() ? ", pinned by " + pins : "") + "]";
    }
}
