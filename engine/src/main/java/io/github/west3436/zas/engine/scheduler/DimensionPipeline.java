package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.WorldLimits;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.flow.FlowEdge;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;

/**
 * The server-thread side of one dimension's atmos pipeline (architecture §3.2 to §3.4): it queues
 * operations with sequence numbers, closes batches, and applies results with the staleness check.
 * Plain Java, so the whole protocol runs in unit tests; the platform supplies the world through
 * {@link World} and decides when to {@link #close} and {@link #apply}.
 *
 * <p><b>Closing a batch</b> takes the queued operations, drains dirty sections the worker mirrors
 * (GEO-011) and captures them, captures sections the worker asked for, prefetches the
 * neighbourhood of new zone-triggering operations whose section isn't mirrored (§4.3), and records
 * the lifecycle generation of every chunk the worker may build zones in. It then submits the batch
 * to the {@link DimensionScheduler}.
 *
 * <p><b>Applying a result</b> collects it, rejects every exchange component that spans a chunk
 * whose lifecycle generation changed since batch close (CORE-070), and publishes the snapshot,
 * with rejected zones answered from the previous one. The rejected components go back to the
 * worker in the next batch, which restores them and runs their operations again (CORE-074). The
 * {@link Applied} report lists the operations to complete and the open-air events to fire; the
 * apply itself is never split (CORE-047).
 *
 * <p><b>Persistence</b> (architecture §4.5). A loaded chunk's saved {@link ChunkRecord} is handed
 * in with {@link #chunkLoaded(long, ChunkRecord)} and goes to the worker in the next batch, with
 * the sections its cells are in; its queued additions are queued again (DATA-011). {@link
 * #recordFor} builds what a chunk saves: its share of the published zones, the shares of records
 * the worker hasn't confirmed yet, the positions whose changes are held (DATA-010) and, at
 * shutdown, the queued additions that target it. Nothing here waits for the worker.
 *
 * <p><b>Settling chunks.</b> Until the result of the batch that carried a loaded chunk's record is
 * applied, the published table doesn't hold that chunk's zones yet, so its cells look unzoned.
 * {@link #isSettling} says which loaded chunks are in that window, so a query there can answer
 * "not known yet" rather than the ambient (API-010).
 *
 * <p>Server thread only, except {@link #published()} and {@link #isSettling}, which any thread may
 * read (API-046).
 */
public final class DimensionPipeline {
    /** The world, as the pipeline sees it. Called on the server thread only. */
    public interface World {
        /** Removes the dirty sections and returns those {@code interest} accepts, sorted by {@link SectionKeys#compare}. */
        long[] drainDirty(LongPredicate interest);

        /** Whether a chunk is loaded. */
        boolean isLoaded(long chunk);

        /** Copies a section of a loaded chunk inside the world's height; null otherwise. Never loads a chunk. */
        SectionSource capture(long section);

        /** A chunk's lifecycle generation ({@link io.github.west3436.zas.engine.ChunkGenerations}). */
        long lifecycleGeneration(long chunk);

        /** The dimension's ambient now. */
        Ambient ambient();

        /** The world border now, and whether the dimension seals its edges (GEO-031). */
        default WorldLimits limits() {
            return WorldLimits.OPEN;
        }
    }

    /**
     * What one apply produced.
     *
     * @param tick the atmos tick applied
     * @param outcomes operations finished, in sequence order; their futures complete now
     * @param events open-air events to fire, in sequence order (CORE-058)
     * @param rejected how many components were rejected
     * @param requeued how many operations of rejected components run again later
     * @param conservation the invariant checker's report, or null
     * @param fault the exception that faulted the engine, or null
     * @param changedChunks the chunks whose saved atmosphere changed with this apply, ascending:
     *     those of zones whose published state changed, appeared or went away, and those of
     *     records the worker took in; the platform marks them for saving (DATA-002)
     * @param fireIntents gas-fire blocks to place or remove (FIRE-018), without those of rejected
     *     zones
     * @param fireEvents zones that caught fire or stopped burning (API-031), without rejected
     *     zones
     * @param pressureBreaks fragile blocks to break (FLOW-007), without those read from a rejected zone
     */
    public record Applied(
            long tick,
            List<OpOutcome> outcomes,
            List<OpenAirEvent> events,
            int rejected,
            int requeued,
            ConservationReport conservation,
            Throwable fault,
            long[] changedChunks,
            List<FireIntent> fireIntents,
            List<FireEvent> fireEvents,
            List<PressureBreak> pressureBreaks) {}

    private final DimensionScheduler scheduler;
    private final World world;
    private final int prefetchRadius;
    private final int minSectionY;
    private final int maxSectionY;

    private final List<GasOp> queue = new ArrayList<>();
    private final Set<Long> loads = new LinkedHashSet<>();
    private final Set<Long> unloads = new LinkedHashSet<>();
    private long nextSeq = 1;
    private long[] mirrored = new long[0]; // ascending
    private long[] requested = new long[0];
    private Rejection pendingRejection;
    private Map<Long, Long> generationsAtClose = Map.of();
    private long closedTick = Long.MIN_VALUE;
    private int lastBatchSections;
    private volatile List<FlowEdge> flows = List.of();
    private volatile long flowsTick = Long.MIN_VALUE;

    // Persistence (architecture §4.5).
    /** Records of chunks that loaded since the last batch. */
    private final TreeMap<Long, ChunkRecord> loadedRecords = new TreeMap<>();
    /** Records sent to the worker and not yet confirmed by an applied result, with their tick. */
    private final TreeMap<Long, Sent> unconfirmed = new TreeMap<>();
    /** Every queued operation whose outcome hasn't been applied, by sequence number. */
    private final TreeMap<Long, GasOp> outstanding = new TreeMap<>();
    /**
     * What chunks saved at their unload, by chunk and then by zone, until they load again or the zone leaves
     * memory: their shares are written and can't change, so later ones make up the rest (DATA-008). Kept only
     * here: a restart starts from what's on disk, which holds no more than this did.
     */
    private final TreeMap<Long, Map<Long, double[]>> savedAway = new TreeMap<>();

    private long[] heldBlocks = new long[0];
    private long nextZoneId = 1;

    private record Sent(long tick, ChunkRecord record) {}

    /** A settling chunk's record hasn't gone to the worker yet. */
    private static final long NOT_SENT = Long.MAX_VALUE;

    /**
     * Loaded chunks whose record the published table doesn't hold yet, with the tick of the batch
     * that carries it, or {@link #NOT_SENT}. Written on the server thread, read on any.
     */
    private final Map<Long, Long> settling = new ConcurrentHashMap<>();

    private volatile AtmosSnapshot published;

    /** The hotspots of the last applied tick (API-055). Written on the server thread, read on any. */
    private volatile HotspotTable hotspots = HotspotTable.EMPTY;

    /**
     * @param scheduler the dimension's scheduler
     * @param world the world
     * @param minSectionY the dimension's lowest section y
     * @param maxSectionY its highest section y
     */
    public DimensionPipeline(DimensionScheduler scheduler, World world, int minSectionY, int maxSectionY) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.world = Objects.requireNonNull(world, "world");
        this.prefetchRadius = scheduler.engine().classifier().prefetchRadius();
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
        this.published = AtmosSnapshot.empty(scheduler.engine().registry());
    }

    public DimensionScheduler scheduler() {
        return scheduler;
    }

    /** The last published zone table. Any thread (API-046). */
    public AtmosSnapshot published() {
        return published;
    }

    /**
     * Whether a loaded chunk's saved zones haven't reached the published table yet: it loaded with
     * a record, and the result of the batch that carried the record hasn't been applied. Any
     * thread.
     */
    public boolean isSettling(long chunk) {
        return settling.containsKey(chunk);
    }

    /** Whether a batch is closed and not yet applied. */
    public boolean isInFlight() {
        return scheduler.isInFlight();
    }

    /** The atmos tick of the batch last closed. */
    public long closedTick() {
        return closedTick;
    }

    /** How many operations wait for the next batch. */
    public int queuedOps() {
        return queue.size();
    }

    /** How many sections the last batch carried. */
    public int lastBatchSections() {
        return lastBatchSections;
    }

    /** The sections the worker mirrors, as of the last apply, ascending. */
    public long[] mirroredSections() {
        return mirrored.clone();
    }

    // ---- Inputs between batches -------------------------------------------------

    /**
     * Queues an addition for the next batch.
     *
     * @return its sequence number
     */
    public long add(
            long block,
            double[] moles,
            double temperature,
            LedgerCategory category,
            boolean triggersZone,
            String source) {
        return add(block, -1, moles, temperature, category, triggersZone, source);
    }

    /**
     * Queues an addition to one sub-cell of a position for the next batch (API-015): {@code sub}
     * as in {@link GasOp#sub()}, -1 for the largest cell.
     *
     * @return its sequence number
     */
    public long add(
            long block,
            int sub,
            double[] moles,
            double temperature,
            LedgerCategory category,
            boolean triggersZone,
            String source) {
        long seq = nextSeq++;
        enqueue(new GasOp(seq, GasOp.Kind.ADD, block, moles, temperature, category, triggersZone, source, sub));
        return seq;
    }

    /**
     * Queues a removal for the next batch.
     *
     * @return its sequence number
     */
    public long remove(long block, double[] moles, LedgerCategory category, boolean triggersZone, String source) {
        return remove(block, -1, moles, category, triggersZone, source);
    }

    /**
     * Queues a removal from one sub-cell of a position for the next batch (API-015): {@code sub}
     * as in {@link GasOp#sub()}, -1 for the largest cell.
     *
     * @return its sequence number
     */
    public long remove(
            long block, int sub, double[] moles, LedgerCategory category, boolean triggersZone, String source) {
        long seq = nextSeq++;
        enqueue(new GasOp(seq, GasOp.Kind.REMOVE, block, moles, 0, category, triggersZone, source, sub));
        return seq;
    }

    /**
     * Queues an addition ({@code add}) or removal of {@code joules} of heat at one sub-cell of a
     * position for the next batch (API-013): {@code sub} as in {@link GasOp#sub()}, -1 for the
     * largest cell. Heat operations aren't saved with their chunk (DATA-011).
     *
     * @return its sequence number
     */
    public long heat(long block, int sub, boolean add, double joules, boolean triggersZone, String source) {
        long seq = nextSeq++;
        enqueue(GasOp.heat(seq, block, sub, add, joules, triggersZone, source));
        return seq;
    }

    /**
     * Queues a pin of the zone at one sub-cell of a position for the next batch (API-017, API-050):
     * {@code sub} as in {@link GasOp#sub()}, -1 for the largest cell. If the cell is enclosed and
     * unzoned, a zone forms there holding the ambient's gas.
     *
     * @param owner the pin's owner
     * @return its sequence number
     */
    public long pin(long block, int sub, String owner) {
        long seq = nextSeq++;
        enqueue(GasOp.pin(seq, block, sub, owner));
        return seq;
    }

    /**
     * Queues the removal of an owner's pins from every zone for the next batch (API-017).
     *
     * @return its sequence number
     */
    public long unpin(String owner) {
        long seq = nextSeq++;
        enqueue(GasOp.unpin(seq, owner));
        return seq;
    }

    /**
     * Queues an ignition at a position for the next batch (FIRE-014): a flammable zone in the
     * position's cell or a neighbouring one starts burning there, in the hotspot phase. It never
     * forms a zone, and it isn't saved with its chunk.
     *
     * @return its sequence number
     */
    public long ignite(long block, String source) {
        long seq = nextSeq++;
        enqueue(GasOp.ignite(seq, block, source));
        return seq;
    }

    /**
     * Queues a reset of the zone at one sub-cell of a position to the ambient for the next batch
     * (OPS-013): {@code sub} as in {@link GasOp#sub()}, -1 for the largest cell. It isn't saved with
     * its chunk.
     *
     * @param source who asked, whom the gas removed is attributed to
     * @return its sequence number
     */
    public long reset(long block, int sub, String source) {
        long seq = nextSeq++;
        enqueue(GasOp.reset(seq, block, sub, source));
        return seq;
    }

    /**
     * Queues a saved addition again, under a new sequence number (DATA-011). Its outcome has no
     * caller to go to.
     *
     * @return its new sequence number
     */
    public long requeue(GasOp saved) {
        if (saved.kind() != GasOp.Kind.ADD) {
            throw new IllegalArgumentException("only additions are saved: " + saved);
        }
        return add(
                saved.block(),
                saved.moles(),
                saved.temperature(),
                saved.category(),
                saved.triggersZone(),
                saved.source());
    }

    /**
     * Queues an operation taken from another pipeline under a new sequence number, its kind,
     * amounts and source kept: for carrying the outstanding operations of a retired engine over to
     * the one that replaces it, so none is lost (architecture §4.5).
     *
     * @return its new sequence number
     */
    public long resubmit(GasOp op) {
        return switch (op.kind()) {
            case ADD -> add(op.block(), op.moles(), op.temperature(), op.category(), op.triggersZone(), op.source());
            case REMOVE -> remove(op.block(), op.moles(), op.category(), op.triggersZone(), op.source());
            case PIN -> pin(op.block(), op.sub(), op.source());
            case UNPIN -> unpin(op.source());
            case IGNITE -> ignite(op.block(), op.source());
            case RESET -> reset(op.block(), op.sub(), op.source());
            case ADD_HEAT, REMOVE_HEAT ->
                heat(
                        op.block(),
                        op.sub(),
                        op.kind() == GasOp.Kind.ADD_HEAT,
                        op.energy(),
                        op.triggersZone(),
                        op.source());
        };
    }

    private void enqueue(GasOp op) {
        queue.add(op);
        outstanding.put(op.seq(), op);
    }

    /** A chunk loaded, with nothing saved. */
    public void chunkLoaded(long chunk) {
        chunkLoaded(chunk, null);
    }

    /**
     * A chunk loaded, with what it saved (DATA-002), or null. The record's zones and held blocks go
     * to the worker in the next batch; its queued additions are queued again now (DATA-011).
     */
    public void chunkLoaded(long chunk, ChunkRecord record) {
        savedAway.remove(chunk);
        unloads.remove(chunk);
        loads.add(chunk);
        loadedRecords.remove(chunk);
        settling.remove(chunk);
        if (record != null) {
            if (record.chunk() != chunk) {
                throw new IllegalArgumentException("the record is of another chunk: " + record);
            }
            ChunkRecord kept = record.withoutQueued();
            if (!kept.isEmpty()) {
                loadedRecords.put(chunk, kept);
                settling.put(chunk, NOT_SENT);
            }
            for (GasOp op : record.queuedAdditions()) {
                requeue(op);
            }
        }
    }

    /**
     * A chunk unloaded. Build what it saves with {@link #recordFor} first: a record it loaded with
     * that hasn't gone to the worker yet is dropped here.
     */
    public void chunkUnloaded(long chunk) {
        Map<Long, double[]> saved = new HashMap<>();
        ChunkRecord record = recordFor(chunk, false);
        if (record != null) {
            for (ZoneShare share : record.zones()) {
                saved.put(share.zoneId(), share.moles());
            }
        }
        savedAway.put(chunk, saved);
        loads.remove(chunk);
        unloads.add(chunk);
        loadedRecords.remove(chunk);
        settling.remove(chunk);
    }

    // ---- Close ----------------------------------------------------------------------

    /**
     * Closes batch {@code tick} and submits it.
     *
     * @param overBudget polled between prefetch captures; once true, the rest of the prefetch is
     *     skipped and the operations it was for fetch their sections a tick later (CORE-047)
     * @throws IllegalStateException if a batch is in flight
     */
    public void close(long tick, EngineSettings settings, BooleanSupplier overBudget) {
        if (scheduler.isInFlight()) {
            throw new IllegalStateException("batch " + closedTick + " hasn't been applied");
        }
        // A change in a section the worker doesn't mirror needs no copy, but it needs telling of: a
        // classification may rest on that section (GEO-027).
        List<Long> changedOutsideMirror = new ArrayList<>();
        long[] dirty = world.drainDirty(k -> {
            if (mirrors(k)) {
                return true;
            }
            changedOutsideMirror.add(k);
            return false;
        });
        TreeSet<Long> keys = new TreeSet<>(SectionKeys::compare);
        for (long k : dirty) {
            keys.add(k);
        }
        TreeSet<Long> unavailable = new TreeSet<>();
        for (long k : requested) {
            keys.add(k);
        }
        List<GasOp> ops = List.copyOf(queue);
        queue.clear();
        boolean prefetching = true;
        for (GasOp op : ops) {
            long target = SectionKeys.sectionOfBlockKey(op.block());
            if (!op.triggersZone() || mirrors(target) || keys.contains(target)) {
                continue;
            }
            if (!world.isLoaded(SectionKeys.chunkOf(target))) {
                unavailable.add(target);
                continue;
            }
            keys.add(target);
            if (prefetching && overBudget.getAsBoolean()) {
                prefetching = false;
            }
            if (prefetching) {
                prefetch(target, keys);
            }
        }
        // Fresh copies of what the worker mirrors in chunks that loaded again, and the sections the
        // loaded records' cells are in, with their neighbours (architecture §4.5).
        for (long k : mirrored) {
            if (loads.contains(SectionKeys.chunkOf(k))) {
                keys.add(k);
            }
        }
        // A record whose chunk is already gone again, before the server reports its unload, waits:
        // its sections can't be captured, so the worker would drop its shares, and once that
        // result applied the chunk would save without them. Its unload saves it from here.
        List<ChunkRecord> records = new ArrayList<>();
        for (Iterator<ChunkRecord> it = loadedRecords.values().iterator(); it.hasNext(); ) {
            ChunkRecord r = it.next();
            if (world.isLoaded(r.chunk())) {
                records.add(r);
                it.remove();
            }
        }
        TreeSet<Long> unloadedListed = new TreeSet<>();
        Classifier classifier = scheduler.engine().classifier();
        for (ChunkRecord r : records) {
            Set<Long> recordSections = new HashSet<>();
            for (ZoneShare share : r.zones()) {
                for (long c : share.zoneChunks()) {
                    if (!world.isLoaded(c)) {
                        unloadedListed.add(c);
                    }
                }
                for (long cell : share.cells()) {
                    recordSections.add(classifier.sectionOf(cell));
                }
            }
            for (long k : recordSections) {
                addWithNeighbours(k, keys);
            }
            unconfirmed.put(r.chunk(), new Sent(tick, r));
            settling.replace(r.chunk(), NOT_SENT, tick);
        }
        List<SectionSource> sections = new ArrayList<>(keys.size());
        Map<Long, Long> generations = new HashMap<>();
        for (long k : keys) {
            int y = SectionKeys.y(k);
            if (y < minSectionY || y > maxSectionY) {
                continue;
            }
            SectionSource s = world.capture(k);
            if (s == null) {
                unavailable.add(k);
                continue;
            }
            sections.add(s);
            recordGeneration(generations, SectionKeys.chunkOf(k));
        }
        for (long k : mirrored) {
            recordGeneration(generations, SectionKeys.chunkOf(k));
        }
        generationsAtClose = generations;
        lastBatchSections = sections.size();
        Batch batch = new Batch(
                tick,
                sections,
                toArray(loads),
                toArray(unloads),
                toArray(unavailable),
                world.ambient(),
                ops,
                pendingRejection,
                settings,
                records,
                toArray(unloadedListed),
                toArray(changedOutsideMirror),
                world.limits());
        loads.clear();
        unloads.clear();
        pendingRejection = null;
        closedTick = tick;
        scheduler.submit(batch);
    }

    private void prefetch(long center, Set<Long> keys) {
        int cx = SectionKeys.x(center);
        int cy = SectionKeys.y(center);
        int cz = SectionKeys.z(center);
        int r = prefetchRadius;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!world.isLoaded(SectionKeys.chunk(x, z))) {
                    continue;
                }
                for (int y = Math.max(minSectionY, cy - r); y <= Math.min(maxSectionY, cy + r); y++) {
                    long k = SectionKeys.section(x, y, z);
                    if (!mirrors(k)) {
                        keys.add(k);
                    }
                }
            }
        }
        // The column check looks all the way up (GEO-021).
        for (int y = Math.max(minSectionY, cy + r + 1); y <= maxSectionY; y++) {
            long k = SectionKeys.section(cx, y, cz);
            if (!mirrors(k)) {
                keys.add(k);
            }
        }
    }

    /** Adds a section and its six neighbours that are in loaded chunks and not mirrored. */
    private void addWithNeighbours(long section, Set<Long> keys) {
        int x = SectionKeys.x(section);
        int y = SectionKeys.y(section);
        int z = SectionKeys.z(section);
        long[] all = {
            section,
            SectionKeys.section(x + 1, y, z),
            SectionKeys.section(x - 1, y, z),
            SectionKeys.section(x, y + 1, z),
            SectionKeys.section(x, y - 1, z),
            SectionKeys.section(x, y, z + 1),
            SectionKeys.section(x, y, z - 1)
        };
        for (long k : all) {
            int ky = SectionKeys.y(k);
            if (ky >= minSectionY
                    && ky <= maxSectionY
                    && !mirrors(k)
                    && (k == section || world.isLoaded(SectionKeys.chunkOf(k)))) {
                keys.add(k);
            }
        }
    }

    private void recordGeneration(Map<Long, Long> generations, long chunk) {
        generations.computeIfAbsent(chunk, world::lifecycleGeneration);
    }

    private boolean mirrors(long section) {
        return Arrays.binarySearch(mirrored, section) >= 0;
    }

    private static long[] toArray(List<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static long[] toArray(Set<Long> set) {
        long[] out = new long[set.size()];
        int i = 0;
        for (long v : set) {
            out[i++] = v;
        }
        return out;
    }

    // ---- Apply --------------------------------------------------------------------

    /** The hotspots burning after the last applied atmos tick (FIRE-011, API-055). Any thread. */
    public HotspotTable hotspots() {
        return hotspots;
    }

    /**
     * The flow edges of the last applied atmos tick (FLOW-001): empty with airflow off. Any
     * thread.
     */
    public List<FlowEdge> flows() {
        return flows;
    }

    /** The atmos tick {@link #flows} came from, or {@link Long#MIN_VALUE} before the first. Any thread. */
    public long flowsTick() {
        return flowsTick;
    }

    /**
     * Collects the in-flight result, waiting for the worker if needed, checks it against the
     * world and publishes it (CORE-069, CORE-070).
     *
     * @throws IllegalStateException if nothing is in flight
     */
    public Applied apply() {
        long tick = closedTick;
        Result result = scheduler.await();
        if (result == null) {
            return new Applied(
                    tick,
                    List.of(),
                    List.of(),
                    0,
                    0,
                    null,
                    scheduler.fault(),
                    new long[0],
                    List.of(),
                    List.of(),
                    List.of());
        }
        List<Integer> rejectedIndices = new ArrayList<>();
        List<Long> rejectedZones = new ArrayList<>();
        int requeued = 0;
        for (Component c : result.components()) {
            if (isStale(c)) {
                rejectedIndices.add(c.index());
                for (long id : c.zoneIds()) {
                    rejectedZones.add(id);
                }
                requeued += c.opSeqs().length;
            }
        }
        List<OpOutcome> outcomes = new ArrayList<>(result.outcomes().size());
        for (OpOutcome o : result.outcomes()) {
            if (o.component() < 0 || !rejectedIndices.contains(o.component())) {
                outcomes.add(o);
            }
        }
        AtmosSnapshot snapshot = result.snapshot();
        if (!rejectedIndices.isEmpty()) {
            int[] indices = new int[rejectedIndices.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = rejectedIndices.get(i);
            }
            pendingRejection = new Rejection(result.tick(), indices);
            long[] zones = new long[rejectedZones.size()];
            for (int i = 0; i < zones.length; i++) {
                zones[i] = rejectedZones.get(i);
            }
            snapshot = snapshot.withRejected(published, zones);
        }
        Set<Long> rejectedSet = new HashSet<>(rejectedZones);
        List<FireIntent> intents = new ArrayList<>();
        for (FireIntent i : result.fireIntents()) {
            if (!rejectedSet.contains(i.zoneId())) {
                intents.add(i);
            }
        }
        List<FireEvent> fireEvents = new ArrayList<>();
        for (FireEvent e : result.fireEvents()) {
            if (!rejectedSet.contains(e.zoneId())) {
                fireEvents.add(e);
            }
        }
        List<PressureBreak> breaks = new ArrayList<>();
        for (PressureBreak b : result.pressureBreaks()) {
            if (!rejectedSet.contains(b.highZone()) && !rejectedSet.contains(b.lowZone())) {
                breaks.add(b);
            }
        }
        HotspotTable burning = result.hotspots().withRejected(hotspots, rejectedSet);
        TreeSet<Long> changed = changedChunks(published, snapshot);
        // Nothing below can throw, so an exception above leaves the last result whole (NFR-014).
        for (OpOutcome o : outcomes) {
            outstanding.remove(o.seq());
        }
        for (java.util.Iterator<Map.Entry<Long, Sent>> it =
                        unconfirmed.entrySet().iterator();
                it.hasNext(); ) {
            Map.Entry<Long, Sent> e = it.next();
            if (e.getValue().tick() <= result.tick()) {
                changed.add(e.getKey());
                it.remove();
            }
        }
        for (long b : heldBlocks) {
            changed.add(SectionKeys.chunkOfBlockKey(b));
        }
        heldBlocks = result.heldBlocks();
        for (long b : heldBlocks) {
            changed.add(SectionKeys.chunkOfBlockKey(b));
        }
        nextZoneId = result.nextZoneId();
        hotspots = burning;
        published = snapshot;
        if (rejectedIndices.isEmpty()) {
            // A rejected zone is answered from the last table, so this one may not show its chunks.
            forgetSavedAway();
        }
        flows = result.flows();
        flowsTick = result.tick();
        // After publishing, so a reader that sees a chunk settled sees its zones.
        settling.values().removeIf(t -> t <= result.tick());
        mirrored = result.mirroredSections();
        requested = result.requestedSections();
        return new Applied(
                result.tick(),
                outcomes,
                result.events(),
                rejectedIndices.size(),
                requeued,
                result.conservation(),
                null,
                toArray(changed),
                intents,
                fireEvents,
                breaks);
    }

    /** The chunks of zones whose published state differs between two tables. */
    private static TreeSet<Long> changedChunks(AtmosSnapshot before, AtmosSnapshot after) {
        TreeSet<Long> out = new TreeSet<>();
        for (ZoneView z : after.zones()) {
            ZoneView old = before.zone(z.id());
            if (!z.equals(old) || !Arrays.equals(after.zoneChunks(z.id()), before.zoneChunks(z.id()))) {
                addAll(out, after.zoneChunks(z.id()));
                addAll(out, before.zoneChunks(z.id()));
            }
        }
        for (ZoneView z : before.zones()) {
            if (after.zone(z.id()) == null) {
                addAll(out, before.zoneChunks(z.id()));
            }
        }
        return out;
    }

    private static void addAll(Set<Long> out, long[] values) {
        for (long v : values) {
            out.add(v);
        }
    }

    // ---- Persistence (architecture §4.5) ---------------------------------------------

    /**
     * What a chunk saves now (DATA-002): its share of every published zone with cells in it, the
     * shares of a record it loaded with that the worker hasn't confirmed yet, the positions in it
     * whose changes are held for paused zones (DATA-010) and, if {@code withQueued}, the queued
     * additions that target it (DATA-011). Built from the published table only, never waiting for
     * the worker.
     *
     * @param withQueued whether to include queued additions; true only for the final save at
     *     shutdown, since they are queued again when the chunk loads
     * @return the record, or null if the chunk has nothing to save
     */
    public ChunkRecord recordFor(long chunk, boolean withQueued) {
        List<ZoneShare> zones = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (ZoneShare z : published.shares(chunk)) {
            zones.add(shareLeft(chunk, z));
            ids.add(z.zoneId());
        }
        ChunkRecord pending = loadedRecords.get(chunk);
        if (pending == null && unconfirmed.containsKey(chunk)) {
            pending = unconfirmed.get(chunk).record();
        }
        List<Long> held = new ArrayList<>();
        if (pending != null) {
            for (ZoneShare z : pending.zones()) {
                if (ids.add(z.zoneId())) {
                    zones.add(z);
                }
            }
            for (long b : pending.heldBlocks()) {
                held.add(b);
            }
            zones.sort((a, b) -> Long.compare(a.zoneId(), b.zoneId()));
        }
        for (long b : heldBlocks) {
            if (SectionKeys.chunkOfBlockKey(b) == chunk) {
                held.add(b);
            }
        }
        List<GasOp> queued = new ArrayList<>();
        if (withQueued) {
            for (GasOp op : outstanding.values()) {
                if (op.kind() == GasOp.Kind.ADD && SectionKeys.chunkOfBlockKey(op.block()) == chunk) {
                    queued.add(op);
                }
            }
        }
        ChunkRecord r = new ChunkRecord(chunk, zones, toArray(held), queued);
        return r.isEmpty() ? null : r;
    }

    /**
     * A chunk's share of a zone once others of its chunks have saved and unloaded: what they left of its
     * gas, by volume among the chunks that haven't. While the zone hasn't changed since they saved that's
     * its plain share. If it has (its cells or its gas can change after a share is written, since the
     * table it came from may be a tick behind, and a paused zone still takes in shares), the shares still
     * add up to what it holds (DATA-008).
     */
    private ZoneShare shareLeft(long chunk, ZoneShare share) {
        long id = share.zoneId();
        double volumeLeft = published.zoneVolume(id);
        double[] left = null;
        for (long other : share.zoneChunks()) {
            Map<Long, double[]> saved = other == chunk ? null : savedAway.get(other);
            // With no cells there, it's a share the zone hasn't taken in, so its gas isn't in the zone.
            double volumeThere = saved == null ? 0 : published.zoneVolumeIn(id, other);
            if (volumeThere > 0) {
                if (left == null) {
                    left = published.zone(id).moles();
                }
                volumeLeft -= volumeThere;
                double[] taken = saved.get(id);
                for (int g = 0; taken != null && g < left.length && g < taken.length; g++) {
                    left[g] -= taken[g];
                }
            }
        }
        if (left == null) {
            return share;
        }
        double volume = published.zoneVolumeIn(id, chunk);
        double f = volume >= volumeLeft ? 1 : volume / volumeLeft;
        for (int g = 0; g < left.length; g++) {
            left[g] = Math.max(0, left[g]) * f;
        }
        return new ZoneShare(id, share.cells(), left, share.temperature(), share.zoneChunks(), share.pins());
    }

    /**
     * Forgets what unloaded chunks saved of zones that have left memory, whose gas is only in those shares
     * now, and the chunks that saved nothing that matters any more.
     */
    private void forgetSavedAway() {
        if (savedAway.isEmpty()) {
            return;
        }
        Set<Long> spanned = new HashSet<>();
        addAll(spanned, published.chunks());
        for (Iterator<Map.Entry<Long, Map<Long, double[]>>> it =
                        savedAway.entrySet().iterator();
                it.hasNext(); ) {
            Map.Entry<Long, Map<Long, double[]>> e = it.next();
            e.getValue().keySet().removeIf(id -> published.zone(id) == null);
            if (e.getValue().isEmpty() && !spanned.contains(e.getKey())) {
                it.remove();
            }
        }
    }

    /**
     * Every chunk {@link #recordFor} may build a record for, ascending: those of published zones,
     * of records the worker hasn't confirmed yet, and of held positions. For rebuilding the zones
     * from the records of the loaded chunks, as after a restart (architecture §4.5).
     */
    public long[] recordChunks() {
        TreeSet<Long> out = new TreeSet<>();
        addAll(out, published.chunks());
        out.addAll(loadedRecords.keySet());
        out.addAll(unconfirmed.keySet());
        for (long b : heldBlocks) {
            out.add(SectionKeys.chunkOfBlockKey(b));
        }
        return toArray(out);
    }

    /** The queued additions whose outcomes haven't been applied, in sequence order (DATA-011). */
    public List<GasOp> outstandingAdditions() {
        List<GasOp> out = new ArrayList<>();
        for (GasOp op : outstanding.values()) {
            if (op.kind() == GasOp.Kind.ADD) {
                out.add(op);
            }
        }
        return out;
    }

    /** The queued operations whose outcomes haven't been applied, in sequence order. */
    public List<GasOp> outstanding() {
        return List.copyOf(outstanding.values());
    }

    /** The positions whose changes are held for paused zones, as of the last apply (GEO-037). */
    public long[] heldBlocks() {
        return heldBlocks.clone();
    }

    /** The id the next new zone gets, as of the last apply (CORE-026). */
    public long nextZoneId() {
        return nextZoneId;
    }

    /** Whether a chunk the component spans unloaded since batch close (§3.3). */
    private boolean isStale(Component c) {
        for (long chunk : c.chunks()) {
            Long atClose = generationsAtClose.get(chunk);
            if (atClose == null || atClose != world.lifecycleGeneration(chunk)) {
                return true;
            }
        }
        return false;
    }
}
