package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.FaceMask;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.heat.HeatMath;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.engine.zone.MembershipListener;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The heat phase of the atmos tick (phase 5): conduction through zone boundaries, heat sources
 * and radiation to a vacuum (HEAT-001 to HEAT-015, architecture §4.8).
 *
 * <p><b>Links.</b> Each zone's heat paths are found once and cached. From every face of every
 * zoned cell, the lines of the 1/16 grid that leave the cell through solid are followed along the
 * face's normal, block by block, through at most {@link HeatSettings#maxThickness()} wall layers
 * (HEAT-011). A line ends at the first heat-source block (HEAT-003), at the first cell it reaches,
 * or, past the last layer, at the ground, which is the ambient. Every block the line crosses is a
 * layer in series (HEAT-012); lines with the same end and layers are grouped, each line being
 * 1/256 m². Two cells of one block with solid between them, such as the sides of a pane, are
 * linked through that block (HEAT-013). A block of U 0 has an infinite resistance: a line that
 * crosses one ends there and makes no link, so it neither conducts nor radiates (HEAT-019).
 *
 * <p><b>Resolution.</b> A link to a cell is resolved each time membership changes: the cell's zone,
 * or the ambient if it is unzoned. A zone's links to sources become a total conductance and a
 * weighted temperature, and its links to the ground and an ambient with gas a total conductance to
 * the ambient, whose temperature each step reads, so a change of temperature needs no resolution
 * (ENV-017); links to an unzoned cell in a vacuum ambient radiate (HEAT-004); links between two
 * zones become one conductance per pair, the mean of what each side found, so the pair is symmetric.
 *
 * <p><b>The step.</b> Each zone moves toward its reservoirs, then each pair exchanges, by the exact
 * exponential steps of {@link HeatMath}, so nothing overshoots (HEAT-006) and pairs conserve
 * energy. Zones below the near-vacuum density take no part (HEAT-008), and paused zones keep their
 * state (GEO-035). Zones, links and pairs are processed in id order (architecture §3.5).
 *
 * <p><b>Invalidation.</b> A zone's links are rebuilt when a cell joins, leaves or resizes in it
 * (heard as a {@link MembershipListener}), when a block within reach of its cells changes, or when
 * a section next to its sections arrives in the mirror. Every link is rebuilt when the settings
 * that shape links change. Worker thread only.
 */
final class Conduction implements MembershipListener {
    /** A zone closer than this to its equilibrium, in K, isn't stepped, so settled zones don't change. */
    static final double SETTLED = 1e-4;

    private static final Face[] FACES = Face.values();
    private static final int CELL = 0;
    private static final int SOURCE = 1;
    private static final int GROUND = 2;

    private final ZoneGraph graph;
    private final SectionLookup mirror;
    private final GeometryPalette palette;
    private final CellNaming naming;
    private final MembershipTracker membership;
    private final int minY;
    private final int maxY;

    private HeatSettings settings;
    /** Each geometry's U, with the fallback applied. */
    private double[] transmittance;

    private final Map<Long, ZoneLinks> links = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();
    private final List<Long> changedBlocks = new ArrayList<>();
    private final List<Long> arrivedSections = new ArrayList<>();
    private boolean membershipChanged = true;
    private Resolved resolved;
    private boolean resolvedForVacuum;
    private long resolutions;

    /** Zone pairs that exchanged heat in the last tick, as {@code {a, b}}, a < b. */
    private final List<long[]> exchanged = new ArrayList<>();

    Conduction(
            ZoneGraph graph,
            SectionLookup mirror,
            GeometryPalette palette,
            CellNaming naming,
            MembershipTracker membership) {
        this.graph = graph;
        this.mirror = mirror;
        this.palette = palette;
        this.naming = naming;
        this.membership = membership;
        this.minY = mirror.minSectionY() * 16;
        this.maxY = mirror.maxSectionY() * 16 + 15;
    }

    // ---- Invalidation ---------------------------------------------------------------------

    @Override
    public void cellAdded(long cell, Zone zone, double volume) {
        dirty.add(zone.id());
        membershipChanged = true;
    }

    @Override
    public void cellRemoved(long cell, Zone zone, double volume) {
        dirty.add(zone.id());
        membershipChanged = true;
    }

    @Override
    public void cellResized(long cell, Zone zone, double oldVolume, double newVolume) {
        dirty.add(zone.id());
        membershipChanged = true;
    }

    @Override
    public void zoneMerged(Zone from, Zone into) {
        dirty.add(from.id());
        dirty.add(into.id());
        membershipChanged = true;
    }

    /** A block's geometry changed in the mirror this tick. */
    void blockChanged(long block) {
        changedBlocks.add(block);
    }

    /** A section entered the mirror this tick. */
    void sectionArrived(long key) {
        arrivedSections.add(key);
    }

    /** The zone pairs that exchanged heat in the last tick, {@code {a, b}} with a < b. */
    List<long[]> exchanged() {
        return exchanged;
    }

    // ---- The tick -------------------------------------------------------------------------

    /**
     * Runs one heat phase.
     *
     * @param heat the settings
     * @param dt the atmos period in seconds
     * @param ambient the dimension's ambient
     */
    void tick(HeatSettings heat, double dt, Ambient ambient) {
        exchanged.clear();
        if (settings == null || heat.changesLinks(settings)) {
            settings = heat;
            transmittance = new double[palette.size()];
            for (int id = 0; id < palette.size(); id++) {
                double u = palette.geometry(id).transmittance();
                transmittance[id] = Double.isNaN(u) ? heat.fallbackTransmittance() : u;
            }
            links.clear();
            membershipChanged = true;
        }
        settings = heat;
        if (transmittance.length < palette.size()) {
            // The geometries of single positions added since (GEO-043, API-023).
            int from = transmittance.length;
            transmittance = Arrays.copyOf(transmittance, palette.size());
            for (int id = from; id < transmittance.length; id++) {
                double u = palette.geometry(id).transmittance();
                transmittance[id] = Double.isNaN(u) ? heat.fallbackTransmittance() : u;
            }
        }
        if (!heat.enabled()) {
            // Keep tracking, so links are right when it is turned on again.
            markChangedZones();
            return;
        }
        markChangedZones();
        boolean relinked = false;
        for (long id : dirty) {
            links.remove(id);
            relinked = true;
        }
        dirty.clear();
        // Only whether the ambient has gas decides what a link resolves to. Its temperature is read
        // each step, so an ambient changing every tick costs no resolution (ENV-017).
        if (relinked || membershipChanged || resolved == null || ambient.isVacuum() != resolvedForVacuum) {
            resolved = resolve(ambient);
            resolvedForVacuum = ambient.isVacuum();
            resolutions++;
            membershipChanged = false;
        }
        step(resolved, dt, ambient);
    }

    /** Marks the zones near this tick's changed blocks and arrived sections. */
    private void markChangedZones() {
        int reach = (settings == null ? HeatSettings.DEFAULT.maxThickness() : settings.maxThickness()) + 1;
        for (long block : changedBlocks) {
            int x = SectionKeys.blockX(block);
            int y = SectionKeys.blockY(block);
            int z = SectionKeys.blockZ(block);
            markAt(x, y, z);
            for (Face f : FACES) {
                for (int k = 1; k <= reach; k++) {
                    markAt(x + f.dx() * k, y + f.dy() * k, z + f.dz() * k);
                }
            }
        }
        changedBlocks.clear();
        for (long key : arrivedSections) {
            int sx = SectionKeys.x(key);
            int sy = SectionKeys.y(key);
            int sz = SectionKeys.z(key);
            membership.forEachZoneIn(key, dirty::add);
            for (Face f : FACES) {
                membership.forEachZoneIn(SectionKeys.section(sx + f.dx(), sy + f.dy(), sz + f.dz()), dirty::add);
            }
        }
        arrivedSections.clear();
        if (!dirty.isEmpty()) {
            membershipChanged = true;
        }
    }

    private void markAt(int x, int y, int z) {
        int id = geometryId(x, y, z);
        if (id < 0) {
            return;
        }
        int cells = palette.geometry(id).cellCount();
        long block = SectionKeys.block(x, y, z);
        for (int sub = 0; sub < Math.min(cells, CellIds.MAX_SUB_CELLS); sub++) {
            Zone zone = graph.zoneOf(naming.cell(block, sub));
            if (zone != null) {
                dirty.add(zone.id());
            }
        }
    }

    // ---- Links ----------------------------------------------------------------------------

    /** A zone's heat paths: to cells (resolved later), to heat sources and to the ground. */
    record ZoneLinks(
            long[] cells, double[] cellU, double[] cellArea, double sourceG, double sourceGT, double groundG) {}

    private record LinkKey(int kind, long far, double resistance) {}

    private ZoneLinks linksOf(Zone zone) {
        ZoneLinks cached = links.get(zone.id());
        if (cached == null) {
            cached = build(zone);
            links.put(zone.id(), cached);
        }
        return cached;
    }

    /** Finds every heat path of a zone's cells (HEAT-011 to HEAT-013). */
    private ZoneLinks build(Zone zone) {
        // Insertion-ordered, so areas sum in the order the cells are visited.
        Map<LinkKey, double[]> found = new java.util.LinkedHashMap<>();
        long[] group = new long[4];
        for (long cell : zone.cells()) {
            long block = naming.position(cell);
            int sub = naming.subIndex(cell);
            int x = SectionKeys.blockX(block);
            int y = SectionKeys.blockY(block);
            int z = SectionKeys.blockZ(block);
            int id = geometryId(x, y, z);
            if (id < 0) {
                continue;
            }
            Geometry g = palette.geometry(id);
            if (sub >= g.cellCount()) {
                continue;
            }
            Geometry.Cell c = g.cell(sub);
            double ownLayer = HeatMath.resistance(transmittance[id]);
            for (Face f : FACES) {
                int qx = x + f.dx();
                int qy = y + f.dy();
                int qz = z + f.dz();
                int qid = geometryId(qx, qy, qz);
                if (qid < 0) {
                    continue; // outside the world or not mirrored: open, or unknown
                }
                Geometry q = palette.geometry(qid);
                Face back = f.opposite();
                // Lines that cross the cell's own solid before leaving it.
                long[] shielded = words(c.shield(f));
                // Lines the cell reaches the face with, sealed there by the neighbour.
                long[] sealed = and(words(c.footprint(f)), q.coverage(back), group);
                if (isEmpty(shielded) && isEmpty(sealed)) {
                    continue;
                }
                walk(found, x, y, z, f, new long[][] {shielded, sealed.clone()}, new double[] {ownLayer, 0});
            }
            for (Geometry.Partition p : g.partitions()) {
                int other = p.first() == sub ? p.second() : p.second() == sub ? p.first() : -1;
                if (other >= 0 && other < CellIds.MAX_SUB_CELLS) {
                    add(found, CELL, naming.cell(block, other), ownLayer, p.area());
                }
            }
        }
        int n = 0;
        for (LinkKey k : found.keySet()) {
            if (k.kind() == CELL) {
                n++;
            }
        }
        long[] cells = new long[n];
        double[] u = new double[n];
        double[] area = new double[n];
        double sourceG = 0;
        double sourceGT = 0;
        double groundG = 0;
        List<Map.Entry<LinkKey, double[]>> entries = new ArrayList<>(found.entrySet());
        entries.sort((a, b) -> {
            int c = Integer.compare(a.getKey().kind(), b.getKey().kind());
            if (c == 0) {
                c = Long.compare(a.getKey().far(), b.getKey().far());
            }
            return c != 0
                    ? c
                    : Double.compare(a.getKey().resistance(), b.getKey().resistance());
        });
        int i = 0;
        for (Map.Entry<LinkKey, double[]> e : entries) {
            LinkKey k = e.getKey();
            double a = e.getValue()[0];
            double g = a / k.resistance();
            switch (k.kind()) {
                case CELL -> {
                    cells[i] = k.far();
                    u[i] = 1 / k.resistance();
                    area[i] = a;
                    i++;
                }
                case SOURCE -> {
                    sourceG += g;
                    sourceGT += g * Double.longBitsToDouble(k.far());
                }
                default -> groundG += g;
            }
        }
        return new ZoneLinks(cells, u, area, sourceG, sourceGT, groundG);
    }

    /**
     * Follows groups of lines from a cell's face into the blocks beyond (HEAT-011). Each group is a
     * line mask and the resistance of the layers crossed so far.
     */
    private void walk(Map<LinkKey, double[]> found, int x, int y, int z, Face f, long[][] masks, double[] resistances) {
        Face back = f.opposite();
        long[] scratch = new long[4];
        for (int k = 1; k <= settings.maxThickness(); k++) {
            int qx = x + f.dx() * k;
            int qy = y + f.dy() * k;
            int qz = z + f.dz() * k;
            int qid = geometryId(qx, qy, qz);
            if (qid < 0) {
                return; // outside the world or not mirrored
            }
            Geometry q = palette.geometry(qid);
            double layer = HeatMath.resistance(transmittance[qid]);
            long block = SectionKeys.block(qx, qy, qz);
            boolean any = false;
            for (int m = 0; m < masks.length; m++) {
                long[] mask = masks[m];
                double r = resistances[m];
                if (Double.isInfinite(r)) {
                    // Lines that crossed the cell's own block, of U 0, carry nothing (HEAT-019).
                    masks[m] = new long[4];
                    continue;
                }
                if (isEmpty(mask)) {
                    continue;
                }
                if (q.isHeatSource()) {
                    // A heat source ends every line, conducting with its own U (HEAT-003).
                    add(found, SOURCE, Double.doubleToLongBits(q.sourceTemperature()), r + layer, count(mask));
                    continue;
                }
                int cells = Math.min(q.cellCount(), CellIds.MAX_SUB_CELLS);
                for (int j = 0; j < cells; j++) {
                    Geometry.Cell cj = q.cell(j);
                    // Lines that reach the cell at the face: the layers so far, or this block's if
                    // none, since something sealed the face.
                    double direct = count(and(mask, cj.footprint(back), scratch));
                    if (direct > 0) {
                        add(found, CELL, naming.cell(block, j), r > 0 ? r : layer, direct);
                    }
                    double behind = count(and(mask, cj.shield(back), scratch));
                    if (behind > 0) {
                        add(found, CELL, naming.cell(block, j), r + layer, behind);
                    }
                }
                // Past a layer of U 0 a line carries no heat to anything, so it ends (HEAT-019).
                long[] next = Double.isInfinite(layer) ? new long[4] : and(mask, q.through(back), new long[4]);
                masks[m] = next;
                resistances[m] = r + layer;
                any |= !isEmpty(next);
            }
            if (q.isHeatSource() || !any) {
                return;
            }
        }
        // Nothing found within the maximum thickness: the ground, at the ambient's temperature.
        for (int m = 0; m < masks.length; m++) {
            if (!isEmpty(masks[m])) {
                add(found, GROUND, 0, resistances[m], count(masks[m]));
            }
        }
    }

    /**
     * Adds {@code area} m² to the link with this end and resistance, in (m²·K)/W. A path through a
     * layer of U 0 has an infinite resistance and carries no heat, so it is no link (HEAT-019): no
     * conductance, radiating wall or pair ever divides by it.
     */
    private static void add(Map<LinkKey, double[]> found, int kind, long far, double resistance, double area) {
        if (!(resistance > 0 && resistance < Double.POSITIVE_INFINITY) || !(area > 0)) {
            return;
        }
        found.computeIfAbsent(new LinkKey(kind, far, resistance), k -> new double[1])[0] += area;
    }

    // ---- Resolution -----------------------------------------------------------------------

    /**
     * Every zone's reservoirs and radiating walls, and the zone pairs, as of now. A zone's reservoirs
     * are its heat sources, a conductance and a weighted temperature, and the ambient's conductance,
     * which holds at any ambient temperature.
     */
    private record Resolved(
            long[] zones,
            double[] sourceG,
            double[] sourceGT,
            double[] ambientG,
            double[][] radiatingU,
            double[][] radiatingArea,
            long[] pairA,
            long[] pairB,
            double[] pairG) {}

    private Resolved resolve(Ambient ambient) {
        int n = graph.zoneCount();
        long[] zones = new long[n];
        double[] srcG = new double[n];
        double[] srcGT = new double[n];
        double[] ambG = new double[n];
        double[][] radU = new double[n][];
        double[][] radA = new double[n][];
        // Each side's half of a pair's conductance, in the order found: zones in id order, then links.
        long[] halfA = new long[64];
        long[] halfB = new long[64];
        double[] halfG = new double[64];
        int halves = 0;
        boolean vacuum = ambient.isVacuum();
        double[] radiatingU = new double[8];
        double[] radiatingA = new double[8];
        int i = 0;
        for (Zone z : graph.zones()) {
            zones[i] = z.id();
            ZoneLinks l = linksOf(z);
            double toAmbient = l.groundG();
            int radiating = 0;
            for (int k = 0; k < l.cells().length; k++) {
                Zone far = graph.zoneOf(l.cells()[k]);
                if (far == z) {
                    continue;
                }
                double ua = l.cellU()[k] * l.cellArea()[k];
                if (far == null) {
                    if (vacuum) {
                        // Grouped by U: a few distinct values, so a linear search.
                        int r = 0;
                        while (r < radiating && radiatingU[r] != l.cellU()[k]) {
                            r++;
                        }
                        if (r == radiating) {
                            if (r == radiatingU.length) {
                                radiatingU = Arrays.copyOf(radiatingU, 2 * r);
                                radiatingA = Arrays.copyOf(radiatingA, 2 * r);
                            }
                            radiatingU[r] = l.cellU()[k];
                            radiatingA[r] = 0;
                            radiating++;
                        }
                        radiatingA[r] += l.cellArea()[k];
                    } else {
                        toAmbient += ua;
                    }
                } else {
                    // Each side finds the pair; the conductance is the mean of what they found.
                    if (halves == halfA.length) {
                        halfA = Arrays.copyOf(halfA, 2 * halves);
                        halfB = Arrays.copyOf(halfB, 2 * halves);
                        halfG = Arrays.copyOf(halfG, 2 * halves);
                    }
                    halfA[halves] = Math.min(z.id(), far.id());
                    halfB[halves] = Math.max(z.id(), far.id());
                    halfG[halves] = ua / 2;
                    halves++;
                }
            }
            srcG[i] = l.sourceG();
            srcGT[i] = l.sourceGT();
            ambG[i] = toAmbient;
            // In order of U, so the sum over them doesn't depend on the order links were found.
            Integer[] order = new Integer[radiating];
            for (int r = 0; r < radiating; r++) {
                order[r] = r;
            }
            double[] us = radiatingU;
            Arrays.sort(order, (x, y) -> Double.compare(us[x], us[y]));
            radU[i] = new double[radiating];
            radA[i] = new double[radiating];
            for (int r = 0; r < radiating; r++) {
                radU[i][r] = radiatingU[order[r]];
                radA[i][r] = radiatingA[order[r]];
            }
            i++;
        }
        links.keySet().removeIf(id -> graph.zone(id) == null);
        // Pairs in (lower id, higher id) order; each pair's halves summed in the order found (a
        // stable sort keeps it).
        Integer[] order = new Integer[halves];
        for (int h = 0; h < halves; h++) {
            order[h] = h;
        }
        long[] ka = halfA;
        long[] kb = halfB;
        Arrays.sort(order, (x, y) -> {
            int c = Long.compare(ka[x], ka[y]);
            return c != 0 ? c : Long.compare(kb[x], kb[y]);
        });
        long[] pa = new long[halves];
        long[] pb = new long[halves];
        double[] pg = new double[halves];
        int p = -1;
        for (int h = 0; h < halves; h++) {
            int o = order[h];
            if (p < 0 || pa[p] != halfA[o] || pb[p] != halfB[o]) {
                p++;
                pa[p] = halfA[o];
                pb[p] = halfB[o];
            }
            pg[p] += halfG[o];
        }
        int count = p + 1;
        return new Resolved(
                zones,
                srcG,
                srcGT,
                ambG,
                radU,
                radA,
                Arrays.copyOf(pa, count),
                Arrays.copyOf(pb, count),
                Arrays.copyOf(pg, count));
    }

    // ---- The step -------------------------------------------------------------------------

    private void step(Resolved r, double dt, Ambient ambient) {
        double tBackground = ambient.temperature();
        for (int i = 0; i < r.zones().length; i++) {
            Zone z = graph.zone(r.zones()[i]);
            if (!active(z)) {
                continue;
            }
            GasMixture m = z.mixture();
            double t = m.temperature();
            double g = r.sourceG()[i] + r.ambientG()[i];
            double gt = r.sourceGT()[i] + r.ambientG()[i] * tBackground;
            double[] radU = r.radiatingU()[i];
            if (radU.length > 0 && t != tBackground) {
                for (int k = 0; k < radU.length; k++) {
                    double flux = HeatMath.radiativeFlux(radU[k], t, tBackground, settings.emissivity());
                    double gr = r.radiatingArea()[i][k] * flux / (t - tBackground);
                    if (gr > 0) {
                        g += gr;
                        gt += gr * tBackground;
                    }
                }
            }
            if (!(g > 0)) {
                continue;
            }
            double teq = gt / g;
            if (Math.abs(t - teq) < SETTLED) {
                continue;
            }
            double c = m.heatCapacity();
            double next = HeatMath.towardReservoirs(t, c, g, teq, dt);
            apply(z, c * (next - t));
        }
        for (int p = 0; p < r.pairA().length; p++) {
            Zone a = graph.zone(r.pairA()[p]);
            Zone b = graph.zone(r.pairB()[p]);
            if (!active(a) || !active(b)) {
                continue;
            }
            double ta = a.mixture().temperature();
            double tb = b.mixture().temperature();
            if (Math.abs(ta - tb) < SETTLED) {
                continue;
            }
            double e = HeatMath.pairEnergy(
                    ta, a.mixture().heatCapacity(), tb, b.mixture().heatCapacity(), r.pairG()[p], dt);
            if (e > 0) {
                e = graph.removeThermalEnergy(a, e);
                graph.addThermalEnergy(b, e);
            } else if (e < 0) {
                e = graph.removeThermalEnergy(b, -e);
                graph.addThermalEnergy(a, e);
            }
            exchanged.add(new long[] {a.id(), b.id()});
        }
    }

    /** Whether a zone takes part: alive, not paused, and not near-vacuum (HEAT-008). */
    private boolean active(Zone z) {
        return z != null
                && z.isAlive()
                && !z.isPaused()
                && z.mixture().totalMoles() >= settings.nearVacuumDensity() * z.volume();
    }

    private void apply(Zone z, double joules) {
        if (joules > 0) {
            graph.addThermalEnergy(z, joules);
        } else if (joules < 0) {
            graph.removeThermalEnergy(z, -joules);
        }
    }

    // ---- Geometry and masks ---------------------------------------------------------------

    /** The geometry id at a block, or -1 outside the world or in a section the mirror lacks. */
    private int geometryId(int x, int y, int z) {
        if (y < minY || y > maxY || !CellIds.inRange(x, y, z)) {
            return -1;
        }
        SectionSnapshot s = mirror.section(SectionKeys.sectionOfBlock(x, y, z));
        return s == null ? -1 : s.geometryId(SectionKeys.index(x & 15, y & 15, z & 15));
    }

    private static long[] words(FaceMask m) {
        return new long[] {m.word(0), m.word(1), m.word(2), m.word(3)};
    }

    private static long[] and(long[] a, FaceMask b, long[] out) {
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] & b.word(i);
        }
        return out;
    }

    private static boolean isEmpty(long[] w) {
        return (w[0] | w[1] | w[2] | w[3]) == 0;
    }

    /** The area of a mask's lines, in m². */
    private static double count(long[] w) {
        return (Long.bitCount(w[0]) + Long.bitCount(w[1]) + Long.bitCount(w[2]) + Long.bitCount(w[3]))
                * Geometry.LINE_AREA;
    }

    /** For tests and the bench: the cached links of a zone, building them if needed. */
    ZoneLinks linksFor(Zone zone) {
        return linksOf(zone);
    }

    /** For tests: how many times every zone's links have been resolved (ENV-017). */
    long resolutions() {
        return resolutions;
    }

    /** For the bench: how many zones have cached links. */
    int cachedZones() {
        return links.size();
    }

    @Override
    public String toString() {
        return "Conduction[" + links.size() + " zones linked, dirty " + Arrays.toString(dirty.toArray()) + "]";
    }
}
