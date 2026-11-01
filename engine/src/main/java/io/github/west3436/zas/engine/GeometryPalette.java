package io.github.west3436.zas.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dense table of distinct block geometries (architecture §2.3, §4.1). Section snapshots
 * refer to positions by geometry id, an index into this table.
 *
 * <p>Ids {@link #AIR_ID} and {@link #SOLID_ID} always hold {@link Geometry#AIR} and {@link
 * Geometry#SOLID}. A palette is immutable; build one with {@link #builder()}.
 *
 * <p>The seal test between adjacent positions (GEO-003): the shared face is gas-tight wherever
 * the two blocks' coverage together covers it, and two cells connect when their footprints
 * overlap on a part of the face neither block covers.
 */
public final class GeometryPalette {
    public static final int AIR_ID = 0;
    public static final int SOLID_ID = 1;

    private final Geometry[] entries;

    private GeometryPalette(Geometry[] entries) {
        this.entries = entries;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this palette's entries under the same ids, to add more. */
    public Builder toBuilder() {
        Builder b = new Builder();
        for (int i = 2; i < entries.length; i++) {
            b.intern(entries[i]);
        }
        return b;
    }

    /** The number of entries. Ids run from 0 to {@code size() - 1}. */
    public int size() {
        return entries.length;
    }

    public Geometry geometry(int id) {
        return entries[id];
    }

    /** Whether the face between {@code a} and its neighbour {@code b} across {@code face} is fully gas-tight. */
    public boolean faceSealed(int a, Face face, int b) {
        return sealed(entries[a], face, entries[b]);
    }

    /**
     * Whether cell {@code cellA} of {@code a} connects to cell {@code cellB} of {@code b}, where
     * {@code b} is the neighbour of {@code a} across {@code face}.
     */
    public boolean connects(int a, int cellA, Face face, int b, int cellB) {
        return connects(entries[a], cellA, face, entries[b], cellB);
    }

    /** {@link #faceSealed(int, Face, int)} for geometries. */
    public static boolean sealed(Geometry a, Face face, Geometry b) {
        return FaceMask.unionIsFull(a.coverage(face), b.coverage(face.opposite()));
    }

    /** {@link #connects(int, int, Face, int, int)} for geometries. */
    public static boolean connects(Geometry a, int cellA, Face face, Geometry b, int cellB) {
        FaceMask fa = a.cell(cellA).footprint(face);
        FaceMask fb = b.cell(cellB).footprint(face.opposite());
        if (!fa.intersects(fb)) {
            return false;
        }
        // Footprints never overlap their own block's coverage, but may overlap the neighbour's.
        FaceMask open = a.coverage(face).or(b.coverage(face.opposite())).not();
        return fa.and(fb).intersects(open);
    }

    /** Builds a palette, deduplicating equal geometries. */
    public static final class Builder {
        private final List<Geometry> entries = new ArrayList<>();
        private final Map<Geometry, Integer> ids = new HashMap<>();

        private Builder() {
            intern(Geometry.AIR);
            intern(Geometry.SOLID);
        }

        /** The id of {@code geometry}, adding it if no equal geometry is present yet. */
        public int intern(Geometry geometry) {
            Integer id = ids.get(geometry);
            if (id != null) {
                return id;
            }
            int next = entries.size();
            entries.add(geometry);
            ids.put(geometry, next);
            return next;
        }

        public int size() {
            return entries.size();
        }

        /** The geometry with this id, of those added so far. */
        public Geometry geometry(int id) {
            return entries.get(id);
        }

        public GeometryPalette build() {
            return new GeometryPalette(entries.toArray(Geometry[]::new));
        }
    }
}
