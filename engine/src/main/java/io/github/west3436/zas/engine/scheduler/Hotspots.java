package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.reaction.Flammability;
import io.github.west3436.zas.engine.reaction.ReactionResult;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The hotspot phase (phase 6 of the atmos tick, architecture §3.2 and §5's fire row): per-cell
 * fires that burn their zone's gas a parcel at a time (FIRE-011), spread to neighbouring cells
 * (FIRE-016), go out when their gas won't burn (FIRE-017) and start by ignition (FIRE-014) or by
 * themselves (FIRE-010).
 *
 * <p><b>Every hotspot is a block.</b> A hotspot is a vanilla fire block in a zone (FIRE-019) or a
 * {@code zas:gas_fire} block (FIRE-018), both flagged in the geometry palette ({@link
 * Geometry#FLAG_FIRE}, {@link Geometry#FLAG_GAS_FIRE}) so the mirror sees them. Each tick starts by
 * bringing the hotspots in line with the mirror: every flagged block in a zone is a hotspot, and a
 * hotspot whose block is gone is dropped. Nothing about hotspots is saved; after a load they come
 * back from the blocks (DATA-009). A new gas hotspot asks for its block with a {@link FireIntent},
 * which the server thread applies with the result; until the next tick sees the block it is
 * <i>pending</i>: it neither burns, spreads nor counts as a fire. If the block never appears (the
 * position wasn't air after all, or the world changed) the hotspot is dropped, and the position
 * isn't tried again for {@link #REFUSAL_TICKS} ticks.
 *
 * <p><b>One tick</b>, in order: sync with the mirror; ignitions (operations in sequence order, then
 * ignition-source blocks, {@link Geometry#FLAG_IGNITION_SOURCE}, in position order, then
 * autoignition by zone in id order); every active hotspot in position order burns a parcel of
 * {@link ReactionSet#PARCEL_LITRES_PER_SECOND} litres per second of its zone's gas with the
 * batch's reactions ({@link ReactionSet#burn}), or goes out
 * if the gas won't burn; then every hotspot that burned may spread to each open neighbouring cell
 * that could hold a fire. Vanilla fire never goes out here: putting it out without O₂ is FIRE-020's.
 * Hotspots in paused zones keep still (GEO-035).
 *
 * <p><b>Determinism</b> (§3.5). Hotspots are kept by position, in a sorted map. A spread's draw is
 * a hash of the dimension's seed, the atmos tick, the zone, the hotspot and the target, so it
 * doesn't depend on the order draws are made in.
 *
 * <p><b>Rejection</b> (§3.3). Burning changes zone mixtures, so a burning zone is in an exchange
 * component and is restored with it. The server drops the intents and events of rejected zones;
 * {@link #restore} drops the pending hotspots those intents were for and takes back the zones'
 * fire state, so their events come again when the work is redone.
 */
final class Hotspots {
    /** How long a position whose gas fire never appeared isn't tried again, in atmos ticks. */
    static final long REFUSAL_TICKS = 30;

    private static final long CONFIRMED = Long.MIN_VALUE;

    /**
     * One hotspot.
     *
     * @param block its position
     * @param gas a gas fire, not a vanilla fire block
     * @param askedAt the tick its gas fire was asked for while pending, or {@link #CONFIRMED}
     * @param zoneId the zone it was asked for in, while pending
     */
    private record Hotspot(long block, boolean gas, long askedAt, long zoneId) {
        boolean pending() {
            return askedAt != CONFIRMED;
        }
    }

    /** What an ignition found, with the zone it acted on (0 if none). */
    record Ignition(OpOutcome.IgnitionStatus status, long block, Zone zone) {}

    private final ZoneGraph graph;
    private final CellSpace space;
    private final SectionMirror mirror;
    private final boolean[] fire;
    private final boolean[] gasFire;
    private final boolean[] source;

    private final TreeMap<Long, Hotspot> byBlock = new TreeMap<>();
    /** Positions whose gas fire didn't appear, with the tick that was found. */
    private final TreeMap<Long, Long> refused = new TreeMap<>();
    /** Zones with an active hotspot after the last tick: id to their lowest hotspot. */
    private TreeMap<Long, Long> burning = new TreeMap<>();
    /** {@link #burning} as it was before the last tick, for {@link #restore}. */
    private TreeMap<Long, Long> burningBefore = new TreeMap<>();

    private long seed;

    // This tick's settings.
    private long tick;
    private ReactionSet reactions = ReactionSet.EMPTY;
    private FireSettings fireSettings = FireSettings.DEFAULT;
    private List<FireIntent> intents = new ArrayList<>();

    Hotspots(ZoneGraph graph, CellSpace space, SectionMirror mirror, GeometryPalette palette) {
        this.graph = graph;
        this.space = space;
        this.mirror = mirror;
        int n = palette.size();
        fire = new boolean[n];
        gasFire = new boolean[n];
        source = new boolean[n];
        for (int id = 0; id < n; id++) {
            Geometry g = palette.geometry(id);
            gasFire[id] = g.hasFlag(Geometry.FLAG_GAS_FIRE);
            fire[id] = !gasFire[id] && g.hasFlag(Geometry.FLAG_FIRE);
            source[id] = g.hasFlag(Geometry.FLAG_IGNITION_SOURCE);
        }
        mirror.indexFlagged(id -> id >= 0 && id < n && (fire[id] || gasFire[id] || source[id]));
    }

    /** Seeds the spread draws, per dimension (§3.5). */
    void setSeed(long seed) {
        this.seed = seed;
    }

    /** How many hotspots there are, pending ones included. */
    int count() {
        return byBlock.size();
    }

    /** The hotspots whose block is there, with their zones, for publishing (API-055). */
    HotspotTable table() {
        long[] blocks = new long[byBlock.size()];
        long[] zones = new long[byBlock.size()];
        int n = 0;
        for (Hotspot h : byBlock.values()) {
            if (h.pending()) {
                continue;
            }
            Zone z = zoneAt(h.block());
            if (z != null) {
                blocks[n] = h.block();
                zones[n++] = z.id();
            }
        }
        return new HotspotTable(Arrays.copyOf(blocks, n), Arrays.copyOf(zones, n));
    }

    // ---- The phase ---------------------------------------------------------------------

    /**
     * Runs the hotspot phase.
     *
     * @param tick the atmos tick
     * @param seconds the atmos period
     * @param settings this tick's settings: hotspot spread, reactions and the fire oxygen threshold
     * @param fireSettings this tick's fire oxygen threshold (FIRE-030)
     * @param ignitions the positions of this tick's ignition operations, in sequence order
     * @param out receives the world writes asked for
     * @param events receives the zones that caught fire or stopped burning
     * @return what each ignition operation found, in the order given
     */
    Ignition[] tick(
            long tick,
            double seconds,
            EngineSettings settings,
            long[] ignitions,
            List<FireIntent> out,
            List<FireEvent> events) {
        this.tick = tick;
        this.reactions = settings.reactions();
        this.fireSettings = settings.fire();
        this.intents = out;
        burningBefore = burning;

        sync();

        Ignition[] found = new Ignition[ignitions.length];
        for (int i = 0; i < ignitions.length; i++) {
            found[i] = ignite(ignitions[i]);
        }
        long[] flagged = mirror.flagged();
        for (long block : flagged) {
            int id = mirror.geometryAt(block);
            if (id >= 0 && source[id]) {
                ignite(block);
            }
        }
        autoignite();

        List<long[]> burned = burn(seconds);
        spread(burned, settings.hotspots().chanceIn(seconds));

        TreeMap<Long, Long> now = new TreeMap<>();
        for (Hotspot h : byBlock.values()) {
            if (h.pending()) {
                continue;
            }
            Zone z = zoneAt(h.block());
            if (z != null) {
                now.merge(z.id(), h.block(), Math::min);
            }
        }
        for (Map.Entry<Long, Long> e : now.entrySet()) {
            if (!burningBefore.containsKey(e.getKey())) {
                events.add(new FireEvent(FireEvent.Kind.IGNITED, e.getKey(), e.getValue()));
            }
        }
        for (Map.Entry<Long, Long> e : burningBefore.entrySet()) {
            if (!now.containsKey(e.getKey())) {
                events.add(new FireEvent(FireEvent.Kind.EXTINGUISHED, e.getKey(), e.getValue()));
            }
        }
        events.sort((a, b) -> Long.compare(a.zoneId(), b.zoneId()));
        burning = now;
        intents = new ArrayList<>();
        return found;
    }

    /**
     * Brings the hotspots in line with the mirror: every fire block in a zone is one; a hotspot
     * whose block is gone, or whose gas fire never appeared, is dropped; a gas fire whose cell is
     * no longer zoned is put out.
     */
    private void sync() {
        refused.values().removeIf(at -> tick - at > REFUSAL_TICKS);
        Set<Long> seen = new HashSet<>();
        for (long block : mirror.flagged()) {
            int id = mirror.geometryAt(block);
            if (id < 0 || !(fire[id] || gasFire[id])) {
                continue;
            }
            boolean gas = gasFire[id];
            Zone z = zoneAt(block);
            if (z == null) {
                byBlock.remove(block);
                if (gas) {
                    intents.add(new FireIntent(FireIntent.Kind.REMOVE, block, 0));
                }
                continue;
            }
            seen.add(block);
            Hotspot h = byBlock.get(block);
            if (h == null || h.pending() || h.gas() != gas) {
                byBlock.put(block, new Hotspot(block, gas, CONFIRMED, 0));
            }
        }
        for (Iterator<Hotspot> it = byBlock.values().iterator(); it.hasNext(); ) {
            Hotspot h = it.next();
            if (!seen.contains(h.block())) {
                if (h.pending()) {
                    refused.put(h.block(), tick);
                }
                it.remove();
            }
        }
    }

    /**
     * Ignites a flammable zone at a position or a neighbouring one (FIRE-014). A hotspot in an
     * active zone at any of them means the fire is already there. Otherwise the position itself is
     * tried first, then its neighbours in {@link Face} order, and a hotspot starts at the first
     * that is in a flammable, active zone and could hold a fire block.
     */
    Ignition ignite(long block) {
        Face[] faces = Face.values();
        for (int k = 0; k <= faces.length; k++) {
            long here = k == 0 ? block : offset(block, faces[k - 1]);
            if (byBlock.containsKey(here)) {
                Zone z = zoneAt(here);
                if (z != null && !z.isPaused()) {
                    return new Ignition(OpOutcome.IgnitionStatus.BURNING, here, z);
                }
            }
        }
        OpOutcome.IgnitionStatus status = OpOutcome.IgnitionStatus.OPEN;
        Zone at = null;
        Map<Long, Boolean> flammable = new HashMap<>();
        for (int k = 0; k <= faces.length; k++) {
            long here = k == 0 ? block : offset(block, faces[k - 1]);
            List<Long> cells = new ArrayList<>(2);
            space.forEachCellAt(here, cells::add);
            for (long cell : cells) {
                Zone z = graph.zoneOf(cell);
                if (z == null) {
                    continue;
                }
                if (z.isPaused()) {
                    if (status == OpOutcome.IgnitionStatus.OPEN) {
                        status = OpOutcome.IgnitionStatus.PAUSED;
                        at = z;
                    }
                    continue;
                }
                Hotspot h = byBlock.get(here);
                if (h != null) {
                    return new Ignition(OpOutcome.IgnitionStatus.BURNING, here, z);
                }
                if (!flammable.computeIfAbsent(
                        z.id(), id -> flammability(z.mixture()).flammable())) {
                    if (rank(status) < rank(OpOutcome.IgnitionStatus.NOT_FLAMMABLE)) {
                        status = OpOutcome.IgnitionStatus.NOT_FLAMMABLE;
                        at = z;
                    }
                    continue;
                }
                if (placeable(here)) {
                    start(here, z);
                    return new Ignition(OpOutcome.IgnitionStatus.IGNITED, here, z);
                }
                status = OpOutcome.IgnitionStatus.NO_SPACE;
                at = z;
            }
        }
        return new Ignition(status, 0, at);
    }

    private static int rank(OpOutcome.IgnitionStatus s) {
        return switch (s) {
            case OPEN -> 0;
            case PAUSED -> 1;
            case NOT_FLAMMABLE -> 2;
            case NO_SPACE -> 3;
            case IGNITED, BURNING -> 4;
        };
    }

    /**
     * Autoignition (FIRE-010): an active zone with no hotspot, whose gas is flammable and at or
     * above the autoignition point of a fuel within its limits, ignites at its lowest position that
     * could hold a fire block.
     */
    private void autoignite() {
        Set<Long> withFire = new HashSet<>();
        for (Hotspot h : byBlock.values()) {
            Zone z = zoneAt(h.block());
            if (z != null) {
                withFire.add(z.id());
            }
        }
        for (Zone z : graph.zones()) {
            if (z.isPaused() || withFire.contains(z.id())) {
                continue;
            }
            GasMixture m = z.mixture();
            if (!flammability(m).ignitesAt(m.temperature())) {
                continue;
            }
            long[] lowest = {Long.MAX_VALUE};
            boolean[] any = {false};
            z.forEachCell(cell -> {
                long block = space.position(cell);
                if ((!any[0] || block < lowest[0]) && placeable(block)) {
                    lowest[0] = block;
                    any[0] = true;
                }
            });
            if (any[0]) {
                start(lowest[0], z);
            }
        }
    }

    /**
     * Each active hotspot burns a parcel of its zone's gas, or goes out if the gas won't burn
     * (FIRE-011, FIRE-017).
     *
     * @return the hotspots that burned, as {block, cell, zone id}, in position order
     */
    private List<long[]> burn(double seconds) {
        double litres = ReactionSet.PARCEL_LITRES_PER_SECOND * seconds;
        List<long[]> burned = new ArrayList<>();
        for (Hotspot h : new ArrayList<>(byBlock.values())) {
            if (h.pending()) {
                continue;
            }
            long cell = space.cellAt(h.block());
            Zone z = cell == CellSpace.NO_CELL ? null : graph.zoneOf(cell);
            if (z == null) {
                goOut(h, 0);
                continue;
            }
            if (z.isPaused()) {
                continue;
            }
            GasMixture m = z.mixture();
            if (!flammability(m).flammable()) {
                if (h.gas()) {
                    goOut(h, z.id());
                }
                continue;
            }
            ReactionResult r = reactions.burn(m, litres, true, fireSettings);
            if (r.reacted()) {
                graph.ledger().record(LedgerCategory.REACTION, r.moles());
            }
            graph.markChanged(z);
            burned.add(new long[] {h.block(), cell, z.id()});
        }
        return burned;
    }

    /** Whether a zone's gas can burn, and its autoignition point, with this tick's reactions. */
    private Flammability flammability(GasMixture m) {
        return reactions.isEmpty() ? Flammability.NONE : reactions.flammability(m, fireSettings);
    }

    private void goOut(Hotspot h, long zoneId) {
        byBlock.remove(h.block());
        if (h.gas()) {
            intents.add(new FireIntent(FireIntent.Kind.REMOVE, h.block(), zoneId));
        }
    }

    /** Each hotspot that burned may spread to each open neighbouring cell (FIRE-016). */
    private void spread(List<long[]> burned, double chance) {
        if (!(chance > 0)) {
            return;
        }
        Map<Long, Boolean> flammable = new HashMap<>();
        for (long[] b : burned) {
            long from = b[0];
            long zoneId = b[2];
            List<Long> neighbours = new ArrayList<>(6);
            space.forEachOpenNeighbour(b[1], (n, direct) -> neighbours.add(n));
            long[] sorted = new long[neighbours.size()];
            for (int i = 0; i < sorted.length; i++) {
                sorted[i] = neighbours.get(i);
            }
            Arrays.sort(sorted);
            for (long n : sorted) {
                long to = space.position(n);
                if (to == from || byBlock.containsKey(to)) {
                    continue;
                }
                Zone z = graph.zoneOf(n);
                if (z == null || z.isPaused() || !placeable(to)) {
                    continue;
                }
                if (!flammable.computeIfAbsent(
                        z.id(), id -> flammability(z.mixture()).flammable())) {
                    continue;
                }
                if (draw(seed, tick, zoneId, from, to) < chance) {
                    start(to, z);
                }
            }
        }
    }

    /** Starts a pending gas hotspot and asks for its block. */
    private void start(long block, Zone zone) {
        byBlock.put(block, new Hotspot(block, true, tick, zone.id()));
        intents.add(new FireIntent(FireIntent.Kind.PLACE, block, zone.id()));
    }

    /** Whether a gas fire could go at a position: plain air, with no hotspot and no recent refusal. */
    private boolean placeable(long block) {
        return mirror.geometryAt(block) == GeometryPalette.AIR_ID
                && !byBlock.containsKey(block)
                && !refused.containsKey(block);
    }

    private Zone zoneAt(long block) {
        long cell = space.cellAt(block);
        return cell == CellSpace.NO_CELL ? null : graph.zoneOf(cell);
    }

    // ---- Rejection, export and hashing ------------------------------------------------------

    /**
     * Undoes the last tick's hotspot changes in a rejected component's zones (§3.3): their pending
     * hotspots go, as the intents for them were dropped, and their fire state goes back to what it
     * was before, so the events come again when the work is redone.
     */
    void restore(long[] zoneIds) {
        Set<Long> ids = new HashSet<>();
        for (long id : zoneIds) {
            ids.add(id);
        }
        byBlock.values().removeIf(h -> h.pending() && ids.contains(h.zoneId()));
        for (long id : zoneIds) {
            Long before = burningBefore.get(id);
            if (before != null) {
                burning.put(id, before);
            } else {
                burning.remove(id);
            }
        }
    }

    /** The hotspots inside the regions, for the debug overlay (CLIENT-013). */
    List<DebugView.HotspotEntry> debug(List<DebugRegion> regions) {
        List<DebugView.HotspotEntry> out = new ArrayList<>();
        for (Hotspot h : byBlock.values()) {
            boolean inside = false;
            for (DebugRegion r : regions) {
                inside |= r.contains(h.block());
            }
            Zone z = inside ? zoneAt(h.block()) : null;
            if (z != null) {
                out.add(new DebugView.HotspotEntry(h.block(), z.id(), h.gas()));
            }
        }
        return out;
    }

    /** Feeds the hotspots into a state hash (CORE-044). */
    void hash(java.util.function.LongConsumer h) {
        for (Hotspot s : byBlock.values()) {
            h.accept(s.block());
            h.accept(s.gas() ? 1 : 0);
            h.accept(s.askedAt());
        }
    }

    // ---- Helpers ---------------------------------------------------------------------

    private static long offset(long block, Face f) {
        return SectionKeys.block(
                SectionKeys.blockX(block) + f.dx(),
                SectionKeys.blockY(block) + f.dy(),
                SectionKeys.blockZ(block) + f.dz());
    }

    /** A uniform draw in [0, 1) from the arguments alone (§3.5). */
    static double draw(long seed, long tick, long zone, long from, long to) {
        long h = mix(seed + 0x9E3779B97F4A7C15L);
        h = mix(h ^ tick);
        h = mix(h ^ zone);
        h = mix(h ^ from);
        h = mix(h ^ to);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** SplitMix64's finalizer. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
