package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.LedgerCategory;

/**
 * CORE-023, CORE-024 and CORE-025 through the pipeline: splitting a 49,001-cell zone in two,
 * merging it back, and local edits that leave zones of growing size connected.
 */
final class ZoneBench {
    private ZoneBench() {}

    static void run(boolean quick) {
        EngineBench.section("Merge and split (CORE-023, CORE-024, CORE-025)");
        splitAndMerge(quick);
        localEdits(quick);
    }

    /**
     * A 50 × 20 × 50 room with a wall down the middle at x = 25 and one hole in it: 49,001 cells.
     * Filling the hole splits the zone into 25,000 + 24,000 (CORE-024); opening it again joins the
     * halves by a direct opening, and the zones merge once they're equivalent (CORE-023), which
     * they are at once, since a split shares gas by volume (CORE-064).
     */
    private static void splitAndMerge(boolean quick) {
        int warm = quick ? 2 : 5;
        int runs = quick ? 5 : 30;
        BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-3, -3, 7, 7);
        w.room(0, 64, 0, 49, 83, 49);
        w.box(25, 64, 0, 25, 83, 49, BenchWorld.SOLID);
        int[] hole = {25, 70, 25};
        w.set(hole[0], hole[1], hole[2], BenchWorld.AIR);
        w.clearDirty();
        Stats splitWorker = new Stats();
        Stats splitServer = new Stats();
        Stats mergeWorker = new Stats();
        Stats mergeServer = new Stats();
        Stats idleWorker = new Stats();
        int mergeTicks = 0;
        try (Harness h = new Harness(w, 20)) {
            h.add(10, 70, 10, Harness.air(49_001, 1.0), LedgerCategory.API);
            for (int i = 0; i < 10 && h.zoneCount() == 0; i++) {
                h.step();
            }
            check(h.zoneCount() == 1 && h.zoneAt(10, 70, 10).cellCount() == 49_001, "the room didn't zone");
            h.steps(3);
            for (int round = 0; round < warm + runs; round++) {
                boolean measured = round >= warm;
                h.step();
                if (measured) {
                    idleWorker.add(h.workerNanos);
                }
                w.set(hole[0], hole[1], hole[2], BenchWorld.SOLID);
                h.step();
                check(h.zoneCount() == 2, "no split: " + h.zoneCount() + " zones");
                check(h.zoneAt(10, 70, 10).cellCount() == 25_000, "split sizes");
                if (measured) {
                    splitWorker.add(h.workerNanos);
                    splitServer.add(h.closeNanos + h.applyNanos);
                }
                w.set(hole[0], hole[1], hole[2], BenchWorld.AIR);
                long worker = 0;
                long server = 0;
                int ticks = 0;
                while (h.zoneCount() != 1 && ticks < 20) {
                    h.step();
                    ticks++;
                    worker = Math.max(worker, h.workerNanos);
                    server = Math.max(server, h.closeNanos + h.applyNanos);
                }
                check(h.zoneCount() == 1, "no merge after " + ticks + " ticks");
                if (measured) {
                    mergeWorker.add(worker);
                    mergeServer.add(server);
                    mergeTicks = Math.max(mergeTicks, ticks);
                }
            }
            check(h.warnings.get() == 0, h.warnings.get() + " engine warnings");
        }
        System.out.println("A 49,001-cell zone split into 25,000 + 24,000 by filling the one hole in a wall, and"
                + " merged back by opening it, " + runs + " rounds after " + warm + " warm-up rounds. The merge"
                + " finished within " + mergeTicks + " atmos tick(s); its row is the slowest tick of each merge.");
        System.out.println();
        System.out.println("| Measure | Mean | Median | p99 | Max |");
        System.out.println("|---|---|---|---|---|");
        System.out.println("| Worker: idle tick, same zone, for reference | " + idleWorker.row() + " |");
        System.out.println("| Worker: tick holding the split | " + splitWorker.row() + " |");
        System.out.println("| Worker: tick holding the merge | " + mergeWorker.row() + " |");
        System.out.println("| Server: close plus apply, split tick | " + splitServer.row() + " |");
        System.out.println("| Server: close plus apply, merge tick | " + mergeServer.row() + " |");
        System.out.println();
    }

    /**
     * A block placed at the centre of zones of 1,000, 8,000 and 50,000 cells leaves each
     * connected: the split search should visit the same few cells whatever the zone's size
     * (CORE-025, spike S4 case A). Each zone is alone in its own engine, so the worker tick shows
     * everything the change costs.
     */
    private static void localEdits(boolean quick) {
        int warm = quick ? 3 : 10;
        int runs = quick ? 10 : 50;
        int[][] rooms = {
            {0, 64, 0, 9, 73, 9}, // 1,000
            {0, 64, 0, 19, 83, 19}, // 8,000
            {0, 64, 0, 49, 83, 49}, // 50,000
        };
        int[] cells = {1_000, 8_000, 50_000};
        System.out.println("A block placed at the centre of a zone and removed again, " + runs + " times after " + warm
                + " warm-up times; the zone stays whole (CORE-025). Each zone is alone in its engine:");
        System.out.println();
        System.out.println("| Zone | Split-search visits | Worker tick: mean | median | p99 | max |");
        System.out.println("|---|---|---|---|---|---|");
        for (int i = 0; i < rooms.length; i++) {
            int[] r = rooms[i];
            BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-3, -3, 7, 7);
            w.room(r[0], r[1], r[2], r[3], r[4], r[5]);
            w.clearDirty();
            try (Harness h = new Harness(w, 20)) {
                h.add(r[0] + 1, r[1] + 1, r[2] + 1, Harness.air(cells[i], 1.0), LedgerCategory.API);
                for (int k = 0; k < 10 && h.zoneCount() < 1; k++) {
                    h.step();
                }
                check(h.zoneCount() == 1, "the room didn't zone");
                int cx = (r[0] + r[3]) / 2;
                int cy = (r[1] + r[4]) / 2;
                int cz = (r[2] + r[5]) / 2;
                Stats place = new Stats();
                int visits = -1;
                for (int k = 0; k < warm + runs; k++) {
                    w.set(cx, cy, cz, BenchWorld.SOLID);
                    h.step();
                    check(h.zoneCount() == 1, "a local edit split the zone");
                    if (k >= warm) {
                        place.add(h.workerNanos);
                        visits = Math.max(visits, h.engine.graph().lastSplitVisits());
                    }
                    w.set(cx, cy, cz, BenchWorld.AIR);
                    h.step();
                }
                System.out.printf("| %,d cells | %d | %s |%n", cells[i], visits, place.row());
            }
        }
        System.out.println();
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
