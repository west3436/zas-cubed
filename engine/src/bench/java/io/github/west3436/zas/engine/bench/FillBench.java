package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.classify.EnclosureFill;
import io.github.west3436.zas.engine.classify.FillResult;
import io.github.west3436.zas.engine.classify.OpenCells;
import io.github.west3436.zas.engine.classify.SectionTopology;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.OpOutcome;
import java.util.HashMap;
import java.util.Map;

/**
 * GEO-020 and GEO-022: the bounded fill's cost, and that it runs on the worker, not the server thread.
 * GEO-021 and GEO-027: what finding a cell open without a fill saves.
 */
final class FillBench {
    /** Keeps the JIT from dropping the lookups being timed. */
    private static long sink;

    private FillBench() {}

    static void run(boolean quick) {
        EngineBench.section("Bounded fill (GEO-020, GEO-022)");
        direct(quick);
        pipeline(quick);
        EngineBench.section("Open classification (GEO-021, GEO-027)");
        openAlone(quick);
        openPipeline(quick);
        if (sink == 42) {
            System.out.println();
        }
    }

    /** The fill alone, on the worker's view of the sections, as spike S3 measured it. */
    private static void direct(boolean quick) {
        int warm = quick ? 5 : 30;
        int runs = quick ? 10 : 50;
        System.out.println("Fill alone (`EnclosureFill` on a `SectionTopology`), " + runs + " runs after " + warm
                + " warm-up runs:");
        System.out.println();
        System.out.println("| Case | Outcome | Visited | Mean | Median | p99 | Max | Cells/s (median) |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.SOLID);
        w.box(0, 0, 0, 63, 15, 63, BenchWorld.AIR); // 65,536 cells
        w.box(100, 0, 0, 163, 15, 63, BenchWorld.AIR).set(164, 0, 0, BenchWorld.AIR); // 65,537 cells
        w.box(200, 0, 0, 219, 19, 19, BenchWorld.AIR); // 8,000 cells
        w.box(300, 0, 0, 302, 2, 2, BenchWorld.AIR); // 27 cells
        Map<Long, SectionSnapshot> cache = new HashMap<>();
        SectionTopology topology =
                new SectionTopology(BenchWorld.PALETTE, key -> cache.computeIfAbsent(key, w::snapshot));
        EnclosureFill fill = new EnclosureFill();
        row("Sealed hall, 65,536 cells (the cap)", fill, topology, CellIds.of(10, 5, 10), warm, runs);
        row("Hall of 65,537 cells: open at the cap", fill, topology, CellIds.of(110, 5, 10), warm, runs);
        row("S-10's largest zone, 8,000 cells", fill, topology, CellIds.of(210, 5, 10), warm, runs);
        row("3 × 3 × 3 room, 27 cells", fill, topology, CellIds.of(301, 1, 1), warm, runs);
        System.out.println();
    }

    private static void row(String name, EnclosureFill fill, SectionTopology topology, long seed, int warm, int runs) {
        FillResult r = null;
        for (int i = 0; i < warm; i++) {
            r = fill.fill(topology, seed);
        }
        Stats s = new Stats();
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            r = fill.fill(topology, seed);
            s.add(System.nanoTime() - t0);
        }
        String outcome = r instanceof FillResult.Enclosed ? "enclosed" : r instanceof FillResult.Open ? "open" : "?";
        double cellsPerSecond = r.visited() / (s.medianMs() / 1000);
        System.out.printf(
                "| %s | %s | %,d | %s | %.1f M |%n", name, outcome, r.visited(), s.row(), cellsPerSecond / 1e6);
    }

    /**
     * A 65,536-cell hall zoned through the whole pipeline (GEO-022): the add closes into a batch,
     * the worker fills and zones the hall, and the server thread applies the result. Each round
     * uses a fresh hall in a fresh engine.
     */
    private static void pipeline(boolean quick) {
        int warm = quick ? 2 : 5;
        int runs = quick ? 4 : 20;
        Stats close = new Stats();
        Stats apply = new Stats();
        Stats worker = new Stats();
        Stats total = new Stats();
        int maxTicks = 0;
        for (int i = 0; i < warm + runs; i++) {
            // A new engine each round: zones from earlier rounds would add their own cost.
            BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-2, -2, 6, 6);
            w.room(0, 64, 0, 63, 79, 63);
            w.clearDirty();
            try (Harness h = new Harness(w, 20)) {
                long seq = h.add(32, 70, 32, Harness.air(65_536, 1.0), LedgerCategory.API);
                long server = 0;
                long maxClose = 0;
                long maxApply = 0;
                long maxWorker = 0;
                int ticks = 0;
                OpOutcome done = null;
                while (done == null && ticks < 10) {
                    h.step();
                    ticks++;
                    server += h.closeNanos + h.applyNanos;
                    maxClose = Math.max(maxClose, h.closeNanos);
                    maxApply = Math.max(maxApply, h.applyNanos);
                    maxWorker = Math.max(maxWorker, h.workerNanos);
                    for (OpOutcome o : h.lastApplied.outcomes()) {
                        if (o.seq() == seq) {
                            done = o;
                        }
                    }
                }
                if (done == null || done.outcome() != OpOutcome.Outcome.ZONE) {
                    throw new IllegalStateException("hall " + i + " didn't zone: " + done);
                }
                if (h.zoneAt(32, 70, 32).cellCount() != 65_536) {
                    throw new IllegalStateException("hall " + i + " zoned the wrong cells");
                }
                if (i >= warm) {
                    close.add(maxClose);
                    apply.add(maxApply);
                    worker.add(maxWorker);
                    total.add(server);
                    maxTicks = Math.max(maxTicks, ticks);
                }
            }
        }
        System.out.println("Through the pipeline: a fresh 65,536-cell sealed hall zoned by one add, " + runs
                + " rounds after " + warm + " warm-up rounds; the add finished within " + maxTicks
                + " atmos tick(s). Per round:");
        System.out.println();
        System.out.println("| Thread | Measure | Mean | Median | p99 | Max |");
        System.out.println("|---|---|---|---|---|---|");
        System.out.println("| Server | Batch close (captures the prefetch) | " + close.row() + " |");
        System.out.println("| Server | Apply (publishes the zone) | " + apply.row() + " |");
        System.out.println("| Server | Close plus apply, all ticks of the round | " + total.row() + " |");
        System.out.println("| Worker | Engine tick holding the fill | " + worker.row() + " |");
        System.out.println();
    }

    /**
     * A flat world with ground at y = 63, a cell under open sky at (32, 64, 32), and a room with a roof
     * and a hole in its side around (1, 65, 1), whose column stops at the roof.
     */
    private static BenchWorld flatWorld(BenchWorld w) {
        w.box(-160, 62, -160, 223, 63, 223, BenchWorld.SOLID);
        w.room(0, 64, 0, 2, 66, 2).set(1, 65, 3, BenchWorld.AIR);
        return w;
    }

    /** Each way of finding those two cells open, alone, on a view of the whole world's height. */
    private static void openAlone(boolean quick) {
        int warm = quick ? 5 : 30;
        int runs = quick ? 10 : 50;
        int lookups = 1000;
        System.out.println("Each way alone, " + runs + " runs after " + warm
                + " warm-up runs; the column check and the cache time " + lookups + " calls a run, and give the"
                + " cost of one:");
        System.out.println();
        System.out.println("| Way | Cell | Visited | Mean | Median | p99 | Max |");
        System.out.println("|---|---|---|---|---|---|---|");
        BenchWorld w = flatWorld(new BenchWorld(Ambient.VACUUM, BenchWorld.AIR));
        Map<Long, SectionSnapshot> cache = new HashMap<>();
        SectionTopology topology = new SectionTopology(
                BenchWorld.PALETTE,
                key -> cache.computeIfAbsent(key, w::snapshot),
                BenchWorld.MIN_SECTION_Y * 16,
                BenchWorld.MAX_SECTION_Y * 16 + 15);
        long sky = CellIds.of(32, 64, 32);
        long roofed = CellIds.of(1, 65, 1);
        EnclosureFill fill = new EnclosureFill();
        OpenCells open = new OpenCells();

        fillRow("Fill", "Open sky", fill, topology, sky, warm, runs);
        for (int i = 0; i < warm; i++) {
            columnRun(topology, sky, lookups);
        }
        Stats column = new Stats();
        for (int i = 0; i < runs; i++) {
            column.add(columnRun(topology, sky, lookups));
        }
        System.out.printf("| Column check | Open sky | 256 | %s |%n", nanos(column));

        fillRow("Fill", "Roof, hole in the side", fill, topology, roofed, warm, runs);
        Stats sections = new Stats();
        int rests = 0;
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            long[] visited = fill.visitedSections(topology);
            open.add(roofed, visited);
            sections.add(System.nanoTime() - t0);
            rests = visited.length;
        }
        System.out.printf("| Remembering the result of that fill | %d sections | | %s |%n", rests, sections.row());

        open.add(sky, topology.columnSections(sky));
        for (int i = 0; i < warm; i++) {
            lookupRun(open, sky, roofed, lookups);
        }
        Stats lookup = new Stats();
        for (int i = 0; i < runs; i++) {
            lookup.add(lookupRun(open, sky, roofed, lookups));
        }
        System.out.printf("| From the cache | Either | 0 | %s |%n", nanos(lookup));
        System.out.println();
    }

    /** A table row like {@link Stats#row()} in nanoseconds, for what's far below a microsecond. */
    private static String nanos(Stats s) {
        return String.format(
                "%.0f ns | %.0f ns | %.0f ns | %.0f ns",
                s.meanMs() * 1e6, s.medianMs() * 1e6, s.percentileMs(99) * 1e6, s.maxMs() * 1e6);
    }

    private static void fillRow(
            String way, String cell, EnclosureFill fill, SectionTopology topology, long seed, int warm, int runs) {
        FillResult r = null;
        for (int i = 0; i < warm; i++) {
            r = fill.fill(topology, seed);
        }
        Stats s = new Stats();
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            r = fill.fill(topology, seed);
            s.add(System.nanoTime() - t0);
        }
        if (!(r instanceof FillResult.Open)) {
            throw new IllegalStateException(cell + " isn't open: " + r);
        }
        System.out.printf("| %s | %s | %,d | %s |%n", way, cell, r.visited(), s.row());
    }

    /** The nanoseconds one column check takes, over {@code calls} of them. */
    private static long columnRun(SectionTopology topology, long cell, int calls) {
        long t0 = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            if (topology.column(cell, c -> false) != SectionTopology.Column.OPEN) {
                throw new IllegalStateException("the column isn't open");
            }
        }
        return (System.nanoTime() - t0) / calls;
    }

    /** The nanoseconds one cache lookup takes, over {@code calls} of them. */
    private static long lookupRun(OpenCells open, long a, long b, int calls) {
        long t0 = System.nanoTime();
        long hits = 0;
        for (int i = 0; i < calls; i++) {
            hits += open.contains((i & 1) == 0 ? a : b) ? 1 : 0;
        }
        long nanos = (System.nanoTime() - t0) / calls;
        sink += hits;
        return nanos;
    }

    /**
     * The same two cells through the whole pipeline, in a fresh engine each round: an add under open sky,
     * the first add under the roof, and the second, each a tick of its own. An add's tick counts until
     * its outcome comes back.
     */
    private static void openPipeline(boolean quick) {
        int warm = quick ? 2 : 5;
        int runs = quick ? 4 : 20;
        Stats[] close = {new Stats(), new Stats(), new Stats(), new Stats()};
        Stats[] worker = {new Stats(), new Stats(), new Stats(), new Stats()};
        int[] ticks = new int[4];
        for (int i = 0; i < warm + runs; i++) {
            BenchWorld w =
                    flatWorld(new BenchWorld(Ambient.VACUUM, BenchWorld.AIR)).loadChunks(-8, -8, 8, 8);
            w.clearDirty();
            try (Harness h = new Harness(w, 20)) {
                h.steps(3);
                long idleClose = h.closeNanos;
                long idleWorker = h.workerNanos;
                long[][] rounds = {
                    {idleClose, idleWorker, 1},
                    addUntilDone(h, 32, 64, 32),
                    addUntilDone(h, 1, 65, 1),
                    addUntilDone(h, 1, 65, 1)
                };
                if (i >= warm) {
                    for (int k = 0; k < rounds.length; k++) {
                        close[k].add(rounds[k][0]);
                        worker[k].add(rounds[k][1]);
                        ticks[k] = Math.max(ticks[k], (int) rounds[k][2]);
                    }
                }
            }
        }
        String[] names = {
            "No add, for reference", "An add under open sky", "First add under the roof", "Second add under the roof"
        };
        System.out.println("Through the pipeline, " + runs + " rounds after " + warm + " warm-up rounds:");
        System.out.println();
        System.out.println("| Tick | Atmos ticks to finish | Worker median | Worker max | Close median | Close max |");
        System.out.println("|---|---|---|---|---|---|");
        for (int k = 0; k < names.length; k++) {
            System.out.printf(
                    "| %s | %d | %s | %s | %s | %s |%n",
                    names[k],
                    ticks[k],
                    Stats.ms(worker[k].medianMs()),
                    Stats.ms(worker[k].maxMs()),
                    Stats.ms(close[k].medianMs()),
                    Stats.ms(close[k].maxMs()));
        }
        System.out.println();
    }

    /** Adds at a cell, steps until the add finishes, and gives the slowest close, worker tick and tick count. */
    private static long[] addUntilDone(Harness h, int x, int y, int z) {
        long seq = h.add(x, y, z, Harness.air(27, 1.0), LedgerCategory.API);
        long maxClose = 0;
        long maxWorker = 0;
        for (int ticks = 1; ticks <= 10; ticks++) {
            h.step();
            maxClose = Math.max(maxClose, h.closeNanos);
            maxWorker = Math.max(maxWorker, h.workerNanos);
            for (OpOutcome o : h.lastApplied.outcomes()) {
                if (o.seq() == seq) {
                    if (o.outcome() != OpOutcome.Outcome.AMBIENT) {
                        throw new IllegalStateException("(" + x + ", " + y + ", " + z + ") isn't open: " + o);
                    }
                    return new long[] {maxClose, maxWorker, ticks};
                }
            }
        }
        throw new IllegalStateException("(" + x + ", " + y + ", " + z + ") didn't finish");
    }
}
