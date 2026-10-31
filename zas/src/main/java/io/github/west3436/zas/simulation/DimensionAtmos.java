package io.github.west3436.zas.simulation;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.DimensionEngine;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.DimensionScheduler;
import io.github.west3436.zas.engine.scheduler.EnclosureClassifier;
import io.github.west3436.zas.engine.scheduler.EngineSettings;
import io.github.west3436.zas.engine.scheduler.OpOutcome;
import io.github.west3436.zas.engine.scheduler.OpenAirEvent;
import io.github.west3436.zas.engine.scheduler.Phase;
import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * One dimension's atmos simulation on the server thread (CORE-042): its {@link DimensionPipeline}
 * and engine, the futures of its queued mutations, and the publishing of its zone table.
 *
 * <p>An excluded dimension (ENV-007) has no engine: its mutations act on the open air, with the
 * same latency. A dimension whose engine threw is faulted (D-55): its last table stays readable
 * and new mutations fail.
 *
 * <p>Server thread only, except {@link #published()}.
 */
public final class DimensionAtmos {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    private final Executor workers;
    private volatile DimensionPipeline pipeline;
    private GasRegistry registry = GasRegistry.EMPTY;
    private ResourceLocation[] gasIds = new ResourceLocation[0];
    private GeometryPalette palette;
    private long nextAtmosTick = 1;
    private long closedAtGameTick = Long.MIN_VALUE;
    private final Map<Long, Pending> pending = new TreeMap<>();
    private long nextExcludedSeq = -1;
    private List<Long> excludedInFlight = List.of();
    private boolean excludedClosed;
    private final List<Long> excludedQueue = new ArrayList<>();

    // Statistics for commands and the profiler (OPS-004).
    private long lastCloseNanos;
    private long lastApplyNanos;
    private long applyWaits;
    private long rejectedComponents;
    private volatile String lastWorkerThread = "";
    private boolean lastApplyOnServerThread;
    private ConservationReport lastConservation;

    private record Pending(
            CompletableFuture<MutationResult> future,
            boolean add,
            BlockPos pos,
            GasParcel offered,
            Map<ResourceLocation, Double> wanted,
            SourceId source) {}

    DimensionAtmos(ServerLevel level, Executor workers) {
        this.level = level;
        this.workers = workers;
    }

    public ServerLevel level() {
        return level;
    }

    public ResourceKey<Level> dimension() {
        return level.dimension();
    }

    /** The published zone table, or null while the dimension has no engine. Any thread (API-046). */
    public AtmosSnapshot published() {
        DimensionPipeline p = pipeline;
        return p == null ? null : p.published();
    }

    /** The gas registry the published table's moles are indexed by. Any thread. */
    public GasRegistry registry() {
        return registry;
    }

    /** The gas id of each registry index. Any thread. */
    ResourceLocation gasId(int index) {
        return gasIds[index];
    }

    /** The pipeline, or null if the dimension is excluded or not started. */
    public DimensionPipeline pipeline() {
        return pipeline;
    }

    /** Whether the engine threw and the dimension is paused (D-55). */
    public boolean isFaulted() {
        DimensionPipeline p = pipeline;
        return p != null && p.scheduler().isFaulted();
    }

    public boolean isInFlight() {
        DimensionPipeline p = pipeline;
        return (p != null && p.isInFlight()) || excludedClosed;
    }

    /** The name of the worker thread that ran the last atmos tick (CORE-045). */
    public String lastWorkerThread() {
        return lastWorkerThread;
    }

    /** Whether the last result was applied on the server thread (CORE-069). */
    public boolean lastApplyOnServerThread() {
        return lastApplyOnServerThread;
    }

    /** The invariant checker's last report, or null if it hasn't run (CORE-073). */
    public ConservationReport lastConservation() {
        return lastConservation;
    }

    public long lastCloseNanos() {
        return lastCloseNanos;
    }

    public long lastApplyNanos() {
        return lastApplyNanos;
    }

    /** How many applies had to wait for the worker. */
    public long applyWaits() {
        return applyWaits;
    }

    /** How many exchange components have been rejected as stale (CORE-070). */
    public long rejectedComponents() {
        return rejectedComponents;
    }

    /** How many mutations wait for their atmos tick. */
    public int pendingMutations() {
        return pending.size();
    }

    // ---- Mutations ----------------------------------------------------------------

    CompletableFuture<MutationResult> add(BlockPos pos, GasParcel gas, SourceId source) {
        CompletableFuture<MutationResult> future = new CompletableFuture<>();
        Pending p = new Pending(future, true, pos.immutable(), gas, Map.of(), source);
        queue(p, gas.moles(), gas.temperature());
        return future;
    }

    CompletableFuture<MutationResult> remove(BlockPos pos, Map<ResourceLocation, Double> moles, SourceId source) {
        CompletableFuture<MutationResult> future = new CompletableFuture<>();
        Pending p = new Pending(future, false, pos.immutable(), GasParcel.EMPTY, moles, source);
        queue(p, moles, 0);
        return future;
    }

    private void queue(Pending p, Map<ResourceLocation, Double> moles, double temperature) {
        if (isFaulted()) {
            p.future()
                    .completeExceptionally(new IllegalStateException(
                            "the atmosphere of " + dimension().location() + " is paused after an error"));
            return;
        }
        ensureEngine();
        DimensionPipeline pl = pipeline;
        if (pl == null) {
            long seq = nextExcludedSeq--;
            pending.put(seq, p);
            excludedQueue.add(seq);
            return;
        }
        double[] amounts = new double[registry.size()];
        moles.forEach((gas, n) -> {
            int index = registry.indexOf(gas.toString());
            if (index >= 0) {
                amounts[index] = n;
            }
        });
        long block = p.pos().asLong();
        String source = p.source().toString();
        long seq = p.add()
                ? pl.add(block, amounts, temperature, LedgerCategory.API, true, source)
                : pl.remove(block, amounts, LedgerCategory.API, true, source);
        pending.put(seq, p);
    }

    // ---- Lifecycle ------------------------------------------------------------------

    void chunkLoaded(long chunk) {
        DimensionPipeline p = pipeline;
        if (p != null) {
            p.chunkLoaded(chunk);
        }
    }

    void chunkUnloaded(long chunk) {
        DimensionPipeline p = pipeline;
        if (p != null) {
            p.chunkUnloaded(chunk);
        }
    }

    /**
     * Creates the engine if the dimension isn't excluded and has none; drops it if the dimension
     * became excluded or the geometry palette changed.
     */
    private void ensureEngine() {
        boolean excluded = Ambients.isExcluded(level.dimension());
        if (excluded) {
            if (pipeline != null) {
                LOGGER.warn(
                        "{} is now excluded from simulation; its zones are dropped",
                        dimension().location());
                retire("the dimension is now excluded from simulation");
            }
            return;
        }
        GeometryTable table = Geometries.tableIfReady();
        if (table == null) {
            return; // keep the current engine until the rebuilt table is ready
        }
        if (pipeline != null && table.palette() != palette) {
            if (samePalette(palette, table.palette())) {
                palette = table.palette();
            } else if (!pipeline.isInFlight()) {
                LOGGER.warn(
                        "Gas geometry changed; the zones of {} are rebuilt from scratch",
                        dimension().location());
                retire("the gas geometry changed");
            }
        }
        if (pipeline == null) {
            create(table);
        }
    }

    private void create(GeometryTable table) {
        registry = ServerGasRegistry.current();
        ResourceLocation[] ids = new ResourceLocation[registry.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = ResourceLocation.parse(registry.definition(i).id());
        }
        gasIds = ids;
        palette = table.palette();
        int minSectionY = level.getMinSection();
        int maxSectionY = level.getMaxSection() - 1;
        Ambient ambient = Ambients.ambient(level.dimension());
        GasConfig config = GasConfig.DEFAULT.withLog(
                message -> LOGGER.warn("[{}] {}", dimension().location(), message));
        DimensionEngine engine = new DimensionEngine(
                registry,
                config,
                EnclosureClassifier.FACTORY,
                palette,
                minSectionY,
                maxSectionY,
                ambient,
                settings(ZasServerConfig.scheduler()));
        engine.setPhaseListener(phase -> {
            if (phase == Phase.GEOMETRY) {
                lastWorkerThread = Thread.currentThread().getName();
            }
        });
        DimensionScheduler scheduler =
                new DimensionScheduler(engine, ZasServerConfig.singleThreaded() ? null : workers);
        pipeline = new DimensionPipeline(scheduler, new LevelWorld(level), minSectionY, maxSectionY);
    }

    /** Drops the engine, failing the mutations still waiting on it. */
    private void retire(String why) {
        DimensionPipeline p = pipeline;
        if (p != null && p.isInFlight()) {
            p.apply(); // collect it so the worker is idle; its results are discarded
        }
        pipeline = null;
        failAll(new CancellationException(why));
    }

    private static boolean samePalette(GeometryPalette a, GeometryPalette b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.geometry(i).equals(b.geometry(i))) {
                return false;
            }
        }
        return true;
    }

    private static EngineSettings settings(SchedulerSettings timing) {
        return new EngineSettings(
                ExchangeSettings.DEFAULT.withPeriod(timing.periodSeconds()), ZasServerConfig.conservationChecker());
    }

    // ---- Close and apply ----------------------------------------------------------------

    /** Closes a batch at game tick {@code gameTick} (architecture §3.2). */
    void close(long gameTick, SchedulerSettings timing, BooleanSupplier overBudget) {
        if (isInFlight()) {
            apply();
        }
        long start = System.nanoTime();
        ensureEngine();
        DimensionPipeline p = pipeline;
        closedAtGameTick = gameTick;
        if (p == null) {
            excludedInFlight = List.copyOf(excludedQueue);
            excludedQueue.clear();
            excludedClosed = true;
        } else if (!p.scheduler().isFaulted()) {
            p.close(nextAtmosTick++, settings(timing), overBudget);
        }
        lastCloseNanos = System.nanoTime() - start;
    }

    /** Whether the in-flight batch is due at {@code gameTick}. */
    boolean dueAt(long gameTick, SchedulerSettings timing) {
        return isInFlight() && gameTick - closedAtGameTick >= timing.applyDelayTicks();
    }

    /** Applies the in-flight batch on the server thread (CORE-069). Never split (CORE-047). */
    void apply() {
        long start = System.nanoTime();
        lastApplyOnServerThread = level.getServer().isSameThread();
        if (excludedClosed) {
            excludedClosed = false;
            for (long seq : excludedInFlight) {
                Pending p = pending.remove(seq);
                if (p != null) {
                    completeInOpenAir(p);
                }
            }
            excludedInFlight = List.of();
        }
        DimensionPipeline pl = pipeline;
        if (pl == null || !pl.isInFlight()) {
            lastApplyNanos = System.nanoTime() - start;
            return;
        }
        if (!pl.scheduler().isDone()) {
            applyWaits++;
        }
        DimensionPipeline.Applied a = pl.apply();
        if (a.fault() != null) {
            LOGGER.error(
                    "The atmosphere of {} failed and is paused; its last state stays readable",
                    dimension().location(),
                    a.fault());
            failAll(new IllegalStateException("the atmosphere of " + dimension().location() + " failed", a.fault()));
            lastApplyNanos = System.nanoTime() - start;
            return;
        }
        rejectedComponents += a.rejected();
        if (a.conservation() != null) {
            lastConservation = a.conservation();
        }
        for (OpenAirEvent e : a.events()) {
            Pending p = pending.get(e.seq());
            SourceId source = p != null ? p.source() : SourceId.of("zas", "unknown");
            NeoForge.EVENT_BUS.post(new OpenAirGasEvent(
                    level, BlockPos.of(e.block()), e.added(), parcel(e.moles(), e.temperature()), source));
        }
        for (OpOutcome o : a.outcomes()) {
            Pending p = pending.remove(o.seq());
            if (p != null) {
                p.future().complete(new MutationResult(outcome(o.outcome()), parcel(o.moved(), o.temperature())));
            }
        }
        lastApplyNanos = System.nanoTime() - start;
    }

    private static MutationOutcome outcome(OpOutcome.Outcome o) {
        return switch (o) {
            case ZONE -> MutationOutcome.ZONE;
            case AMBIENT -> MutationOutcome.AMBIENT;
            case UNRESOLVED -> MutationOutcome.PAUSED;
        };
    }

    /** Mutations in an excluded dimension act on the open air (ENV-007, ENV-018). */
    private void completeInOpenAir(Pending p) {
        GasParcel moved;
        if (p.add()) {
            moved = p.offered();
        } else {
            Ambient ambient = Ambients.ambient(level.dimension());
            Map<ResourceLocation, Double> supplied = new TreeMap<>();
            p.wanted().forEach((gas, n) -> {
                if (ambient.partialPressure(gas.toString()) > 0) {
                    supplied.put(gas, n);
                }
            });
            moved = new GasParcel(supplied, ambient.temperature());
        }
        if (!moved.isEmpty()) {
            NeoForge.EVENT_BUS.post(new OpenAirGasEvent(level, p.pos(), p.add(), moved, p.source()));
        }
        p.future().complete(new MutationResult(MutationOutcome.AMBIENT, moved));
    }

    private GasParcel parcel(double[] moles, double temperature) {
        Map<ResourceLocation, Double> map = new TreeMap<>();
        for (int i = 0; i < moles.length; i++) {
            if (moles[i] > 0) {
                map.put(gasIds[i], moles[i]);
            }
        }
        return new GasParcel(map, temperature);
    }

    /** Fails every waiting mutation. */
    void failAll(Throwable why) {
        List<Pending> all = new ArrayList<>(pending.values());
        pending.clear();
        excludedQueue.clear();
        excludedInFlight = List.of();
        excludedClosed = false;
        for (Pending p : all) {
            p.future().completeExceptionally(why);
        }
    }
}
