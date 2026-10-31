package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.classify.EnclosureFill;
import io.github.west3436.zas.engine.classify.FillResult;
import io.github.west3436.zas.engine.classify.SectionTopology;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The geometry audit (GEO-032, OPS-010): checks, for a region, that the engine's view of the world
 * matches the blocks actually there, and reports every difference.
 *
 * <p>The server thread captures the region's loaded sections fresh; the audit runs on the thread
 * that owns the engine, between ticks ({@link DimensionScheduler#inspect}), and compares:
 *
 * <ul>
 *   <li><b>The mirror against the blocks.</b> A mirrored position whose geometry differs from the
 *       fresh capture is {@link Kind#STALE_GEOMETRY}: a block change the engine never saw.
 *   <li><b>Zone membership against the blocks.</b> A zoned cell the blocks no longer have, such as
 *       one now filled by stone, is {@link Kind#ZONED_NOT_A_CELL}.
 *   <li><b>Connections against the blocks.</b> A zoned cell open to an unzoned cell while its zone
 *       has no ambient edge is {@link Kind#UNRECORDED_OPENING}; open to another zone's cell with no
 *       edge between the zones, {@link Kind#MISSING_EDGE}.
 *   <li><b>Membership against a fresh fill.</b> For each zone with cells in the region, an {@link
 *       EnclosureFill} from one of its cells over the fresh blocks, with other zones' cells as
 *       walls, should find exactly the zone. A sealed zone whose fill reaches the open air, or a
 *       section neither view holds (its own and bordering sections always are), is
 *       {@link Kind#LEAKS_TO_OPEN}; unzoned cells the fill encloses with it are {@link
 *       Kind#UNZONED_IN_ENCLOSURE}; zone cells the fill can't reach are {@link
 *       Kind#UNREACHED_ZONE_CELL}; a zone with an ambient edge whose fill is enclosed by its own
 *       cells is {@link Kind#STALE_AMBIENT_EDGE}.
 * </ul>
 *
 * <p>Paused zones (GEO-030) are left out: block changes that touch them are held until they resume
 * (GEO-037), so the mirror keeps the old geometry there on purpose. Held positions are counted, not
 * reported as {@link Kind#STALE_GEOMETRY}, and a paused zone's cells aren't checked; the report
 * counts both.
 *
 * <p>Fresh sections stand in for mirrored ones inside the region; outside it the audit reads the
 * mirror, so a fill that leaves the region sees what the engine sees. A fill that reaches a
 * section neither holds is unresolved and the zone is counted, not reported.
 *
 * <p>Read only: it never changes the engine. Deterministic: zones in id order, cells in ascending
 * order, findings sorted by kind and position.
 */
public final class GeometryAudit {
    /** What kind of difference a finding is. */
    public enum Kind {
        /** The mirror holds a different geometry than the block there now. */
        STALE_GEOMETRY,
        /** A zone holds a cell the blocks no longer have. */
        ZONED_NOT_A_CELL,
        /** A zoned cell is open to an unzoned cell, but its zone has no ambient edge. */
        UNRECORDED_OPENING,
        /** Cells of two zones are open to each other, but the zones have no edge. */
        MISSING_EDGE,
        /**
         * A zone without an ambient edge isn't enclosed: a fresh fill from it reaches the open air,
         * or sections beyond those bordering the zone.
         */
        LEAKS_TO_OPEN,
        /** An unzoned cell is enclosed together with a zone. */
        UNZONED_IN_ENCLOSURE,
        /** A zone's cell can't be reached from the rest of the zone. */
        UNREACHED_ZONE_CELL,
        /** A zone has an ambient edge, but is enclosed by its own cells. */
        STALE_AMBIENT_EDGE
    }

    /**
     * One difference.
     *
     * @param kind what differs
     * @param block the block position ({@link SectionKeys#block})
     * @param zoneId the zone involved, or 0
     */
    public record Finding(Kind kind, long block, long zoneId) {
        public int x() {
            return SectionKeys.blockX(block);
        }

        public int y() {
            return SectionKeys.blockY(block);
        }

        public int z() {
            return SectionKeys.blockZ(block);
        }
    }

    /**
     * A box of block positions, corners inclusive.
     */
    public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Region {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("region corners out of order");
            }
        }

        /** The box spanning two corners in any order. */
        public static Region spanning(int x0, int y0, int z0, int x1, int y1, int z1) {
            return new Region(
                    Math.min(x0, x1),
                    Math.min(y0, y1),
                    Math.min(z0, z1),
                    Math.max(x0, x1),
                    Math.max(y0, y1),
                    Math.max(z0, z1));
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** The keys of the sections the box touches, in {@link SectionKeys#compare} order. */
        public long[] sections() {
            List<Long> keys = new ArrayList<>();
            for (int sx = minX >> 4; sx <= maxX >> 4; sx++) {
                for (int sz = minZ >> 4; sz <= maxZ >> 4; sz++) {
                    for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                        keys.add(SectionKeys.section(sx, sy, sz));
                    }
                }
            }
            long[] out = new long[keys.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = keys.get(i);
            }
            return out;
        }

        /** How many sections the box touches. */
        public long sectionCount() {
            return (long) ((maxX >> 4) - (minX >> 4) + 1)
                    * ((maxY >> 4) - (minY >> 4) + 1)
                    * ((maxZ >> 4) - (minZ >> 4) + 1);
        }
    }

    /**
     * What an audit found.
     *
     * @param sectionsChecked fresh sections compared
     * @param sectionsUnmirrored of those, how many the mirror doesn't hold (nothing to compare; the
     *     zone checks still read them)
     * @param zonesChecked zones with cells in the region
     * @param zonesUnresolved zones whose fresh fill reached a section neither view holds
     * @param zonesPaused paused zones with cells in the region, not checked (GEO-030)
     * @param heldPositions positions in the region whose block changes are held for paused zones
     *     (GEO-037): the mirror differs from the blocks there on purpose, so they aren't findings
     * @param counts findings per {@link Kind}, by ordinal
     * @param findings the first findings, sorted by kind and position
     * @param staleSections mirrored sections with {@link Kind#STALE_GEOMETRY}, which a repair sends
     *     to the engine again, sorted
     */
    public record Report(
            int sectionsChecked,
            int sectionsUnmirrored,
            int zonesChecked,
            int zonesUnresolved,
            int zonesPaused,
            int heldPositions,
            int[] counts,
            List<Finding> findings,
            long[] staleSections) {

        public Report {
            counts = counts.clone();
            findings = List.copyOf(findings);
            staleSections = staleSections.clone();
        }

        @Override
        public int[] counts() {
            return counts.clone();
        }

        @Override
        public long[] staleSections() {
            return staleSections.clone();
        }

        /** The number of findings of a kind. */
        public int count(Kind kind) {
            return counts[kind.ordinal()];
        }

        /** The number of findings. */
        public int total() {
            int t = 0;
            for (int c : counts) {
                t += c;
            }
            return t;
        }

        /** Whether the engine matches the blocks everywhere it could check. */
        public boolean clean() {
            return total() == 0;
        }
    }

    private GeometryAudit() {}

    /**
     * Audits a region. Call on the thread that owns the engine, between ticks.
     *
     * @param engine the engine
     * @param region the region
     * @param fresh fresh snapshots of the region's loaded sections, using the engine's palette
     * @param maxFindings how many findings to list; all are counted
     */
    public static Report run(
            DimensionEngine engine, Region region, Collection<SectionSnapshot> fresh, int maxFindings) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(region, "region");
        SectionMirror mirror = engine.mirror();
        ZoneGraph graph = engine.graph();
        Map<Long, SectionSnapshot> live = new HashMap<>();
        for (SectionSnapshot s : fresh) {
            live.put(s.key(), s);
        }
        SectionTopology topology = new SectionTopology(
                engine.palette(),
                key -> {
                    SectionSnapshot s = live.get(key);
                    return s != null ? s : mirror.section(key);
                },
                mirror.minSectionY() * 16,
                mirror.maxSectionY() * 16 + 15);

        Collector out = new Collector(maxFindings);

        // The mirror against the blocks, except where changes are held for paused zones.
        long[] held = engine.heldBlocks();
        int heldPositions = 0;
        long[] keys = live.keySet().stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(keys);
        int unmirrored = 0;
        List<Long> stale = new ArrayList<>();
        for (long key : keys) {
            SectionSnapshot now = live.get(key);
            SectionSnapshot old = mirror.section(key);
            if (old == null) {
                unmirrored++;
                continue;
            }
            int bx = SectionKeys.x(key) << 4;
            int by = SectionKeys.y(key) << 4;
            int bz = SectionKeys.z(key) << 4;
            boolean differs = false;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!region.contains(bx + x, by + y, bz + z)) {
                            continue;
                        }
                        int index = SectionKeys.index(x, y, z);
                        if (old.geometryId(index) != now.geometryId(index)) {
                            long block = SectionKeys.block(bx + x, by + y, bz + z);
                            if (Arrays.binarySearch(held, block) >= 0) {
                                heldPositions++;
                                continue;
                            }
                            out.add(Kind.STALE_GEOMETRY, block, 0);
                            differs = true;
                        }
                    }
                }
            }
            if (differs) {
                stale.add(key);
            }
        }

        // Membership and connections against the blocks, zone by zone.
        EnclosureFill fill = new EnclosureFill();
        int zonesChecked = 0;
        int unresolved = 0;
        int paused = 0;
        for (Zone zone : sortedZones(graph)) {
            long[] cells = zone.cells();
            Arrays.sort(cells);
            if (zone.isPaused()) {
                // Its mirror is behind the blocks on purpose until it resumes (GEO-037).
                for (long cell : cells) {
                    if (region.contains(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell))) {
                        paused++;
                        break;
                    }
                }
                continue;
            }
            long seed = -1;
            boolean inRegion = false;
            for (long cell : cells) {
                if (!region.contains(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell))) {
                    continue;
                }
                inRegion = true;
                if (topology.volume(cell) <= 0) {
                    out.add(Kind.ZONED_NOT_A_CELL, blockOf(cell), zone.id());
                    continue;
                }
                if (seed < 0) {
                    seed = cell;
                }
                topology.forEachOpenNeighbour(cell, (neighbour, direct) -> {
                    Zone other = graph.zoneOf(neighbour);
                    if (other == null) {
                        if (zone.ambientEdge() == null) {
                            out.add(Kind.UNRECORDED_OPENING, blockOf(neighbour), zone.id());
                        }
                    } else if (other != zone && zone.edgeTo(other) == null) {
                        out.add(Kind.MISSING_EDGE, blockOf(neighbour), zone.id());
                    }
                });
            }
            if (!inRegion) {
                continue;
            }
            zonesChecked++;
            if (seed < 0) {
                continue; // every cell of it in the region is gone; reported above
            }
            FillResult result = fill.fill(topology, seed, c -> {
                Zone z = graph.zoneOf(c);
                return z != null && z != zone;
            });
            switch (result) {
                case FillResult.Open open -> {
                    if (zone.ambientEdge() == null) {
                        out.add(Kind.LEAKS_TO_OPEN, blockOf(seed), zone.id());
                    }
                }
                case FillResult.Unresolved u -> {
                    // A sealed zone's own sections and their neighbours are always mirrored, so a
                    // fill that reaches a section neither view holds has left the zone.
                    if (zone.ambientEdge() == null) {
                        out.add(Kind.LEAKS_TO_OPEN, blockOf(seed), zone.id());
                    } else {
                        unresolved++;
                    }
                }
                case FillResult.Enclosed enclosed -> {
                    boolean onlyOwn = true;
                    for (long c : enclosed.cells()) {
                        if (Arrays.binarySearch(cells, c) < 0) {
                            onlyOwn = false;
                            out.add(Kind.UNZONED_IN_ENCLOSURE, blockOf(c), zone.id());
                        }
                    }
                    for (long c : cells) {
                        if (!enclosed.contains(c) && topology.volume(c) > 0) {
                            out.add(Kind.UNREACHED_ZONE_CELL, blockOf(c), zone.id());
                        }
                    }
                    if (onlyOwn && zone.ambientEdge() != null) {
                        out.add(Kind.STALE_AMBIENT_EDGE, blockOf(seed), zone.id());
                    }
                }
            }
        }
        long[] staleSections = new long[stale.size()];
        for (int i = 0; i < staleSections.length; i++) {
            staleSections[i] = stale.get(i);
        }
        Arrays.sort(staleSections);
        return new Report(
                keys.length,
                unmirrored,
                zonesChecked,
                unresolved,
                paused,
                heldPositions,
                out.counts,
                out.sorted(),
                staleSections);
    }

    /** The block position ({@link SectionKeys#block}) of a cell. */
    private static long blockOf(long cell) {
        return SectionKeys.block(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell));
    }

    private static List<Zone> sortedZones(ZoneGraph graph) {
        List<Zone> zones = new ArrayList<>(graph.zones());
        zones.sort((a, b) -> Long.compare(a.id(), b.id()));
        return zones;
    }

    /** Counts every finding once per kind and position, and keeps the first ones in sorted order. */
    private static final class Collector {
        private final int max;
        final int[] counts = new int[Kind.values().length];
        private final Set<Long>[] seen;
        private final TreeSet<Finding> kept = new TreeSet<>((a, b) -> {
            int c = a.kind().compareTo(b.kind());
            return c != 0 ? c : Long.compare(a.block(), b.block());
        });

        @SuppressWarnings("unchecked")
        Collector(int max) {
            this.max = Math.max(0, max);
            this.seen = new Set[Kind.values().length];
            for (int i = 0; i < seen.length; i++) {
                seen[i] = new HashSet<>();
            }
        }

        void add(Kind kind, long block, long zoneId) {
            if (!seen[kind.ordinal()].add(block)) {
                return;
            }
            counts[kind.ordinal()]++;
            if (max == 0) {
                return;
            }
            kept.add(new Finding(kind, block, zoneId));
            if (kept.size() > max) {
                kept.pollLast();
            }
        }

        List<Finding> sorted() {
            return new ArrayList<>(kept);
        }
    }
}
