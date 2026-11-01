package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.conservation.ConservationLedger;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;

/**
 * The per-section membership palettes (architecture §4.4): {@link MembershipTracker} follows the
 * zone graph change by change, and each published {@link Membership} table matches the graph and
 * never changes afterwards.
 */
class MembershipTest {
    private static final double FULL = 1000;

    /** A topology of block positions with up to 4 sub-cells each; cells of adjacent blocks connect. */
    static final class Space implements CellSpace {
        private final Map<Long, double[]> blocks = new HashMap<>();

        /** Makes a position open with the given sub-cell volumes, or solid with none. */
        void set(int x, int y, int z, double... subVolumes) {
            long b = SectionKeys.block(x, y, z);
            if (subVolumes.length == 0) {
                blocks.remove(b);
            } else {
                blocks.put(b, subVolumes.clone());
            }
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, FULL);
                    }
                }
            }
        }

        List<Long> blocks() {
            List<Long> out = new ArrayList<>(blocks.keySet());
            out.sort(Long::compare);
            return out;
        }

        @Override
        public double volume(long cell) {
            double[] v = blocks.get(position(cell));
            int sub = CellIds.sub(cell);
            return v == null || sub >= v.length ? 0 : v[sub];
        }

        @Override
        public void forEachOpenNeighbour(long cell, NeighbourVisitor visitor) {
            if (volume(cell) <= 0) {
                return;
            }
            long b = position(cell);
            for (Face f : Face.values()) {
                forEachCellAt(offset(b, f), n -> visitor.accept(n, true));
            }
        }

        @Override
        public void forEachCellAt(long block, LongConsumer action) {
            double[] v = blocks.get(block);
            if (v == null) {
                return;
            }
            for (int sub = 0; sub < v.length; sub++) {
                if (v[sub] > 0) {
                    action.accept(CellNaming.CELL_IDS.cell(block, sub));
                }
            }
        }

        @Override
        public long position(long cell) {
            return CellNaming.CELL_IDS.position(cell);
        }

        @Override
        public CellNaming naming() {
            return CellNaming.CELL_IDS;
        }
    }

    /** A graph over a {@link Space} with a tracker listening to it. */
    static final class Fixture {
        final Space space = new Space();
        final ZoneGraph graph;
        final MembershipTracker tracker;

        Fixture(Ambient ambient) {
            GasMixture air = new GasMixture(Sim.REGISTRY, Sim.CONFIG, FULL, ambient.temperature());
            if (ambient.moles(Ambient.NITROGEN, FULL) > 0) {
                air.setMoles(Sim.N2, ambient.moles(Ambient.NITROGEN, FULL));
                air.setMoles(Sim.O2, ambient.moles(Ambient.OXYGEN, FULL));
            }
            graph = new ZoneGraph(
                    Sim.REGISTRY,
                    Sim.CONFIG,
                    space,
                    air,
                    ExchangeSettings.DEFAULT,
                    ConservationLedger.of(Sim.REGISTRY, Sim.CONFIG));
            tracker = new MembershipTracker(CellNaming.CELL_IDS, graph::cellVolume);
            graph.setMembershipListener(tracker);
        }

        Fixture() {
            this(Ambient.STANDARD_AIR);
        }

        Zone zone(int x0, int y0, int z0, int x1, int y1, int z1) {
            List<Long> cells = new ArrayList<>();
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        space.forEachCellAt(SectionKeys.block(x, y, z), cells::add);
                    }
                }
            }
            return graph.createZone(cells.stream().mapToLong(Long::longValue).toArray());
        }

        /** Changes a position's geometry and refreshes it and its neighbours, as the engine does. */
        void setBlock(int x, int y, int z, double... subVolumes) {
            long b = SectionKeys.block(x, y, z);
            List<Long> changed = new ArrayList<>();
            List<Long> around = new ArrayList<>();
            space.forEachCellAt(b, changed::add);
            space.set(x, y, z, subVolumes);
            space.forEachCellAt(b, changed::add);
            for (Face f : Face.values()) {
                space.forEachCellAt(offset(b, f), around::add);
            }
            // Sub-cells that stopped existing are refreshed too.
            for (int sub = 0; sub < CellIds.MAX_SUB_CELLS; sub++) {
                changed.add(CellNaming.CELL_IDS.cell(b, sub));
            }
            long[] first = changed.stream()
                    .mapToLong(Long::longValue)
                    .sorted()
                    .distinct()
                    .toArray();
            for (long c : first) {
                graph.refresh(c);
            }
            for (long c : around.stream()
                    .mapToLong(Long::longValue)
                    .sorted()
                    .distinct()
                    .toArray()) {
                if (Arrays.binarySearch(first, c) < 0) {
                    graph.refresh(c);
                }
            }
        }

        long cell(int x, int y, int z) {
            return CellIds.of(x, y, z);
        }
    }

    private static long offset(long block, Face f) {
        return SectionKeys.block(
                SectionKeys.blockX(block) + f.dx(),
                SectionKeys.blockY(block) + f.dy(),
                SectionKeys.blockZ(block) + f.dz());
    }

    // ---- The check -------------------------------------------------------------------------

    /** Every answer of the published table, checked against the graph by brute force. */
    private static Membership check(Fixture f) {
        Membership m = f.tracker.publish();
        assertEquals(f.graph.zoneCount(), m.size(), "zones");
        int i = 0;
        Set<Long> sections = new HashSet<>();
        for (Zone z : f.graph.zones()) {
            assertEquals(z.id(), m.zoneIds[i], "zone order");
            long[] expected = z.cells();
            Arrays.sort(expected);
            long[] got = m.cells(i);
            Arrays.sort(got);
            assertArrayEquals(expected, got, () -> "cells of " + z);
            assertEquals(z.volume(), m.zoneVolume(i), 1e-9 * Math.max(1, z.volume()), () -> "volume of " + z);
            long[] chunks = new long[expected.length];
            for (int k = 0; k < expected.length; k++) {
                long block = CellNaming.CELL_IDS.position(expected[k]);
                chunks[k] = SectionKeys.chunkOfBlockKey(block);
                sections.add(SectionKeys.sectionOfBlockKey(block));
                assertEquals(z.id(), m.zoneOfCell(expected[k]), "zone of a cell");
            }
            assertArrayEquals(Membership.sortedDistinct(chunks), m.chunks(i), () -> "chunks of " + z);
            i++;
        }
        long[] keys = sections.stream().mapToLong(Long::longValue).sorted().toArray();
        assertArrayEquals(keys, m.sectionKeys, "sections holding zoned cells");
        int positions = 0;
        for (long block : f.space.blocks()) {
            long[] best = {0};
            double[] bestVolume = {-1};
            boolean[] any = {false};
            f.space.forEachCellAt(block, c -> {
                Zone z = f.graph.zoneOf(c);
                assertEquals(z == null ? 0 : z.id(), m.zoneOfCell(c), "zone of a cell");
                if (z != null) {
                    any[0] = true;
                    if (f.graph.cellVolume(c) > bestVolume[0]) {
                        bestVolume[0] = f.graph.cellVolume(c);
                        best[0] = z.id();
                    }
                }
            });
            assertEquals(best[0], m.zoneAt(block), () -> "zone at " + block);
            positions += any[0] ? 1 : 0;
        }
        assertEquals(positions, m.positions(), "zoned positions");
        return m;
    }

    /** Every answer a table gives for the positions of a box, to compare later (API-046). */
    private static Map<Long, Long> answers(Membership m, int x0, int y0, int z0, int x1, int y1, int z1) {
        Map<Long, Long> out = new TreeMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    long block = SectionKeys.block(x, y, z);
                    out.put(block, m.zoneAt(block));
                    for (int sub = 0; sub < CellIds.MAX_SUB_CELLS; sub++) {
                        long c = CellNaming.CELL_IDS.cell(block, sub);
                        out.put(c ^ Long.MIN_VALUE, m.zoneOfCell(c)); // keyed apart from blocks
                    }
                }
            }
        }
        for (int i = 0; i < m.size(); i++) {
            long[] cells = m.cells(i);
            Arrays.sort(cells);
            out.put(-1L - i, Arrays.hashCode(cells) * 31L + m.zoneIds[i]);
        }
        return out;
    }

    // ---- Tests -----------------------------------------------------------------------------

    @Test
    void core_025_local_edit_publishes_only_its_section() {
        Fixture f = new Fixture();
        f.space.box(0, 64, 0, 47, 67, 47); // 3 × 1 × 3 sections
        Zone z = f.zone(0, 64, 0, 47, 67, 47);
        Membership before = check(f);
        assertEquals(9, before.sectionKeys.length);

        f.setBlock(24, 65, 24); // fill one cell in the middle section
        Membership after = check(f);
        long edited = SectionKeys.sectionOfBlock(24, 65, 24);
        assertArrayEquals(before.sectionKeys, after.sectionKeys);
        for (int i = 0; i < before.sectionKeys.length; i++) {
            if (before.sectionKeys[i] == edited) {
                assertNotSame(before.sections[i], after.sections[i], "the edited section is a new copy");
            } else {
                assertSame(before.sections[i], after.sections[i], "an unchanged section is shared");
            }
        }
        assertSame(before.zones[0], after.zones[0], "the zone's section list didn't change");
        assertEquals(z.id(), after.zoneAt(SectionKeys.block(24, 64, 24)));
        assertEquals(0, after.zoneAt(SectionKeys.block(24, 65, 24)));
        // Nothing changed since: publishing again returns the same table.
        assertSame(after, f.tracker.publish());
    }

    @Test
    void core_023_merge_renames_palette_entries() {
        Fixture f = new Fixture();
        f.space.box(0, 64, 0, 47, 65, 15);
        Zone small = f.zone(0, 64, 0, 15, 65, 15); // section x = 0
        Zone big = f.zone(16, 64, 0, 47, 65, 15); // sections x = 1 and 2
        Membership before = check(f);

        Zone kept = f.graph.merge(small, big);
        assertSame(big, kept);
        assertFalse(small.isAlive());
        Membership after = check(f);
        assertEquals(1, after.size());
        assertEquals(3, after.zones[0].sections().length);
        // The absorbed zone's section was renamed, not relabelled: its positions are shared.
        int s0 = Arrays.binarySearch(after.sectionKeys, SectionKeys.sectionOfBlock(0, 64, 0));
        assertTrue(after.sections[s0].sharesPositions(before.sections[s0]), "a merge copies no positions");
        for (int i = 0; i < before.sectionKeys.length; i++) {
            if (i != s0) {
                assertSame(before.sections[i], after.sections[i], "the surviving zone's sections are untouched");
            }
        }
        assertEquals(big.id(), after.zoneAt(SectionKeys.block(3, 64, 3)));
        // The table before the merge still answers as it did.
        assertEquals(small.id(), before.zoneAt(SectionKeys.block(3, 64, 3)));
    }

    @Test
    void core_024_split_moves_the_remnant_to_its_own_entry() {
        Fixture f = new Fixture();
        f.space.box(0, 64, 0, 30, 64, 4);
        for (int z = 0; z <= 4; z++) {
            if (z != 2) {
                f.space.set(8, 64, z); // a wall at x = 8 with a hole at z = 2
            }
        }
        Zone zone = f.zone(0, 64, 0, 30, 64, 4);
        check(f);

        f.setBlock(8, 64, 2); // close the hole
        assertEquals(2, f.graph.zoneCount(), "the zone split");
        Membership m = check(f);
        long west = m.zoneAt(SectionKeys.block(1, 64, 1));
        long east = m.zoneAt(SectionKeys.block(29, 64, 1));
        assertTrue(west != east && west != 0 && east != 0);
        assertTrue(west == zone.id() || east == zone.id(), "one part keeps the id");
        // x = 0 to 7 and 9 to 15 are both in section x = 0: it holds both zones.
        int s0 = Arrays.binarySearch(m.sectionKeys, SectionKeys.sectionOfBlock(0, 64, 0));
        assertEquals(2, m.sections[s0].paletteSize() - 1, "two palette entries, one per zone");
    }

    @Test
    void core_021_join_and_leave_change_one_position() {
        Fixture f = new Fixture();
        f.space.box(0, 64, 0, 40, 64, 4);
        Zone zone = f.zone(0, 64, 0, 39, 64, 4);
        Membership before = check(f);
        long cell = f.cell(40, 64, 2);

        f.graph.join(cell, zone);
        Membership joined = check(f);
        assertEquals(zone.id(), joined.zoneOfCell(cell));
        assertEquals(0, before.zoneOfCell(cell), "the earlier table is unchanged");
        long section = SectionKeys.sectionOfBlock(40, 64, 2);
        for (int i = 0; i < joined.sectionKeys.length; i++) {
            if (joined.sectionKeys[i] != section) {
                assertSame(before.sections[i], joined.sections[i]);
            }
        }

        f.graph.leave(cell);
        Membership left = check(f);
        assertEquals(0, left.zoneOfCell(cell));
        assertEquals(zone.id(), joined.zoneOfCell(cell), "the earlier table is unchanged");
    }

    @Test
    void core_076_dissolved_zone_leaves_the_membership() {
        Fixture f = new Fixture(Ambient.VACUUM);
        f.space.box(0, 64, 0, 5, 64, 5);
        Zone zone = f.zone(0, 64, 0, 4, 64, 4); // x = 5 is open: an ambient edge
        assertNotNull(zone.ambientEdge());
        Membership before = check(f);

        f.graph.exchange(); // holds vacuum, as the ambient does: dissolves
        assertFalse(zone.isAlive());
        Membership after = check(f);
        assertEquals(0, after.size());
        assertEquals(0, after.sectionKeys.length);
        assertEquals(0, after.positions());
        assertEquals(zone.id(), before.zoneAt(SectionKeys.block(1, 64, 1)));
    }

    @Test
    void core_070_restore_rebuilds_a_merged_zone() {
        Fixture f = new Fixture();
        f.space.box(0, 64, 0, 40, 65, 6);
        Zone a = f.zone(0, 64, 0, 9, 65, 6);
        Zone b = f.zone(10, 64, 0, 40, 65, 6);
        Membership undo = check(f);
        int ia = undo.indexOf(a.id());
        GasMixture copy = a.mixture().copy();
        long[] cellsBefore = undo.cells(ia);

        f.graph.merge(a, b);
        check(f);
        assertNull(f.graph.zone(a.id()));

        // Restoring reads the zone's cells back from the undo table (architecture §3.3).
        Zone restored = f.graph.restoreZone(a.id(), undo.cells(ia), copy, false);
        Membership after = check(f);
        int ir = after.indexOf(restored.id());
        long[] got = after.cells(ir);
        long[] want = cellsBefore.clone();
        Arrays.sort(got);
        Arrays.sort(want);
        assertArrayEquals(want, got);
        assertArrayEquals(cellsBefore, undo.cells(ia), "the undo table is unchanged");
    }

    @Test
    void geo_004_sub_cells_use_the_side_table() {
        Fixture f = new Fixture();
        f.space.box(0, 64, 0, 4, 64, 0);
        f.space.set(2, 64, 0, 300, 700); // a pane: two sub-cells, the second larger
        long p0 = CellIds.of(2, 64, 0, 0);
        long p1 = CellIds.of(2, 64, 0, 1);
        long block = SectionKeys.block(2, 64, 0);
        Zone x = f.graph.createZone(p0, f.cell(1, 64, 0), f.cell(0, 64, 0));
        Membership m = check(f);
        assertEquals(x.id(), m.zoneAt(block), "the only zoned cell is sub-cell 0");
        assertEquals(0, m.zoneOfCell(p1));

        Zone y = f.graph.createZone(p1, f.cell(3, 64, 0), f.cell(4, 64, 0));
        m = check(f);
        assertEquals(x.id(), m.zoneOfCell(p0));
        assertEquals(y.id(), m.zoneOfCell(p1));
        assertEquals(y.id(), m.zoneAt(block), "the position answers with its largest cell (API-015)");

        f.graph.leave(p1);
        m = check(f);
        assertEquals(x.id(), m.zoneAt(block), "back to sub-cell 0 alone");
        assertEquals(0, m.zoneOfCell(p1));

        f.graph.join(p1, y);
        f.graph.leave(p0);
        m = check(f);
        assertEquals(y.id(), m.zoneAt(block), "sub-cell 1 alone");
        assertEquals(0, m.zoneOfCell(p0));

        // The second sub-cell shrinks below where the first would be; with one zoned cell it still answers.
        f.setBlock(2, 64, 0, 300, 100);
        m = check(f);
        assertEquals(y.id(), m.zoneAt(block));
        f.graph.join(p0, x);
        m = check(f);
        assertEquals(x.id(), m.zoneAt(block), "now sub-cell 0 is the larger");
    }

    @Test
    void api_046_published_membership_never_changes() {
        Fixture f = new Fixture(Ambient.VACUUM);
        f.space.box(0, 64, 0, 35, 66, 20);
        f.space.set(5, 65, 5, 400, 600);
        for (int z = 0; z <= 20; z++) {
            if (z != 10) {
                for (int y = 64; y <= 66; y++) {
                    f.space.set(18, y, z);
                }
            }
        }
        Zone zone = f.zone(0, 64, 0, 35, 66, 19); // z = 20 is open to the ambient
        Membership first = check(f);
        Map<Long, Long> firstAnswers = answers(first, 0, 64, 0, 35, 66, 20);

        f.setBlock(18, 65, 10); // a hole of 3 high: two cells stay open
        f.setBlock(18, 64, 10);
        f.setBlock(18, 66, 10); // now split
        assertEquals(2, f.graph.zoneCount());
        Membership second = check(f);
        Map<Long, Long> secondAnswers = answers(second, 0, 64, 0, 35, 66, 20);
        f.setBlock(18, 65, 10, FULL); // reopen: the parts touch again, but don't merge until exchange
        f.graph.leave(f.cell(1, 64, 1));
        f.graph.exchange(); // vacuum zones dissolve
        check(f);

        assertEquals(firstAnswers, answers(first, 0, 64, 0, 35, 66, 20), "the first table answers as it did");
        assertEquals(secondAnswers, answers(second, 0, 64, 0, 35, 66, 20), "the second table answers as it did");
        assertEquals(zone.id(), first.zoneAt(SectionKeys.block(1, 64, 1)));
    }

    /**
     * Through the pipeline: a published snapshot answers the same by position and by cell after
     * later ticks merge, split and reject (API-046), and a rejected merge publishes both zones'
     * cells as they were (CORE-070).
     */
    @Test
    void api_046_published_snapshots_never_change_across_ticks() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(1, 64, 1, 3, 66, 3);
        sim.world.room(5, 64, 1, 7, 66, 3);
        sim.add(2, 65, 2, 10, 0);
        sim.add(6, 65, 2, 10, 0);
        sim.step();
        AtmosSnapshot first = sim.snapshot();
        long a = first.zoneAt(2, 65, 2).id();
        long b = first.zoneAt(6, 65, 2).id();
        Map<Long, Long> firstAnswers = snapshotAnswers(first);
        assertEquals(a, first.zoneOfCell(CellIds.of(3, 66, 3)).id());
        assertNull(first.zoneOfCell(CellIds.of(4, 65, 2)), "the wall holds no cell");

        // The wall opens and the rooms merge, but the tick is rejected: both stay as they were.
        sim.world.set(4, 65, 2, TestWorld.AIR);
        sim.betweenCloseAndApply = s -> {
            s.world.unload(SectionKeys.chunk(0, 0));
            s.pipeline.chunkUnloaded(SectionKeys.chunk(0, 0));
            s.world.load(SectionKeys.chunk(0, 0));
            s.pipeline.chunkLoaded(SectionKeys.chunk(0, 0));
        };
        assertEquals(1, sim.step().rejected());
        sim.betweenCloseAndApply = s -> {};
        AtmosSnapshot rejected = sim.snapshot();
        assertEquals(a, rejected.zoneOfCell(CellIds.of(2, 65, 2)).id(), "answered from the previous table");
        assertEquals(b, rejected.zoneOfCell(CellIds.of(6, 65, 2)).id(), "answered from the previous table");

        sim.step(); // the merge again, accepted
        AtmosSnapshot merged = sim.snapshot();
        assertEquals(1, merged.zoneCount());
        long kept = merged.zoneAt(2, 65, 2).id();
        assertEquals(kept, merged.zoneOfCell(CellIds.of(4, 65, 2)).id());
        assertEquals(kept, merged.zoneOfCell(CellIds.of(6, 65, 2)).id());
        Map<Long, Long> mergedAnswers = snapshotAnswers(merged);

        sim.world.set(4, 65, 2, TestWorld.SOLID); // split again
        sim.steps(2);
        assertEquals(2, sim.snapshot().zoneCount());

        assertEquals(firstAnswers, snapshotAnswers(first), "the first snapshot changed");
        assertEquals(mergedAnswers, snapshotAnswers(merged), "the merged snapshot changed");
    }

    private static Map<Long, Long> snapshotAnswers(AtmosSnapshot s) {
        Map<Long, Long> out = new TreeMap<>();
        for (int x = 0; x <= 8; x++) {
            for (int y = 63; y <= 67; y++) {
                for (int z = 0; z <= 4; z++) {
                    ZoneView at = s.zoneAt(x, y, z);
                    ZoneView of = s.zoneOfCell(CellIds.of(x, y, z));
                    out.put(
                            SectionKeys.block(x, y, z),
                            (at == null ? 0 : at.id()) * 1_000_003 + (of == null ? 0 : of.id()));
                }
            }
        }
        return out;
    }

    @Test
    void core_020_membership_follows_the_graph_fuzz() {
        Random random = new Random(20261101);
        Fixture f = new Fixture();
        int nx = 36;
        int ny = 4;
        int nz = 36;
        for (int x = 0; x < nx; x++) {
            for (int y = 64; y < 64 + ny; y++) {
                for (int z = 0; z < nz; z++) {
                    int r = random.nextInt(20);
                    if (r < 3) {
                        continue; // solid
                    } else if (r == 3) {
                        f.space.set(x, y, z, 250, 750);
                    } else if (r == 4) {
                        f.space.set(x, y, z, 200, 300, 500);
                    } else {
                        f.space.set(x, y, z, FULL);
                    }
                }
            }
        }
        List<Membership> tables = new ArrayList<>();
        List<Map<Long, Long>> tableAnswers = new ArrayList<>();
        int done = 0;
        for (int step = 0; step < 1500; step++) {
            int op = random.nextInt(10);
            int x = random.nextInt(nx);
            int y = 64 + random.nextInt(ny);
            int z = random.nextInt(nz);
            long block = SectionKeys.block(x, y, z);
            List<Long> cells = new ArrayList<>();
            f.space.forEachCellAt(block, cells::add);
            long cell = cells.isEmpty() ? CellIds.NONE : cells.get(random.nextInt(cells.size()));
            boolean zoned = cell != CellIds.NONE && f.graph.zoneOf(cell) != null;
            switch (op) {
                case 0, 1 -> {
                    if (cell != CellIds.NONE && !zoned) {
                        f.graph.createZone(region(f, cell, 1 + random.nextInt(200)));
                        done++;
                    }
                }
                case 2 -> {
                    if (cell != CellIds.NONE && !zoned) {
                        Zone[] next = {null};
                        f.space.forEachOpenNeighbour(cell, (n, direct) -> {
                            if (next[0] == null && f.graph.zoneOf(n) != null) {
                                next[0] = f.graph.zoneOf(n);
                            }
                        });
                        if (next[0] != null) {
                            f.graph.join(cell, next[0]);
                            done++;
                        }
                    }
                }
                case 3 -> {
                    if (zoned) {
                        f.graph.leave(cell);
                        done++;
                    }
                }
                case 4, 5 -> {
                    int r = random.nextInt(4);
                    double[] shape = r == 0
                            ? new double[0]
                            : r == 1 ? new double[] {FULL} : r == 2 ? new double[] {250, 750} : new double[] {600, 400};
                    f.setBlock(x, y, z, shape);
                    done++;
                }
                case 6 -> {
                    f.graph.exchange();
                    done++;
                }
                case 7 -> {
                    if (zoned) {
                        Zone a = f.graph.zoneOf(cell);
                        for (var e : a.edges()) {
                            Zone b = e.other(a);
                            if (b != null && e.directConnections() > 0 && !a.isPaused() && !b.isPaused()) {
                                f.graph.merge(a, b);
                                done++;
                                break;
                            }
                        }
                    }
                }
                case 8 -> {
                    if (zoned && random.nextInt(4) == 0) {
                        f.graph.evict(f.graph.zoneOf(cell));
                        done++;
                    }
                }
                case 9 -> {
                    if (cell != CellIds.NONE && !zoned) {
                        long id = random.nextBoolean() || f.graph.zoneCount() == 0
                                ? f.graph.nextZoneId() + random.nextInt(3)
                                : f.graph.zones().iterator().next().id();
                        GasMixture gas = new GasMixture(Sim.REGISTRY, Sim.CONFIG, 1, Sim.ROOM_T);
                        gas.setMoles(Sim.N2, 1);
                        f.graph.loadShare(id, region(f, cell, 1 + random.nextInt(20)), gas, false);
                        done++;
                    }
                }
                default -> throw new AssertionError(op);
            }
            Membership m = check(f);
            if (step % 100 == 0) {
                tables.add(m);
                tableAnswers.add(answers(m, 0, 64, 0, nx - 1, 63 + ny, nz - 1));
            }
        }
        assertTrue(done > 600, "too few operations applied: " + done);
        for (int i = 0; i < tables.size(); i++) {
            assertEquals(
                    tableAnswers.get(i),
                    answers(tables.get(i), 0, 64, 0, nx - 1, 63 + ny, nz - 1),
                    "published table " + i + " changed");
        }
    }

    /** Up to {@code max} unzoned cells connected to {@code start}, breadth first. */
    private static long[] region(Fixture f, long start, int max) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        List<Long> out = new ArrayList<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && out.size() < max) {
            long c = queue.poll();
            out.add(c);
            f.space.forEachOpenNeighbour(c, (n, direct) -> {
                if (direct && f.graph.zoneOf(n) == null && seen.add(n)) {
                    queue.add(n);
                }
            });
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }
}
