package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.List;
import java.util.Objects;

/**
 * What the worker exports for the operator debug overlay after an atmos tick, limited to the
 * {@link DebugRegion}s asked for (CLIENT-005, CLIENT-011, D-53). Built on the thread that owns the
 * engine, between ticks, so the server thread never reads the zone graph; immutable afterwards.
 *
 * <p>Each list is capped (see {@link DimensionEngine#debugView}); {@link #truncated()} says
 * whether anything was left out.
 *
 * @param tick the atmos tick the view is from
 * @param cells the zoned cells inside a region, zone by zone in id order
 * @param faces the open cell faces inside a region that lead out of a zone: to another zone or to
 *     the ambient; a face between two zones is listed once, from the zone with the lower id
 * @param unresolved unzoned cells inside a region that belong to a region left unresolved because
 *     it reaches unloaded chunks (GEO-024, GEO-026)
 * @param truncated whether a cap left entries out
 */
public record DebugView(
        long tick, List<CellEntry> cells, List<FaceEntry> faces, List<UnresolvedEntry> unresolved, boolean truncated) {

    /** The {@link FaceEntry#other()} of a face onto the ambient. Zone ids start at 1. */
    public static final long AMBIENT = 0;

    /** No regions asked for, or nothing in them. */
    public static final DebugView EMPTY = new DebugView(-1, List.of(), List.of(), List.of(), false);

    public DebugView {
        cells = List.copyOf(cells);
        faces = List.copyOf(faces);
        unresolved = List.copyOf(unresolved);
    }

    /**
     * One zoned cell.
     *
     * @param block its block position ({@link SectionKeys#block})
     * @param sub its index among the cells of its block (GEO-004)
     * @param zone its zone's id
     */
    public record CellEntry(long block, int sub, long zone) {}

    /**
     * One open face out of a zone.
     *
     * @param block the zoned cell's block position ({@link SectionKeys#block})
     * @param sub the zoned cell's index among the cells of its block
     * @param face the face of that block the opening crosses
     * @param zone the zoned cell's zone
     * @param other the zone on the other side, or {@link #AMBIENT}
     * @param direct whether the opening is direct, not through a zone boundary such as an open
     *     door (CORE-037, GEO-007)
     */
    public record FaceEntry(long block, int sub, Face face, long zone, long other, boolean direct) {
        public FaceEntry {
            Objects.requireNonNull(face, "face");
        }

        /** Whether the face leads to the ambient. */
        public boolean ambient() {
            return other == AMBIENT;
        }
    }

    /**
     * One cell of an unresolved region.
     *
     * @param block its block position ({@link SectionKeys#block})
     * @param sub its index among the cells of its block
     */
    public record UnresolvedEntry(long block, int sub) {}
}
