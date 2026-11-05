package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.fire.BlockIgnitionSettings;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.zone.MembershipListener;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Flammable blocks catching fire from the gas beside them (FIRE-033, FIRE-034), in the hotspot
 * phase. A block's ignite odds and any ignition temperature of its own are in its {@link Geometry},
 * so the worker reads them from the mirror. Each success asks the server thread for a vanilla fire
 * in the open cell beside the block, with a {@link FireIntent.Kind#LIGHT} intent, and that fire is a
 * hotspot once the next tick sees its block (FIRE-019).
 *
 * <p><b>Hot gas</b> (FIRE-033). A zone at or above the lowest ignition temperature, whose gas passes
 * the fire oxygen threshold (FIRE-030), draws for each of its <i>faces</i>: a plain air cell of the
 * zone next to a flammable block. Only such a zone has its faces found. They are found once by
 * walking its cells, and kept until a cell joins, leaves or resizes in it, a block at or next to one
 * of its cells changes, or a section arrives in the mirror. So a cool zone costs one comparison per
 * tick, a hot zone with no changes costs a draw per face, and a change costs a hot zone one walk of
 * its cells. Open air is the ambient, not a zone, so it never lights a block.
 *
 * <p><b>Gas fires</b> (FIRE-034). A gas fire that burned this tick draws for each of its six
 * neighbours that is flammable, and on success asks for a vanilla fire in its own cell, which may be
 * the only open cell beside the block. A vanilla fire has vanilla's own spread instead.
 *
 * <p><b>Determinism</b> (§3.5). Draws hash the dimension's seed, the tick, the zone and the two
 * positions, as {@link Hotspots#draw} does for spread, salted so the two kinds of draw differ. The
 * faces are cached, but found the same whenever they are found, so nothing here is in the state
 * hash.
 *
 * <p>Worker thread only.
 */
final class BlockIgniter implements MembershipListener {
    /** Keeps these draws apart from spread's, which may name the same two positions. */
    private static final long SALT = 0x6C8E9CF570932BD5L;

    private static final Face[] FACES = Face.values();

    /** A face's entries in a zone's array: its air cell, the flammable block, the block's geometry id. */
    private static final int STRIDE = 3;

    private final ZoneGraph graph;
    private final CellSpace space;
    private final SectionMirror mirror;
    private final GeometryPalette palette;

    /** Each geometry's ignite odds; 0 if it isn't flammable. */
    private int[] odds = new int[0];

    /** Each geometry's own ignition temperature, or NaN for the configured one. */
    private double[] ignition = new double[0];

    /** The lowest ignition temperature of any flammable geometry that has its own. */
    private double lowestOwn = Double.POSITIVE_INFINITY;

    /** The faces of hot zones, by zone id, sorted by air cell then block. Lookups only (CORE-072). */
    private final Map<Long, long[]> faces = new HashMap<>();

    private long seed = SALT;

    BlockIgniter(ZoneGraph graph, CellSpace space, SectionMirror mirror, GeometryPalette palette) {
        this.graph = graph;
        this.space = space;
        this.mirror = mirror;
        this.palette = palette;
        geometriesAdded();
    }

    /** The palette took in the geometries of single positions (GEO-043, API-023). */
    void geometriesAdded() {
        int from = odds.length;
        int n = palette.size();
        odds = Arrays.copyOf(odds, n);
        ignition = Arrays.copyOf(ignition, n);
        for (int id = from; id < n; id++) {
            Geometry g = palette.geometry(id);
            odds[id] = g.igniteOdds();
            ignition[id] = g.ignitionTemperature();
            if (g.isFlammable() && g.ignitionTemperature() < lowestOwn) {
                lowestOwn = g.ignitionTemperature();
            }
        }
    }

    void setSeed(long seed) {
        this.seed = seed ^ SALT;
    }

    /** How many zones have their faces cached. For tests. */
    int cachedZones() {
        return faces.size();
    }

    // ---- The two rules ------------------------------------------------------------------

    /**
     * FIRE-034: a gas fire that burned may light its flammable neighbours.
     *
     * @param block the gas fire's position
     * @param zone its zone
     */
    void gasFire(
            long block,
            Zone zone,
            long tick,
            double seconds,
            BlockIgnitionSettings settings,
            FireSettings fire,
            List<FireIntent> out) {
        if (!fire.sustains(zone.mixture())) {
            return;
        }
        for (Face f : FACES) {
            long next = offset(block, f);
            int id = mirror.geometryAt(next);
            if (id < 0 || odds[id] == 0) {
                continue;
            }
            double chance = BlockIgnitionSettings.inPeriod(settings.gasFireChance(odds[id]), seconds);
            if (Hotspots.draw(seed, tick, zone.id(), block, next) < chance) {
                out.add(new FireIntent(FireIntent.Kind.LIGHT, block, zone.id()));
                return;
            }
        }
    }

    /**
     * FIRE-033: every hot zone draws for its faces.
     *
     * @param burning whether a position has a hotspot, pending ones included
     */
    void hotGas(
            long tick,
            double seconds,
            BlockIgnitionSettings settings,
            FireSettings fire,
            LongPredicate burning,
            List<FireIntent> out) {
        double lowest = Math.min(settings.temperature(), lowestOwn);
        for (Zone z : graph.zones()) {
            double gas = z.mixture().temperature();
            if (gas < lowest) {
                if (!faces.isEmpty()) {
                    faces.remove(z.id());
                }
                continue;
            }
            if (z.isPaused() || !fire.sustains(z.mixture())) {
                continue;
            }
            long[] found = faces.get(z.id());
            if (found == null) {
                found = find(z);
                faces.put(z.id(), found);
            }
            draw(z, found, gas, tick, seconds, settings, burning, out);
        }
    }

    private void draw(
            Zone z,
            long[] found,
            double gas,
            long tick,
            double seconds,
            BlockIgnitionSettings settings,
            LongPredicate burning,
            List<FireIntent> out) {
        // Walls are long runs of one block, so the chance is kept from one face to the next.
        int chanceFor = -1;
        double chance = 0;
        boolean lit = false;
        for (int i = 0; i < found.length; i += STRIDE) {
            long air = found[i];
            if (lit && air == found[i - STRIDE]) {
                continue;
            }
            lit = false;
            int id = (int) found[i + 2];
            if (id != chanceFor) {
                chanceFor = id;
                double own = ignition[id];
                double at = Double.isNaN(own) ? settings.temperature() : own;
                chance = BlockIgnitionSettings.inPeriod(settings.hotGasChance(odds[id], gas, at), seconds);
            }
            if (chance > 0 && !burning.test(air) && Hotspots.draw(seed, tick, z.id(), air, found[i + 1]) < chance) {
                out.add(new FireIntent(FireIntent.Kind.LIGHT, air, z.id()));
                lit = true;
            }
        }
    }

    /** A zone's faces, from the mirror: its plain air cells' flammable neighbours, sorted. */
    private long[] find(Zone z) {
        List<long[]> found = new ArrayList<>();
        z.forEachCell(cell -> {
            long block = space.position(cell);
            if (mirror.geometryAt(block) != GeometryPalette.AIR_ID) {
                return;
            }
            for (Face f : FACES) {
                long next = offset(block, f);
                int id = mirror.geometryAt(next);
                if (id >= 0 && odds[id] > 0) {
                    found.add(new long[] {block, next, id});
                }
            }
        });
        found.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        long[] flat = new long[found.size() * STRIDE];
        for (int i = 0; i < found.size(); i++) {
            System.arraycopy(found.get(i), 0, flat, i * STRIDE, STRIDE);
        }
        return flat;
    }

    // ---- Invalidation ---------------------------------------------------------------------

    @Override
    public void cellAdded(long cell, Zone zone, double volume) {
        faces.remove(zone.id());
    }

    @Override
    public void cellRemoved(long cell, Zone zone, double volume) {
        faces.remove(zone.id());
    }

    @Override
    public void cellResized(long cell, Zone zone, double oldVolume, double newVolume) {
        faces.remove(zone.id());
    }

    @Override
    public void zoneMerged(Zone from, Zone into) {
        faces.remove(from.id());
        faces.remove(into.id());
    }

    /** A block's geometry changed in the mirror this tick: the zones at and next to it look again. */
    void blockChanged(long block) {
        if (faces.isEmpty()) {
            return;
        }
        dropAt(block);
        for (Face f : FACES) {
            dropAt(offset(block, f));
        }
    }

    /** A section entered the mirror this tick: a zone beside it may have new neighbours. */
    void sectionArrived() {
        faces.clear();
    }

    private void dropAt(long block) {
        space.forEachCellAt(block, cell -> {
            Zone z = graph.zoneOf(cell);
            if (z != null) {
                faces.remove(z.id());
            }
        });
    }

    private static long offset(long block, Face f) {
        return SectionKeys.block(
                SectionKeys.blockX(block) + f.dx(),
                SectionKeys.blockY(block) + f.dy(),
                SectionKeys.blockZ(block) + f.dz());
    }
}
