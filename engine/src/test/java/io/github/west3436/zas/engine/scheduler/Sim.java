package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A headless stand-in for the platform: a {@link TestWorld}, a {@link DimensionPipeline} and its
 * engine, driven one atmos tick at a time (close, then apply), with every outcome and event kept.
 */
final class Sim {
    static final GasRegistry REGISTRY =
            GasRegistry.of(List.of(gas("zas:nitrogen", 0.028, 20.8), gas("zas:oxygen", 0.032, 21.0)));
    static final int N2 = REGISTRY.indexOf("zas:nitrogen");
    static final int O2 = REGISTRY.indexOf("zas:oxygen");
    static final double ROOM_T = 293.15;
    /** Fails the test on any engine warning. */
    static final GasConfig CONFIG = GasConfig.DEFAULT.withLog(message -> {
        throw new AssertionError("unexpected engine warning: " + message);
    });

    final TestWorld world;
    final DimensionEngine engine;
    final DimensionScheduler scheduler;
    final DimensionPipeline pipeline;
    EngineSettings settings = EngineSettings.DEFAULT.withConservationCheck(true);
    long tick;
    final Map<Long, OpOutcome> outcomes = new HashMap<>();
    final Map<Long, Integer> completions = new HashMap<>();
    final List<OpenAirEvent> events = new ArrayList<>();
    final java.util.Set<Long> addSeqs = new java.util.HashSet<>();
    final List<DimensionPipeline.Applied> applied = new ArrayList<>();
    /** Runs between close and apply, as the server's game ticks in between would. */
    Consumer<Sim> betweenCloseAndApply = s -> {};

    Sim(Ambient ambient) {
        this(new TestWorld(ambient), null, EnclosureClassifier.FACTORY);
    }

    Sim(TestWorld world, Executor executor, Classifier.Factory classifier) {
        this.world = world;
        this.engine = new DimensionEngine(
                REGISTRY,
                CONFIG,
                classifier,
                TestWorld.PALETTE,
                TestWorld.MIN_SECTION_Y,
                TestWorld.MAX_SECTION_Y,
                world.ambient,
                settings);
        this.scheduler = new DimensionScheduler(engine, executor);
        this.pipeline = new DimensionPipeline(scheduler, world, TestWorld.MIN_SECTION_Y, TestWorld.MAX_SECTION_Y);
    }

    static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(4), molarMass, heatCapacity, Optional.empty());
    }

    static double[] moles(double n2, double o2) {
        double[] m = new double[REGISTRY.size()];
        m[N2] = n2;
        m[O2] = o2;
        return m;
    }

    long add(int x, int y, int z, double n2, double o2) {
        long seq = pipeline.add(SectionKeys.block(x, y, z), moles(n2, o2), ROOM_T, LedgerCategory.API, true, "test");
        addSeqs.add(seq);
        return seq;
    }

    long remove(int x, int y, int z, double n2, double o2) {
        return pipeline.remove(SectionKeys.block(x, y, z), moles(n2, o2), LedgerCategory.API, true, "test");
    }

    /** One atmos tick: close, the hook, apply. */
    DimensionPipeline.Applied step() {
        tick++;
        pipeline.close(tick, settings, () -> false);
        betweenCloseAndApply.accept(this);
        DimensionPipeline.Applied a = pipeline.apply();
        assertNull(a.fault(), () -> "engine faulted: " + a.fault());
        if (a.conservation() != null) {
            assertTrue(
                    a.conservation().conserved(),
                    () -> "conservation: " + a.conservation().violations());
        }
        for (OpOutcome o : a.outcomes()) {
            outcomes.put(o.seq(), o);
            completions.merge(o.seq(), 1, Integer::sum);
        }
        events.addAll(a.events());
        applied.add(a);
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
}
