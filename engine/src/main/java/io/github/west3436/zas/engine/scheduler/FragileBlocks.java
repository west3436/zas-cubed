package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.flow.FragileSettings;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Which fragile blocks a pressure difference breaks this tick (FLOW-007, FLOW-015). It only reads:
 * each break is asked of the server thread with a {@link PressureBreak}, and is then an ordinary
 * block change, so the zones meet through the usual path and the gas stays accounted for.
 *
 * <p><b>Across a block.</b> A block's <i>sides</i> are its own cells, as with a pane's two halves
 * (GEO-004), and the cells next to it that reach the shared face. A zoned cell reads its zone's
 * pressure and any other cell the ambient's. The block breaks when two of its sides differ by more
 * than the threshold. A solid neighbour isn't a side, so of two glass blocks back to back neither
 * sees a difference.
 *
 * <p><b>What it won't judge</b> (GEO-034, GEO-035). A block holds while any of its sides is a paused
 * zone or an unresolved region, or is in an unloaded chunk. The numbers there are a last-known state
 * or a guess, good enough to answer a query but not to break a window, and breaking it would be a
 * change held for that zone (GEO-037), or would pull that chunk up to read its neighbours. The block
 * is judged again each tick, so it breaks when the far side comes back, if the difference still says
 * so.
 *
 * <p><b>A section the mirror lacks.</b> It holds no zoned cell, so any cell in it reads the ambient,
 * but the worker can't tell a cell from a solid block there. If that would break the block, the
 * section is asked for and the block judged again next tick, as long as {@code wanted} accepts it. A
 * section beyond those the mirror keeps isn't: a block whose only low side is in such a section, across
 * a section border from the block and from every zone, holds until something mirrors it.
 *
 * <p><b>Cost.</b> With the option off, nothing. With it on, one comparison per zone per tick while
 * no zone differs by more than the threshold from the ambient or from another zone, which 1 atm
 * against a vacuum can't at the default. Otherwise a look at each fragile block in the mirror, which
 * {@link SectionMirror} indexes by section. At most {@link FragileSettings#maxBreaks} blocks are
 * asked for in a tick, so the server thread's work is bounded however many windows fail. The rest
 * are found again next tick, if the pressure still says so.
 *
 * <p><b>Determinism</b> (§3.5). Sections and blocks are visited in key order, and nothing is kept
 * between ticks. Worker thread only.
 */
final class FragileBlocks {
    private static final Face[] FACES = Face.values();

    private final ZoneGraph graph;
    private final CellSpace space;
    private final SectionMirror mirror;
    private final GeometryPalette palette;
    private final boolean any;

    private double ambient;
    private LongPredicate unresolved = block -> false;
    private long judged;

    // The block being judged: its highest and lowest sides, and whether a side stops it being judged.
    private double high;
    private double low;
    private long highZone;
    private long lowZone;
    private boolean holds;

    // The neighbour being read: which of its cells is next, and the face that must reach the block.
    private Geometry nextGeometry;
    private Face back;
    private int nextSub;

    private final LongConsumer ownCell = this::side;
    private final LongConsumer neighbourCell = this::neighbourCell;

    FragileBlocks(ZoneGraph graph, CellSpace space, SectionMirror mirror, GeometryPalette palette) {
        this.graph = graph;
        this.space = space;
        this.mirror = mirror;
        this.palette = palette;
        int n = palette.size();
        boolean[] fragile = new boolean[n];
        boolean found = false;
        for (int id = 0; id < n; id++) {
            fragile[id] = palette.geometry(id).hasFlag(Geometry.FLAG_PRESSURE_FRAGILE);
            found |= fragile[id];
        }
        any = found;
        if (found) {
            mirror.indexFragile(id -> id >= 0 && id < n && fragile[id]);
        }
    }

    /** How many blocks have been judged since the engine started. For tests. */
    long judged() {
        return judged;
    }

    /**
     * The blocks to break this tick, after the tick's exchange, in ascending section and block order.
     *
     * @param unresolved whether a position has a cell in an unresolved region (GEO-026)
     * @param wanted told of a section the mirror lacks that would settle a block
     */
    List<PressureBreak> find(FragileSettings settings, LongPredicate unresolved, LongConsumer wanted) {
        if (!any || !settings.enabled()) {
            return List.of();
        }
        ambient = graph.ambient().pressure();
        this.unresolved = unresolved;
        double threshold = settings.thresholdKpa();
        if (!anyDifference(threshold)) {
            return List.of();
        }
        List<PressureBreak> out = new ArrayList<>();
        for (long[] blocks : mirror.fragile()) {
            for (long block : blocks) {
                PressureBreak found = judge(block, threshold, wanted);
                if (found != null) {
                    out.add(found);
                    if (out.size() == settings.maxBreaks()) {
                        return out;
                    }
                }
            }
        }
        return out;
    }

    /**
     * Whether a zone that isn't paused differs by more than the threshold from the ambient or from
     * another such zone: the cheap test that spares looking at each fragile block.
     */
    private boolean anyDifference(double threshold) {
        double highest = ambient;
        double lowest = ambient;
        for (Zone z : graph.zones()) {
            if (!z.isPaused()) {
                double p = z.mixture().pressure();
                highest = Math.max(highest, p);
                lowest = Math.min(lowest, p);
            }
        }
        return highest - lowest > threshold;
    }

    /** What to break for a block, or null if it holds. */
    private PressureBreak judge(long block, double threshold, LongConsumer wanted) {
        judged++;
        if (mirror.isChunkUnloaded(SectionKeys.chunkOfBlockKey(block))) {
            return null;
        }
        high = Double.NEGATIVE_INFINITY;
        low = Double.POSITIVE_INFINITY;
        highZone = 0;
        lowZone = 0;
        holds = false;
        space.forEachCellAt(block, ownCell);
        long[] missing = null;
        int missed = 0;
        for (Face f : FACES) {
            if (holds) {
                return null;
            }
            long next = offset(block, f);
            long section = SectionKeys.sectionOfBlockKey(next);
            int sy = SectionKeys.y(section);
            if (sy < mirror.minSectionY() || sy > mirror.maxSectionY()) {
                read(ambient, 0); // above or below the world: open (GEO-031)
                continue;
            }
            if (mirror.isChunkUnloaded(SectionKeys.chunkOf(section)) || mirror.isUnloaded(section)) {
                return null;
            }
            int id = mirror.geometryAt(next);
            if (id < 0) {
                if (missing == null) {
                    missing = new long[FACES.length];
                }
                missing[missed++] = section;
                continue;
            }
            Geometry g = palette.geometry(id);
            if (g.cellCount() > 0) {
                nextGeometry = g;
                back = f.opposite();
                nextSub = 0;
                space.forEachCellAt(next, neighbourCell);
            }
        }
        if (holds) {
            return null;
        }
        if (high - low > threshold) {
            return new PressureBreak(block, highZone, lowZone);
        }
        if (missing != null && Math.max(high, ambient) - Math.min(low, ambient) > threshold) {
            for (int i = 0; i < missed; i++) {
                wanted.accept(missing[i]);
            }
        }
        return null;
    }

    /** Reads a cell of the neighbour being read, if it reaches the face shared with the block. */
    private void neighbourCell(long cell) {
        if (nextGeometry.cell(nextSub++).reaches(back)) {
            side(cell);
        }
    }

    private void side(long cell) {
        Zone z = graph.zoneOf(cell);
        if (z == null) {
            if (unresolved.test(space.position(cell))) {
                holds = true;
            } else {
                read(ambient, 0);
            }
        } else if (z.isPaused()) {
            holds = true;
        } else {
            read(z.mixture().pressure(), z.id());
        }
    }

    private void read(double kpa, long zone) {
        if (kpa > high) {
            high = kpa;
            highZone = zone;
        }
        if (kpa < low) {
            low = kpa;
            lowZone = zone;
        }
    }

    private static long offset(long block, Face f) {
        return SectionKeys.block(
                SectionKeys.blockX(block) + f.dx(),
                SectionKeys.blockY(block) + f.dy(),
                SectionKeys.blockZ(block) + f.dz());
    }
}
