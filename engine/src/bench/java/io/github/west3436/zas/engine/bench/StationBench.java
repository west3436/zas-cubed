package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.ZoneView;

/**
 * The engine side of S-10 (CORE-045, CORE-046, CORE-047; the engine's share of NFR-001, NFR-002
 * and NFR-005): {@link Station} scripted for 10 minutes of game time after 2 minutes of warm-up,
 * with an explosion breach of the big room halfway through.
 */
final class StationBench {
    private StationBench() {}

    /** One run's numbers. */
    static final class Run {
        final int periodTicks;
        final Stats close = new Stats();
        final Stats apply = new Stats();
        final Stats server = new Stats();
        final Stats worker = new Stats();
        /** Per server tick: close, apply, and the game ticks between with no atmos work. */
        final Stats perServerTick = new Stats();

        long explosionClose;
        long explosionApply;
        long explosionWorker;
        int explosionBlocks;
        long sections;
        int zonesMin = Integer.MAX_VALUE;
        int zonesMax;
        long warnings;

        Run(int periodTicks) {
            this.periodTicks = periodTicks;
        }

        void record(Harness h) {
            close.add(h.closeNanos);
            apply.add(h.applyNanos);
            server.add(h.closeNanos + h.applyNanos);
            worker.add(h.workerNanos);
            perServerTick.add(h.closeNanos);
            perServerTick.add(h.applyNanos);
            for (int i = 2; i < periodTicks; i++) {
                perServerTick.add(0);
            }
            sections += h.pipeline.lastBatchSections();
            zonesMin = Math.min(zonesMin, h.zoneCount());
            zonesMax = Math.max(zonesMax, h.zoneCount());
        }

        /** Mean server-thread atmos time per server tick (NFR-001's measure), in ms. */
        double serverPerServerTickMs() {
            return server.meanMs() / periodTicks;
        }

        double maxServerTickMs() {
            return Math.max(close.maxMs(), apply.maxMs());
        }

        /** Worker time per second of game time, in ms. */
        double workerPerSecondMs() {
            return worker.meanMs() * 20.0 / periodTicks;
        }
    }

    static void run(boolean quick) {
        EngineBench.section("S-10 engine load (CORE-045, CORE-046, CORE-047; NFR-001, NFR-002, NFR-005)");
        int minutes = quick ? 2 : 10;
        int warmMinutes = quick ? 1 : 2;

        Station probe = new Station(20, true);
        System.out.printf(
                "Station: %d rooms, %,d zoned cells, the largest %,d; %d open doors, each a one-cell zone, so"
                        + " %d zones in all. Vacuum ambient.%n",
                probe.points.size(),
                probe.zonedCells(),
                Station.BIG_SIZE * Station.BIG_SIZE * Station.BIG_SIZE,
                probe.doors,
                probe.points.size() + probe.doors);
        probe.h.close();
        System.out.println("Script: " + minutes + " minutes of game time measured after " + warmMinutes
                + " minutes of warm-up; a breach of the big room every 60 s, resealed and topped up 10 s later; an"
                + " explosion breach of the big room (radius 3 at its wall) halfway through. \"Ops\" runs add"
                + " spike S5's 408 engine gas operations per second (300 device and 108 breather stand-ins).");
        System.out.println();

        long[] heap = new long[3];
        Run noOps = script(20, false, minutes, warmMinutes, null);
        Run ops = script(20, true, minutes, warmMinutes, heap);

        System.out.println("**Per atmos tick at the default 1 s period:**");
        System.out.println();
        System.out.println("| Run | Thread | Measure | Mean | Median | p99 | Max |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (Run r : new Run[] {noOps, ops}) {
            String name = r == ops ? "Ops" : "No ops";
            System.out.println("| " + name + " | Server | Batch close | " + r.close.row() + " |");
            System.out.println("| " + name + " | Server | Apply | " + r.apply.row() + " |");
            System.out.println("| " + name + " | Worker | Engine tick | " + r.worker.row() + " |");
        }
        System.out.println();
        System.out.println("**Against the budgets** (engine share only; see the notes for what is not measured):");
        System.out.println();
        System.out.println("| Run | Server ms per server tick: mean (NFR-001 ≤ 2) | p99 (NFR-001 ≤ 5) | worst"
                + " server tick (CORE-047 ≤ 10) | worst worker tick (NFR-005 ≤ 250) | explosion tick:"
                + " close / apply / worker | sections per batch | zones |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (Run r : new Run[] {noOps, ops}) {
            System.out.printf(
                    "| %s | %s | %s | %s | %s | %s / %s / %s (%d blocks) | %.1f | %d to %d |%n",
                    r == ops ? "Ops" : "No ops",
                    Stats.ms(r.serverPerServerTickMs()),
                    Stats.ms(r.perServerTick.percentileMs(99)),
                    Stats.ms(r.maxServerTickMs()),
                    Stats.ms(r.worker.maxMs()),
                    Stats.ms(r.explosionClose / 1e6),
                    Stats.ms(r.explosionApply / 1e6),
                    Stats.ms(r.explosionWorker / 1e6),
                    r.explosionBlocks,
                    r.sections / (double) r.close.count(),
                    r.zonesMin,
                    r.zonesMax);
        }
        System.out.println();
        System.out.printf(
                "Heap (NFR-004, rough): the world alone %.1f MiB; with the station zoned and running, %.1f MiB more,"
                        + " or %.0f bytes per zoned cell (all atmos structures: graph, section mirror, snapshots).%n",
                heap[1] / 1048576.0, (heap[2] - heap[1]) / 1048576.0, (heap[2] - heap[1]) / 40_000.0);
        System.out.println();

        if (!quick) {
            System.out.println("**Cost by period (CORE-046), with ops, same game time:**");
            System.out.println();
            System.out.println("| Period | Atmos ticks | Worker per tick: mean | max | Worker ms per game second |"
                    + " Server ms per server tick: mean | worst server tick |");
            System.out.println("|---|---|---|---|---|---|---|");
            for (int period : new int[] {10, 20, 40}) {
                Run r = period == 20 ? ops : script(period, true, minutes, warmMinutes, null);
                System.out.printf(
                        "| %.1f s | %d | %s | %s | %s | %s | %s |%n",
                        period / 20.0,
                        r.worker.count(),
                        Stats.ms(r.worker.meanMs()),
                        Stats.ms(r.worker.maxMs()),
                        Stats.ms(r.workerPerSecondMs()),
                        Stats.ms(r.serverPerServerTickMs()),
                        Stats.ms(r.maxServerTickMs()));
            }
            System.out.println();
        }
    }

    /** Builds a station, pressurizes it, warms up and scripts it; heap, if given, gets three readings. */
    static Run script(int periodTicks, boolean withOps, int minutes, int warmMinutes, long[] heap) {
        if (heap != null) {
            heap[0] = EngineBench.usedHeap();
        }
        Station st = new Station(periodTicks, withOps);
        st.h.world.clearDirty();
        if (heap != null) {
            heap[1] = EngineBench.usedHeap() - heap[0];
        }
        Run run = new Run(periodTicks);
        try (Harness h = st.h) {
            st.pressurize();
            int perMinute = st.ticksPerMinute();
            int t = 0;
            for (; t < warmMinutes * perMinute; t++) {
                st.scriptedStep(t);
            }
            if (heap != null) {
                heap[2] = EngineBench.usedHeap() - heap[0];
            }
            int explodeAt = t + (minutes / 2) * perMinute + perMinute / 3;
            int repairAt = explodeAt + perMinute / 6;
            int end = t + minutes * perMinute;
            for (; t < end; t++) {
                if (t == explodeAt) {
                    run.explosionBlocks = st.explode();
                    st.scriptedStep(t);
                    run.explosionClose = h.closeNanos;
                    run.explosionApply = h.applyNanos;
                    run.explosionWorker = h.workerNanos;
                } else {
                    if (t == repairAt) {
                        st.repairExplosion();
                        topUp(st);
                    }
                    st.scriptedStep(t);
                }
                run.record(h);
            }
            run.warnings = h.warnings.get();
        }
        return run;
    }

    private static void topUp(Station st) {
        int[] p = st.points.get(Station.ROOMS);
        double[] full = Harness.air(Station.BIG_SIZE * Station.BIG_SIZE * Station.BIG_SIZE, 1.0);
        ZoneView z = st.h.zoneAt(p[0], p[1], p[2]);
        double have = z == null ? 0 : z.totalMoles();
        double want = full[Harness.N2] + full[Harness.O2];
        if (want > have) {
            double f = (want - have) / want;
            full[Harness.N2] *= f;
            full[Harness.O2] *= f;
            st.h.add(p[0], p[1], p[2], full, LedgerCategory.API);
        }
    }
}
