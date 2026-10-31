package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Which cells each zone holds, as of one point in an atmos tick: zone by zone for restoring
 * rejected components (architecture §3.3), and by block position for the published lookup.
 * Immutable once built. Hash sets are used for membership tests only.
 */
final class Membership {
    final long[] zoneIds; // ascending
    private final int[] offsets; // zone i's cells are cells[offsets[i] .. offsets[i + 1])
    private final long[] cells;
    /** The volume of each entry of {@code cells}. */
    private final double[] volumes;
    /** The block position of each entry of {@code cells}. */
    private final long[] cellPositions;
    /** Per zone, the chunks its cells are in, ascending. */
    private final long[][] chunks;
    /** Block keys, ascending, without duplicates; the zone index of each position's largest cell. */
    final long[] positions;

    final int[] zoneOfPosition;
    /** Sections holding zoned cells. */
    final Set<Long> sections;

    private Membership(
            long[] zoneIds,
            int[] offsets,
            long[] cells,
            double[] volumes,
            long[] cellPositions,
            long[][] chunks,
            long[] positions,
            int[] zoneOfPosition,
            Set<Long> sections) {
        this.zoneIds = zoneIds;
        this.offsets = offsets;
        this.cells = cells;
        this.volumes = volumes;
        this.cellPositions = cellPositions;
        this.chunks = chunks;
        this.positions = positions;
        this.zoneOfPosition = zoneOfPosition;
        this.sections = sections;
    }

    static final Membership EMPTY = new Membership(
            new long[0],
            new int[] {0},
            new long[0],
            new double[0],
            new long[0],
            new long[0][],
            new long[0],
            new int[0],
            Set.of());

    static Membership of(ZoneGraph graph, CellSpace space) {
        int zoneCount = graph.zoneCount();
        long[] ids = new long[zoneCount];
        int[] offsets = new int[zoneCount + 1];
        int total = 0;
        for (Zone z : graph.zones()) {
            total += z.cellCount();
        }
        long[] cells = new long[total];
        long[] pos = new long[total];
        double[] vol = new double[total];
        int[] zoneOf = new int[total];
        int zi = 0;
        int n = 0;
        for (Zone z : graph.zones()) {
            ids[zi] = z.id();
            offsets[zi] = n;
            for (long cell : z.cells()) {
                cells[n] = cell;
                pos[n] = space.position(cell);
                vol[n] = space.volume(cell);
                zoneOf[n] = zi;
                n++;
            }
            zi++;
        }
        offsets[zoneCount] = n;
        long[][] chunks = new long[zoneCount][];
        for (int z = 0; z < zoneCount; z++) {
            long[] c = new long[offsets[z + 1] - offsets[z]];
            for (int i = 0; i < c.length; i++) {
                c[i] = SectionKeys.chunkOfBlockKey(pos[offsets[z] + i]);
            }
            chunks[z] = sortedDistinct(c);
        }

        // Sort by position, then volume descending, then cell id: the first of each position wins.
        Integer[] order = new Integer[total];
        for (int i = 0; i < total; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int c = Long.compare(pos[a], pos[b]);
            if (c == 0) {
                c = Double.compare(vol[b], vol[a]);
            }
            return c != 0 ? c : Long.compare(cells[a], cells[b]);
        });
        long[] positions = new long[total];
        int[] zoneOfPosition = new int[total];
        Set<Long> sections = new HashSet<>();
        int p = 0;
        for (int k = 0; k < total; k++) {
            int i = order[k];
            if (p > 0 && positions[p - 1] == pos[i]) {
                continue;
            }
            positions[p] = pos[i];
            zoneOfPosition[p] = zoneOf[i];
            sections.add(SectionKeys.sectionOfBlockKey(pos[i]));
            p++;
        }
        return new Membership(
                ids,
                offsets,
                cells,
                vol,
                pos,
                chunks,
                Arrays.copyOf(positions, p),
                Arrays.copyOf(zoneOfPosition, p),
                sections);
    }

    static long[] sortedDistinct(long[] values) {
        long[] out = values.clone();
        Arrays.sort(out);
        int n = 0;
        for (int i = 0; i < out.length; i++) {
            if (n == 0 || out[i] != out[n - 1]) {
                out[n++] = out[i];
            }
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    /** The number of zones. */
    int size() {
        return zoneIds.length;
    }

    /** The chunks the zone at an index has cells in, ascending. Not a copy. */
    long[] chunks(int index) {
        return chunks[index];
    }

    /** The first cell of the zone at an index in {@link #cell} order. */
    int from(int index) {
        return offsets[index];
    }

    /** One past the last cell of the zone at an index. */
    int to(int index) {
        return offsets[index + 1];
    }

    /** The cell at a flat position, as {@link #from} and {@link #to} count. */
    long cell(int i) {
        return cells[i];
    }

    /** The volume of the cell at a flat position. */
    double volume(int i) {
        return volumes[i];
    }

    /** The block position of the cell at a flat position. */
    long position(int i) {
        return cellPositions[i];
    }

    /** The index of a zone id, or -1. */
    int indexOf(long zoneId) {
        return Math.max(-1, Arrays.binarySearch(zoneIds, zoneId));
    }

    /** The cells of the zone at an index. */
    long[] cells(int index) {
        return Arrays.copyOfRange(cells, offsets[index], offsets[index + 1]);
    }

    /** The chunks the zone at an index spans, into {@code out}. */
    void addChunks(int index, Set<Long> out) {
        for (long c : chunks[index]) {
            out.add(c);
        }
    }

    /**
     * Whether the graph still has the same zones with the same cell counts. Cells change zone only
     * through refreshes (which the engine tracks itself), joins, merges, splits and dissolution,
     * and each of those changes a zone's cell count or the set of zones.
     */
    boolean sameZones(ZoneGraph graph) {
        if (graph.zoneCount() != zoneIds.length) {
            return false;
        }
        int i = 0;
        for (Zone z : graph.zones()) {
            if (z.id() != zoneIds[i] || z.cellCount() != offsets[i + 1] - offsets[i]) {
                return false;
            }
            i++;
        }
        return true;
    }
}
