package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The zones of one dimension as of one atmos tick: the immutable table the server thread
 * publishes and any thread reads (architecture §3.1, API-046). The worker builds it; nothing in
 * it changes afterwards.
 *
 * <p>Lookups by zone id and by block position are binary searches over sorted arrays. A block
 * position holding several cells (GEO-004) reports the zone of its largest cell (API-015).
 *
 * <p>When the server thread rejects components of a result (CORE-070), it publishes {@link
 * #withRejected}: the result's table, with the rejected zones answered from the previous table.
 */
public final class AtmosSnapshot {
    private static final long[] NO_LONGS = new long[0];
    private static final int[] NO_INTS = new int[0];

    private final long tick;
    private final GasRegistry registry;
    private final ZoneView[] zones; // by id
    private final long[] positions; // sorted block keys
    private final int[] zoneOfPosition; // index into zones
    // Overlay for rejected components.
    private final long[] rejected; // sorted zone ids
    private final AtmosSnapshot previous;
    private volatile List<ZoneView> zoneList;

    private AtmosSnapshot(
            long tick,
            GasRegistry registry,
            ZoneView[] zones,
            long[] positions,
            int[] zoneOfPosition,
            long[] rejected,
            AtmosSnapshot previous) {
        this.tick = tick;
        this.registry = registry;
        this.zones = zones;
        this.positions = positions;
        this.zoneOfPosition = zoneOfPosition;
        this.rejected = rejected;
        this.previous = previous;
    }

    /** A table with no zones, before the first atmos tick. */
    public static AtmosSnapshot empty(GasRegistry registry) {
        return new AtmosSnapshot(-1, registry, new ZoneView[0], NO_LONGS, NO_INTS, NO_LONGS, null);
    }

    /**
     * A table from zones and a membership index.
     *
     * @param zones the zones in ascending id order
     * @param positions block keys ({@link SectionKeys#block}), ascending, without duplicates
     * @param zoneOfPosition per position, the index in {@code zones} of its zone
     * @throws IllegalArgumentException if the arrays are out of order or don't match
     */
    public static AtmosSnapshot of(
            long tick, GasRegistry registry, List<ZoneView> zones, long[] positions, int[] zoneOfPosition) {
        Objects.requireNonNull(registry, "registry");
        ZoneView[] z = zones.toArray(ZoneView[]::new);
        for (int i = 1; i < z.length; i++) {
            if (z[i].id() <= z[i - 1].id()) {
                throw new IllegalArgumentException("zones must be in ascending id order");
            }
        }
        if (positions.length != zoneOfPosition.length) {
            throw new IllegalArgumentException("positions and zones differ in length");
        }
        for (int i = 0; i < positions.length; i++) {
            if (i > 0 && positions[i] <= positions[i - 1]) {
                throw new IllegalArgumentException("positions must be ascending without duplicates");
            }
            if (zoneOfPosition[i] < 0 || zoneOfPosition[i] >= z.length) {
                throw new IllegalArgumentException("zone index out of range: " + zoneOfPosition[i]);
            }
        }
        return new AtmosSnapshot(tick, registry, z, positions.clone(), zoneOfPosition.clone(), NO_LONGS, null);
    }

    /**
     * This table with the zones of rejected components answered from {@code previous} (CORE-070):
     * a zone id in {@code rejectedZones} and every position whose zone here, or in {@code
     * previous}, is one of them read as {@code previous} has them.
     */
    public AtmosSnapshot withRejected(AtmosSnapshot previous, long[] rejectedZones) {
        Objects.requireNonNull(previous, "previous");
        if (rejectedZones.length == 0) {
            return this;
        }
        long[] sorted = rejectedZones.clone();
        Arrays.sort(sorted);
        return new AtmosSnapshot(tick, registry, zones, positions, zoneOfPosition, sorted, previous);
    }

    /** The atmos tick this table is from; -1 before the first. */
    public long tick() {
        return tick;
    }

    /** The gas registry the zones' moles are indexed by. */
    public GasRegistry registry() {
        return registry;
    }

    /** Whether some zones are answered from the previous table. */
    public boolean hasRejections() {
        return rejected.length > 0;
    }

    /** The zone with this id, or null. */
    public ZoneView zone(long id) {
        if (isRejected(id)) {
            return previous.zone(id);
        }
        return ownZone(id);
    }

    /** The zone holding the largest cell at a block position ({@link SectionKeys#block}), or null. */
    public ZoneView zoneAt(long block) {
        ZoneView own = ownZoneAt(block);
        if (rejected.length == 0) {
            return own;
        }
        if (own != null && !isRejected(own.id())) {
            return own;
        }
        ZoneView before = previous.zoneAt(block);
        return before != null && isRejected(before.id()) ? before : null;
    }

    /** The zone at block {@code (x, y, z)}, or null. */
    public ZoneView zoneAt(int x, int y, int z) {
        return zoneAt(SectionKeys.block(x, y, z));
    }

    /** Every zone, in ascending id order. */
    public List<ZoneView> zones() {
        List<ZoneView> list = zoneList;
        if (list == null) {
            List<ZoneView> out = new ArrayList<>(zones.length);
            if (rejected.length == 0) {
                out.addAll(Arrays.asList(zones));
            } else {
                for (ZoneView z : zones) {
                    if (!isRejected(z.id())) {
                        out.add(z);
                    }
                }
                for (long id : rejected) {
                    ZoneView before = previous.zone(id);
                    if (before != null) {
                        out.add(before);
                    }
                }
                out.sort((a, b) -> Long.compare(a.id(), b.id()));
            }
            list = Collections.unmodifiableList(out);
            zoneList = list;
        }
        return list;
    }

    /** The number of zones. */
    public int zoneCount() {
        return zones().size();
    }

    /** The number of block positions holding zoned cells in this tick's own table. */
    public int zonedPositions() {
        return positions.length;
    }

    private boolean isRejected(long id) {
        return rejected.length > 0 && Arrays.binarySearch(rejected, id) >= 0;
    }

    private ZoneView ownZone(long id) {
        int lo = 0;
        int hi = zones.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long v = zones[mid].id();
            if (v < id) {
                lo = mid + 1;
            } else if (v > id) {
                hi = mid - 1;
            } else {
                return zones[mid];
            }
        }
        return null;
    }

    private ZoneView ownZoneAt(long block) {
        int i = Arrays.binarySearch(positions, block);
        return i >= 0 ? zones[zoneOfPosition[i]] : null;
    }

    @Override
    public String toString() {
        return "AtmosSnapshot[tick " + tick + ", " + zoneCount() + " zones" + (hasRejections() ? ", rejections" : "")
                + "]";
    }
}
