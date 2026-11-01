package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Which zone each cell is in, as of one point in an atmos tick (architecture §4.4): the
 * per-section palettes ({@link SectionMembers}) of every section holding zoned cells, and per zone
 * the sections it has cells in. Immutable, so a published {@link AtmosSnapshot} can read it from
 * any thread while the worker moves on (API-046).
 *
 * <p>A {@link MembershipTracker} builds each table from the last: sections and zones that didn't
 * change are the same objects in both, so publishing costs the changed sections plus a copy of
 * two arrays of references, not a pass over the cells.
 *
 * <p>Zone by zone it answers the cells for restoring rejected components (architecture §3.3) and
 * the chunks a zone spans; by position it answers the published lookup; by chunk, the cells and
 * volume of each zone's saved share (DATA-002).
 */
final class Membership {
    /** The sections a zone has cells in, ascending, and the chunks they are in. Immutable. */
    record ZoneSections(long[] sections, long[] chunks) {
        static ZoneSections of(long[] sections) {
            long[] c = new long[sections.length];
            for (int i = 0; i < c.length; i++) {
                c[i] = SectionKeys.chunkOf(sections[i]);
            }
            return new ZoneSections(sections, sortedDistinct(c));
        }

        ZoneSections with(long section) {
            int i = Arrays.binarySearch(sections, section);
            if (i >= 0) {
                return this;
            }
            int at = -i - 1;
            long[] out = new long[sections.length + 1];
            System.arraycopy(sections, 0, out, 0, at);
            out[at] = section;
            System.arraycopy(sections, at, out, at + 1, sections.length - at);
            return of(out);
        }

        ZoneSections without(long section) {
            int i = Arrays.binarySearch(sections, section);
            if (i < 0) {
                return this;
            }
            long[] out = new long[sections.length - 1];
            System.arraycopy(sections, 0, out, 0, i);
            System.arraycopy(sections, i + 1, out, i, out.length - i);
            return of(out);
        }

        ZoneSections union(ZoneSections other) {
            long[] all = Arrays.copyOf(sections, sections.length + other.sections.length);
            System.arraycopy(other.sections, 0, all, sections.length, other.sections.length);
            return of(sortedDistinct(all));
        }
    }

    /** One zone's cells in one chunk, and their volume. */
    record Part(int zone, long[] cells, double volume) {}

    private static final ZoneSections[] NO_ZONES = new ZoneSections[0];
    private static final SectionMembers[] NO_SECTIONS = new SectionMembers[0];

    static final Membership EMPTY =
            new Membership(CellNaming.CELL_IDS, new long[0], NO_SECTIONS, new long[0], NO_ZONES);

    private final CellNaming naming;
    /** Ascending, each holding at least one zoned cell. */
    final long[] sectionKeys;

    final SectionMembers[] sections;
    /** Ascending. */
    final long[] zoneIds;

    final ZoneSections[] zones;

    Membership(CellNaming naming, long[] sectionKeys, SectionMembers[] sections, long[] zoneIds, ZoneSections[] zones) {
        this.naming = naming;
        this.sectionKeys = sectionKeys;
        this.sections = sections;
        this.zoneIds = zoneIds;
        this.zones = zones;
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

    // ---- By zone ------------------------------------------------------------------------

    /** The number of zones. */
    int size() {
        return zoneIds.length;
    }

    /** The index of a zone id, or -1. */
    int indexOf(long zoneId) {
        return Math.max(-1, Arrays.binarySearch(zoneIds, zoneId));
    }

    /** The chunks the zone at an index has cells in, ascending. Not a copy. */
    long[] chunks(int index) {
        return zones[index].chunks();
    }

    /** The chunks the zone at an index spans, into {@code out}. */
    void addChunks(int index, Set<Long> out) {
        for (long c : zones[index].chunks()) {
            out.add(c);
        }
    }

    /**
     * The cells of the zone at an index, by section, then position, then sub-cell. Costs a pass over
     * the zone's sections.
     */
    long[] cells(int index) {
        long id = zoneIds[index];
        long[] out = new long[64];
        int n = 0;
        for (long key : zones[index].sections()) {
            SectionMembers s = section(key);
            boolean[] mine = entriesOf(s, id);
            for (int i = 0; i < SectionMembers.SIZE; i++) {
                int l = s.local(i);
                if (l > 0 && mine[l]) {
                    if (n == out.length) {
                        out = Arrays.copyOf(out, n * 2);
                    }
                    out[n++] = naming.cell(s.block(i), 0);
                }
            }
            for (int i = 0; i < s.sideSize(); i++) {
                if (mine[s.sideEntry(i)]) {
                    if (n == out.length) {
                        out = Arrays.copyOf(out, n * 2);
                    }
                    out[n++] = s.sideCell(i);
                }
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** The summed volume of the zone at an index, in the order {@link #parts} sums it. */
    double zoneVolume(int index) {
        long id = zoneIds[index];
        double total = 0;
        for (long key : zones[index].sections()) {
            SectionMembers s = section(key);
            for (int e = 1; e < s.paletteSize(); e++) {
                if (s.zoneOfEntry(e) == id) {
                    total += s.volumeOfEntry(e);
                }
            }
        }
        return total;
    }

    // ---- By position and cell ------------------------------------------------------------

    /** Whether a section holds zoned cells. */
    boolean hasSection(long key) {
        return Arrays.binarySearch(sectionKeys, key) >= 0;
    }

    /** The zone of a position's largest zoned cell (API-015), or 0 if it holds none. */
    long zoneAt(long block) {
        SectionMembers s = section(SectionKeys.sectionOfBlockKey(block));
        if (s == null) {
            return 0;
        }
        int e = s.largestEntry(index(block));
        return e == 0 ? 0 : s.zoneOfEntry(e);
    }

    /** The zone of a cell, or 0 if it is unzoned (or isn't a cell). */
    long zoneOfCell(long cell) {
        if (sections.length == 0) {
            return 0;
        }
        long block = naming.position(cell);
        SectionMembers s = section(SectionKeys.sectionOfBlockKey(block));
        if (s == null) {
            return 0;
        }
        int e = s.entryOf(index(block), cell, naming.subIndex(cell));
        return e == 0 ? 0 : s.zoneOfEntry(e);
    }

    /** The number of positions holding a zoned cell. */
    int positions() {
        int n = 0;
        for (SectionMembers s : sections) {
            n += s.positions();
        }
        return n;
    }

    // ---- By chunk (persistence) ------------------------------------------------------------

    /**
     * Each zone's cells in a chunk and their volume, in ascending zone order; empty if no zone has
     * cells there. Costs a pass over the chunk's zoned sections.
     */
    List<Part> parts(long chunk) {
        int x = SectionKeys.chunkX(chunk);
        int z = SectionKeys.chunkZ(chunk);
        long prefix = SectionKeys.section(x, 0, z) >> 20;
        int from = Arrays.binarySearch(sectionKeys, prefix << 20);
        if (from < 0) {
            from = -from - 1;
        }
        int[] zoneOfEntry = new int[8];
        long[][] cells = new long[zoneIds.length][];
        int[] counts = new int[zoneIds.length];
        double[] volumes = new double[zoneIds.length];
        boolean any = false;
        for (int k = from; k < sectionKeys.length && (sectionKeys[k] >> 20) == prefix; k++) {
            SectionMembers s = sections[k];
            if (zoneOfEntry.length < s.paletteSize()) {
                zoneOfEntry = new int[s.paletteSize()];
            }
            for (int e = 1; e < s.paletteSize(); e++) {
                long id = s.zoneOfEntry(e);
                int zi = id == 0 ? -1 : indexOf(id);
                zoneOfEntry[e] = zi;
                if (zi >= 0) {
                    volumes[zi] += s.volumeOfEntry(e);
                    if (cells[zi] == null) {
                        cells[zi] = new long[Math.max(8, s.countOfEntry(e))];
                    }
                }
            }
            for (int i = 0; i < SectionMembers.SIZE; i++) {
                int l = s.local(i);
                if (l > 0) {
                    int zi = zoneOfEntry[l];
                    cells[zi] = append(cells[zi], counts[zi]++, naming.cell(s.block(i), 0));
                    any = true;
                }
            }
            for (int i = 0; i < s.sideSize(); i++) {
                int zi = zoneOfEntry[s.sideEntry(i)];
                cells[zi] = append(cells[zi], counts[zi]++, s.sideCell(i));
                any = true;
            }
        }
        if (!any) {
            return List.of();
        }
        List<Part> out = new ArrayList<>();
        for (int zi = 0; zi < zoneIds.length; zi++) {
            if (counts[zi] > 0) {
                out.add(new Part(zi, Arrays.copyOf(cells[zi], counts[zi]), volumes[zi]));
            }
        }
        return out;
    }

    private static long[] append(long[] a, int at, long v) {
        if (at == a.length) {
            a = Arrays.copyOf(a, a.length * 2);
        }
        a[at] = v;
        return a;
    }

    // ---- Helpers ------------------------------------------------------------------------

    private SectionMembers section(long key) {
        int i = Arrays.binarySearch(sectionKeys, key);
        return i >= 0 ? sections[i] : null;
    }

    /** Per palette entry, whether it names a zone. */
    private static boolean[] entriesOf(SectionMembers s, long zoneId) {
        boolean[] out = new boolean[s.paletteSize()];
        for (int e = 1; e < out.length; e++) {
            out[e] = s.zoneOfEntry(e) == zoneId;
        }
        return out;
    }

    private static int index(long block) {
        return SectionKeys.index(
                SectionKeys.blockX(block) & 15, SectionKeys.blockY(block) & 15, SectionKeys.blockZ(block) & 15);
    }
}
