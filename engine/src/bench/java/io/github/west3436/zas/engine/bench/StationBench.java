package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.bench.HeapAccounting.Part;
import io.github.west3436.zas.engine.bench.HeapAccounting.Scale;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
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

    /** NFR-004's readings: rough GC'd differences, and the retained-size accounting. */
    static final class Heap {
        long baseline;
        long world;
        long running;
        HeapAccounting station;
        int zones;
        long cells;
        int positions;
        HeapAccounting sparse;
        int sparseZones;
        long sparseCells;

        void count(AtmosSnapshot s, boolean sparseCase) {
            long n = 0;
            for (ZoneView z : s.zones()) {
                n += z.cellCount();
            }
            if (sparseCase) {
                sparseZones = s.zoneCount();
                sparseCells = n;
            } else {
                zones = s.zoneCount();
                cells = n;
                positions = s.zonedPositions();
            }
        }
    }

    static void run(boolean quick) {
        EngineBench.section("S-10 engine load (CORE-045, CORE-046, CORE-047; NFR-001, NFR-002, NFR-005)");
        int minutes = quick ? 2 : 10;
        int warmMinutes = quick ? 1 : 2;

        Station probe = new Station(20, true);
        System.out.printf(
                "Station: %d rooms, %,d zoned cells, the largest %,d; %d open doors between them, each"
                        + " joining the room on one side, so %d zones and %d door edges. Vacuum ambient.%n",
                probe.points.size(),
                probe.zonedCells(),
                Station.BIG_SIZE * Station.BIG_SIZE * Station.BIG_SIZE,
                probe.doors,
                probe.points.size(),
                probe.doors);
        probe.h.close();
        System.out.println("Script: " + minutes + " minutes of game time measured after " + warmMinutes
                + " minutes of warm-up; a breach of the big room every 60 s, resealed and topped up 10 s later; an"
                + " explosion breach of the big room (radius 3 at its wall) halfway through. \"Ops\" runs add"
                + " spike S5's 408 engine gas operations per second (300 device and 108 breather stand-ins).");
        System.out.println();

        Heap heap = new Heap();
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
        printHeap(heap);

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

    /**
     * Builds a station, pressurizes it, warms up and scripts it. With {@code heap}, the heap is
     * measured after the warm-up, and once more after the run with 1,000 sparse idle zones added.
     */
    static Run script(int periodTicks, boolean withOps, int minutes, int warmMinutes, Heap heap) {
        if (heap != null) {
            heap.baseline = EngineBench.usedHeap();
        }
        Station st = new Station(periodTicks, withOps);
        st.h.world.clearDirty();
        if (heap != null) {
            heap.world = EngineBench.usedHeap() - heap.baseline;
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
                heap.running = EngineBench.usedHeap() - heap.baseline;
                heap.station = HeapAccounting.of(h);
                heap.count(h.snapshot(), false);
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
            if (heap != null) {
                // Sparse zones: 1,000 sealed 27-cell rooms, about 10 to a section.
                ScalingBench.addIdleZones(st, Harness.BUDGET_NANOS);
                h.steps(3);
                heap.sparse = HeapAccounting.of(h);
                heap.count(h.snapshot(), true);
            }
        }
        return run;
    }

    private static void printHeap(Heap heap) {
        HeapAccounting a = heap.station;
        HeapAccounting b = heap.sparse;
        int addedZones = heap.sparseZones - heap.zones;
        long addedCells = heap.sparseCells - heap.cells;
        System.out.printf(
                "**Heap (NFR-004).** Retained-size accounting (`HeapAccounting`) of one dimension's atmos"
                        + " structures after the warm-up: %,d zones, %,d zoned cells at %,d positions. \"Sparse\""
                        + " adds 1,000 sealed 27-cell rooms after the run (%,d zones and %,d cells more).%n%n",
                heap.zones, heap.cells, heap.positions, addedZones, addedCells);
        System.out.println("| Part | Scales with | S-10 | Per zoned cell | Per zone | Sparse: per added cell |"
                + " per added zone |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (Part p : Part.values()) {
            long delta = b.bytes(p) - a.bytes(p);
            System.out.printf(
                    "| %s | %s | %s | %.1f B | %.0f B | %.1f B | %.0f B |%n",
                    p.label,
                    p.scale.name().toLowerCase(java.util.Locale.ROOT),
                    mib(a.bytes(p)),
                    a.bytes(p) / (double) heap.cells,
                    a.bytes(p) / (double) heap.zones,
                    delta / (double) addedCells,
                    delta / (double) addedZones);
        }
        System.out.printf(
                "| **Total** | | %s | %.1f B | %.0f B | %.1f B | %.0f B |%n%n",
                mib(a.total()),
                a.total() / (double) heap.cells,
                a.total() / (double) heap.zones,
                (b.total() - a.total()) / (double) addedCells,
                (b.total() - a.total()) / (double) addedZones);
        System.out.println("Largest classes in each part, S-10:");
        System.out.println();
        for (Part p : Part.values()) {
            System.out.println("- " + p.label + ": " + String.join("; ", a.topClasses(p, 4)));
        }
        System.out.println();
        long perCell = a.total(Scale.CELL);
        long mirror = a.bytes(Part.MIRROR);
        long perZone = a.total(Scale.ZONE);
        long sparsePerCell = b.total(Scale.CELL) - perCell;
        long sparseMirror = b.bytes(Part.MIRROR) - mirror;
        long sparsePerZone = b.total(Scale.ZONE) - perZone;
        System.out.printf(
                "Against NFR-004 (S-10 / sparse rooms): per zoned cell, the parts that scale with cells come to"
                        + " %.1f B / %.1f B, of which the section mirror is %.1f B / %.1f B (%.1f B / %.1f B"
                        + " without it); per zone, the parts that scale with zones come to %.0f B / %.0f B; in all,"
                        + " %s.%n",
                perCell / (double) heap.cells,
                sparsePerCell / (double) addedCells,
                mirror / (double) heap.cells,
                sparseMirror / (double) addedCells,
                (perCell - mirror) / (double) heap.cells,
                (sparsePerCell - sparseMirror) / (double) addedCells,
                perZone / (double) heap.zones,
                sparsePerZone / (double) addedZones,
                mib(a.total()));
        System.out.printf(
                "Cross-check, GC'd `Runtime` difference: the world alone %s; the world and the zoned, running"
                        + " station %s, so %s for atmos, %.0f B per zoned cell.%n%n",
                mib(heap.world),
                mib(heap.running),
                mib(heap.running - heap.world),
                (heap.running - heap.world) / (double) heap.cells);
    }

    private static String mib(long bytes) {
        return String.format("%.2f MiB", bytes / 1048576.0);
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
