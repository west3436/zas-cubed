package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.EngineLog;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.conservation.ConservationLedger;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.zone.Edge;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One dimension's simulation (CORE-042): its zone graph, section mirror, classifier and the
 * operations waiting for classification, advanced one atmos tick per {@link Batch} by {@link
 * #tick}. Plain Java with no Minecraft (CORE-043).
 *
 * <p><b>Threads.</b> The engine is owned by whichever thread calls {@link #tick}: the atmos worker
 * in game (CORE-045), or the caller in single-threaded mode (CORE-071). Ticks of one engine must
 * not overlap. It never sees the live world, only batches (CORE-049); what it returns is
 * immutable.
 *
 * <p><b>One tick</b> (CORE-040, architecture §3.2):
 *
 * <ol start="0">
 *   <li>Restore the components the server thread rejected from the previous result, and re-queue
 *       their operations (CORE-070, CORE-074).
 *   <li>Geometry: note chunk loads and unloads, take zones whose chunks have all unloaded out of
 *       memory, store the batch's section snapshots in the mirror and refresh the cells of every
 *       changed position and its neighbours (GEO-012), holding changes that touch paused zones
 *       (GEO-037); then build or extend zones from the saved shares of chunks that loaded
 *       (DATA-002).
 *   <li>Boundaries: the classifier resolves breaches ({@link Classifier#afterGeometry}); then every
 *       zone is paused or resumed (GEO-030).
 *   <li>Gas operations, in sequence order: each finds the cell it targets. A zone-triggering
 *       operation at an unzoned cell has it classified; if enclosed, the zone is created here
 *       (CORE-051, CORE-055). Then the <i>undo point</i> is taken, and every operation that isn't
 *       waiting for classification is applied to its zone, the open air (CORE-053, CORE-057) or an
 *       unresolved region.
 *   <li>Exchange (CORE-031 to CORE-038, CORE-023, CORE-076).
 *   <li>to 8. Heat, hotspots, zone updates and garbage collection: R1, empty for now.
 * </ol>
 *
 * <p>Then the invariant checker runs if enabled (CORE-073), and the engine builds the {@link
 * Result}: the zone table, the exchange components with the chunks they span, operation outcomes
 * and open-air events (CORE-058).
 *
 * <p><b>Paused zones</b> (GEO-030 to GEO-038, architecture §4.6). A zone pauses while any chunk
 * it spans is unloaded, while it waits for chunks its saved shares list, or while it opens onto a
 * region that can't be classified because it reaches an unloaded chunk ({@link
 * Classifier#heldZones}, GEO-026). A paused zone keeps its mixture and its cells: it doesn't
 * exchange, gas operations on it are voided or supplied at its last-known state (GEO-036,
 * GEO-038), and block changes touching it are held, in order, until it resumes (GEO-037). The
 * engine never asks for a chunk to be loaded (GEO-034). Once every chunk a paused zone spans has
 * unloaded, and no zone with a loaded chunk shares an edge with it, it is taken out of memory: its
 * state is in the chunks' saved shares, from which it is rebuilt when they load again (DATA-002,
 * DATA-008).
 *
 * <p><b>Determinism</b> (CORE-044, CORE-072, architecture §3.5): operations run in sequence order,
 * sections in key order, cells in ascending id order, zones and edges in id order; hash
 * collections are used for lookups only; nothing reads a clock or a shared random source.
 */
public final class DimensionEngine {
    private final GasRegistry registry;
    private final GasConfig config;
    private final EngineLog log;
    private final SectionMirror mirror;
    private final Classifier classifier;
    private final CellSpace space;
    private final ZoneGraph graph;
    private final GeometryPalette palette;

    private Ambient ambient;
    private Phase.Listener phases = Phase.Listener.NONE;
    private long lastTick = Long.MIN_VALUE;
    private final List<GasOp> buffered = new ArrayList<>();

    // Paused zones and storage (architecture §4.5, §4.6).
    /** Zone id: the chunks its saved shares list that haven't loaded yet, ascending. */
    private final TreeMap<Long, long[]> missingChunks = new TreeMap<>();
    /** Zones rebuilt from saved shares whose surface is classified when they resume: id to held blocks. */
    private final TreeMap<Long, long[]> rebuilt = new TreeMap<>();
    /** The latest capture of each section with changes held for paused zones (GEO-037). */
    private final TreeMap<Long, SectionSnapshot> heldTruth = new TreeMap<>(SectionKeys::compare);
    /** Zones the classifier holds at unresolved breaches (GEO-026), from the last boundaries phase. */
    private long[] heldZoneIds = new long[0];

    /** Membership as of the end of the last tick, for the snapshot; null when it must be rebuilt. */
    private Membership membership = Membership.EMPTY;

    private AtmosSnapshot lastSnapshot;

    // The last tick's undo point, kept until the next tick in case its components are rejected.
    private Membership undoMembership = Membership.EMPTY;
    private GasMixture[] undoMixtures = new GasMixture[0];
    private boolean[] undoPinned = new boolean[0];
    private List<Component> lastComponents = List.of();
    private Map<Long, GasOp> lastZoneOps = Map.of();

    /** The cells operations found unresolved in the last tick, for the debug overlay (CLIENT-005). */
    private final List<Long> lastUnresolved = new ArrayList<>();

    // Side tables of the published snapshot (API-015, GEO-026), each kept until what it is built
    // from changes.
    private Membership partialFor;
    private CellTable partialCells = CellTable.EMPTY;
    private long[] heldFor = new long[0];
    private Membership heldMembership;
    private boolean geometryChanged;
    private CellTable heldCells = CellTable.EMPTY;

    /**
     * @param registry the gases; fixed for the engine's lifetime
     * @param config mixture settings
     * @param classifierFactory builds the classifier over this engine's mirror
     * @param palette the geometry palette section snapshots refer to
     * @param minSectionY the dimension's lowest section y
     * @param maxSectionY its highest section y
     * @param ambient the dimension's ambient until a batch brings another
     * @param settings exchange settings until a batch brings others
     */
    public DimensionEngine(
            GasRegistry registry,
            GasConfig config,
            Classifier.Factory classifierFactory,
            GeometryPalette palette,
            int minSectionY,
            int maxSectionY,
            Ambient ambient,
            EngineSettings settings) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.log = config.log();
        this.palette = Objects.requireNonNull(palette, "palette");
        this.mirror = new SectionMirror(minSectionY, maxSectionY);
        this.classifier = Objects.requireNonNull(classifierFactory.create(mirror, palette), "classifier");
        this.space = Objects.requireNonNull(classifier.space(), "cell space");
        this.ambient = Objects.requireNonNull(ambient, "ambient");
        this.graph = new ZoneGraph(
                registry,
                config,
                space,
                ambientMixture(ambient),
                settings.exchange(),
                ConservationLedger.of(registry, config));
        this.lastSnapshot = AtmosSnapshot.empty(registry);
    }

    // ---- Queries (owner thread) -----------------------------------------------

    /** The zone graph. Read it only on the thread that ticks this engine, between ticks. */
    public ZoneGraph graph() {
        return graph;
    }

    /** The section mirror. Same thread rule as {@link #graph()}. */
    public SectionMirror mirror() {
        return mirror;
    }

    /** The geometry palette the mirrored sections' ids index. */
    public GeometryPalette palette() {
        return palette;
    }

    public Classifier classifier() {
        return classifier;
    }

    public GasRegistry registry() {
        return registry;
    }

    /**
     * Makes sure new zones get ids of at least {@code next} (CORE-026): the platform passes the
     * counter saved with the dimension before the first tick, so ids are never reused across
     * sessions. Owner thread, between ticks.
     */
    public void reserveZoneIds(long next) {
        graph.reserveZoneIds(next);
    }

    /** The atmos tick last run, or {@link Long#MIN_VALUE}. */
    public long lastTick() {
        return lastTick;
    }

    /** The operations waiting for classification, in sequence order. */
    public List<GasOp> bufferedOps() {
        return List.copyOf(buffered);
    }

    /** The table built by the last tick. */
    public AtmosSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    /** Observes the phases of each tick (CORE-040). */
    public void setPhaseListener(Phase.Listener listener) {
        phases = Objects.requireNonNull(listener, "listener");
    }

    /**
     * A hash of the engine's state: zones with their cells, mixtures and edges, waiting operations
     * and mirrored sections. Equal states hash equally in any JVM (CORE-044, CORE-071).
     */
    public long stateHash() {
        Hasher h = new Hasher();
        h.add(lastTick);
        for (Zone z : graph.zones()) {
            h.add(z.id());
            long[] cells = z.cells();
            Arrays.sort(cells);
            for (long c : cells) {
                h.add(c);
            }
            h.add(z.volume());
            GasMixture m = z.mixture();
            for (int i = 0; i < registry.size(); i++) {
                h.add(m.moles(i));
            }
            h.add(m.temperature());
            h.add(z.isPaused() ? 1 : 0);
            for (Edge e : z.edges()) {
                Zone other = e.other(z);
                h.add(other == null ? 0 : other.id());
                h.add(e.connections());
                h.add(e.directConnections());
                h.add(e.isSleeping() ? 1 : 0);
            }
        }
        for (GasOp op : buffered) {
            h.add(op.seq());
        }
        long[] keys = mirror.keys();
        for (long k : keys) {
            h.add(k);
        }
        return h.value;
    }

    // ---- The tick ---------------------------------------------------------------

    /**
     * Runs one atmos tick.
     *
     * @throws IllegalArgumentException if the batch's tick isn't after the last one
     */
    public Result tick(Batch batch) {
        if (batch.tick() <= lastTick) {
            throw new IllegalArgumentException("atmos tick " + batch.tick() + " is not after " + lastTick);
        }
        long tick = batch.tick();

        // 0. Restore rejected components.
        phases.enter(Phase.RESTORE);
        graph.setSettings(batch.settings().exchange());
        if (!batch.ambient().equals(ambient)) {
            ambient = batch.ambient();
            graph.setAmbient(ambientMixture(ambient));
        }
        List<GasOp> requeued = restore(batch.rejection());
        lastTick = tick;
        graph.beginConservationPeriod();

        // 1. Geometry.
        phases.enter(Phase.GEOMETRY);
        CellSet surfaces = new CellSet();
        List<Long> resuming = new ArrayList<>();
        long[] refreshed = geometry(batch, surfaces, resuming);

        // 2. Boundaries, then pause and resume zones.
        phases.enter(Phase.BOUNDARIES);
        TreeSet<Long> requested = new TreeSet<>();
        long[] extra = surfaces.sorted();
        long[] toClassify = extra.length == 0 ? refreshed : union(refreshed, extra);
        geometryChanged = toClassify.length > 0;
        classifier.afterGeometry(graph, toClassify, requested::add);
        heldZoneIds = classifier.heldZones();
        // A rebuilt zone stays paused until what its surface opens onto is classified.
        Set<Long> waiting = new HashSet<>();
        for (long id : resuming) {
            if (classifier.hasPending()) {
                waiting.add(id);
            } else {
                rebuilt.remove(id);
            }
        }
        updatePaused(waiting);

        // 3. Gas operations.
        phases.enter(Phase.GAS_OPERATIONS);
        List<GasOp> ops = mergeBySeq(requeued, buffered, batch.ops());
        buffered.clear();
        Target[] targets = classifyAll(ops, requested);
        lastUnresolved.clear();
        for (Target t : targets) {
            if (t.route() == Route.UNRESOLVED && t.cell() != CellSpace.NO_CELL) {
                lastUnresolved.add(t.cell());
            }
        }
        if (membership == null || toClassify.length > 0 || !membership.sameZones(graph)) {
            membership = Membership.of(graph, space);
        }
        Membership undo = membership;
        GasMixture[] undoCopies = new GasMixture[undo.zoneIds.length];
        long[][] undoPartners = new long[undo.zoneIds.length][];
        boolean[] undoPins = new boolean[undo.zoneIds.length];
        int zi = 0;
        for (Zone z : graph.zones()) {
            undoCopies[zi] = z.mixture().copy();
            undoPins[zi] = z.isPinned();
            undoPartners[zi] = partners(z);
            zi++;
        }
        List<OpenAirEvent> events = new ArrayList<>();
        long[] opZone = new long[ops.size()];
        OpOutcome[] outcomes = new OpOutcome[ops.size()];
        for (int i = 0; i < ops.size(); i++) {
            outcomes[i] = apply(ops.get(i), targets[i], events, opZone, i);
        }

        // 4. Exchange.
        phases.enter(Phase.EXCHANGE);
        graph.exchange();

        // 5-8. R1.
        phases.enter(Phase.HEAT);
        phases.enter(Phase.HOTSPOTS);
        phases.enter(Phase.ZONE_UPDATES);
        phases.enter(Phase.GARBAGE_COLLECTION);

        ConservationReport report = batch.settings().checkConservation() ? graph.checkConservation(tick) : null;

        // Components, outcomes and the snapshot.
        int[] componentOf = new int[undo.zoneIds.length];
        List<Component> components = components(undo, undoCopies, undoPartners, ops, opZone, componentOf);
        List<OpOutcome> finished = new ArrayList<>(ops.size());
        Map<Long, GasOp> zoneOps = new HashMap<>();
        for (int i = 0; i < ops.size(); i++) {
            OpOutcome o = outcomes[i];
            if (o == null) {
                continue; // waiting for classification
            }
            if (opZone[i] != 0) {
                int idx = undo.indexOf(opZone[i]);
                int component = idx >= 0 ? componentOf[idx] : -1;
                o = new OpOutcome(o.seq(), o.outcome(), o.zoneId(), o.moved(), o.temperature(), component);
                if (component >= 0) {
                    zoneOps.put(o.seq(), ops.get(i));
                }
            }
            finished.add(o);
        }
        if (!undo.sameZones(graph)) {
            membership = Membership.of(graph, space);
        }
        AtmosSnapshot snapshot = snapshot(tick);
        retain();

        undoMembership = undo;
        undoMixtures = undoCopies;
        undoPinned = undoPins;
        lastComponents = components;
        lastZoneOps = zoneOps;
        lastSnapshot = snapshot;

        long[] mirrored = mirror.keys();
        Arrays.sort(mirrored);
        long[] wanted = new long[requested.size()];
        int w = 0;
        for (long k : requested) {
            wanted[w++] = k;
        }
        Result result = new Result(
                tick,
                snapshot,
                components,
                finished,
                events,
                report,
                mirrored,
                wanted,
                buffered.size(),
                heldBlocks(),
                graph.nextZoneId());
        phases.finished();
        return result;
    }

    // ---- 0. Restore ---------------------------------------------------------------

    private List<GasOp> restore(Rejection rejection) {
        if (rejection == null || rejection.isEmpty()) {
            return List.of();
        }
        if (rejection.tick() != lastTick) {
            log.warn("Ignoring a rejection of atmos tick " + rejection.tick() + "; the last tick was " + lastTick);
            return List.of();
        }
        List<GasOp> requeued = new ArrayList<>();
        for (int index : rejection.components()) {
            if (index < 0 || index >= lastComponents.size()) {
                log.warn("Ignoring a rejection of unknown component " + index + " of atmos tick " + lastTick);
                continue;
            }
            Component c = lastComponents.get(index);
            // Recreate destroyed zones first: they take their cells back from the zones that absorbed them.
            for (long id : c.zoneIds()) {
                if (graph.zone(id) == null) {
                    int i = undoMembership.indexOf(id);
                    graph.restoreZone(id, undoMembership.cells(i), undoMixtures[i], undoPinned[i]);
                }
            }
            for (long id : c.zoneIds()) {
                int i = undoMembership.indexOf(id);
                graph.restoreMixture(graph.zone(id), undoMixtures[i]);
                graph.setPinned(graph.zone(id), undoPinned[i]);
            }
            for (long seq : c.opSeqs()) {
                requeued.add(lastZoneOps.get(seq));
            }
        }
        membership = null;
        return requeued;
    }

    // ---- 1. Geometry ----------------------------------------------------------------

    private long[] geometry(Batch batch, CellSet surfaces, List<Long> resuming) {
        for (long chunk : batch.unloadedChunks()) {
            mirror.chunkUnloaded(chunk);
        }
        for (long chunk : batch.loadedChunks()) {
            mirror.chunkLoaded(chunk);
            chunkArrived(chunk);
        }
        for (long section : batch.unavailableSections()) {
            mirror.unavailable(section);
        }
        // The cells of changed positions are refreshed before their neighbours, so a filled cell's
        // gas stays in its zone (CORE-022) before any split the change causes is found (CORE-024).
        CellSet changedCells = new CellSet();
        CellSet toRefresh = new CellSet();
        if (batch.unloadedChunks().length > 0) {
            evictUnloaded();
        }
        dropUnloadedSections(toRefresh);
        Set<Long> zonedSections = zonedSections();
        Set<Long> pausedNow = pausedZones(membership);
        // This batch's captures, and the latest capture of every section with held changes.
        TreeMap<Long, SectionSnapshot> truths = new TreeMap<>(heldTruth);
        heldTruth.clear();
        for (SectionSource source : batch.sections()) {
            SectionSnapshot s = source.decode();
            if (s.key() != source.key()) {
                log.warn("Section capture decoded to the wrong key; skipped");
                continue;
            }
            truths.put(s.key(), s);
        }
        for (SectionSnapshot truth : truths.values()) {
            SectionSnapshot s = truth;
            SectionSnapshot old = mirror.section(s.key());
            if (old == null) {
                mirror.put(s);
                addBorderCells(s.key(), zonedSections, toRefresh);
                continue;
            }
            if (old.isUniform() && s.isUniform() && old.geometryId(0) == s.geometryId(0)) {
                mirror.put(s);
                continue;
            }
            int[] changed = changedIndices(old, s);
            long base = s.key();
            if (changed.length > 0 && !pausedNow.isEmpty()) {
                // Hold the changes that touch a paused zone (GEO-037): the mirror keeps the old
                // geometry there until the zone resumes.
                int[] applied = new int[changed.length];
                int a = 0;
                short[] ids = null;
                for (int index : changed) {
                    if (touchesPaused(blockAt(base, index), pausedNow)) {
                        if (ids == null) {
                            ids = s.geometryIds();
                        }
                        ids[index] = (short) old.geometryId(index);
                    } else {
                        applied[a++] = index;
                    }
                }
                if (ids != null) {
                    heldTruth.put(s.key(), truth);
                    s = SectionSnapshot.of(s.key(), s.lifecycleGeneration(), s.blockGeneration(), ids);
                    changed = Arrays.copyOf(applied, a);
                }
            }
            for (int index : changed) {
                space.forEachCellAt(blockAt(base, index), changedCells::add);
            }
            mirror.put(s);
            for (int index : changed) {
                long block = blockAt(base, index);
                space.forEachCellAt(block, changedCells::add);
                for (Face f : Face.values()) {
                    space.forEachCellAt(offset(block, f), toRefresh::add);
                }
            }
        }
        long[] first = changedCells.sorted();
        for (long cell : first) {
            graph.refresh(cell);
            toRefresh.add(cell);
        }
        long[] cells = toRefresh.sorted();
        for (long cell : cells) {
            if (Arrays.binarySearch(first, cell) < 0) {
                graph.refresh(cell);
            }
        }
        if (cells.length > 0) {
            membership = null;
        }
        ingest(batch);
        resumeRebuilt(surfaces, resuming);
        return cells;
    }

    // ---- 1. Paused zones and storage (architecture §4.5, §4.6) ----------------------------

    /** A chunk the zones rebuilt from saved shares were waiting for has loaded. */
    private void chunkArrived(long chunk) {
        for (Map.Entry<Long, long[]> e : missingChunks.entrySet()) {
            long[] waiting = e.getValue();
            int i = Arrays.binarySearch(waiting, chunk);
            if (i >= 0) {
                long[] rest = new long[waiting.length - 1];
                System.arraycopy(waiting, 0, rest, 0, i);
                System.arraycopy(waiting, i + 1, rest, i, rest.length - i);
                e.setValue(rest);
            }
        }
        missingChunks.values().removeIf(w -> w.length == 0);
    }

    /**
     * Takes out of memory every zone whose chunks have all unloaded and that shares no edge with a
     * zone that has a loaded chunk (architecture §4.5). Its state is in the chunks' saved shares.
     */
    private void evictUnloaded() {
        Membership m = zonedMembership();
        int n = m.size();
        boolean[] evict = new boolean[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            long[] chunks = m.chunks(i);
            boolean all = chunks.length > 0;
            for (long c : chunks) {
                if (!mirror.isChunkUnloaded(c)) {
                    all = false;
                    break;
                }
            }
            evict[i] = all;
            any |= all;
        }
        if (!any) {
            return;
        }
        // Keep zones linked by edges to a zone that stays, so loaded zones keep exchanging with their
        // last-known state (GEO-035).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                if (!evict[i]) {
                    continue;
                }
                for (Edge e : graph.zone(m.zoneIds[i]).edges()) {
                    Zone other = e.other(graph.zone(m.zoneIds[i]));
                    if (other != null && !evict[m.indexOf(other.id())]) {
                        evict[i] = false;
                        changed = true;
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (evict[i]) {
                long id = m.zoneIds[i];
                graph.evict(graph.zone(id));
                missingChunks.remove(id);
                rebuilt.remove(id);
                membership = null;
            }
        }
    }

    /**
     * Drops mirrored sections of unloaded chunks that hold no zoned cells, refreshing the zoned
     * cells across their faces: faces into unloaded chunks lead nowhere until they load, and fills
     * that reach them are unresolved (GEO-024, GEO-026).
     */
    private void dropUnloadedSections(CellSet toRefresh) {
        if (!mirror.hasUnloadedChunks()) {
            return;
        }
        Set<Long> zoned = zonedSections();
        for (long key : mirror.keys()) {
            if (mirror.isChunkUnloaded(SectionKeys.chunkOf(key)) && !zoned.contains(key)) {
                mirror.remove(key);
                heldTruth.remove(key);
                addBorderCells(key, zoned, toRefresh);
            }
        }
    }

    /** Whether a position or one of its neighbours holds a cell of a paused zone. */
    private boolean touchesPaused(long block, Set<Long> paused) {
        if (holdsPaused(block, paused)) {
            return true;
        }
        for (Face f : Face.values()) {
            if (holdsPaused(offset(block, f), paused)) {
                return true;
            }
        }
        return false;
    }

    private boolean holdsPaused(long block, Set<Long> paused) {
        boolean[] found = {false};
        space.forEachCellAt(block, cell -> {
            Zone z = graph.zoneOf(cell);
            if (z != null && paused.contains(z.id())) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /** The zones paused by an unloaded chunk, a missing share or a held breach, as of {@code m}. */
    private Set<Long> pausedZones(Membership m) {
        Set<Long> out = new HashSet<>();
        if (mirror.hasUnloadedChunks()) {
            for (int i = 0; i < m.size(); i++) {
                for (long c : m.chunks(i)) {
                    if (mirror.isChunkUnloaded(c)) {
                        out.add(m.zoneIds[i]);
                        break;
                    }
                }
            }
        }
        out.addAll(missingChunks.keySet());
        for (long z : heldZoneIds) {
            out.add(z);
        }
        return out;
    }

    /** Sets every zone's paused flag for this tick (GEO-030); {@code extra} are paused too. */
    private void updatePaused(Set<Long> extra) {
        missingChunks.keySet().removeIf(id -> graph.zone(id) == null);
        rebuilt.keySet().removeIf(id -> graph.zone(id) == null);
        Membership m = zonedMembership();
        Set<Long> paused = pausedZones(m);
        paused.addAll(extra);
        for (Zone z : graph.zones()) {
            graph.setPaused(z, paused.contains(z.id()));
        }
    }

    /** The membership as of now, rebuilt if the zones changed since it was taken. */
    private Membership zonedMembership() {
        if (membership == null || !membership.sameZones(graph)) {
            membership = Membership.of(graph, space);
        }
        return membership;
    }

    /**
     * Builds or extends zones from the saved shares of chunks that loaded (DATA-002). A share of a
     * zone already in memory with cells in that chunk is ignored: the zone was paused there, so it
     * hasn't changed (architecture §4.5). A zone stays paused while chunks its shares list are
     * unloaded, and its surface is classified once it resumes.
     */
    private void ingest(Batch batch) {
        if (batch.records().isEmpty()) {
            return;
        }
        Membership m = zonedMembership();
        long[] unloadedListed = batch.unloadedListedChunks();
        for (ChunkRecord r : batch.records()) {
            for (ZoneShare share : r.zones()) {
                Zone z = graph.zone(share.zoneId());
                if (z != null) {
                    int idx = m.indexOf(z.id());
                    if (idx >= 0 && Arrays.binarySearch(m.chunks(idx), r.chunk()) >= 0) {
                        continue;
                    }
                }
                Zone loaded = graph.loadShare(share.zoneId(), share.cells(), shareMixture(share), share.pinned());
                if (loaded == null) {
                    log.warn("The saved share of zone " + share.zoneId() + " in chunk " + SectionKeys.chunkX(r.chunk())
                            + ", " + SectionKeys.chunkZ(r.chunk()) + " has no cells left; its gas is dropped");
                    continue;
                }
                TreeSet<Long> waiting = new TreeSet<>();
                for (long c : missingChunks.getOrDefault(loaded.id(), new long[0])) {
                    waiting.add(c);
                }
                for (long c : share.zoneChunks()) {
                    if (c != r.chunk() && Arrays.binarySearch(unloadedListed, c) >= 0) {
                        waiting.add(c);
                    }
                }
                waiting.remove(r.chunk());
                if (waiting.isEmpty()) {
                    missingChunks.remove(loaded.id());
                } else {
                    missingChunks.put(loaded.id(), toArray(new ArrayList<>(waiting)));
                }
                long[] held = rebuilt.getOrDefault(loaded.id(), new long[0]);
                rebuilt.put(loaded.id(), Membership.sortedDistinct(concat(held, r.heldBlocks())));
            }
        }
        membership = null;
    }

    private GasMixture shareMixture(ZoneShare share) {
        GasMixture m = new GasMixture(registry, config, 1, share.temperature());
        int n = Math.min(share.moles().length, registry.size());
        for (int i = 0; i < n; i++) {
            if (share.moles(i) > 0) {
                m.setMoles(i, share.moles(i));
            }
        }
        return m;
    }

    /**
     * For every zone rebuilt from saved shares that is no longer waiting for a chunk, adds its cells
     * and those of its held blocks to {@code surfaces}, so the boundaries phase classifies whatever
     * they open onto (GEO-023, GEO-025, GEO-026): the blocks may have changed while it was saved
     * (DATA-010). Their ids go to {@code resuming}; they stay paused, and are classified again,
     * while the classification waits for sections.
     */
    private void resumeRebuilt(CellSet surfaces, List<Long> resuming) {
        if (rebuilt.isEmpty()) {
            return;
        }
        Membership m = zonedMembership();
        for (java.util.Iterator<Map.Entry<Long, long[]>> it = rebuilt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, long[]> e = it.next();
            Zone z = graph.zone(e.getKey());
            if (z == null) {
                it.remove();
                continue;
            }
            if (missingChunks.containsKey(z.id())) {
                continue;
            }
            int idx = m.indexOf(z.id());
            boolean unloaded = false;
            for (long c : m.chunks(idx)) {
                unloaded |= mirror.isChunkUnloaded(c);
            }
            if (unloaded) {
                continue;
            }
            z.forEachCell(surfaces::add);
            for (long block : e.getValue()) {
                space.forEachCellAt(block, surfaces::add);
                for (Face f : Face.values()) {
                    space.forEachCellAt(offset(block, f), surfaces::add);
                }
            }
            resuming.add(z.id());
        }
    }

    /**
     * The positions whose block changes are held for paused zones, ascending (GEO-037, DATA-010).
     * Engine thread, between ticks.
     */
    public long[] heldBlocks() {
        if (heldTruth.isEmpty()) {
            return new long[0];
        }
        List<Long> out = new ArrayList<>();
        for (SectionSnapshot truth : heldTruth.values()) {
            SectionSnapshot now = mirror.section(truth.key());
            for (int i = 0; i < SectionSnapshot.SIZE; i++) {
                if (now == null || now.geometryId(i) != truth.geometryId(i)) {
                    out.add(blockAt(truth.key(), i));
                }
            }
        }
        return Membership.sortedDistinct(toArray(out));
    }

    private static long[] concat(long[] a, long[] b) {
        long[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static long[] union(long[] a, long[] b) {
        return Membership.sortedDistinct(concat(a, b));
    }

    private Set<Long> zonedSections() {
        return zonedMembership().sections;
    }

    /** For a newly mirrored section, the cells on each face that borders a zoned section, both sides. */
    private void addBorderCells(long key, Set<Long> zonedSections, CellSet out) {
        int sx = SectionKeys.x(key);
        int sy = SectionKeys.y(key);
        int sz = SectionKeys.z(key);
        for (Face f : Face.values()) {
            long neighbour = SectionKeys.section(sx + f.dx(), sy + f.dy(), sz + f.dz());
            if (!zonedSections.contains(neighbour)) {
                continue;
            }
            for (int a = 0; a < 16; a++) {
                for (int b = 0; b < 16; b++) {
                    int lx = f.dx() != 0 ? (f.dx() > 0 ? 15 : 0) : a;
                    int ly = f.dy() != 0 ? (f.dy() > 0 ? 15 : 0) : (f.dx() != 0 ? a : b);
                    int lz = f.dz() != 0 ? (f.dz() > 0 ? 15 : 0) : b;
                    long block = SectionKeys.block(sx * 16 + lx, sy * 16 + ly, sz * 16 + lz);
                    space.forEachCellAt(block, out::add);
                    space.forEachCellAt(offset(block, f), out::add);
                }
            }
        }
    }

    private static int[] changedIndices(SectionSnapshot old, SectionSnapshot now) {
        int[] out = new int[SectionSnapshot.SIZE];
        int n = 0;
        for (int i = 0; i < SectionSnapshot.SIZE; i++) {
            if (old.geometryId(i) != now.geometryId(i)) {
                out[n++] = i;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static long blockAt(long section, int index) {
        int x = index & 15;
        int z = (index >> 4) & 15;
        int y = index >> 8;
        return SectionKeys.block(
                SectionKeys.x(section) * 16 + x, SectionKeys.y(section) * 16 + y, SectionKeys.z(section) * 16 + z);
    }

    private static long offset(long block, Face f) {
        return SectionKeys.block(
                SectionKeys.blockX(block) + f.dx(),
                SectionKeys.blockY(block) + f.dy(),
                SectionKeys.blockZ(block) + f.dz());
    }

    // ---- 3. Gas operations ------------------------------------------------------------

    private enum Route {
        ZONE,
        AMBIENT,
        UNRESOLVED,
        WAIT
    }

    private record Target(Route route, long cell) {}

    private Target[] classifyAll(List<GasOp> ops, TreeSet<Long> requested) {
        Target[] targets = new Target[ops.size()];
        for (int i = 0; i < ops.size(); i++) {
            GasOp op = ops.get(i);
            long cell = space.cellAt(op.block(), op.sub());
            Route route;
            long section = SectionKeys.sectionOfBlockKey(op.block());
            int sy = SectionKeys.y(section);
            if (sy < mirror.minSectionY() || sy > mirror.maxSectionY()) {
                route = Route.AMBIENT; // above or below the world: open (GEO-031)
            } else if (cell == CellSpace.NO_CELL && mirror.section(section) == null) {
                // The target's section isn't mirrored: wait for it, or treat it as unresolved if
                // its chunk isn't loaded (GEO-024).
                if (mirror.isUnloaded(section)) {
                    route = Route.UNRESOLVED;
                } else if (op.triggersZone()) {
                    requested.add(section);
                    route = Route.WAIT;
                } else {
                    route = Route.AMBIENT;
                }
            } else if (cell == CellSpace.NO_CELL) {
                // A position with no cells: the platform resolves a point inside a full block to a
                // neighbour's cell (API-015), so this is a block with no cell next to it, or one
                // that filled in since the call. It holds no gas of its own.
                route = Route.AMBIENT;
            } else if (graph.zoneOf(cell) == null && mirror.isChunkUnloaded(SectionKeys.chunkOf(section))) {
                route = Route.UNRESOLVED; // an unzoned cell in an unloaded chunk (GEO-024)
            } else if (graph.zoneOf(cell) != null) {
                route = Route.ZONE;
            } else if (!op.triggersZone()) {
                route = Route.AMBIENT;
            } else {
                route = classify(op, cell, requested);
            }
            if (route == Route.WAIT) {
                buffered.add(op);
            }
            targets[i] = new Target(route, cell);
        }
        return targets;
    }

    private Route classify(GasOp op, long cell, TreeSet<Long> requested) {
        Classification c = classifier.classify(graph, cell);
        if (c instanceof Classification.Enclosed e) {
            try {
                classifier.zone(graph, e);
                return graph.zoneOf(cell) != null ? Route.ZONE : Route.AMBIENT;
            } catch (IllegalArgumentException ex) {
                log.warn("Classifier returned an enclosure that can't be zoned (" + ex.getMessage() + "); " + op
                        + " goes to the ambient");
                return Route.AMBIENT;
            }
        } else if (c instanceof Classification.Unresolved) {
            return Route.UNRESOLVED;
        } else if (c instanceof Classification.NeedsSections n) {
            for (long k : n.sections()) {
                requested.add(k);
            }
            return Route.WAIT;
        }
        return Route.AMBIENT;
    }

    private OpOutcome apply(GasOp op, Target target, List<OpenAirEvent> events, long[] opZone, int index) {
        double[] moved = new double[registry.size()];
        int n = Math.min(op.gasCount(), moved.length);
        switch (target.route()) {
            case WAIT:
                return null;
            case ZONE: {
                Zone zone = graph.zoneOf(target.cell());
                if (zone.isPaused()) {
                    return atPausedZone(op, zone, moved, n);
                }
                opZone[index] = zone.id();
                if (op.kind() == GasOp.Kind.ADD) {
                    GasMixture parcel = new GasMixture(registry, config, 1, op.temperature());
                    for (int i = 0; i < n; i++) {
                        if (op.moles(i) > 0) {
                            parcel.setMoles(i, op.moles(i));
                        }
                    }
                    if (graph.add(zone, parcel, op.category())) {
                        for (int i = 0; i < n; i++) {
                            moved[i] = parcel.moles(i);
                        }
                    }
                    return new OpOutcome(op.seq(), OpOutcome.Outcome.ZONE, zone.id(), moved, op.temperature(), -1);
                }
                double temperature = zone.mixture().temperature();
                for (int i = 0; i < n; i++) {
                    if (op.moles(i) > 0) {
                        moved[i] = zone.mixture().removeGas(i, op.moles(i));
                        graph.ledger().record(op.category(), i, -moved[i]);
                    }
                }
                graph.markChanged(zone);
                return new OpOutcome(op.seq(), OpOutcome.Outcome.ZONE, zone.id(), moved, temperature, -1);
            }
            case UNRESOLVED:
            case AMBIENT: {
                boolean open = target.route() == Route.AMBIENT;
                OpOutcome.Outcome outcome = open ? OpOutcome.Outcome.AMBIENT : OpOutcome.Outcome.UNRESOLVED;
                double temperature;
                if (op.kind() == GasOp.Kind.ADD) {
                    // Released to the open air, or voided at an unresolved region (GEO-036).
                    for (int i = 0; i < n; i++) {
                        moved[i] = op.moles(i);
                        if (!open && moved[i] > 0) {
                            graph.ledger().recordVoided(i, moved[i]);
                        }
                    }
                    temperature = op.temperature();
                } else {
                    // The ambient supplies any gas it holds without depleting (CORE-057, GEO-038).
                    GasMixture air = graph.ambient();
                    for (int i = 0; i < n; i++) {
                        moved[i] = air.moles(i) > 0 ? op.moles(i) : 0;
                        if (!open && moved[i] > 0) {
                            graph.ledger().recordSupplied(i, moved[i]);
                        }
                    }
                    temperature = air.temperature();
                }
                if (open && any(moved)) {
                    events.add(new OpenAirEvent(op.seq(), op.block(), op.kind() == GasOp.Kind.ADD, moved, temperature));
                }
                return new OpOutcome(op.seq(), outcome, 0, moved, temperature, -1);
            }
            default:
                throw new AssertionError(target.route());
        }
    }

    /**
     * An operation on a paused zone (GEO-036, GEO-038): an addition is voided, and a removal is
     * supplied at the zone's last-known state, up to what it holds, without depleting it. Neither
     * changes the zone; both are kept by the ledger.
     */
    private OpOutcome atPausedZone(GasOp op, Zone zone, double[] moved, int n) {
        double temperature;
        if (op.kind() == GasOp.Kind.ADD) {
            for (int i = 0; i < n; i++) {
                moved[i] = op.moles(i);
                if (moved[i] > 0) {
                    graph.ledger().recordVoided(i, moved[i]);
                }
            }
            temperature = op.temperature();
        } else {
            GasMixture m = zone.mixture();
            for (int i = 0; i < n; i++) {
                moved[i] = Math.min(op.moles(i), m.moles(i));
                if (moved[i] > 0) {
                    graph.ledger().recordSupplied(i, moved[i]);
                }
            }
            temperature = m.temperature();
        }
        return new OpOutcome(op.seq(), OpOutcome.Outcome.UNRESOLVED, zone.id(), moved, temperature, -1);
    }

    private static boolean any(double[] values) {
        for (double v : values) {
            if (v > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<GasOp> mergeBySeq(List<GasOp> a, List<GasOp> b, List<GasOp> c) {
        List<GasOp> all = new ArrayList<>(a.size() + b.size() + c.size());
        all.addAll(a);
        all.addAll(b);
        all.addAll(c);
        all.sort((x, y) -> Long.compare(x.seq(), y.seq()));
        for (int i = 1; i < all.size(); i++) {
            if (all.get(i).seq() == all.get(i - 1).seq()) {
                throw new IllegalStateException("operation " + all.get(i).seq() + " is queued twice");
            }
        }
        return all;
    }

    // ---- Components (architecture §3.3) -------------------------------------------------

    /** The ids of the zones a zone shares a zone edge with, ascending. */
    private static long[] partners(Zone z) {
        long[] out = new long[z.edges().size()];
        int n = 0;
        for (Edge e : z.edges()) {
            Zone other = e.other(z);
            if (other != null) {
                out[n++] = other.id();
            }
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * Groups the zones changed since the undo point: zones whose mixture differs, or that were
     * destroyed (merged or dissolved), linked when they shared a zone edge at the undo point.
     * Merges need such an edge, and a zone that absorbs another inherits its edges, so every
     * chain of exchange stays inside one component.
     */
    private List<Component> components(
            Membership undo,
            GasMixture[] copies,
            long[][] partners,
            List<GasOp> ops,
            long[] opZone,
            int[] componentOf) {
        int n = undo.zoneIds.length;
        boolean[] changed = new boolean[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            Zone z = graph.zone(undo.zoneIds[i]);
            changed[i] = z == null || !sameState(z.mixture(), copies[i]);
        }
        for (int i = 0; i < n; i++) {
            if (!changed[i]) {
                continue;
            }
            for (long other : partners[i]) {
                int j = undo.indexOf(other);
                if (j >= 0 && changed[j]) {
                    union(parent, i, j);
                }
            }
        }
        Arrays.fill(componentOf, -1);
        List<List<Integer>> groups = new ArrayList<>();
        int[] groupOfRoot = new int[n];
        Arrays.fill(groupOfRoot, -1);
        for (int i = 0; i < n; i++) {
            if (!changed[i]) {
                continue;
            }
            int r = find(parent, i);
            if (groupOfRoot[r] < 0) {
                groupOfRoot[r] = groups.size();
                groups.add(new ArrayList<>());
            }
            groups.get(groupOfRoot[r]).add(i);
            componentOf[i] = groupOfRoot[r];
        }
        List<Component> out = new ArrayList<>(groups.size());
        for (int g = 0; g < groups.size(); g++) {
            List<Integer> members = groups.get(g);
            long[] ids = new long[members.size()];
            Set<Long> chunks = new HashSet<>();
            for (int k = 0; k < members.size(); k++) {
                int i = members.get(k);
                ids[k] = undo.zoneIds[i];
                undo.addChunks(i, chunks);
            }
            List<Long> seqs = new ArrayList<>();
            for (int i = 0; i < ops.size(); i++) {
                if (opZone[i] != 0) {
                    int idx = undo.indexOf(opZone[i]);
                    if (idx >= 0 && componentOf[idx] == g) {
                        seqs.add(ops.get(i).seq());
                    }
                }
            }
            out.add(new Component(g, ids, sortedArray(chunks), toArray(seqs)));
        }
        return out;
    }

    private static boolean sameState(GasMixture a, GasMixture b) {
        if (Double.compare(a.temperature(), b.temperature()) != 0 || Double.compare(a.volume(), b.volume()) != 0) {
            return false;
        }
        for (int i = 0; i < a.registry().size(); i++) {
            if (Double.compare(a.moles(i), b.moles(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    private static long[] sortedArray(Set<Long> set) {
        long[] out = new long[set.size()];
        int i = 0;
        for (long v : set) {
            out[i++] = v;
        }
        Arrays.sort(out);
        return out;
    }

    private static long[] toArray(List<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    // ---- Snapshot and mirror retention ---------------------------------------------------

    private AtmosSnapshot snapshot(long tick) {
        List<ZoneView> views = new ArrayList<>(graph.zoneCount());
        for (Zone z : graph.zones()) {
            GasMixture m = z.mixture();
            double[] moles = new double[registry.size()];
            for (int i = 0; i < moles.length; i++) {
                moles[i] = m.moles(i);
            }
            int zoneEdges = 0;
            int ambientEdges = 0;
            int faces = 0;
            for (Edge e : z.edges()) {
                if (e.isAmbient()) {
                    ambientEdges++;
                } else {
                    zoneEdges++;
                }
                faces += e.connections();
            }
            views.add(new ZoneView(
                    z.id(),
                    z.volume(),
                    z.cellCount(),
                    moles,
                    m.temperature(),
                    zoneEdges,
                    ambientEdges,
                    faces,
                    z.isPaused(),
                    z.isPinned()));
        }
        long[][] chunks = new long[membership.size()][];
        for (int i = 0; i < chunks.length; i++) {
            long[] waiting = missingChunks.get(membership.zoneIds[i]);
            chunks[i] = waiting == null ? membership.chunks(i) : union(membership.chunks(i), waiting);
        }
        return AtmosSnapshot.of(tick, registry, views, membership, chunks, partialCells(), heldCells());
    }

    /**
     * The zoned cells holding less than a full block of gas, by block and sub-cell, valued by their
     * zone's index in the membership (API-015). Only such positions can hold several cells, so the
     * table answers for every sub-cell of them. Rebuilt when the membership is.
     */
    private CellTable partialCells() {
        if (partialFor == membership) {
            return partialCells;
        }
        CellTable.Builder b = new CellTable.Builder();
        for (int z = 0; z < membership.size(); z++) {
            for (int i = membership.from(z); i < membership.to(z); i++) {
                if (membership.volume(i) < io.github.west3436.zas.engine.Geometry.FULL_VOLUME) {
                    int sub = space.subIndex(membership.cell(i));
                    if (sub >= 0) {
                        b.add(membership.position(i), sub, z);
                    }
                }
            }
        }
        partialCells = b.build();
        partialFor = membership;
        return partialCells;
    }

    /** The most cells {@link #heldCells} lists. */
    static final int HELD_MAX_CELLS = 65_536;

    /**
     * The unzoned cells of the regions whose breaches pause zones because they reach unloaded
     * chunks (GEO-026): those reachable from the classifier's {@linkplain Classifier#heldSeeds held
     * seeds} through open faces without crossing a zoned cell, as far as the mirror holds them, up
     * to {@link #HELD_MAX_CELLS}. Rebuilt when the seeds, the zones or the geometry change.
     */
    private CellTable heldCells() {
        long[] seeds = classifier.heldSeeds();
        if (seeds.length == 0) {
            heldFor = seeds;
            heldCells = CellTable.EMPTY;
            return heldCells;
        }
        if (!geometryChanged && heldMembership == membership && Arrays.equals(seeds, heldFor)) {
            return heldCells;
        }
        CellTable.Builder b = new CellTable.Builder();
        Set<Long> seen = new HashSet<>();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        for (long s : seeds) {
            if (seen.add(s)) {
                queue.add(s);
            }
        }
        int listed = 0;
        while (!queue.isEmpty() && listed < HELD_MAX_CELLS) {
            long cell = queue.poll();
            if (graph.zoneOf(cell) != null || space.volume(cell) <= 0) {
                continue;
            }
            int sub = space.subIndex(cell);
            if (sub < 0) {
                continue;
            }
            b.add(space.position(cell), sub, 0);
            listed++;
            space.forEachOpenNeighbour(cell, (neighbour, direct) -> {
                if (seen.add(neighbour)) {
                    queue.add(neighbour);
                }
            });
        }
        heldCells = b.build();
        heldFor = seeds;
        heldMembership = membership;
        return heldCells;
    }

    /**
     * Keeps the sections holding zoned cells and their six neighbours, and drops the rest
     * (architecture §4.3). While a classification waits for sections nothing is dropped, so each
     * round of fetching adds to what the next fill sees and the classification always finishes.
     */
    private void retain() {
        if (!buffered.isEmpty() || classifier.hasPending()) {
            return;
        }
        Set<Long> zoned = membership.sections;
        for (long key : mirror.keys()) {
            if (!zoned.contains(key) && !bordersZoned(key, zoned) && !heldTruth.containsKey(key)) {
                mirror.remove(key);
            }
        }
    }

    private static boolean bordersZoned(long key, Set<Long> zoned) {
        int x = SectionKeys.x(key);
        int y = SectionKeys.y(key);
        int z = SectionKeys.z(key);
        for (Face f : Face.values()) {
            if (zoned.contains(SectionKeys.section(x + f.dx(), y + f.dy(), z + f.dz()))) {
                return true;
            }
        }
        return false;
    }

    // ---- Debug export (CLIENT-005, CLIENT-011) ------------------------------------------

    /** The most cells a {@link #debugView} lists. */
    public static final int DEBUG_MAX_CELLS = 65_536;

    /** The most faces a {@link #debugView} lists. */
    public static final int DEBUG_MAX_FACES = 32_768;

    /** The most unresolved cells a {@link #debugView} lists. */
    public static final int DEBUG_MAX_UNRESOLVED = 16_384;

    /**
     * Exports the zoned cells, the faces leading out of zones and the cells of unresolved regions
     * inside the given regions, for the operator debug overlay (CLIENT-005, CLIENT-011). Read
     * only: it changes nothing, so it can't affect the simulation (CORE-044). Call it on the thread
     * that ticks this engine, between ticks; {@link DimensionScheduler} does so right after a tick
     * when the server asked for regions.
     *
     * <p>The cost is one pass over the zones' cells plus the neighbours of those inside a region.
     * Each list stops at its cap ({@link #DEBUG_MAX_CELLS}, {@link #DEBUG_MAX_FACES}, {@link
     * #DEBUG_MAX_UNRESOLVED}) and the view is then marked truncated. A failure is logged and
     * gives an empty view: the overlay never faults the dimension.
     */
    public DebugView debugView(List<DebugRegion> regions) {
        if (regions.isEmpty()) {
            return DebugView.EMPTY;
        }
        try {
            return collectDebugView(regions);
        } catch (RuntimeException e) {
            log.warn("The debug overlay export failed: " + e);
            return DebugView.EMPTY;
        }
    }

    private DebugView collectDebugView(List<DebugRegion> regions) {
        DebugCollector out = new DebugCollector(regions);
        for (Zone z : graph.zones()) {
            z.forEachCell(cell -> {
                long block = space.position(cell);
                if (!out.inside(block)) {
                    return;
                }
                int sub = subIndex(cell, block);
                out.cell(new DebugView.CellEntry(block, sub, z.id()));
                space.forEachOpenNeighbour(cell, (neighbour, direct) -> {
                    Zone other = graph.zoneOf(neighbour);
                    if (other == z) {
                        return;
                    }
                    long there = space.position(neighbour);
                    if (other != null && other.id() < z.id() && out.inside(there)) {
                        return; // listed from the other zone's side
                    }
                    Face face = faceBetween(block, there);
                    if (face != null) {
                        out.face(new DebugView.FaceEntry(
                                block, sub, face, z.id(), other == null ? DebugView.AMBIENT : other.id(), direct));
                    }
                });
            });
        }

        // Unresolved regions: flood the unzoned cells reachable from known seeds, inside the regions.
        TreeSet<Long> seeds = new TreeSet<>(lastUnresolved);
        for (long s : classifier.unresolvedSeeds()) {
            seeds.add(s);
        }
        Set<Long> seen = new HashSet<>(seeds);
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>(seeds);
        while (!queue.isEmpty() && !out.unresolvedFull()) {
            long cell = queue.poll();
            long block = space.position(cell);
            if (graph.zoneOf(cell) != null || !out.inside(block) || space.volume(cell) <= 0) {
                continue;
            }
            out.unresolved(new DebugView.UnresolvedEntry(block, subIndex(cell, block)));
            space.forEachOpenNeighbour(cell, (neighbour, direct) -> {
                if (seen.add(neighbour)) {
                    queue.add(neighbour);
                }
            });
        }
        return new DebugView(
                lastTick,
                out.cells,
                out.faces,
                out.unresolved,
                out.truncated || (out.unresolvedFull() && !queue.isEmpty()));
    }

    /** A cell's index among the cells of its block, in {@link CellSpace#forEachCellAt} order. */
    private int subIndex(long cell, long block) {
        int[] index = {-1};
        int[] n = {0};
        space.forEachCellAt(block, c -> {
            if (c == cell) {
                index[0] = n[0];
            }
            n[0]++;
        });
        return Math.max(index[0], 0);
    }

    /** The face of block {@code from} that touches the adjacent block {@code to}, or null. */
    private static Face faceBetween(long from, long to) {
        int dx = SectionKeys.blockX(to) - SectionKeys.blockX(from);
        int dy = SectionKeys.blockY(to) - SectionKeys.blockY(from);
        int dz = SectionKeys.blockZ(to) - SectionKeys.blockZ(from);
        for (Face f : Face.values()) {
            if (f.dx() == dx && f.dy() == dy && f.dz() == dz) {
                return f;
            }
        }
        return null;
    }

    /** Collects a {@link DebugView}'s entries up to their caps. */
    private static final class DebugCollector {
        final List<DebugRegion> regions;
        final List<DebugView.CellEntry> cells = new ArrayList<>();
        final List<DebugView.FaceEntry> faces = new ArrayList<>();
        final List<DebugView.UnresolvedEntry> unresolved = new ArrayList<>();
        boolean truncated;

        DebugCollector(List<DebugRegion> regions) {
            this.regions = regions;
        }

        boolean inside(long block) {
            for (DebugRegion r : regions) {
                if (r.contains(block)) {
                    return true;
                }
            }
            return false;
        }

        void cell(DebugView.CellEntry e) {
            if (cells.size() < DEBUG_MAX_CELLS) {
                cells.add(e);
            } else {
                truncated = true;
            }
        }

        void face(DebugView.FaceEntry e) {
            if (faces.size() < DEBUG_MAX_FACES) {
                faces.add(e);
            } else {
                truncated = true;
            }
        }

        void unresolved(DebugView.UnresolvedEntry e) {
            unresolved.add(e);
        }

        boolean unresolvedFull() {
            return unresolved.size() >= DEBUG_MAX_UNRESOLVED;
        }
    }

    // ---- Helpers -----------------------------------------------------------------------

    private GasMixture ambientMixture(Ambient a) {
        double litres = 1000;
        GasMixture m = new GasMixture(registry, config, litres, a.temperature());
        for (String id : a.partialPressures().keySet()) {
            int index = registry.indexOf(id);
            if (index < 0) {
                log.warn("The ambient holds " + id + ", which the gas registry lacks; it is left out");
                continue;
            }
            m.setMoles(index, a.moles(id, litres));
        }
        return m;
    }

    /** A sorted, de-duplicated set of cells, built by appending. */
    private static final class CellSet {
        private long[] values = new long[64];
        private int size;

        void add(long cell) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = cell;
        }

        long[] sorted() {
            long[] out = Arrays.copyOf(values, size);
            Arrays.sort(out);
            int n = 0;
            for (int i = 0; i < out.length; i++) {
                if (n == 0 || out[i] != out[n - 1]) {
                    out[n++] = out[i];
                }
            }
            return Arrays.copyOf(out, n);
        }
    }

    /** FNV-1a over 64-bit words. */
    private static final class Hasher {
        long value = 0xcbf29ce484222325L;

        void add(long v) {
            for (int i = 0; i < 8; i++) {
                value ^= (v >>> (i * 8)) & 0xFF;
                value *= 0x100000001b3L;
            }
        }

        void add(double v) {
            add(Double.doubleToLongBits(v));
        }
    }
}
