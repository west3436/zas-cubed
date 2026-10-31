package io.github.west3436.zas.simulation;

import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.Phase;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.engine.zone.Edge;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The profiler's report on one dimension (OPS-004), for {@code /zas profile} and the periodic log
 * (OPS-012).
 *
 * <ul>
 *   <li>the time of each phase of the atmos tick on the worker, and of closing and applying on
 *       the server thread, averaged over the last {@value AtmosProfiler#WINDOW} atmos ticks, with
 *       the largest;
 *   <li>the server thread's time on atmos work per game tick, average and p99, over the last
 *       {@value AtmosService#TICK_WINDOW} game ticks (NFR-001);
 *   <li>counts of zones, edges, active edges, hotspots, pending classifications and paused zones,
 *       and the scheduler's queues.
 * </ul>
 *
 * <p>Built on the server thread; the engine's counts are read on the worker between ticks
 * ({@link DimensionAtmos#inspect}), so building completes on the worker.
 *
 * @param counts the engine's counts, or null if the dimension has no engine
 */
public record ProfileReport(
        ResourceLocation dimension,
        boolean singleThreaded,
        boolean faulted,
        int samples,
        Map<Phase, AtmosProfiler.Timing> phases,
        AtmosProfiler.Timing worker,
        AtmosProfiler.Timing close,
        AtmosProfiler.Timing apply,
        long applyWaits,
        long rejectedComponents,
        int queuedOps,
        int pendingMutations,
        int lastBatchSections,
        AtmosService.TickTimes server,
        Counts counts) {

    /**
     * The engine's counts.
     *
     * @param zones zones
     * @param edges edges between zones and to the ambient
     * @param activeEdges edges that aren't sleeping (CORE-033)
     * @param hotspots hotspots; always 0 until fire lands (R1)
     * @param pendingClassifications operations waiting for classification (GEO-028)
     * @param pausedZones paused zones (GEO-030)
     * @param mirroredSections sections in the worker's mirror (architecture §4.3)
     */
    public record Counts(
            int zones,
            int edges,
            int activeEdges,
            int hotspots,
            int pendingClassifications,
            int pausedZones,
            int mirroredSections) {}

    /** Builds a dimension's report. Server thread; completes on the worker, or now without an engine. */
    public static CompletableFuture<ProfileReport> of(AtmosService service, DimensionAtmos d) {
        AtmosProfiler p = d.profiler();
        Map<Phase, AtmosProfiler.Timing> phases = new EnumMap<>(Phase.class);
        for (Phase phase : Phase.values()) {
            phases.put(phase, p.phase(phase));
        }
        var pipeline = d.pipeline();
        boolean single = pipeline != null && pipeline.scheduler().isSingleThreaded();
        int queued = pipeline == null ? 0 : pipeline.queuedOps();
        int batchSections = pipeline == null ? 0 : pipeline.lastBatchSections();
        AtmosSnapshot snapshot = d.published();
        int paused = 0;
        if (snapshot != null) {
            for (ZoneView z : snapshot.zones()) {
                if (z.paused()) {
                    paused++;
                }
            }
        }
        int pausedZones = paused;
        AtmosService.TickTimes server = service.tickTimes();
        java.util.function.Function<Counts, ProfileReport> build = counts -> new ProfileReport(
                d.dimension().location(),
                single,
                d.isFaulted(),
                p.samples(),
                phases,
                p.worker(),
                p.close(),
                p.apply(),
                d.applyWaits(),
                d.rejectedComponents(),
                queued,
                d.pendingMutations(),
                batchSections,
                server,
                counts);
        if (pipeline == null || d.isFaulted()) {
            return CompletableFuture.completedFuture(build.apply(null));
        }
        return d.inspect(engine -> {
                    int edges = 0;
                    int active = 0;
                    for (Edge e : engine.graph().edges()) {
                        edges++;
                        if (!e.isSleeping()) {
                            active++;
                        }
                    }
                    return new Counts(
                            engine.graph().zoneCount(),
                            edges,
                            active,
                            0,
                            engine.bufferedOps().size(),
                            pausedZones,
                            engine.mirror().size());
                })
                .orTimeout(30, TimeUnit.SECONDS)
                .handle((counts, error) -> build.apply(error == null ? counts : null));
    }

    /** The report as chat lines. */
    public List<Component> lines() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(
                "commands.zas.profile.header",
                dimension.toString(),
                Component.translatable(
                        singleThreaded ? "commands.zas.profile.single_threaded" : "commands.zas.profile.threaded"),
                samples));
        if (faulted) {
            out.add(Component.translatable("commands.zas.profile.faulted"));
        }
        out.add(Component.translatable(
                "commands.zas.profile.server",
                ms(server.averageMs()),
                ms(server.p99Ms()),
                ms(server.maxMs()),
                server.samples()));
        out.add(Component.translatable("commands.zas.profile.close", ms(close.averageMs()), ms(close.maxMs())));
        out.add(Component.translatable("commands.zas.profile.apply", ms(apply.averageMs()), ms(apply.maxMs())));
        out.add(Component.translatable("commands.zas.profile.worker", ms(worker.averageMs()), ms(worker.maxMs())));
        for (Map.Entry<Phase, AtmosProfiler.Timing> e : phases.entrySet()) {
            out.add(Component.translatable(
                    "commands.zas.profile.phase",
                    Component.translatable("zas.phase." + e.getKey().name().toLowerCase(Locale.ROOT)),
                    ms(e.getValue().averageMs()),
                    ms(e.getValue().maxMs())));
        }
        if (counts == null) {
            out.add(Component.translatable("commands.zas.profile.no_engine"));
        } else {
            out.add(Component.translatable(
                    "commands.zas.profile.counts",
                    counts.zones(),
                    counts.edges(),
                    counts.activeEdges(),
                    counts.hotspots(),
                    counts.pendingClassifications(),
                    counts.pausedZones(),
                    counts.mirroredSections()));
        }
        out.add(Component.translatable(
                "commands.zas.profile.queues",
                queuedOps,
                pendingMutations,
                lastBatchSections,
                applyWaits,
                rejectedComponents));
        return out;
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
