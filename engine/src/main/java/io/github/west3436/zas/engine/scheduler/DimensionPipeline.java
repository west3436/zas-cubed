package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * <p>Server thread only, except {@link #published()}, which any thread may read (API-046).
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
     */
    public record Applied(
            long tick,
            List<OpOutcome> outcomes,
            List<OpenAirEvent> events,
            int rejected,
            int requeued,
            ConservationReport conservation,
            Throwable fault,
            long[] changedChunks) {}

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

    // Persistence (architecture §4.5).
    /** Records of chunks that loaded since the last batch. */
    private final TreeMap<Long, ChunkRecord> loadedRecords = new TreeMap<>();
    /** Records sent to the worker and not yet confirmed by an applied result, with their tick. */
    private final TreeMap<Long, Sent> unconfirmed = new TreeMap<>();
    /** Every queued operation whose outcome hasn't been applied, by sequence number. */
    private final TreeMap<Long, GasOp> outstanding = new TreeMap<>();

    private long[] heldBlocks = new long[0];
    private long nextZoneId = 1;

    private record Sent(long tick, ChunkRecord record) {}

    private volatile AtmosSnapshot published;

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
        long seq = nextSeq++;
        enqueue(new GasOp(seq, GasOp.Kind.ADD, block, moles, temperature, category, triggersZone, source));
        return seq;
    }

    /**
     * Queues a removal for the next batch.
     *
     * @return its sequence number
     */
    public long remove(long block, double[] moles, LedgerCategory category, boolean triggersZone, String source) {
        long seq = nextSeq++;
        enqueue(new GasOp(seq, GasOp.Kind.REMOVE, block, moles, 0, category, triggersZone, source));
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
        unloads.remove(chunk);
        loads.add(chunk);
        loadedRecords.remove(chunk);
        if (record != null) {
            if (record.chunk() != chunk) {
                throw new IllegalArgumentException("the record is of another chunk: " + record);
            }
            ChunkRecord kept = record.withoutQueued();
            if (!kept.isEmpty()) {
                loadedRecords.put(chunk, kept);
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
        loads.remove(chunk);
        unloads.add(chunk);
        loadedRecords.remove(chunk);
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
        long[] dirty = world.drainDirty(this::mirrors);
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
        List<ChunkRecord> records = new ArrayList<>(loadedRecords.values());
        loadedRecords.clear();
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
                toArray(unloadedListed));
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
            return new Applied(tick, List.of(), List.of(), 0, 0, null, scheduler.fault(), new long[0]);
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
        TreeSet<Long> changed = changedChunks(published, snapshot);
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
        published = snapshot;
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
                toArray(changed));
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
        List<ZoneShare> zones = new ArrayList<>(published.shares(chunk));
        Set<Long> ids = new HashSet<>();
        for (ZoneShare z : zones) {
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
