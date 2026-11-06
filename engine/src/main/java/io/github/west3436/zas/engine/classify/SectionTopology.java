package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * The cell topology of the world as a set of section snapshots and the geometry palette their ids
 * index (GEO-001 to GEO-004): a thin, read-only adapter, so the worker's section mirror
 * (architecture §4.3) can be passed to the zone graph and the enclosure fill as it is.
 *
 * <p><b>Cells.</b> A cell is one entry of its block's {@link io.github.west3436.zas.engine.Geometry#cells()},
 * named by a {@link CellIds} id. Its volume is that entry's volume; a position whose geometry has
 * no cell at the id's sub-cell index, or whose section is not in the view, is not a cell.
 *
 * <p><b>Openings.</b> Two cells of adjacent positions are open to each other when {@link
 * GeometryPalette#connects} says so. The opening is direct unless either block makes its side of
 * the shared face a zone boundary ({@link io.github.west3436.zas.engine.Geometry#isBoundary},
 * GEO-007), as an open door does across the face it closes against. Neighbours are visited face by
 * face in {@link Face} order, and each face's cells in sub-cell order.
 *
 * <p><b>Limits.</b> Sections the lookup returns null for are unknown: {@link
 * #forEachNeighbour} reports the faces that reach them, so a fill can leave the region unresolved
 * (GEO-024). Positions below {@code minY}, above {@code maxY}, past the {@linkplain WorldLimits world
 * border} or beyond {@link CellIds}'s range are outside the world and aren't cells. Faces that reach
 * them count as open, unless the {@linkplain WorldLimits#sealed limits are sealed} (GEO-031).
 *
 * <p>The lookup is called on every query and nothing is cached, so the view may change between
 * calls; within one call it must not. Single-threaded: the palette connection cache is not safe
 * to share between threads.
 */
public final class SectionTopology implements ResolvableTopology {
    private static final Face[] FACES = Face.values();

    private final ConnectionTable table;
    private final LongFunction<SectionSnapshot> sections;
    private final int minY;
    private final int maxY;
    private WorldLimits limits = WorldLimits.OPEN;

    /**
     * A topology over the whole range {@link CellIds} can name.
     *
     * @param palette the palette the snapshots' geometry ids index
     * @param sections the section with a given key, or null if it isn't in the view
     */
    public SectionTopology(GeometryPalette palette, LongFunction<SectionSnapshot> sections) {
        this(palette, sections, CellIds.MIN_Y, CellIds.MAX_Y);
    }

    /**
     * A topology for a world whose build height runs from {@code minY} to {@code maxY}.
     *
     * @param palette the palette the snapshots' geometry ids index
     * @param sections the section with a given key, or null if it isn't in the view
     * @param minY the lowest block y in the world
     * @param maxY the highest block y in the world
     */
    public SectionTopology(GeometryPalette palette, LongFunction<SectionSnapshot> sections, int minY, int maxY) {
        this.table = new ConnectionTable(Objects.requireNonNull(palette, "palette"));
        this.sections = Objects.requireNonNull(sections, "sections");
        if (minY > maxY) {
            throw new IllegalArgumentException("minY " + minY + " is above maxY " + maxY);
        }
        this.minY = Math.max(minY, CellIds.MIN_Y);
        this.maxY = Math.min(maxY, CellIds.MAX_Y);
    }

    public GeometryPalette palette() {
        return table.palette();
    }

    /** Sets the world border and whether the world's edges are sealed (GEO-031). */
    public void limits(WorldLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Whether block {@code (x, y, z)} is inside the world. */
    public boolean inWorld(int x, int y, int z) {
        return y >= minY && y <= maxY && limits.contains(x, z) && CellIds.inRange(x, y, z);
    }

    /** Whether a cell lies outside the world or has a face leading out of it. */
    public boolean touchesLimit(long cell) {
        int x = CellIds.x(cell);
        int y = CellIds.y(cell);
        int z = CellIds.z(cell);
        return !inWorld(x, y, z)
                || !inWorld(x, y + 1, z)
                || !inWorld(x, y - 1, z)
                || !inWorld(x + 1, y, z)
                || !inWorld(x - 1, y, z)
                || !inWorld(x, y, z + 1)
                || !inWorld(x, y, z - 1);
    }

    @Override
    public long sectionOf(long cell) {
        return CellIds.section(cell);
    }

    @Override
    public boolean isKnown(long cell) {
        return inWorld(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell)) && sections.apply(sectionOf(cell)) != null;
    }

    @Override
    public double volume(long cell) {
        int x = CellIds.x(cell);
        int y = CellIds.y(cell);
        int z = CellIds.z(cell);
        if (!inWorld(x, y, z)) {
            return 0;
        }
        SectionSnapshot s = sections.apply(SectionKeys.sectionOfBlock(x, y, z));
        if (s == null) {
            return 0;
        }
        return table.volume(s.geometryId(SectionKeys.index(x & 15, y & 15, z & 15)), CellIds.sub(cell));
    }

    @Override
    public void forEachOpenNeighbour(long cell, NeighbourVisitor visitor) {
        visit(cell, visitor, null);
    }

    @Override
    public void forEachNeighbour(long cell, FillVisitor visitor) {
        visit(cell, visitor, visitor);
    }

    private void visit(long cell, NeighbourVisitor open, FillVisitor beyond) {
        int x = CellIds.x(cell);
        int y = CellIds.y(cell);
        int z = CellIds.z(cell);
        int sub = CellIds.sub(cell);
        if (!inWorld(x, y, z)) {
            return;
        }
        long key = SectionKeys.sectionOfBlock(x, y, z);
        SectionSnapshot home = sections.apply(key);
        if (home == null) {
            return;
        }
        int id = home.geometryId(SectionKeys.index(x & 15, y & 15, z & 15));
        if (sub >= table.cellCount(id)) {
            return;
        }
        for (Face face : FACES) {
            int nx = x + face.dx();
            int ny = y + face.dy();
            int nz = z + face.dz();
            if (!inWorld(nx, ny, nz)) {
                if (beyond != null && !limits.sealed() && reachesFace(id, sub, face)) {
                    beyond.outsideWorld();
                }
                continue;
            }
            SectionSnapshot s;
            long nkey;
            if ((nx >> 4) == (x >> 4) && (ny >> 4) == (y >> 4) && (nz >> 4) == (z >> 4)) {
                s = home;
                nkey = key;
            } else {
                nkey = SectionKeys.sectionOfBlock(nx, ny, nz);
                s = sections.apply(nkey);
            }
            if (s == null) {
                if (beyond != null && reachesFace(id, sub, face)) {
                    beyond.unknown(nkey);
                }
                continue;
            }
            int nid = s.geometryId(SectionKeys.index(nx & 15, ny & 15, nz & 15));
            int count = table.cellCount(nid);
            if (count == 0) {
                continue;
            }
            long[] bits = table.connections(id, face, nid);
            boolean direct = !table.boundary(id, face, nid);
            for (int c = 0; c < count; c++) {
                if (ConnectionTable.connected(bits, sub, c)) {
                    open.accept(CellIds.pack(nx, ny, nz, c), direct);
                }
            }
        }
    }

    @Override
    public void forEachOutsideFace(long cell, NeighbourVisitor visitor) {
        if (limits.sealed() || !touchesLimit(cell)) {
            return;
        }
        int x = CellIds.x(cell);
        int y = CellIds.y(cell);
        int z = CellIds.z(cell);
        int sub = CellIds.sub(cell);
        if (!inWorld(x, y, z)) {
            return;
        }
        SectionSnapshot s = sections.apply(SectionKeys.sectionOfBlock(x, y, z));
        if (s == null) {
            return;
        }
        int id = s.geometryId(SectionKeys.index(x & 15, y & 15, z & 15));
        if (sub >= table.cellCount(id)) {
            return;
        }
        for (Face face : FACES) {
            int nx = x + face.dx();
            int ny = y + face.dy();
            int nz = z + face.dz();
            // A y past what an id can hold would wrap round to a real cell's id.
            if (!inWorld(nx, ny, nz) && ny >= CellIds.MIN_Y && ny <= CellIds.MAX_Y && reachesFace(id, sub, face)) {
                visitor.accept(CellIds.pack(nx, ny, nz, 0), !table.boundary(id, face, GeometryPalette.AIR_ID));
            }
        }
    }

    @Override
    public Column column(long cell, LongPredicate stopAt) {
        int x = CellIds.x(cell);
        int y = CellIds.y(cell);
        int z = CellIds.z(cell);
        int sub = CellIds.sub(cell);
        if (!inWorld(x, y, z)) {
            return Column.SEALED;
        }
        SectionSnapshot s = sections.apply(SectionKeys.sectionOfBlock(x, y, z));
        if (s == null) {
            return Column.UNKNOWN;
        }
        int id = s.geometryId(SectionKeys.index(x & 15, y & 15, z & 15));
        if (sub >= table.cellCount(id)) {
            return Column.SEALED;
        }
        while (true) {
            int above = y + 1;
            if (!inWorld(x, above, z)) {
                return !limits.sealed() && reachesFace(id, sub, Face.UP) ? Column.OPEN : Column.SEALED;
            }
            if ((above >> 4) != (y >> 4)) {
                s = sections.apply(SectionKeys.sectionOfBlock(x, above, z));
                if (s == null) {
                    return reachesFace(id, sub, Face.UP) ? Column.UNKNOWN : Column.SEALED;
                }
            }
            int nid = s.geometryId(SectionKeys.index(x & 15, above & 15, z & 15));
            int count = table.cellCount(nid);
            if (count == 0) {
                return Column.SEALED;
            }
            long[] bits = table.connections(id, Face.UP, nid);
            int next = -1;
            for (int c = 0; c < count && next < 0; c++) {
                if (ConnectionTable.connected(bits, sub, c) && !stopAt.test(CellIds.pack(x, above, z, c))) {
                    next = c;
                }
            }
            if (next < 0) {
                return Column.SEALED;
            }
            y = above;
            id = nid;
            sub = next;
        }
    }

    /** The keys of a cell's section and the ones above it up to the top of the world, lowest first. */
    public long[] columnSections(long cell) {
        int sx = CellIds.x(cell) >> 4;
        int sz = CellIds.z(cell) >> 4;
        int first = CellIds.y(cell) >> 4;
        long[] out = new long[Math.max(0, (maxY >> 4) - first + 1)];
        for (int i = 0; i < out.length; i++) {
            out[i] = SectionKeys.section(sx, first + i, sz);
        }
        return out;
    }

    @Override
    public long[] columnGaps(long cell) {
        long[] all = columnSections(cell);
        long[] out = new long[all.length];
        int n = 0;
        for (long k : all) {
            if (sections.apply(k) == null) {
                out[n++] = k;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Whether gas in the cell could cross the face if the neighbour were air. */
    private boolean reachesFace(int id, int sub, Face face) {
        return ConnectionTable.connected(table.connections(id, face, GeometryPalette.AIR_ID), sub, 0);
    }
}
