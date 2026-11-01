package io.github.west3436.zas.simulation;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.change.SectionCapture;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.config.ZoneTrigger;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.persist.ChunkRecordCodec;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.DimensionEngine;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.DimensionScheduler;
import io.github.west3436.zas.engine.scheduler.EnclosureClassifier;
import io.github.west3436.zas.engine.scheduler.EngineSettings;
import io.github.west3436.zas.engine.scheduler.GasOp;
import io.github.west3436.zas.engine.scheduler.GeometryAudit;
import io.github.west3436.zas.engine.scheduler.OpOutcome;
import io.github.west3436.zas.engine.scheduler.OpenAirEvent;
import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.persistence.DimensionData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * <p><b>Persistence</b> (architecture §4.5). The dimension is the {@link AtmosPersistence.Store}
 * of its level: a chunk saves {@link DimensionPipeline#recordFor its record}, built from the
 * published table, and a loaded chunk's saved record goes to the pipeline. At server stop the
 * worker is abandoned, never awaited; queued additions are saved with their chunk, or with the
 * dimension ({@link DimensionData}) if their chunk isn't loaded, and queued removals complete with
 * nothing moved (DATA-011). Chunks whose saved atmosphere changed are marked for saving at every
 * apply. Nothing here forces a chunk to load or stay loaded (GEO-034).
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

    // Persistence.
    private boolean stopping;
    private final Set<Long> withData = new HashSet<>();
    private final Set<Long> simulatedUnloaded = ConcurrentHashMap.newKeySet();
    /** What each simulated-unloaded chunk saved at its unload, which its saves keep writing. */
    private final Map<Long, byte[]> simulatedSaved = new ConcurrentHashMap<>();

    private final AtmosPersistence.Store store = new AtmosPersistence.Store() {
        @Override
        public byte[] save(long chunk) {
            if (simulatedUnloaded.contains(chunk)) {
                // An unloaded chunk is never saved again; its data stays what it saved at unload.
                byte[] saved = simulatedSaved.get(chunk);
                return saved == null ? null : saved.clone();
            }
            return saveBytes(chunk, new long[0]);
        }

        @Override
        public byte[] unloading(long chunk) {
            return saveBytes(chunk, unseenChanges(chunk));
        }

        @Override
        public boolean hadData(long chunk) {
            return withData.remove(chunk);
        }
    };

    // Statistics for commands and the profiler (OPS-004).
    private final AtmosProfiler profiler = new AtmosProfiler();
    private long lastCloseNanos;
    private long lastApplyNanos;
    private long applyWaits;
    private long rejectedComponents;
    private boolean lastApplyOnServerThread;
    private ConservationReport lastConservation;

    // On-demand invariant checks (OPS-010): asked for, then carried by the next batch.
    private final List<CompletableFuture<ConservationReport>> checkRequests = new ArrayList<>();
    private final List<CompletableFuture<ConservationReport>> checksInFlight = new ArrayList<>();

    private record Pending(
            CompletableFuture<MutationResult> future,
            boolean add,
            BlockPos pos,
            int sub,
            GasParcel offered,
            Map<ResourceLocation, Double> wanted,
            SourceId source) {}

    DimensionAtmos(ServerLevel level, Executor workers) {
        this.level = level;
        this.workers = workers;
        AtmosPersistence.attach(level, store);
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
        return profiler.workerThread();
    }

    /** The timings of the last atmos ticks (OPS-004). Server thread. */
    public AtmosProfiler profiler() {
        return profiler;
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

    CompletableFuture<MutationResult> add(BlockPos pos, int sub, GasParcel gas, SourceId source) {
        CompletableFuture<MutationResult> future = new CompletableFuture<>();
        Pending p = new Pending(future, true, pos.immutable(), sub, gas, Map.of(), source);
        queue(p, gas.moles(), gas.temperature());
        return future;
    }

    CompletableFuture<MutationResult> remove(
            BlockPos pos, int sub, Map<ResourceLocation, Double> moles, SourceId source) {
        CompletableFuture<MutationResult> future = new CompletableFuture<>();
        Pending p = new Pending(future, false, pos.immutable(), sub, GasParcel.EMPTY, moles, source);
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
        // CORE-055: an API call may form a zone in an enclosed, unzoned area, under either preset.
        boolean triggers = ZoneTrigger.API_CALLS.active();
        long seq = p.add()
                ? pl.add(block, p.sub(), amounts, temperature, LedgerCategory.API, triggers, source)
                : pl.remove(block, p.sub(), amounts, LedgerCategory.API, triggers, source);
        pending.put(seq, p);
    }

    // ---- Lifecycle ------------------------------------------------------------------

    void chunkLoaded(long chunk) {
        DimensionPipeline p = pipeline;
        if (p != null && !stopping) {
            byte[] saved = AtmosPersistence.take(level, chunk);
            if (saved != null) {
                deliver(chunk, saved);
            } else {
                p.chunkLoaded(chunk);
            }
        }
    }

    void chunkUnloaded(long chunk) {
        DimensionPipeline p = pipeline;
        if (p != null && !stopping) {
            p.chunkUnloaded(chunk);
        }
    }

    /** Hands a loaded chunk's saved bytes to the pipeline, decoded; corrupt bytes reset it (DATA-004). */
    private void deliver(long chunk, byte[] saved) {
        ChunkRecord record = decode(saved, chunk);
        withData.add(chunk);
        pipeline.chunkLoaded(chunk, record);
        if (record != null && !record.queuedAdditions().isEmpty()) {
            markUnsaved(chunk); // they are queued again, so the saved copy must go
        }
    }

    /**
     * Decodes a chunk's saved record. Gas of gases the registry no longer defines is dropped, with a
     * warning naming the gas, the amount and where it was (CORE-013, CORE-016), and the chunk is
     * marked for saving, so the next save writes it without that gas and the warning isn't repeated
     * at every load.
     */
    private ChunkRecord decode(byte[] saved, long chunk) {
        try {
            boolean[] dropped = {false};
            ChunkRecord record = ChunkRecordCodec.decode(saved, chunk, registry, message -> {
                dropped[0] = true;
                AtmosPersistence.warnDroppedGas(dimension(), message);
            });
            if (dropped[0]) {
                markUnsaved(chunk);
            }
            return record;
        } catch (ChunkRecordCodec.CorruptRecordException | RuntimeException e) {
            AtmosPersistence.warnCorrupt(dimension(), chunk, e.getMessage());
            return null;
        }
    }

    /** What a chunk saves, with extra held positions, encoded; null if nothing (DATA-002). */
    private byte[] saveBytes(long chunk, long[] extraHeld) {
        DimensionPipeline p = pipeline;
        if (p == null) {
            return null;
        }
        ChunkRecord r = p.recordFor(chunk, stopping);
        if (extraHeld.length > 0) {
            ChunkRecord base = r != null ? r : ChunkRecord.empty(chunk);
            long[] held = Arrays.copyOf(base.heldBlocks(), base.heldBlocks().length + extraHeld.length);
            System.arraycopy(extraHeld, 0, held, base.heldBlocks().length, extraHeld.length);
            r = new ChunkRecord(chunk, base.zones(), held, base.queuedAdditions());
        }
        return r == null ? null : ChunkRecordCodec.encode(r, registry);
    }

    /**
     * The changed positions of an unloading chunk the worker hasn't seen, in sections it mirrors:
     * they are saved as held changes (DATA-010), since the change tracker forgets them at unload.
     */
    private long[] unseenChanges(long chunk) {
        DimensionPipeline p = pipeline;
        if (p == null) {
            return new long[0];
        }
        int x = SectionKeys.chunkX(chunk) * 16;
        int z = SectionKeys.chunkZ(chunk) * 16;
        BoundingBox box =
                new BoundingBox(x, level.getMinBuildHeight(), z, x + 15, level.getMaxBuildHeight() - 1, z + 15);
        long[] mirrored = p.mirroredSections();
        Arrays.sort(mirrored);
        List<Long> out = new ArrayList<>();
        for (DirtySection d : ChangeTracker.drainWithin(level, box)) {
            if (Arrays.binarySearch(mirrored, d.key()) < 0) {
                continue;
            }
            d.forEachIndex(i -> out.add(SectionKeys.block(
                    d.sectionX() * 16 + (i & 15), d.sectionY() * 16 + (i >> 8), d.sectionZ() * 16 + ((i >> 4) & 15))));
        }
        long[] blocks = new long[out.size()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = out.get(i);
        }
        return blocks;
    }

    private void markUnsaved(long chunk) {
        LevelChunk c = level.getChunkSource().getChunkNow(SectionKeys.chunkX(chunk), SectionKeys.chunkZ(chunk));
        if (c != null) {
            c.setUnsaved(true);
        }
    }

    /** Whether a chunk is loaded, as the atmosphere sees it. Any thread. */
    public boolean isChunkLoaded(long chunk) {
        return !simulatedUnloaded.contains(chunk) && AtmosPersistence.isLoaded(dimension(), chunk);
    }

    // ---- Simulated unloads and restarts, for GameTests -------------------------------------

    /**
     * What a chunk writes if it is saved now, as {@link AtmosPersistence#TAG} would hold it, or
     * null. A chunk unloaded with {@link #simulateUnload} writes what it saved at its unload, as a
     * really unloaded chunk keeps its data. For GameTests. Server thread.
     */
    public byte[] simulateSave(ChunkPos pos) {
        return store.save(pos.toLong());
    }

    /** What a chunk writes in the final save after {@link #stop}. Server thread. */
    byte[] finalSave(long chunk) {
        return store.save(chunk);
    }

    /** Stops saving through this dimension. */
    void detach() {
        AtmosPersistence.detach(level, store);
    }

    /** Creates the engine now if the dimension simulates and has none. Server thread. */
    void start() {
        ensureEngine();
    }

    /**
     * Treats a loaded chunk as unloaded, without touching it: it saves as it would at unload and
     * stops being captured, and the engine pauses its zones. For GameTests, which can't unload a
     * chunk on demand and must not force chunks (GEO-034). Server thread.
     *
     * @return the bytes the chunk saved, as {@link AtmosPersistence#TAG} would hold them, or null
     */
    public byte[] simulateUnload(ChunkPos pos) {
        long chunk = pos.toLong();
        byte[] saved = store.unloading(chunk);
        withData.remove(chunk);
        simulatedUnloaded.add(chunk);
        if (saved != null) {
            simulatedSaved.put(chunk, saved.clone());
        }
        DimensionPipeline p = pipeline;
        if (p != null) {
            p.chunkUnloaded(chunk);
        }
        return saved;
    }

    /**
     * Ends a {@link #simulateUnload}: the chunk loads again with the given saved bytes (null for
     * none), which are decoded as a real load would decode them. Server thread.
     */
    public void simulateLoad(ChunkPos pos, byte[] saved) {
        long chunk = pos.toLong();
        simulatedUnloaded.remove(chunk);
        simulatedSaved.remove(chunk);
        ChangeTracker.markReloaded(level, pos);
        DimensionPipeline p = pipeline;
        if (p == null) {
            return;
        }
        if (saved != null) {
            deliver(chunk, saved);
        } else {
            p.chunkLoaded(chunk);
        }
    }

    /**
     * Creates the engine if the dimension isn't excluded and has none; drops it if the dimension
     * became excluded, and rebuilds it from the loaded chunks' records if the geometry palette
     * changed ({@link #rebuild}).
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
                        "Gas geometry changed; the zones of {} are rebuilt from the loaded chunks' saved"
                                + " atmosphere and their surfaces classified again",
                        dimension().location());
                rebuild(table);
            }
        }
        if (pipeline == null) {
            create(table);
        }
    }

    /**
     * Replaces the engine after a geometry palette change, as a restart would (architecture §4.5,
     * O-10): each loaded chunk's record is built from the published table, exactly what a save
     * would write, and goes to a new engine with the new palette through the path of a chunk load;
     * the zones rebuilt from those records classify their surfaces again with the new geometry.
     * Unloaded chunks keep what they saved, and zones across them wait for them to load. The
     * operations still outstanding are queued again on the new engine, their futures kept, so no
     * gas is lost. Only between batches, so no result is in flight; the old worker is abandoned,
     * never awaited.
     */
    private void rebuild(GeometryTable table) {
        DimensionPipeline old = pipeline;
        GasRegistry oldRegistry = registry;
        Map<Long, byte[]> records = new TreeMap<>();
        for (long chunk : old.recordChunks()) {
            if (!isChunkLoaded(chunk)) {
                continue; // it saved its record at unload, and keeps it
            }
            ChunkRecord r = old.recordFor(chunk, false);
            if (r != null) {
                // Through the byte format, so the new engine reads it as after a restart, under
                // the gas registry it is created with.
                records.put(chunk, ChunkRecordCodec.encode(r, oldRegistry));
            }
        }
        // Operations whose futures already failed with a fault aren't run again.
        List<GasOp> carried = old.scheduler().isFaulted() ? List.of() : old.outstanding();
        DimensionData.of(level).raiseNextZoneId(old.nextZoneId());
        old.scheduler().abandon();
        pipeline = null;
        create(table);
        for (Map.Entry<Long, byte[]> e : records.entrySet()) {
            deliver(e.getKey(), e.getValue());
        }
        Map<Long, Pending> moved = new TreeMap<>();
        for (GasOp op : carried) {
            Pending p = pending.remove(op.seq());
            long seq = pipeline.resubmit(remap(op, oldRegistry));
            if (p != null) {
                moved.put(seq, p);
            }
        }
        // Anything left under the old engine's numbers has no operation to complete it.
        List<Pending> orphans = new ArrayList<>();
        pending.entrySet().removeIf(e -> {
            if (e.getKey() > 0) {
                orphans.add(e.getValue());
                return true;
            }
            return false;
        });
        pending.putAll(moved);
        for (Pending p : orphans) {
            p.future().completeExceptionally(new CancellationException("the gas geometry changed"));
        }
    }

    /** An operation with its amounts indexed by the current gas registry instead of {@code from}. */
    private GasOp remap(GasOp op, GasRegistry from) {
        if (from == registry) {
            return op;
        }
        double[] moles = new double[registry.size()];
        double[] old = op.moles();
        for (int i = 0; i < old.length && i < from.size(); i++) {
            int index = registry.indexOf(from.definition(i).id());
            if (index >= 0) {
                moles[index] = old[i];
            }
        }
        return new GasOp(
                op.seq(),
                op.kind(),
                op.block(),
                moles,
                op.temperature(),
                op.category(),
                op.triggersZone(),
                op.source(),
                op.sub());
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
        engine.setPhaseListener(profiler.listener);
        DimensionData data = DimensionData.of(level);
        engine.reserveZoneIds(data.nextZoneId());
        DimensionScheduler scheduler =
                new DimensionScheduler(engine, ZasServerConfig.singleThreaded() ? null : workers);
        pipeline = new DimensionPipeline(
                scheduler, new LevelWorld(level, simulatedUnloaded::contains), minSectionY, maxSectionY);
        // Chunks that loaded before the engine existed, then additions saved with the dimension.
        for (Map.Entry<Long, byte[]> e : new TreeMap<>(AtmosPersistence.takeLoaded(level)).entrySet()) {
            deliver(e.getKey(), e.getValue());
        }
        byte[] queued = data.takeQueued();
        if (queued != null) {
            ChunkRecord r = decode(queued, ChunkRecordCodec.LEVEL_WIDE);
            if (r != null) {
                for (GasOp op : r.queuedAdditions()) {
                    pipeline.requeue(op);
                }
            }
        }
    }

    /** Drops the engine, failing the mutations still waiting on it. */
    private void retire(String why) {
        DimensionPipeline p = pipeline;
        if (p != null) {
            p.scheduler().abandon(); // never wait for the worker; its results are discarded
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

    // ---- Commands and the profiler ------------------------------------------------------

    /**
     * Runs a read-only query on the engine on the thread that owns it, between atmos ticks
     * (OPS-004, GEO-032). The future completes on the worker. Server thread.
     *
     * @return the query's result; failed if the dimension has no engine or is faulted
     */
    public <T> CompletableFuture<T> inspect(Function<? super DimensionEngine, ? extends T> query) {
        ensureEngine();
        DimensionPipeline p = pipeline;
        if (p == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(dimension().location() + " isn't simulated"));
        }
        return p.scheduler().inspect(query);
    }

    /**
     * Runs the invariant checker on the next atmos tick, whether or not it is switched on
     * (OPS-010, CORE-073). The future completes on the server thread when that tick is applied.
     * Server thread.
     */
    public CompletableFuture<ConservationReport> checkConservation() {
        CompletableFuture<ConservationReport> f = new CompletableFuture<>();
        ensureEngine();
        if (pipeline == null || isFaulted()) {
            f.completeExceptionally(new IllegalStateException(dimension().location() + " isn't simulated"));
        } else {
            checkRequests.add(f);
        }
        return f;
    }

    /**
     * What a geometry audit found (GEO-032).
     *
     * @param report the engine's findings
     * @param unloadedSections sections of the region whose chunks aren't loaded, never loaded for
     *     the audit (GEO-034)
     * @param pendingSections sections with block changes the engine hasn't been sent yet, left out
     *     of the comparison
     */
    public record AuditResult(GeometryAudit.Report report, int unloadedSections, int pendingSections) {}

    /**
     * Audits a region (GEO-032): captures its loaded sections now, without loading any chunk, and
     * compares them with the engine on the worker, between atmos ticks. Sections with block
     * changes still waiting for the next batch are left out. The future completes on the worker.
     * Server thread.
     *
     * @param maxFindings how many findings to list
     */
    public CompletableFuture<AuditResult> audit(GeometryAudit.Region region, int maxFindings) {
        ensureEngine();
        DimensionPipeline p = pipeline;
        if (p == null || isFaulted()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(dimension().location() + " isn't simulated"));
        }
        GeometryTable table = Geometries.tableIfReady();
        if (table == null || table.palette() != palette) {
            return CompletableFuture.failedFuture(new IllegalStateException("the gas geometry is being rebuilt"));
        }
        int minY = level.getMinSection();
        int maxY = level.getMaxSection() - 1;
        List<SectionCapture> captures = new ArrayList<>();
        int unloaded = 0;
        int dirty = 0;
        for (long key : region.sections()) {
            int y = SectionKeys.y(key);
            if (y < minY || y > maxY) {
                continue;
            }
            if (ChangeTracker.isSectionDirty(level, key)) {
                dirty++;
                continue;
            }
            SectionCapture c = SectionCapture.capture(level, key);
            if (c == null) {
                unloaded++;
            } else {
                captures.add(c);
            }
        }
        int unloadedSections = unloaded;
        int pendingSections = dirty;
        return p.scheduler().inspect(engine -> {
            List<SectionSnapshot> fresh = new ArrayList<>(captures.size());
            for (SectionCapture c : captures) {
                fresh.add(c.decode());
            }
            return new AuditResult(
                    GeometryAudit.run(engine, region, fresh, maxFindings), unloadedSections, pendingSections);
        });
    }

    /**
     * Has the next batch send stale sections to the engine again, so its geometry phase brings the
     * mirror and zones back in step with the blocks: the audit's repair (GEO-032). Server thread.
     */
    public void resend(long[] sections) {
        for (long key : sections) {
            ChangeTracker.markDirty(
                    level, new BlockPos(SectionKeys.x(key) << 4, SectionKeys.y(key) << 4, SectionKeys.z(key) << 4));
        }
    }

    // ---- Close and apply ----------------------------------------------------------------

    /** Closes a batch at game tick {@code gameTick} (architecture §3.2). */
    void close(long gameTick, SchedulerSettings timing, BooleanSupplier overBudget) {
        if (stopping) {
            return;
        }
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
            EngineSettings settings = settings(timing);
            if (!checkRequests.isEmpty()) {
                settings = settings.withConservationCheck(true);
                checksInFlight.addAll(checkRequests);
                checkRequests.clear();
            }
            p.close(nextAtmosTick++, settings, overBudget);
        }
        lastCloseNanos = System.nanoTime() - start;
        profiler.closed(lastCloseNanos);
    }

    /** Whether the in-flight batch is due at {@code gameTick}. */
    boolean dueAt(long gameTick, SchedulerSettings timing) {
        return isInFlight() && gameTick - closedAtGameTick >= timing.applyDelayTicks();
    }

    /** Applies the in-flight batch on the server thread (CORE-069). Never split (CORE-047). */
    void apply() {
        long start = System.nanoTime();
        boolean ranTick = applyBatch();
        lastApplyNanos = System.nanoTime() - start;
        profiler.applied(lastApplyNanos, ranTick);
    }

    /** Applies the in-flight batch; returns whether it held an engine tick. */
    private boolean applyBatch() {
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
            return false;
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
            return true;
        }
        rejectedComponents += a.rejected();
        if (a.conservation() != null) {
            lastConservation = a.conservation();
        }
        for (long chunk : a.changedChunks()) {
            markUnsaved(chunk);
        }
        DimensionData.of(level).raiseNextZoneId(pl.nextZoneId());
        completeChecks(a.conservation());
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
        return true;
    }

    private void completeChecks(ConservationReport report) {
        List<CompletableFuture<ConservationReport>> done = new ArrayList<>(checksInFlight);
        checksInFlight.clear();
        for (CompletableFuture<ConservationReport> f : done) {
            if (report != null) {
                f.complete(report);
            } else {
                f.completeExceptionally(new IllegalStateException("the atmos tick produced no report"));
            }
        }
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

    /**
     * Stops the dimension for a level unload: abandons the tick in flight without waiting for the
     * worker, and fails the waiting mutations.
     */
    void shutdown(String why) {
        DimensionPipeline p = pipeline;
        pipeline = null;
        if (p != null) {
            p.scheduler().abandon();
        }
        AtmosPersistence.detach(level, store);
        failAll(new CancellationException(why));
    }

    /**
     * Stops the dimension for a server stop, before the final save (DATA-011): abandons the tick in
     * flight without waiting for the worker, saves queued additions with their chunks, or with the
     * dimension if their chunk isn't loaded, and marks every chunk with saved atmosphere for saving.
     * The published table stays, for the final save. Waiting removals complete with nothing moved;
     * waiting additions are cancelled, their gas saved.
     */
    void stop() {
        stopping = true;
        DimensionPipeline p = pipeline;
        if (p != null) {
            p.scheduler().abandon();
            List<GasOp> unassigned = new ArrayList<>();
            for (GasOp op : p.outstandingAdditions()) {
                long chunk = SectionKeys.chunkOfBlockKey(op.block());
                if (isChunkLoaded(chunk)) {
                    markUnsaved(chunk);
                } else {
                    unassigned.add(op);
                }
            }
            DimensionData data = DimensionData.of(level);
            data.putQueued(
                    unassigned.isEmpty()
                            ? null
                            : ChunkRecordCodec.encode(
                                    new ChunkRecord(ChunkRecordCodec.LEVEL_WIDE, List.of(), new long[0], unassigned),
                                    registry));
            data.raiseNextZoneId(p.nextZoneId());
            for (long chunk : p.published().chunks()) {
                markUnsaved(chunk);
            }
            for (long block : p.heldBlocks()) {
                markUnsaved(SectionKeys.chunkOfBlockKey(block));
            }
        }
        List<CompletableFuture<ConservationReport>> checks = new ArrayList<>(checkRequests);
        checks.addAll(checksInFlight);
        checkRequests.clear();
        checksInFlight.clear();
        for (CompletableFuture<ConservationReport> f : checks) {
            f.completeExceptionally(new CancellationException("server stopping"));
        }
        List<Pending> all = new ArrayList<>(pending.values());
        pending.clear();
        excludedQueue.clear();
        excludedInFlight = List.of();
        excludedClosed = false;
        for (Pending w : all) {
            if (w.add()) {
                w.future()
                        .completeExceptionally(new CancellationException(
                                "server stopping; the gas is saved and added after the restart"));
            } else {
                w.future().complete(new MutationResult(MutationOutcome.PAUSED, GasParcel.EMPTY));
            }
        }
    }

    /** Fails every waiting mutation and invariant check. */
    void failAll(Throwable why) {
        List<CompletableFuture<ConservationReport>> checks = new ArrayList<>(checkRequests);
        checks.addAll(checksInFlight);
        checkRequests.clear();
        checksInFlight.clear();
        for (CompletableFuture<ConservationReport> f : checks) {
            f.completeExceptionally(why);
        }
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
