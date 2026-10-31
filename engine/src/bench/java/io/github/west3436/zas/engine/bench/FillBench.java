package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.classify.EnclosureFill;
import io.github.west3436.zas.engine.classify.FillResult;
import io.github.west3436.zas.engine.classify.SectionTopology;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.OpOutcome;
import java.util.HashMap;
import java.util.Map;

/** GEO-020 and GEO-022: the bounded fill's cost, and that it runs on the worker, not the server thread. */
final class FillBench {
    private FillBench() {}

    static void run(boolean quick) {
        EngineBench.section("Bounded fill (GEO-020, GEO-022)");
        direct(quick);
        pipeline(quick);
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
}
