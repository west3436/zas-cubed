package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The zones of one dimension as of one atmos tick: the immutable table the server thread
 * publishes and any thread reads (architecture §3.1, API-046). The worker builds it; nothing in
 * it changes afterwards.
 *
 * <p>Lookups by zone id and by block position are binary searches over sorted arrays. A block
 * position holding several cells (GEO-004) reports the zone of its largest zoned cell; {@link
 * #zoneAt(long, int)} answers for one sub-cell (API-015), from a side table of the zoned cells
 * at positions that hold less than a full block of gas, the only positions that can hold more
 * than one cell.
 *
 * <p>The table also lists the unzoned cells of regions a held breach left unresolved because they
 * reach unloaded chunks ({@link #heldAt}, GEO-026), so a query there reads as unresolved rather
 * than as the ambient (API-010).
 *
 * <p>When the server thread rejects components of a result (CORE-070), it publishes {@link
 * #withRejected}: the result's table, with the rejected zones answered from the previous table.
 *
 * <p><b>Persistence.</b> A table built by the engine also knows each zone's cells, so the server
 * thread can compute any chunk's {@linkplain #shares share} of the zones in it when the chunk is
 * saved (DATA-002, architecture §4.5). Only published tables are ever saved.
 */
public final class AtmosSnapshot {
    private static final long[] NO_LONGS = new long[0];
    private static final int[] NO_INTS = new int[0];
    private static final long[][] NO_CHUNKS = new long[0][];

    private final long tick;
    private final GasRegistry registry;
    private final ZoneView[] zones; // by id
    private final long[] positions; // sorted block keys
    private final int[] zoneOfPosition; // index into zones
    private final CellTable partialCells; // zoned cells of less than a full block; value: index into zones
    private final CellTable heldCells; // unzoned cells of held breach regions
    // Overlay for rejected components.
    private final long[] rejected; // sorted zone ids
    private final AtmosSnapshot previous;
    // Persistence: each zone's cells, aligned with zones, and the chunks each zone spans.
    private final Membership membership; // null when the table has no cell lists
    private final long[][] zoneChunks;
    private volatile List<ZoneView> zoneList;
    private volatile Map<Long, List<ChunkPart>> chunkIndex;

    /** One zone's cells in one chunk. */
    private record ChunkPart(int zone, long[] cells, double volume) {}

    private AtmosSnapshot(
            long tick,
            GasRegistry registry,
            ZoneView[] zones,
            long[] positions,
            int[] zoneOfPosition,
            CellTable partialCells,
            CellTable heldCells,
            long[] rejected,
            AtmosSnapshot previous,
            Membership membership,
            long[][] zoneChunks) {
        this.tick = tick;
        this.registry = registry;
        this.zones = zones;
        this.positions = positions;
        this.zoneOfPosition = zoneOfPosition;
        this.partialCells = partialCells;
        this.heldCells = heldCells;
        this.rejected = rejected;
        this.previous = previous;
        this.membership = membership;
        this.zoneChunks = zoneChunks;
    }

    /** A table with no zones, before the first atmos tick. */
    public static AtmosSnapshot empty(GasRegistry registry) {
        return new AtmosSnapshot(
                -1,
                registry,
                new ZoneView[0],
                NO_LONGS,
                NO_INTS,
                CellTable.EMPTY,
                CellTable.EMPTY,
                NO_LONGS,
                null,
                null,
                NO_CHUNKS);
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
        return new AtmosSnapshot(
                tick,
                registry,
                z,
                positions.clone(),
                zoneOfPosition.clone(),
                CellTable.EMPTY,
                CellTable.EMPTY,
                NO_LONGS,
                null,
                null,
                new long[z.length][]);
    }

    /**
     * The engine's table: zones, the membership they were built from, and the chunks each zone
     * spans, including chunks it is waiting for (GEO-030).
     */
    static AtmosSnapshot of(
            long tick, GasRegistry registry, List<ZoneView> zones, Membership membership, long[][] zoneChunks) {
        return of(tick, registry, zones, membership, zoneChunks, CellTable.EMPTY, CellTable.EMPTY);
    }

    /**
     * The engine's table, with the zoned cells of positions holding less than a full block of gas
     * ({@code partialCells}, valued by index in {@code zones}) and the unzoned cells of held breach
     * regions ({@code heldCells}).
     */
    static AtmosSnapshot of(
            long tick,
            GasRegistry registry,
            List<ZoneView> zones,
            Membership membership,
            long[][] zoneChunks,
            CellTable partialCells,
            CellTable heldCells) {
        AtmosSnapshot t = of(tick, registry, zones, membership.positions, membership.zoneOfPosition);
        if (membership.size() != t.zones.length || zoneChunks.length != t.zones.length) {
            throw new IllegalArgumentException("membership doesn't match the zones");
        }
        for (int i = 0; i < t.zones.length; i++) {
            if (membership.zoneIds[i] != t.zones[i].id()) {
                throw new IllegalArgumentException("membership doesn't match the zones");
            }
        }
        return new AtmosSnapshot(
                tick,
                registry,
                t.zones,
                t.positions,
                t.zoneOfPosition,
                partialCells,
                heldCells,
                NO_LONGS,
                null,
                membership,
                zoneChunks);
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
        return new AtmosSnapshot(
                tick,
                registry,
                zones,
                positions,
                zoneOfPosition,
                partialCells,
                heldCells,
                sorted,
                previous,
                membership,
                zoneChunks);
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

    /**
     * The zone holding one cell, sub-cell {@code sub} of a block position ({@link
     * SectionKeys#block}), or null if that cell is unzoned (API-015). At a position whose zoned
     * cells all fill the whole block, or with a negative {@code sub}, this is {@link
     * #zoneAt(long)}: such a position holds one cell only.
     */
    public ZoneView zoneAt(long block, int sub) {
        ZoneView own = ownZoneAt(block, sub);
        if (rejected.length == 0) {
            return own;
        }
        if (own != null && !isRejected(own.id())) {
            return own;
        }
        ZoneView before = previous.zoneAt(block, sub);
        return before != null && isRejected(before.id()) ? before : null;
    }

    /**
     * Whether a cell lies in a region left unresolved because it reaches unloaded chunks, where a
     * breach of zones is held (GEO-026): sub-cell {@code sub} of a block position, or with a
     * negative {@code sub} any cell of it. Zoned cells are never listed.
     */
    public boolean heldAt(long block, int sub) {
        return heldCells.contains(block, sub);
    }

    /** The number of unzoned cells listed as held ({@link #heldAt}). */
    public int heldCells() {
        return heldCells.size();
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

    // ---- Persistence (DATA-002) ----------------------------------------------------

    /**
     * The chunks a zone spans, ascending: those its cells are in, and, for a zone rebuilt from
     * saved shares, those it is still waiting for (GEO-030). Empty for an unknown zone, or a table
     * without cell lists.
     */
    public long[] zoneChunks(long id) {
        if (isRejected(id)) {
            return previous.zoneChunks(id);
        }
        int i = ownIndex(id);
        return i < 0 || zoneChunks[i] == null ? NO_LONGS : zoneChunks[i].clone();
    }

    /**
     * A chunk's share of every zone with cells in it (DATA-002): the zone's cells there, and its
     * mixture scaled by their share of its volume. In ascending zone id order; empty if no zone
     * has cells in the chunk. Paused zones are included, with their last-known state.
     */
    public List<ZoneShare> shares(long chunk) {
        List<ZoneShare> out = new ArrayList<>();
        if (membership != null) {
            for (ChunkPart part : index().getOrDefault(chunk, List.of())) {
                ZoneView v = zones[part.zone()];
                if (!isRejected(v.id())) {
                    out.add(share(v, part));
                }
            }
        }
        if (rejected.length > 0) {
            for (ZoneShare share : previous.shares(chunk)) {
                if (isRejected(share.zoneId())) {
                    out.add(share);
                }
            }
            out.sort((a, b) -> Long.compare(a.zoneId(), b.zoneId()));
        }
        return out;
    }

    /** Every chunk some zone of this table spans, ascending. */
    public long[] chunks() {
        java.util.TreeSet<Long> all = new java.util.TreeSet<>();
        for (ZoneView z : zones()) {
            for (long c : zoneChunks(z.id())) {
                all.add(c);
            }
        }
        long[] out = new long[all.size()];
        int i = 0;
        for (long c : all) {
            out[i++] = c;
        }
        return out;
    }

    private ZoneShare share(ZoneView v, ChunkPart part) {
        double total = zoneVolume(part.zone());
        double f = part.volume() >= total ? 1 : part.volume() / total;
        double[] moles = new double[v.gasCount()];
        for (int g = 0; g < moles.length; g++) {
            moles[g] = f == 1 ? v.moles(g) : v.moles(g) * f;
        }
        return new ZoneShare(v.id(), part.cells(), moles, v.temperature(), zoneChunks[part.zone()], v.pinned());
    }

    private double zoneVolume(int zone) {
        double total = 0;
        for (int i = membership.from(zone); i < membership.to(zone); i++) {
            total += membership.volume(i);
        }
        return total;
    }

    private Map<Long, List<ChunkPart>> index() {
        Map<Long, List<ChunkPart>> index = chunkIndex;
        if (index == null) {
            index = new HashMap<>();
            for (int z = 0; z < zones.length; z++) {
                Map<Long, List<Integer>> byChunk = new java.util.TreeMap<>();
                for (int i = membership.from(z); i < membership.to(z); i++) {
                    byChunk.computeIfAbsent(chunkOfCell(i), k -> new ArrayList<>())
                            .add(i);
                }
                for (Map.Entry<Long, List<Integer>> e : byChunk.entrySet()) {
                    List<Integer> flat = e.getValue();
                    long[] cells = new long[flat.size()];
                    double volume = 0;
                    for (int k = 0; k < cells.length; k++) {
                        cells[k] = membership.cell(flat.get(k));
                        volume += membership.volume(flat.get(k));
                    }
                    index.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new ChunkPart(z, cells, volume));
                }
            }
            chunkIndex = index;
        }
        return index;
    }

    private long chunkOfCell(int flat) {
        return SectionKeys.chunkOfBlockKey(membership.position(flat));
    }

    private int ownIndex(long id) {
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
                return mid;
            }
        }
        return -1;
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

    private ZoneView ownZoneAt(long block, int sub) {
        if (sub < 0 || !partialCells.hasBlock(block)) {
            return ownZoneAt(block);
        }
        int z = partialCells.get(block, sub);
        return z >= 0 ? zones[z] : null;
    }

    @Override
    public String toString() {
        return "AtmosSnapshot[tick " + tick + ", " + zoneCount() + " zones" + (hasRejections() ? ", rejections" : "")
                + "]";
    }
}
