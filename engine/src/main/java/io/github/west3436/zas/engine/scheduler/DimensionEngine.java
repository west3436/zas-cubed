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
 *   <li>Geometry: store the batch's section snapshots in the mirror and refresh the cells of every
 *       changed position and its neighbours (GEO-012).
 *   <li>Boundaries: the classifier resolves breaches ({@link Classifier#afterGeometry}).
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

    /** Membership as of the end of the last tick, for the snapshot; null when it must be rebuilt. */
    private Membership membership = Membership.EMPTY;

    private AtmosSnapshot lastSnapshot;

    // The last tick's undo point, kept until the next tick in case its components are rejected.
    private Membership undoMembership = Membership.EMPTY;
    private GasMixture[] undoMixtures = new GasMixture[0];
    private List<Component> lastComponents = List.of();
    private Map<Long, GasOp> lastZoneOps = Map.of();

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
        long[] refreshed = geometry(batch);

        // 2. Boundaries.
        phases.enter(Phase.BOUNDARIES);
        TreeSet<Long> requested = new TreeSet<>();
        classifier.afterGeometry(graph, refreshed, requested::add);

        // 3. Gas operations.
        phases.enter(Phase.GAS_OPERATIONS);
        List<GasOp> ops = mergeBySeq(requeued, buffered, batch.ops());
        buffered.clear();
        Target[] targets = classifyAll(ops, requested);
        if (membership == null || refreshed.length > 0 || !membership.sameZones(graph)) {
            membership = Membership.of(graph, space);
        }
        Membership undo = membership;
        GasMixture[] undoCopies = new GasMixture[undo.zoneIds.length];
        long[][] undoPartners = new long[undo.zoneIds.length][];
        int zi = 0;
        for (Zone z : graph.zones()) {
            undoCopies[zi] = z.mixture().copy();
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
        Result result =
                new Result(tick, snapshot, components, finished, events, report, mirrored, wanted, buffered.size());
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
                    graph.restoreZone(id, undoMembership.cells(i), undoMixtures[i]);
                }
            }
            for (long id : c.zoneIds()) {
                graph.restoreMixture(graph.zone(id), undoMixtures[undoMembership.indexOf(id)]);
            }
            for (long seq : c.opSeqs()) {
                requeued.add(lastZoneOps.get(seq));
            }
        }
        membership = null;
        return requeued;
    }

    // ---- 1. Geometry ----------------------------------------------------------------

    private long[] geometry(Batch batch) {
        for (long chunk : batch.unloadedChunks()) {
            // TODO(#27): pause the zones of an unloaded chunk; until then their sections stay mirrored.
            mirror.chunkUnloaded(chunk);
        }
        for (long chunk : batch.loadedChunks()) {
            mirror.chunkLoaded(chunk);
        }
        for (long section : batch.unavailableSections()) {
            mirror.unavailable(section);
        }
        Set<Long> zonedSections = zonedSections();
        // The cells of changed positions are refreshed before their neighbours, so a filled cell's
        // gas stays in its zone (CORE-022) before any split the change causes is found (CORE-024).
        CellSet changedCells = new CellSet();
        CellSet toRefresh = new CellSet();
        for (SectionSource source : batch.sections()) {
            SectionSnapshot s = source.decode();
            if (s.key() != source.key()) {
                log.warn("Section capture decoded to the wrong key; skipped");
                continue;
            }
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
        return cells;
    }

    private Set<Long> zonedSections() {
        if (membership == null) {
            membership = Membership.of(graph, space);
        }
        return membership.sections;
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
            long cell = space.cellAt(op.block());
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
                // TODO(API-015): a point inside a block's shape should use the nearest cell.
                route = Route.AMBIENT;
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
                    }
                    temperature = op.temperature();
                } else {
                    // The ambient supplies any gas it holds without depleting (CORE-057, GEO-038).
                    GasMixture air = graph.ambient();
                    for (int i = 0; i < n; i++) {
                        moved[i] = air.moles(i) > 0 ? op.moles(i) : 0;
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
                undo.addChunks(i, space, chunks);
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
                    z.id(), z.volume(), z.cellCount(), moles, m.temperature(), zoneEdges, ambientEdges, faces, false));
        }
        return AtmosSnapshot.of(tick, registry, views, membership.positions, membership.zoneOfPosition);
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
            if (!zoned.contains(key) && !bordersZoned(key, zoned)) {
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
