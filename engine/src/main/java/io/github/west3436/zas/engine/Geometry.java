package io.github.west3436.zas.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The gas geometry of one block position: an entry of the {@link GeometryPalette}.
 *
 * <p>{@code coverage} gives, for each {@link Face} in order, the part of the face covered by the
 * block's collision shape. {@code cells} are the connected regions of free space that reach at
 * least one face (GEO-001, GEO-004), in a fixed order. Free space that reaches no face is not a
 * cell. {@code flags} is a set of {@code FLAG_*} bits.
 *
 * @param coverage six masks, indexed by {@link Face#ordinal()}
 * @param cells the cells, possibly none (a solid block)
 * @param flags {@code FLAG_*} bits
 */
public record Geometry(List<FaceMask> coverage, List<Cell> cells, int flags) {
    /** The gas volume of a whole position, in litres (A-02). */
    public static final double FULL_VOLUME = 1000.0;

    /** The gas volume of one 1/16 voxel, in litres. Exact in binary. */
    public static final double VOXEL_VOLUME = FULL_VOLUME / 4096;

    /** The geometry comes from a datapack override, not the collision shape (GEO-005). */
    public static final int FLAG_OVERRIDE = 1;

    /** The block keeps zones apart while passing gas (GEO-007). */
    public static final int FLAG_ZONE_BOUNDARY = 1 << 1;

    /** The position holds a fluid (GEO-006). */
    public static final int FLAG_FLUID = 1 << 2;

    /** The block is a heat source. */
    public static final int FLAG_HEAT_SOURCE = 1 << 3;

    /** The geometry depends on position and is computed per position (GEO-043). */
    public static final int FLAG_POSITIONAL = 1 << 4;

    /** An air position: no coverage, one 1,000 L cell reaching every face. */
    public static final Geometry AIR = new Geometry(
            List.of(FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY),
            List.of(new Cell(
                    FULL_VOLUME,
                    List.of(FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL))),
            0);

    /** A full block: every face covered, no cells. */
    public static final Geometry SOLID = new Geometry(
            List.of(FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL),
            List.of(),
            0);

    public Geometry {
        coverage = List.copyOf(coverage);
        cells = List.copyOf(cells);
        if (coverage.size() != 6) {
            throw new IllegalArgumentException("coverage needs 6 masks, got " + coverage.size());
        }
    }

    /**
     * A declared geometry, as a datapack override gives it (GEO-005): the listed faces are fully
     * covered, the others open, and the block is a single cell of {@code volume} litres reaching
     * every open face. With every face sealed there is no cell, since a cell must reach a face.
     */
    public static Geometry declared(Set<Face> sealed, double volume, int flags) {
        if (!(volume >= 0 && volume <= FULL_VOLUME)) {
            throw new IllegalArgumentException("volume must be 0 to 1000 L, got " + volume);
        }
        List<FaceMask> cover = new ArrayList<>(6);
        List<FaceMask> footprint = new ArrayList<>(6);
        for (Face f : Face.values()) {
            boolean tight = sealed.contains(f);
            cover.add(tight ? FaceMask.FULL : FaceMask.EMPTY);
            footprint.add(tight ? FaceMask.EMPTY : FaceMask.FULL);
        }
        List<Cell> cells = sealed.size() == 6 ? List.of() : List.of(new Cell(volume, footprint));
        return new Geometry(cover, cells, flags | FLAG_OVERRIDE);
    }

    /** The part of {@code face} covered by the collision shape. */
    public FaceMask coverage(Face face) {
        return coverage.get(face.ordinal());
    }

    /** Whether the block alone covers the whole of {@code face}. */
    public boolean isFaceFull(Face face) {
        return coverage(face).isFull();
    }

    public int cellCount() {
        return cells.size();
    }

    public Cell cell(int index) {
        return cells.get(index);
    }

    /** The total gas volume of all cells, in litres (GEO-002). */
    public double volume() {
        double v = 0;
        for (Cell c : cells) {
            v += c.volume();
        }
        return v;
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    /** This geometry with {@code flag} bits added. */
    public Geometry withFlags(int flag) {
        return (flags | flag) == flags ? this : new Geometry(coverage, cells, flags | flag);
    }

    /**
     * One cell: a connected region of free space reaching at least one face.
     *
     * @param volume gas volume in litres (GEO-002)
     * @param footprints six masks, indexed by {@link Face#ordinal()}: the part of each face this
     *     cell's free space touches. Empty for faces it doesn't reach. Never overlaps the coverage.
     */
    public record Cell(double volume, List<FaceMask> footprints) {
        public Cell {
            footprints = List.copyOf(footprints);
            if (footprints.size() != 6) {
                throw new IllegalArgumentException("footprints needs 6 masks, got " + footprints.size());
            }
        }

        public FaceMask footprint(Face face) {
            return footprints.get(face.ordinal());
        }

        /** Whether this cell touches {@code face} at all. */
        public boolean reaches(Face face) {
            return !footprint(face).isEmpty();
        }
    }
}
