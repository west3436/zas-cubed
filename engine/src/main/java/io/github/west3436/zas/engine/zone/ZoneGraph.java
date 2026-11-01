package io.github.west3436.zas.engine.zone;

import io.github.west3436.zas.engine.conservation.ConservationLedger;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.EquivalenceTolerances;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The zones of one dimension, the edges between them and to the ambient, and
 * the exchange across those edges (CORE-020 to CORE-039).
 *
 * <p>The graph runs on an abstract {@link CellTopology}. It stores which zone
 * each cell is in and the open pairs of cells around zones, and keeps the
 * edges' connection counts up to date as cells join, leave and change.
 *
 * <p><b>Driving it.</b> The scheduler (architecture §3.2) calls, in each atmos
 * tick:
 *
 * <ol>
 *   <li>{@link #beginConservationPeriod()} when the invariant checker is on;
 *   <li>geometry: {@link #refresh(long)} for every changed cell, then {@link
 *       #createZone}, {@link #join} and {@link #leave} as classification decides;
 *   <li>gas operations: {@link #add}, {@link #remove}, {@link #addThermalEnergy}
 *       and the like, each attributed to a {@link LedgerCategory};
 *   <li>{@link #exchange()}: every awake edge exchanges once, equivalent zones
 *       joined by a direct opening merge, other equivalent edges sleep and
 *       equalize the zones they join through zone boundaries (CORE-067), and
 *       zones that match their ambient dissolve into it unless pinned (D-64);
 *   <li>{@link #checkConservation(long)} when the checker is on.
 * </ol>
 *
 * <p><b>Determinism</b> (architecture §3.5): zones are kept in id order, edges
 * are processed in (zone id, other zone id) order, and nothing iterates a hash
 * structure. New zone ids come from a per-graph counter and are never reused.
 *
 * <p>Single-threaded: the atmos worker owns the graph, its zones and their
 * mixtures.
 */
public final class ZoneGraph {
    private final GasRegistry registry;
    private final GasConfig config;
    private final CellTopology topology;
    private final ConservationLedger ledger;
    private ExchangeSettings settings;
    private GasMixture ambient;

    private final LongObjectMap<Cell> cells = new LongObjectMap<>(64);
    private final TreeMap<Long, Zone> zones = new TreeMap<>();
    private final Collection<Zone> zonesView = Collections.unmodifiableCollection(zones.values());
    private long nextZoneId = 1;
    private MembershipListener membership = MembershipListener.NONE;

    private final Linker linker = new Linker();
    private final double[] pruneScratch;
    private final ArrayList<Edge> edgeBuffer = new ArrayList<>();
    private final ArrayList<Zone> dissolveBuffer = new ArrayList<>();
    /** Zones on edges that fell asleep this exchange, whose groups are equalized after it (CORE-067). */
    private final ArrayList<Zone> sleptBuffer = new ArrayList<>();
    private final ArrayList<Zone> groupBuffer = new ArrayList<>();
    private int groupEpoch;
    /** CORE-006's tolerances without the temperature test, for near-empty zones (CORE-076). */
    private final EquivalenceTolerances ignoringTemperature;

    private long[] seeds = new long[8];
    private int seedCount;
    private int searchEpoch;

    private long exchangeCount;
    private long mergeCount;
    private long splitCount;
    private long dissolveCount;
    private long splitCheckCount;
    private long equalizeCount;
    private int lastSplitVisits;

    /**
     * @param registry the gases
     * @param config mixture settings; its tolerances decide equivalence
     * @param topology the cells and their connections
     * @param ambient the ambient: the gas of every unzoned cell, at any volume; copied
     * @param settings exchange settings
     * @param ledger where every change to zoned gas is recorded
     */
    public ZoneGraph(
            GasRegistry registry,
            GasConfig config,
            CellTopology topology,
            GasMixture ambient,
            ExchangeSettings settings,
            ConservationLedger ledger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.topology = Objects.requireNonNull(topology, "topology");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.registry().equals(registry)) {
            throw new IllegalArgumentException("ledger counts a different gas registry");
        }
        this.ambient = requireAmbient(ambient);
        this.pruneScratch = new double[registry.size()];
        EquivalenceTolerances t = config.equivalence();
        this.ignoringTemperature = new EquivalenceTolerances(
                Double.MAX_VALUE,
                t.temperatureRelative(),
                t.pressureAbsolute(),
                t.pressureRelative(),
                t.densityAbsolute(),
                t.densityRelative());
    }

    // ---- Queries ------------------------------------------------------------

    public GasRegistry registry() {
        return registry;
    }

    public ConservationLedger ledger() {
        return ledger;
    }

    public ExchangeSettings settings() {
        return settings;
    }

    public void setSettings(ExchangeSettings exchangeSettings) {
        settings = Objects.requireNonNull(exchangeSettings, "settings");
    }

    /** The ambient, as a mixture whose volume is arbitrary. Read only. */
    public GasMixture ambient() {
        return ambient;
    }

    /** The id the next new zone gets; ids below it have been issued (CORE-026). */
    public long nextZoneId() {
        return nextZoneId;
    }

    /**
     * Makes sure new zones get ids of at least {@code next}, so ids saved in an earlier session
     * are never issued again (CORE-026). Never lowers the counter.
     */
    public void reserveZoneIds(long next) {
        if (next > nextZoneId) {
            nextZoneId = next;
        }
    }

    /**
     * Sets what hears each change of a cell's zone (architecture §4.4); {@link
     * MembershipListener#NONE} by default. Set it while the graph has no zones, so it hears every
     * cell.
     */
    public void setMembershipListener(MembershipListener listener) {
        membership = Objects.requireNonNull(listener, "listener");
    }

    /** The volume the graph holds for a cell, in L: as of its last join or refresh; 0 if it holds none. */
    public double cellVolume(long cell) {
        Cell c = cells.get(cell);
        return c == null ? 0 : c.volume;
    }

    /**
     * Pauses or resumes a zone (GEO-030). While paused it keeps its mixture: {@link #exchange()}
     * leaves it out. Pausing changes nothing else; the scheduler holds the zone's block changes
     * and gas operations itself.
     */
    public void setPaused(Zone zone, boolean paused) {
        requireLive(zone);
        zone.paused = paused;
    }

    /**
     * Pins or unpins a zone (D-64). A pinned zone never dissolves into the ambient (CORE-076): once
     * nearly equivalent to it, its ambient edge keeps exchanging as usual instead.
     *
     * <p>The pin follows the zone's gas and cells, the way its identity does (CORE-026): when two
     * zones merge, the surviving zone is pinned if either was, since the absorbed zone's cells and
     * gas live on in it; when a zone splits, the part that keeps the id and every remnant split off
     * under a new id are all pinned if it was, so no part of a pinned zone dissolves. Saving and
     * loading keeps the pin (DATA-002), and so does the scheduler's undo of a rejected component.
     *
     * <p>Set by the zone pins of API-017, persisted by API-018, in R1; nothing sets it in R0 except
     * tests.
     */
    public void setPinned(Zone zone, boolean pinned) {
        requireLive(zone);
        zone.pinned = pinned;
    }

    /** The live zones, in id order. */
    public Collection<Zone> zones() {
        return zonesView;
    }

    public int zoneCount() {
        return zones.size();
    }

    /** The live zone with this id, or null. */
    public Zone zone(long id) {
        return zones.get(id);
    }

    /** The zone a cell is in, or null if it is unzoned. */
    public Zone zoneOf(long cell) {
        Cell c = cells.get(cell);
        return c == null ? null : c.zone;
    }

    /** Every edge, in the order {@link #exchange()} processes them. */
    public List<Edge> edges() {
        List<Edge> out = new ArrayList<>();
        for (Zone z : zones.values()) {
            for (Edge e : z.edges) {
                if (e.first() == z) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /** The edge between two zones, or null. */
    public Edge edgeBetween(Zone a, Zone b) {
        return a.edgeTo(b);
    }

    /** How many times an edge has exchanged gas since the graph was created (CORE-033). */
    public long exchangeCount() {
        return exchangeCount;
    }

    /** How many merges have happened since the graph was created. */
    public long mergeCount() {
        return mergeCount;
    }

    /** How many remnants have split off zones since the graph was created. */
    public long splitCount() {
        return splitCount;
    }

    /** How many zones have dissolved into the ambient since the graph was created (CORE-076). */
    public long dissolveCount() {
        return dissolveCount;
    }

    /**
     * How many times a change made the graph check a zone for a split (CORE-024) since it was
     * created, whether or not the zone split. A change checks only when a cell loses a direct
     * opening to its own zone, so opening and closing a zone boundary never does (GEO-044).
     */
    public long splitCheckCount() {
        return splitCheckCount;
    }

    /** How many cells the last split check visited (CORE-025). */
    public int lastSplitVisits() {
        return lastSplitVisits;
    }

    /**
     * How many groups of zones joined through zone boundaries have been equalized since the graph
     * was created (CORE-067).
     */
    public long equalizeCount() {
        return equalizeCount;
    }

    /** The moles of each gas held in all zones, summed in zone id order, into {@code out}. */
    public void totalMoles(double[] out) {
        Arrays.fill(out, 0);
        for (Zone z : zones.values()) {
            GasMixture m = z.mixture();
            for (int i = 0; i < out.length; i++) {
                out[i] += m.moles(i);
            }
        }
    }

    // ---- Ambient ------------------------------------------------------------

    /**
     * Replaces the ambient. Ambient edges never sleep (CORE-076), so the next
     * exchange vents toward the new ambient, and zones that nearly match it
     * dissolve into it.
     */
    public void setAmbient(GasMixture newAmbient) {
        ambient = requireAmbient(newAmbient);
    }

    private GasMixture requireAmbient(GasMixture mixture) {
        Objects.requireNonNull(mixture, "ambient");
        if (!mixture.registry().equals(registry)) {
            throw new IllegalArgumentException("ambient uses a different gas registry");
        }
        return mixture.copy();
    }

    /** A new mixture holding the ambient's gas for a volume. */
    private GasMixture ambientFor(double litres) {
        GasMixture m = new GasMixture(registry, config, litres, ambient.temperature());
        double scale = litres / ambient.volume();
        for (int i = 0; i < registry.size(); i++) {
            double n = ambient.moles(i);
            if (n > 0) {
                m.setMoles(i, n * scale);
            }
        }
        return m;
    }

    // ---- Zones from cells (CORE-020, CORE-021, CORE-027, CORE-028) ----------

    /**
     * Creates a zone from unzoned cells, each bringing the ambient gas for its
     * volume (CORE-028).
     *
     * @param cellIds the cells; they must be connected to each other by direct openings
     * @throws IllegalArgumentException if the list is empty or has duplicates, a
     *     cell is zoned or is not a cell, or the cells aren't connected
     */
    public Zone createZone(long... cellIds) {
        if (cellIds.length == 0) {
            throw new IllegalArgumentException("a zone needs at least one cell");
        }
        long[] sorted = cellIds.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < sorted.length; i++) {
            if (i > 0 && sorted[i] == sorted[i - 1]) {
                throw new IllegalArgumentException("cell " + sorted[i] + " is listed twice");
            }
            if (!(topology.volume(sorted[i]) > 0)) {
                throw new IllegalArgumentException("position " + sorted[i] + " is not a cell");
            }
            if (zoneOf(sorted[i]) != null) {
                throw new IllegalArgumentException("cell " + sorted[i] + " is already zoned");
            }
        }
        requireConnected(sorted, cellIds[0]);
        Zone zone = newZone(new GasMixture(registry, config, 1, ambient.temperature()));
        for (long id : cellIds) {
            joinCell(zone, id, null, LedgerCategory.AMBIENT);
        }
        return zone;
    }

    private void requireConnected(long[] sorted, long start) {
        boolean[] reached = new boolean[sorted.length];
        long[] queue = new long[sorted.length];
        int head = 0;
        int tail = 0;
        reached[Arrays.binarySearch(sorted, start)] = true;
        queue[tail++] = start;
        int[] count = {1};
        while (head < tail) {
            long cell = queue[head++];
            int[] tailRef = {tail};
            topology.forEachOpenNeighbour(cell, (neighbour, direct) -> {
                if (!direct) {
                    return;
                }
                int index = Arrays.binarySearch(sorted, neighbour);
                if (index >= 0 && !reached[index]) {
                    reached[index] = true;
                    queue[tailRef[0]++] = neighbour;
                    count[0]++;
                }
            });
            tail = tailRef[0];
        }
        if (count[0] != sorted.length) {
            throw new IllegalArgumentException("the cells of a zone must be connected by direct openings");
        }
    }

    /**
     * An unzoned cell joins a zone, bringing the ambient gas for its volume
     * (CORE-021, CORE-028).
     *
     * @throws IllegalArgumentException if the cell is zoned or not a cell, or has
     *     no direct opening to the zone
     */
    public void join(long cell, Zone zone) {
        requireJoinable(cell, zone);
        joinCell(zone, cell, null, LedgerCategory.AMBIENT);
    }

    /**
     * An unzoned cell joins a zone, bringing gas of its own, which is merged into
     * the zone's mixture (CORE-021). The gas isn't changed.
     *
     * @param gas the cell's gas
     * @param category where that gas comes from, for the ledger
     */
    public void join(long cell, Zone zone, GasMixture gas, LedgerCategory category) {
        Objects.requireNonNull(gas, "gas");
        Objects.requireNonNull(category, "category");
        requireJoinable(cell, zone);
        joinCell(zone, cell, gas, category);
    }

    private void requireJoinable(long cell, Zone zone) {
        requireLive(zone);
        if (!(topology.volume(cell) > 0)) {
            throw new IllegalArgumentException("position " + cell + " is not a cell");
        }
        if (zoneOf(cell) != null) {
            throw new IllegalArgumentException("cell " + cell + " is already zoned");
        }
        boolean[] touches = {false};
        topology.forEachOpenNeighbour(cell, (neighbour, direct) -> {
            if (direct && zoneOf(neighbour) == zone) {
                touches[0] = true;
            }
        });
        if (!touches[0]) {
            throw new IllegalArgumentException("cell " + cell + " has no direct opening to " + zone);
        }
    }

    private void joinCell(Zone zone, long id, GasMixture gas, LedgerCategory category) {
        Cell c = cells.get(id);
        if (c == null) {
            c = new Cell(id);
            cells.put(id, c);
        }
        unlinkAll(c);
        c.volume = topology.volume(id);
        addCell(zone, c);
        linkFromTopology(c);
        GasMixture added = gas != null ? gas : ambientFor(c.volume);
        if (zone.mixture().merge(added)) {
            ledger.record(category, added, 1);
        }
        zone.changed = true;
    }

    /**
     * A zoned cell leaves its zone but stays a cell, now unzoned, so it holds the
     * ambient again. Its share of the zone's gas, by volume, goes to the ambient.
     * If the zone is left disconnected it splits (CORE-024); if it is left empty
     * it is destroyed (CORE-065).
     *
     * @throws IllegalArgumentException if the cell is not zoned
     */
    public void leave(long cell) {
        Cell c = cells.get(cell);
        if (c == null || c.zone == null) {
            throw new IllegalArgumentException("cell " + cell + " is not zoned");
        }
        Zone zone = c.zone;
        GasMixture share = zone.cellCount() == 1
                ? zone.mixture().removeFraction(1)
                : zone.mixture().removeFraction(c.volume / zone.volume());
        ledger.record(LedgerCategory.AMBIENT, share, -1);
        drainPruned(share);
        collectZoneNeighbours(c);
        unlinkAll(c);
        removeCell(c);
        c.volume = topology.volume(cell);
        if (c.volume > 0) {
            linkFromTopology(c);
        }
        dropIfIdle(c);
        if (zone.cellCount() == 0) {
            destroyZone(zone);
        } else {
            zone.changed = true;
            splitCheck(zone);
        }
    }

    // ---- Geometry changes (CORE-022, CORE-024, CORE-075) --------------------

    /**
     * Brings one cell up to date with the topology: its volume and open faces.
     * See {@link CellTopology} for which cells to refresh.
     *
     * <ul>
     *   <li>A zoned cell that stopped being a cell leaves its zone and its gas
     *       stays in the zone, so the zone's pressure rises (CORE-022). If it was
     *       the zone's last cell, its gas moves to the zones it was connected to,
     *       by volume, or else to the ambient (CORE-075).
     *   <li>A zoned cell whose volume changed changes its zone's volume; the gas
     *       is kept.
     *   <li>A zone left disconnected splits (CORE-024, CORE-064), found by a
     *       search local to the change (CORE-025).
     *   <li>Edge connection counts follow the faces (CORE-030, CORE-037).
     * </ul>
     *
     * <p>Unzoned cells never join a zone here: classification decides that
     * (GEO), through {@link #join} or {@link #createZone}.
     */
    public void refresh(long cell) {
        double volume = topology.volume(cell);
        Cell c = cells.get(cell);
        if (c == null) {
            if (volume > 0) {
                c = new Cell(cell);
                c.volume = volume;
                cells.put(cell, c);
                linkFromTopology(c);
                dropIfIdle(c);
            }
            return;
        }
        Zone zone = c.zone;
        if (zone == null) {
            unlinkAll(c);
            c.volume = volume;
            if (volume > 0) {
                linkFromTopology(c);
            }
            dropIfIdle(c);
            return;
        }
        if (!(volume > 0)) {
            fill(c);
            return;
        }
        collectZoneNeighbours(c);
        unlinkAll(c);
        if (volume != c.volume) {
            double old = c.volume;
            c.volume = volume;
            zone.setVolume(zone.volume() - old + volume);
            zone.changed = true;
            membership.cellResized(c.id, zone, old, volume);
        }
        linkFromTopology(c);
        // Keep only the neighbours this cell lost a direct opening to.
        int kept = 0;
        for (int i = 0; i < seedCount; i++) {
            int index = c.indexOf(seeds[i]);
            if (index < 0 || !c.direct[index]) {
                seeds[kept++] = seeds[i];
            }
        }
        seedCount = kept;
        if (seedCount > 0) {
            addSeed(c.id);
            splitCheck(zone);
        }
    }

    /** Refreshes several cells, in order. */
    public void refreshAll(long... cellIds) {
        for (long id : cellIds) {
            refresh(id);
        }
    }

    private void fill(Cell c) {
        Zone zone = c.zone;
        if (zone.cellCount() == 1) {
            List<Zone> neighbours = neighbourZones(c);
            unlinkAll(c);
            removeCell(c);
            cells.remove(c.id);
            GasMixture gas = zone.mixture();
            if (neighbours.isEmpty()) {
                ledger.record(LedgerCategory.AMBIENT, gas, -1);
                gas.clear();
            } else {
                double remaining = 0;
                for (Zone z : neighbours) {
                    remaining += z.volume();
                }
                for (int i = 0; i < neighbours.size(); i++) {
                    Zone z = neighbours.get(i);
                    double fraction = i == neighbours.size() - 1 ? 1 : z.volume() / remaining;
                    remaining -= z.volume();
                    GasMixture parcel = gas.removeFraction(fraction);
                    z.mixture().merge(parcel);
                    drainPruned(parcel);
                    z.changed = true;
                }
            }
            destroyZone(zone);
            return;
        }
        collectZoneNeighbours(c);
        unlinkAll(c);
        removeCell(c);
        cells.remove(c.id);
        zone.changed = true;
        splitCheck(zone);
    }

    /** The other zones a cell is connected to, in id order. */
    private List<Zone> neighbourZones(Cell c) {
        List<Zone> out = new ArrayList<>(2);
        for (int i = 0; i < c.degree; i++) {
            Zone z = cells.get(c.neighbours[i]).zone;
            if (z != null && z != c.zone && !out.contains(z)) {
                out.add(z);
            }
        }
        out.sort((x, y) -> Long.compare(x.id(), y.id()));
        return out;
    }

    /** Puts the cell's neighbours in its own zone, across direct openings, into the seed buffer. */
    private void collectZoneNeighbours(Cell c) {
        seedCount = 0;
        for (int i = 0; i < c.degree; i++) {
            if (c.direct[i] && cells.get(c.neighbours[i]).zone == c.zone) {
                addSeed(c.neighbours[i]);
            }
        }
    }

    private void addSeed(long id) {
        if (seedCount == seeds.length) {
            seeds = Arrays.copyOf(seeds, seedCount * 2);
        }
        seeds[seedCount++] = id;
    }

    // ---- Split (CORE-024, CORE-025, CORE-064) --------------------------------

    /**
     * Checks whether the zone is still connected, after a change next to the
     * seed cells. Interleaved breadth-first searches run from each seed, one
     * step each in turn, across direct openings inside the zone. Searches that
     * meet are united; once all are united the zone is still connected. A group
     * of searches that runs out of cells first has found a complete remnant,
     * which becomes a new zone. The work depends on how far apart the seeds are
     * inside the zone, not on its size (CORE-025). The remnant found last keeps
     * the zone and its id. Every remnant inherits the zone's pin (D-64).
     */
    private void splitCheck(Zone zone) {
        splitCheckCount++;
        int epoch = ++searchEpoch;
        int k = 0;
        long[] starts = new long[seedCount];
        for (int i = 0; i < seedCount; i++) {
            Cell s = cells.get(seeds[i]);
            if (s != null && s.zone == zone && s.visitEpoch != epoch) {
                s.visitEpoch = epoch;
                s.visitSearch = k;
                starts[k++] = s.id;
            }
        }
        seedCount = 0;
        lastSplitVisits = k;
        if (k <= 1) {
            return;
        }
        @SuppressWarnings("unchecked")
        ArrayDeque<Cell>[] queues = new ArrayDeque[k];
        @SuppressWarnings("unchecked")
        ArrayList<Cell>[] found = new ArrayList[k];
        int[] parent = new int[k];
        boolean[] done = new boolean[k];
        for (int i = 0; i < k; i++) {
            Cell s = cells.get(starts[i]);
            queues[i] = new ArrayDeque<>();
            queues[i].add(s);
            found[i] = new ArrayList<>();
            found[i].add(s);
            parent[i] = i;
        }
        int groups = k;
        int visits = k;
        while (groups > 1) {
            for (int i = 0; i < k && groups > 1; i++) {
                Cell cell = queues[i].poll();
                if (cell == null) {
                    continue;
                }
                for (int l = 0; l < cell.degree; l++) {
                    if (!cell.direct[l]) {
                        continue;
                    }
                    Cell n = cells.get(cell.neighbours[l]);
                    if (n.zone != zone) {
                        continue;
                    }
                    if (n.visitEpoch != epoch) {
                        n.visitEpoch = epoch;
                        n.visitSearch = i;
                        queues[i].add(n);
                        found[i].add(n);
                        visits++;
                    } else {
                        int a = root(parent, i);
                        int b = root(parent, n.visitSearch);
                        if (a != b) {
                            parent[Math.max(a, b)] = Math.min(a, b);
                            groups--;
                        }
                    }
                }
            }
            if (groups <= 1) {
                break;
            }
            // A group whose searches have all run out is a complete remnant.
            for (int g = 0; g < k && groups > 1; g++) {
                if (done[g] || root(parent, g) != g || !groupExhausted(parent, queues, g)) {
                    continue;
                }
                List<Cell> remnant = new ArrayList<>();
                for (int i = 0; i < k; i++) {
                    if (root(parent, i) == g) {
                        remnant.addAll(found[i]);
                        done[i] = true;
                    }
                }
                splitOff(zone, remnant);
                groups--;
            }
        }
        lastSplitVisits = visits;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static boolean groupExhausted(int[] parent, ArrayDeque<Cell>[] queues, int group) {
        for (int i = 0; i < queues.length; i++) {
            if (root(parent, i) == group && !queues[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves a remnant into a new zone. It takes the zone's composition and
     * temperature, with moles in proportion to its volume (CORE-064), and the
     * zone's pin (D-64).
     */
    private void splitOff(Zone zone, List<Cell> remnant) {
        double volume = 0;
        for (Cell c : remnant) {
            volume += c.volume;
        }
        GasMixture parcel = zone.mixture().removeFraction(volume / zone.volume());
        Zone created = newZone(parcel);
        created.pinned = zone.pinned;
        for (Cell c : remnant) {
            relabel(c, created);
        }
        splitCount++;
        created.changed = true;
        zone.changed = true;
    }

    // ---- Merge (CORE-023) ---------------------------------------------------

    /**
     * Merges two zones into one: the one with more cells (on a tie, the lower id)
     * keeps its id and takes the other's cells and gas, which is conserved. The
     * other is destroyed. The surviving zone is pinned if either was (D-64).
     * {@link #exchange()} calls this for equivalent zones joined by a direct
     * opening (CORE-023).
     *
     * @return the surviving zone
     * @throws IllegalArgumentException if the zones are the same, dead or not
     *     joined by a direct opening
     */
    public Zone merge(Zone a, Zone b) {
        requireLive(a);
        requireLive(b);
        Edge e = a.edgeTo(b);
        if (a == b || e == null || e.direct == 0) {
            throw new IllegalArgumentException(a + " and " + b + " are not joined by a direct opening");
        }
        return mergeZones(a, b);
    }

    private Zone mergeZones(Zone a, Zone b) {
        boolean aKeeps = a.cellCount() > b.cellCount() || (a.cellCount() == b.cellCount() && a.id() < b.id());
        Zone big = aKeeps ? a : b;
        Zone small = aKeeps ? b : a;
        membership.zoneMerged(small, big);
        // Edges move as wholes rather than pair by pair: the pairs between the two zones become
        // internal, and every other connection of the small zone becomes the big zone's, on the edge
        // to the same zone or the ambient, which wakes as counting its pairs one by one would wake it.
        for (Edge e : new ArrayList<>(small.edges)) {
            removeEdge(e);
            Zone other = e.other(small);
            if (other == big) {
                continue;
            }
            Edge to = edgeFor(big, other);
            to.connections += e.connections;
            to.direct += e.direct;
            to.sleeping = false;
        }
        while (small.cellCount() > 0) {
            Cell c = small.cellAt(small.cellCount() - 1);
            small.removeCell(c);
            big.addCell(c);
        }
        big.mixture().merge(small.mixture());
        big.pinned |= small.pinned;
        drainPruned(small.mixture());
        destroyZone(small);
        big.changed = true;
        mergeCount++;
        return big;
    }

    /** Moves a cell to another zone, keeping the edge counts right. */
    private void relabel(Cell c, Zone to) {
        for (int i = 0; i < c.degree; i++) {
            countPair(c, cells.get(c.neighbours[i]), c.direct[i], -1);
        }
        removeCell(c);
        addCell(to, c);
        for (int i = 0; i < c.degree; i++) {
            countPair(c, cells.get(c.neighbours[i]), c.direct[i], 1);
        }
    }

    // ---- Gas operations -----------------------------------------------------

    /**
     * Adds gas to a zone; the gas isn't changed.
     *
     * @param category where the gas comes from, for the ledger
     * @return false, changing nothing, if the mixture rejected it
     */
    public boolean add(Zone zone, GasMixture gas, LedgerCategory category) {
        requireLive(zone);
        if (!zone.mixture().merge(gas)) {
            return false;
        }
        ledger.record(category, gas, 1);
        zone.changed = true;
        return true;
    }

    /**
     * Removes a fraction of a zone's gas.
     *
     * @param category where the gas goes, for the ledger
     * @return the removed gas
     */
    public GasMixture remove(Zone zone, double fraction, LedgerCategory category) {
        requireLive(zone);
        GasMixture parcel = zone.mixture().removeFraction(fraction);
        ledger.record(category, parcel, -1);
        drainPruned(parcel);
        zone.changed = true;
        return parcel;
    }

    /**
     * Removes moles of a zone's gas, in proportion to its composition.
     *
     * @param category where the gas goes, for the ledger
     * @return the removed gas
     */
    public GasMixture removeMoles(Zone zone, double moles, LedgerCategory category) {
        requireLive(zone);
        GasMixture parcel = zone.mixture().removeMoles(moles);
        ledger.record(category, parcel, -1);
        drainPruned(parcel);
        zone.changed = true;
        return parcel;
    }

    /** Adds thermal energy to a zone's gas; returns the energy added. */
    public double addThermalEnergy(Zone zone, double joules) {
        requireLive(zone);
        zone.changed = true;
        return zone.mixture().addThermalEnergy(joules);
    }

    /** Removes thermal energy from a zone's gas; returns the energy removed. */
    public double removeThermalEnergy(Zone zone, double joules) {
        requireLive(zone);
        zone.changed = true;
        return zone.mixture().removeThermalEnergy(joules);
    }

    /**
     * Notes that a zone's mixture was changed directly, so its sleeping edges
     * are re-checked at the next exchange (CORE-066). Changes made through the
     * graph do this already.
     */
    public void markChanged(Zone zone) {
        requireLive(zone);
        zone.changed = true;
    }

    // ---- Exchange (CORE-031 to CORE-039, CORE-066) --------------------------

    /**
     * One atmos tick's exchange.
     *
     * <ol>
     *   <li>Sleeping edges of zones that changed since the last exchange wake if
     *       their sides are no longer equivalent (CORE-066).
     *   <li>Every awake edge, in order, exchanges once: zone to zone by the
     *       sharing ratio for its connection count, weighted by the two volumes
     *       (CORE-031); zone to ambient the same way against ambient gas that is
     *       refilled each time, so the ambient never changes (CORE-032,
     *       CORE-038).
     *   <li>Then, if its sides are equivalent: zones joined by a direct opening
     *       merge (CORE-023), and other zone-to-zone edges, which join their zones
     *       only through zone boundaries, sleep (CORE-033) and never merge
     *       (CORE-034). Sleeping edges cost no exchange work.
     *   <li>Then every zone reachable through sleeping edges from an edge that fell
     *       asleep takes one combined mixture, shared by volume (CORE-067): once per
     *       group, however many of its edges fell asleep.
     *   <li>Last, in zone id order, every zone with an ambient edge that is
     *       nearly equivalent to the ambient dissolves into it (CORE-076, D-63):
     *       its gas goes to the ambient, recorded as {@link
     *       LedgerCategory#AMBIENT}, and its cells become unzoned, so they hold
     *       the ambient again (CORE-050) and a later trigger can zone them anew.
     *       Its other edges become its neighbours' ambient connections. This
     *       takes the place of snapping to the ambient (CORE-039). Pinned zones
     *       never dissolve (D-64): their ambient edges keep exchanging.
     * </ol>
     *
     * <p>Paused zones (GEO-030) take no part: they don't exchange with the ambient, merge or
     * dissolve, and their mixtures don't change. An active zone across an edge from a paused one
     * exchanges with a copy of the paused zone's mixture, its last-known state (GEO-035): gas it
     * sends is voided (GEO-036) and gas it draws is supplied without depleting the paused zone
     * (GEO-038), both recorded as {@link LedgerCategory#PAUSED}.
     */
    public void exchange() {
        for (Zone z : zones.values()) {
            if (!z.changed) {
                continue;
            }
            z.changed = false;
            for (Edge e : z.edges) {
                if (e.sleeping && !sidesEquivalent(e)) {
                    e.sleeping = false;
                }
            }
        }
        edgeBuffer.clear();
        for (Zone z : zones.values()) {
            for (Edge e : z.edges) {
                if (e.first() == z && !e.sleeping) {
                    edgeBuffer.add(e);
                }
            }
        }
        for (int i = 0; i < edgeBuffer.size(); i++) {
            Edge e = edgeBuffer.get(i);
            if (e.removed || e.sleeping) {
                continue;
            }
            if (e.isAmbient()) {
                if (!e.first().paused) {
                    exchangeWithAmbient(e);
                }
            } else if (e.first().paused || e.second().paused) {
                if (!e.first().paused) {
                    exchangeWithPaused(e, e.first(), e.second());
                } else if (!e.second().paused) {
                    exchangeWithPaused(e, e.second(), e.first());
                }
            } else {
                exchangeZones(e);
            }
        }
        edgeBuffer.clear();
        if (!sleptBuffer.isEmpty()) {
            int epoch = ++groupEpoch;
            for (int i = 0; i < sleptBuffer.size(); i++) {
                Zone z = sleptBuffer.get(i);
                if (z.isAlive() && z.groupEpoch != epoch) {
                    equalizeGroup(z, epoch);
                }
            }
            sleptBuffer.clear();
        }
        for (Zone z : zones.values()) {
            if (!z.paused && !z.pinned && z.ambientEdge() != null && nearlyAmbient(z.mixture())) {
                dissolveBuffer.add(z);
            }
        }
        for (int i = 0; i < dissolveBuffer.size(); i++) {
            dissolve(dissolveBuffer.get(i));
        }
        dissolveBuffer.clear();
    }

    /**
     * Whether a mixture is nearly equivalent to the ambient (CORE-076, D-63): the
     * full CORE-006 test, except that temperature isn't compared once the
     * mixture is near-empty, its pressure within the absolute pressure tolerance
     * both of the ambient's and of vacuum, since near-empty gas carries
     * negligible heat. At ordinary pressures temperature is always compared.
     */
    private boolean nearlyAmbient(GasMixture m) {
        double p = m.pressure();
        double tolerance = ignoringTemperature.pressureAbsolute();
        if (p <= tolerance && Math.abs(p - ambient.pressure()) <= tolerance) {
            return m.isEquivalent(ambient, ignoringTemperature);
        }
        return m.isEquivalent(ambient);
    }

    private boolean sidesEquivalent(Edge e) {
        GasMixture other = e.isAmbient() ? ambient : e.second().mixture();
        return e.first().mixture().isEquivalent(other);
    }

    private void exchangeZones(Edge e) {
        Zone a = e.first();
        Zone b = e.second();
        if (!a.mixture().isEquivalent(b.mixture())) {
            share(a, b, settings.ratio(e.connections));
            if (!a.mixture().isEquivalent(b.mixture())) {
                return;
            }
        }
        if (e.direct > 0) {
            mergeZones(a, b);
        } else {
            e.sleeping = true;
            // Both sides: either may be merged away later in this exchange.
            sleptBuffer.add(a);
            sleptBuffer.add(b);
        }
    }

    /**
     * Gives every zone joined to {@code start} through sleeping zone edges one combined mixture,
     * shared by volume (CORE-067). Only zones joined through zone boundaries alone have sleeping
     * edges between them, since equivalent zones with a direct opening merge instead. The gas and
     * its thermal energy are conserved; nothing is recorded in the ledger but pruning. Paused zones
     * keep their mixture (GEO-035) and aren't crossed. Zones are combined and shared out in id
     * order, so the result doesn't depend on how the group was reached. Marks every zone it
     * reaches with {@code epoch}; groups are disjoint, so one epoch serves a whole exchange.
     */
    private void equalizeGroup(Zone start, int epoch) {
        groupBuffer.clear();
        start.groupEpoch = epoch;
        groupBuffer.add(start);
        for (int i = 0; i < groupBuffer.size(); i++) {
            for (Edge e : groupBuffer.get(i).edges) {
                Zone other = e.other(groupBuffer.get(i));
                if (other != null && e.sleeping && !other.paused && other.groupEpoch != epoch) {
                    other.groupEpoch = epoch;
                    groupBuffer.add(other);
                }
            }
        }
        if (groupBuffer.size() > 1) {
            groupBuffer.sort((x, y) -> Long.compare(x.id(), y.id()));
            double volume = 0;
            for (Zone z : groupBuffer) {
                volume += z.volume();
            }
            GasMixture combined =
                    new GasMixture(registry, config, volume, start.mixture().temperature());
            for (Zone z : groupBuffer) {
                GasMixture all = z.mixture().removeFraction(1);
                combined.merge(all);
                drainPruned(all);
            }
            double remaining = volume;
            for (int i = 0; i < groupBuffer.size(); i++) {
                Zone z = groupBuffer.get(i);
                double fraction = i == groupBuffer.size() - 1 ? 1 : z.volume() / remaining;
                remaining -= z.volume();
                GasMixture share = combined.removeFraction(fraction);
                z.mixture().merge(share);
                drainPruned(share);
                z.changed = true;
            }
            drainPruned(combined);
            equalizeCount++;
        }
        groupBuffer.clear();
    }

    /**
     * A gives up r·V_B/(V_A+V_B) of its gas and B gives up r·V_A/(V_A+V_B); each
     * merges the other's parcel, moving both toward the volume-weighted mean.
     */
    private void share(Zone a, Zone b, double r) {
        double va = a.volume();
        double vb = b.volume();
        GasMixture fromA = a.mixture().removeFraction(r * vb / (va + vb));
        GasMixture fromB = b.mixture().removeFraction(r * va / (va + vb));
        a.mixture().merge(fromB);
        b.mixture().merge(fromA);
        drainPruned(fromA);
        drainPruned(fromB);
        a.changed = true;
        b.changed = true;
        exchangeCount++;
    }

    /**
     * One exchange with the ambient, unless the zone is about to dissolve into it (CORE-076). A
     * pinned zone never dissolves, so it always exchanges (D-64).
     */
    private void exchangeWithAmbient(Edge e) {
        Zone z = e.first();
        if (z.pinned || !nearlyAmbient(z.mixture())) {
            double vz = z.volume();
            double vs =
                    settings.ambientSharerVolume() + (settings.venting() == ExchangeSettings.Venting.FAITHFUL ? vz : 0);
            double r = settings.ratio(e.connections);
            GasMixture sharer = ambientFor(vs);
            GasMixture out = z.mixture().removeFraction(r * vs / (vz + vs));
            GasMixture in = sharer.removeFraction(r * vz / (vz + vs));
            if (z.mixture().merge(in)) {
                ledger.record(LedgerCategory.AMBIENT, in, 1);
            }
            ledger.record(LedgerCategory.AMBIENT, out, -1);
            drainPruned(out);
            z.changed = true;
            exchangeCount++;
        }
    }

    /**
     * One exchange of an active zone with a paused one's last-known state (GEO-035): the
     * zone-to-zone share, against a copy of the paused zone's mixture, so the paused zone doesn't
     * change. The edge never sleeps or merges while a side is paused.
     */
    private void exchangeWithPaused(Edge e, Zone active, Zone paused) {
        if (active.mixture().isEquivalent(paused.mixture())) {
            return;
        }
        double va = active.volume();
        double vp = paused.volume();
        double r = settings.ratio(e.connections);
        GasMixture out = active.mixture().removeFraction(r * vp / (va + vp));
        GasMixture in = paused.mixture().copy().removeFraction(r * va / (va + vp));
        if (active.mixture().merge(in)) {
            ledger.record(LedgerCategory.PAUSED, in, 1);
        }
        ledger.record(LedgerCategory.PAUSED, out, -1);
        drainPruned(out);
        active.changed = true;
        exchangeCount++;
    }

    /**
     * Dissolves a zone into the ambient (CORE-076): its gas goes to the ambient,
     * its cells become unzoned, and each zone edge becomes the other zone's
     * ambient connections, since the cells across it are now ambient.
     */
    private void dissolve(Zone z) {
        GasMixture gas = z.mixture().removeFraction(1);
        ledger.record(LedgerCategory.AMBIENT, gas, -1);
        drainPruned(gas);
        for (Edge e : new ArrayList<>(z.edges)) {
            removeEdge(e);
            Zone other = e.other(z);
            if (other != null) {
                Edge toAmbient = edgeFor(other, null);
                toAmbient.connections += e.connections;
                toAmbient.direct += e.direct;
                toAmbient.sleeping = false;
                other.changed = true;
            }
        }
        // Stored pairs between two unzoned cells are dropped; pairs with other zones stay.
        while (z.cellCount() > 0) {
            Cell c = z.cellAt(z.cellCount() - 1);
            for (int i = c.degree - 1; i >= 0; i--) {
                Cell n = cells.get(c.neighbours[i]);
                if (n.zone == null || n.zone == z) {
                    c.removeLinkAt(i);
                    n.removeLinkAt(n.indexOf(c.id));
                    dropIfIdle(n);
                }
            }
            removeCell(c);
            dropIfIdle(c);
        }
        drainPruned(z.mixture());
        zones.remove(z.id());
        z.destroy();
        dissolveCount++;
    }

    // ---- Conservation (CORE-036, CORE-073) ----------------------------------

    /** Moves the moles pruned in every zone's mixture into the ledger, as {@link LedgerCategory#PRUNING}. */
    public void drainPruning() {
        for (Zone z : zones.values()) {
            drainPruned(z.mixture());
        }
    }

    /** Starts a ledger period from the zones' current totals. */
    public void beginConservationPeriod() {
        drainPruning();
        double[] totals = new double[registry.size()];
        totalMoles(totals);
        ledger.begin(totals);
    }

    /**
     * Ends a ledger period: reports every change in zoned moles that no category
     * explains (CORE-073), and starts the next period.
     */
    public ConservationReport checkConservation(long tick) {
        drainPruning();
        double[] totals = new double[registry.size()];
        totalMoles(totals);
        return ledger.check(tick, totals);
    }

    private void drainPruned(GasMixture m) {
        Arrays.fill(pruneScratch, 0);
        m.drainPruned(pruneScratch);
        for (int i = 0; i < pruneScratch.length; i++) {
            if (pruneScratch[i] != 0) {
                ledger.record(LedgerCategory.PRUNING, i, -pruneScratch[i]);
            }
        }
    }

    // ---- Connections and edges (CORE-030, CORE-037) -------------------------

    /** Adds the cell's open pairs from the topology. Its own stored pairs must be empty. */
    private void linkFromTopology(Cell c) {
        linker.current = c;
        topology.forEachOpenNeighbour(c.id, linker);
        linker.current = null;
    }

    private final class Linker implements CellTopology.NeighbourVisitor {
        Cell current;

        @Override
        public void accept(long neighbour, boolean direct) {
            Cell c = current;
            if (neighbour == c.id) {
                return;
            }
            int existing = c.indexOf(neighbour);
            if (existing >= 0) {
                if (direct && !c.direct[existing]) {
                    Cell n = cells.get(neighbour);
                    countPair(c, n, false, -1);
                    c.direct[existing] = true;
                    n.direct[n.indexOf(c.id)] = true;
                    countPair(c, n, true, 1);
                }
                return;
            }
            Cell n = cells.get(neighbour);
            if (c.zone == null && (n == null || n.zone == null)) {
                return;
            }
            if (n == null) {
                double volume = topology.volume(neighbour);
                if (!(volume > 0)) {
                    return;
                }
                n = new Cell(neighbour);
                n.volume = volume;
                cells.put(neighbour, n);
            }
            c.addLink(neighbour, direct);
            n.addLink(c.id, direct);
            countPair(c, n, direct, 1);
        }
    }

    /** Removes every stored pair of a cell, on both sides. */
    private void unlinkAll(Cell c) {
        for (int i = 0; i < c.degree; i++) {
            Cell n = cells.get(c.neighbours[i]);
            countPair(c, n, c.direct[i], -1);
            n.removeLinkAt(n.indexOf(c.id));
            dropIfIdle(n);
        }
        c.degree = 0;
    }

    private void dropIfIdle(Cell c) {
        if (c.zone == null && c.degree == 0) {
            cells.remove(c.id);
        }
    }

    /** Adds ({@code sign} 1) or removes (-1) one pair's connection on the edge it belongs to. */
    private void countPair(Cell x, Cell y, boolean direct, int sign) {
        Zone zx = x.zone;
        Zone zy = y.zone;
        if (zx == zy) {
            return; // inside one zone, or both unzoned
        }
        Edge e;
        if (zx == null) {
            e = edgeFor(zy, null);
        } else if (zy == null) {
            e = edgeFor(zx, null);
        } else {
            e = edgeFor(zx, zy);
        }
        e.connections += sign;
        if (direct) {
            e.direct += sign;
        }
        e.sleeping = false;
        if (e.connections == 0) {
            removeEdge(e);
        }
    }

    private Edge edgeFor(Zone z, Zone other) {
        Edge e = other == null ? z.ambientEdge() : z.edgeTo(other);
        if (e != null) {
            return e;
        }
        if (other == null) {
            e = new Edge(z, null);
            z.insertEdge(e);
        } else {
            e = z.id() < other.id() ? new Edge(z, other) : new Edge(other, z);
            z.insertEdge(e);
            other.insertEdge(e);
        }
        return e;
    }

    private static void removeEdge(Edge e) {
        e.removed = true;
        e.first().edges.remove(e);
        if (e.second() != null) {
            e.second().edges.remove(e);
        }
    }

    // ---- Membership (architecture §4.4) ----------------------------------------

    /** Adds a cell to a zone, telling the membership listener. */
    private void addCell(Zone zone, Cell c) {
        zone.addCell(c);
        membership.cellAdded(c.id, zone, c.volume);
    }

    /** Removes a cell from its zone, telling the membership listener. */
    private void removeCell(Cell c) {
        Zone zone = c.zone;
        membership.cellRemoved(c.id, zone, c.volume);
        zone.removeCell(c);
    }

    // ---- Zone lifecycle (CORE-026, CORE-065) ---------------------------------

    private Zone newZone(GasMixture mixture) {
        Zone z = new Zone(nextZoneId++, mixture);
        zones.put(z.id(), z);
        return z;
    }

    /** Destroys an empty zone (CORE-065). */
    private void destroyZone(Zone z) {
        for (Edge e : new ArrayList<>(z.edges)) {
            removeEdge(e);
        }
        drainPruned(z.mixture());
        zones.remove(z.id());
        z.destroy();
    }

    // ---- Storage (DATA-002, DATA-008) ----------------------------------------

    /**
     * Builds or extends a zone from a share saved in a chunk (DATA-002): the share's cells join
     * the zone with that id, which is created if it isn't live, and the share's gas is merged into
     * it, recorded as {@link LedgerCategory#STORAGE}. A zone rebuilt from its chunks therefore holds
     * the sum of their shares (DATA-008).
     *
     * <p>Cells that are no longer cells, or that another zone holds, are left out, but the share's
     * gas is kept in full, as when a cell is filled (CORE-022). The cells need not be connected to
     * each other or to the zone: a zone may be rebuilt a chunk at a time.
     *
     * @param id the zone's saved id; the counter is raised past it (CORE-026)
     * @param cellIds the share's cells
     * @param gas the share's gas; not changed
     * @param pinned whether the share was saved pinned (D-64, API-018); a pinned share pins the
     *     zone, and an unpinned one leaves its pin as it is
     * @return the zone, or null if it isn't live and none of the cells could join it, in which
     *     case nothing changes
     * @throws IllegalArgumentException if the id isn't positive
     */
    public Zone loadShare(long id, long[] cellIds, GasMixture gas, boolean pinned) {
        Objects.requireNonNull(gas, "gas");
        if (id <= 0) {
            throw new IllegalArgumentException("zone id must be positive, got " + id);
        }
        long[] sorted = cellIds.clone();
        Arrays.sort(sorted);
        long[] usable = new long[sorted.length];
        int n = 0;
        for (int i = 0; i < sorted.length; i++) {
            long c = sorted[i];
            if ((i == 0 || c != sorted[i - 1]) && topology.volume(c) > 0 && zoneOf(c) == null) {
                usable[n++] = c;
            }
        }
        Zone zone = zones.get(id);
        if (zone == null) {
            if (n == 0) {
                return null;
            }
            zone = new Zone(id, new GasMixture(registry, config, 1, gas.temperature()));
            zones.put(id, zone);
            reserveZoneIds(id + 1);
        }
        for (int i = 0; i < n; i++) {
            Cell c = cells.get(usable[i]);
            if (c == null) {
                c = new Cell(usable[i]);
                cells.put(usable[i], c);
            }
            unlinkAll(c);
            c.volume = topology.volume(usable[i]);
            addCell(zone, c);
            linkFromTopology(c);
        }
        if (zone.mixture().merge(gas)) {
            ledger.record(LedgerCategory.STORAGE, gas, 1);
        }
        zone.pinned |= pinned;
        zone.changed = true;
        return zone;
    }

    /**
     * Takes a zone out of memory once its state is in chunk storage (DATA-002): its gas leaves,
     * recorded as {@link LedgerCategory#STORAGE}, and its cells leave the graph. Unlike {@link
     * #leave} or dissolution, nothing goes to the ambient and no neighbour gains an ambient
     * connection: the edges to the zone simply disappear, as if the cells beyond were unknown.
     *
     * @return the gas the zone held
     */
    public GasMixture evict(Zone zone) {
        requireLive(zone);
        GasMixture gas = zone.mixture().removeFraction(1);
        ledger.record(LedgerCategory.STORAGE, gas, -1);
        drainPruned(gas);
        for (Edge e : new ArrayList<>(zone.edges)) {
            removeEdge(e);
            Zone other = e.other(zone);
            if (other != null) {
                other.changed = true;
            }
        }
        // The edges are gone, so the pairs are dropped without counting.
        while (zone.cellCount() > 0) {
            Cell c = zone.cellAt(zone.cellCount() - 1);
            for (int i = c.degree - 1; i >= 0; i--) {
                Cell n = cells.get(c.neighbours[i]);
                c.removeLinkAt(i);
                n.removeLinkAt(n.indexOf(c.id));
                dropIfIdle(n);
            }
            removeCell(c);
            cells.remove(c.id);
        }
        drainPruned(zone.mixture());
        zones.remove(zone.id());
        zone.destroy();
        return gas;
    }

    // ---- Rollback (CORE-070, CORE-074) ---------------------------------------

    /**
     * Recreates a zone that was destroyed since an undo point, under its old id,
     * from its cells and its mixture at that point. Cells now in another zone
     * move back (undoing a merge); unzoned cells rejoin (undoing a dissolution).
     * The scheduler uses this to restore a rejected exchange component
     * (architecture §3.3).
     *
     * <p>Nothing is recorded in the ledger: call it only between conservation
     * periods, before {@link #beginConservationPeriod()}. The cells must be
     * cells of the current topology and connected, as they were.
     *
     * @param id the destroyed zone's id
     * @param cellIds its cells at the undo point
     * @param mixture its mixture at the undo point; copied
     * @param pinned whether it was pinned at the undo point (D-64)
     * @return the recreated zone
     * @throws IllegalArgumentException if the id is live or was never issued, or
     *     the list is empty
     */
    public Zone restoreZone(long id, long[] cellIds, GasMixture mixture, boolean pinned) {
        Objects.requireNonNull(mixture, "mixture");
        if (id <= 0 || id >= nextZoneId || zones.containsKey(id)) {
            throw new IllegalArgumentException("zone id " + id + " is live or was never issued");
        }
        if (cellIds.length == 0) {
            throw new IllegalArgumentException("a zone needs at least one cell");
        }
        Zone zone = new Zone(id, mixture.copy());
        zone.pinned = pinned;
        zones.put(id, zone);
        for (long cellId : cellIds) {
            Cell c = cells.get(cellId);
            if (c != null && c.zone != null) {
                Zone from = c.zone;
                relabel(c, zone);
                from.changed = true;
                if (from.cellCount() == 0) {
                    destroyZone(from);
                }
            } else {
                if (c == null) {
                    c = new Cell(cellId);
                    cells.put(cellId, c);
                }
                unlinkAll(c);
                c.volume = topology.volume(cellId);
                addCell(zone, c);
                linkFromTopology(c);
            }
        }
        zone.setVolume(mixture.volume());
        zone.mixture().copyFrom(mixture);
        zone.changed = true;
        return zone;
    }

    /**
     * Overwrites a live zone's mixture with its copy from an undo point
     * (architecture §3.3). Like {@link #restoreZone}, it records nothing in the
     * ledger, so call it only between conservation periods.
     *
     * <p>The zone's volume is set to the copy's too: moving cells out and back
     * in may leave the summed volume off by a rounding error.
     *
     * @throws IllegalArgumentException if the zone isn't live or the volumes
     *     differ by more than rounding
     */
    public void restoreMixture(Zone zone, GasMixture mixture) {
        requireLive(zone);
        if (Math.abs(mixture.volume() - zone.volume()) > 1e-9 * zone.volume()) {
            throw new IllegalArgumentException(
                    "restored volume " + mixture.volume() + " L differs from " + zone + "'s " + zone.volume() + " L");
        }
        zone.setVolume(mixture.volume());
        zone.mixture().copyFrom(mixture);
        zone.changed = true;
    }

    private void requireLive(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        if (!zone.isAlive() || zones.get(zone.id()) != zone) {
            throw new IllegalArgumentException(zone + " is not a live zone of this graph");
        }
    }
}
