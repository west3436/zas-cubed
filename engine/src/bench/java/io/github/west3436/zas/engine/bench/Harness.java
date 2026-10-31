package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.AtmosWorkers;
import io.github.west3436.zas.engine.scheduler.CellSpace;
import io.github.west3436.zas.engine.scheduler.Classification;
import io.github.west3436.zas.engine.scheduler.Classifier;
import io.github.west3436.zas.engine.scheduler.DimensionEngine;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.DimensionScheduler;
import io.github.west3436.zas.engine.scheduler.EnclosureClassifier;
import io.github.west3436.zas.engine.scheduler.EngineSettings;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * One dimension's pipeline on a {@link BenchWorld}, driven as the platform drives it: close a
 * batch on the "server thread" (the bench's main thread), let one worker thread tick the engine,
 * then apply. Close and apply are timed on the main thread; the worker's tick is timed on the
 * worker. Apply waits until the worker is done, so it measures applying and not waiting, as in
 * game, where the result is applied a fixed 10 game ticks later (CORE-071).
 */
final class Harness implements AutoCloseable {
    static final GasRegistry REGISTRY = GasRegistry.of(List.of(
            gas("zas:nitrogen", 0.028, 20.8), gas("zas:oxygen", 0.032, 21.0), gas("zas:carbon_dioxide", 0.044, 37.1)));
    static final int N2 = REGISTRY.indexOf("zas:nitrogen");
    static final int O2 = REGISTRY.indexOf("zas:oxygen");
    static final int CO2 = REGISTRY.indexOf("zas:carbon_dioxide");
    static final double ROOM_T = 293.15;
    /** CORE-047's default per-server-tick budget. */
    static final long BUDGET_NANOS = 10_000_000;

    final BenchWorld world;
    final DimensionEngine engine;
    final DimensionScheduler scheduler;
    final DimensionPipeline pipeline;
    final int periodTicks;
    final EngineSettings settings;
    private final ExecutorService pool;
    private final AtomicLong lastWorkerNanos = new AtomicLong();
    private final AtomicInteger workerRuns = new AtomicInteger();
    private int submitted;
    final AtomicLong classifications = new AtomicLong();
    final AtomicLong warnings = new AtomicLong();
    long tick;
    /** The prefetch budget each close gets; CORE-047's default unless a bench changes it. */
    long budgetNanos = BUDGET_NANOS;

    // The last tick's timings.
    long closeNanos;
    long applyNanos;
    long workerNanos;
    DimensionPipeline.Applied lastApplied;

    Harness(BenchWorld world, int periodTicks) {
        this.world = world;
        this.periodTicks = periodTicks;
        this.settings = EngineSettings.DEFAULT.withPeriodTicks(periodTicks);
        GasConfig config = GasConfig.DEFAULT.withLog(message -> warnings.incrementAndGet());
        this.engine = new DimensionEngine(
                REGISTRY,
                config,
                counting(EnclosureClassifier.FACTORY),
                BenchWorld.PALETTE,
                BenchWorld.MIN_SECTION_Y,
                BenchWorld.MAX_SECTION_Y,
                world.ambient,
                settings);
        this.pool = AtmosWorkers.create(1);
        Executor timed = r -> pool.execute(() -> {
            long start = System.nanoTime();
            try {
                r.run();
            } finally {
                lastWorkerNanos.set(System.nanoTime() - start);
                workerRuns.incrementAndGet();
            }
        });
        this.scheduler = new DimensionScheduler(engine, timed);
        this.pipeline = new DimensionPipeline(scheduler, world, BenchWorld.MIN_SECTION_Y, BenchWorld.MAX_SECTION_Y);
    }

    static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(4), molarMass, heatCapacity, Optional.empty());
    }

    /** Counts the enclosure classifications (each one a bounded fill) the engine asks for. */
    private Classifier.Factory counting(Classifier.Factory inner) {
        return (sections, palette) -> {
            Classifier c = inner.create(sections, palette);
            return new Classifier() {
                @Override
                public CellSpace space() {
                    return c.space();
                }

                @Override
                public Classification classify(ZoneGraph graph, long cell) {
                    classifications.incrementAndGet();
                    return c.classify(graph, cell);
                }

                @Override
                public void zone(ZoneGraph graph, Classification.Enclosed enclosure) {
                    c.zone(graph, enclosure);
                }

                @Override
                public void afterGeometry(ZoneGraph graph, long[] refreshedCells, LongConsumer requestSection) {
                    c.afterGeometry(graph, refreshedCells, requestSection);
                }

                @Override
                public boolean hasPending() {
                    return c.hasPending();
                }

                @Override
                public int prefetchRadius() {
                    return c.prefetchRadius();
                }
            };
        };
    }

    /** Air at standard pressure for {@code cells} full cells, scaled by {@code pressureFactor}. */
    static double[] air(int cells, double pressureFactor) {
        double volume = cells * 1000.0;
        double[] m = new double[REGISTRY.size()];
        m[N2] = Ambient.STANDARD_AIR.moles(Ambient.NITROGEN, volume) * pressureFactor;
        m[O2] = Ambient.STANDARD_AIR.moles(Ambient.OXYGEN, volume) * pressureFactor;
        return m;
    }

    long add(int x, int y, int z, double[] moles, LedgerCategory category) {
        return pipeline.add(SectionKeys.block(x, y, z), moles, ROOM_T, category, true, "bench");
    }

    long remove(int x, int y, int z, double[] moles, LedgerCategory category) {
        return pipeline.remove(SectionKeys.block(x, y, z), moles, category, true, "bench");
    }

    /**
     * One atmos tick: close (with CORE-047's budget for the prefetch, as the platform passes it),
     * wait for the worker, apply.
     */
    DimensionPipeline.Applied step() {
        tick++;
        long start = System.nanoTime();
        pipeline.close(tick, settings, () -> System.nanoTime() - start > budgetNanos);
        closeNanos = System.nanoTime() - start;
        submitted++;
        while (workerRuns.get() < submitted) {
            Thread.onSpinWait();
        }
        workerNanos = lastWorkerNanos.get();
        long applyStart = System.nanoTime();
        DimensionPipeline.Applied a = pipeline.apply();
        applyNanos = System.nanoTime() - applyStart;
        if (a.fault() != null) {
            throw new IllegalStateException("the engine faulted", a.fault());
        }
        lastApplied = a;
        return a;
    }

    void steps(int n) {
        for (int i = 0; i < n; i++) {
            step();
        }
    }

    AtmosSnapshot snapshot() {
        return pipeline.published();
    }

    ZoneView zoneAt(int x, int y, int z) {
        return snapshot().zoneAt(x, y, z);
    }

    int zoneCount() {
        return snapshot().zoneCount();
    }

    @Override
    public void close() {
        scheduler.abandon();
        pool.shutdownNow();
    }
}
