package io.github.west3436.zas.engine.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The hotspots an atmos tick left burning, published with its result (FIRE-011, API-055): each
 * hotspot's position and zone, in position order. Pending gas fires, whose block hasn't appeared
 * yet, aren't listed. Immutable, so any thread may read it.
 */
public final class HotspotTable {
    /** No hotspots. */
    public static final HotspotTable EMPTY = new HotspotTable(new long[0], new long[0]);

    private final long[] blocks;
    private final long[] zones;

    /**
     * @param blocks the positions ({@link io.github.west3436.zas.engine.SectionKeys#block}),
     *     ascending and distinct
     * @param zones each position's zone id
     */
    public HotspotTable(long[] blocks, long[] zones) {
        if (blocks.length != zones.length) {
            throw new IllegalArgumentException(blocks.length + " positions, " + zones.length + " zones");
        }
        for (int i = 1; i < blocks.length; i++) {
            if (blocks[i] <= blocks[i - 1]) {
                throw new IllegalArgumentException("positions must ascend");
            }
        }
        this.blocks = blocks.clone();
        this.zones = zones.clone();
    }

    /** How many hotspots there are. */
    public int size() {
        return blocks.length;
    }

    /** The position of hotspot {@code i}. */
    public long block(int i) {
        return blocks[i];
    }

    /** The zone of hotspot {@code i}. */
    public long zone(int i) {
        return zones[i];
    }

    /** The zone of the hotspot at a position, or 0 if there is none. */
    public long zoneAt(long block) {
        int i = Arrays.binarySearch(blocks, block);
        return i >= 0 ? zones[i] : 0;
    }

    /** The positions of a zone's hotspots, ascending. */
    public long[] inZone(long zone) {
        int n = 0;
        for (long z : zones) {
            if (z == zone) {
                n++;
            }
        }
        long[] out = new long[n];
        int k = 0;
        for (int i = 0; i < blocks.length; i++) {
            if (zones[i] == zone) {
                out[k++] = blocks[i];
            }
        }
        return out;
    }

    /** Whether a zone has a hotspot. */
    public boolean burning(long zone) {
        for (long z : zones) {
            if (z == zone) {
                return true;
            }
        }
        return false;
    }

    /**
     * This table with the hotspots of {@code rejected} zones taken from {@code previous}, as the
     * published zone table answers rejected zones from the previous one (§3.3).
     */
    public HotspotTable withRejected(HotspotTable previous, Set<Long> rejected) {
        if (rejected.isEmpty()) {
            return this;
        }
        List<long[]> rows = new ArrayList<>();
        for (int i = 0; i < blocks.length; i++) {
            if (!rejected.contains(zones[i])) {
                rows.add(new long[] {blocks[i], zones[i]});
            }
        }
        for (int i = 0; i < previous.blocks.length; i++) {
            if (rejected.contains(previous.zones[i])) {
                rows.add(new long[] {previous.blocks[i], previous.zones[i]});
            }
        }
        rows.sort((a, b) -> Long.compare(a[0], b[0]));
        long[] b = new long[rows.size()];
        long[] z = new long[rows.size()];
        int n = 0;
        for (long[] r : rows) {
            if (n > 0 && b[n - 1] == r[0]) {
                continue;
            }
            b[n] = r[0];
            z[n++] = r[1];
        }
        return new HotspotTable(Arrays.copyOf(b, n), Arrays.copyOf(z, n));
    }
}
