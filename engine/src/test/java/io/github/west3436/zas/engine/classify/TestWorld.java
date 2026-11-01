package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.EngineLog;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.conservation.ConservationLedger;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A synthetic world for the classification tests: sections of geometry ids, solid by default,
 * built up by carving boxes. A section exists once a box touches it or its neighbour; any other
 * section is unknown to the topology.
 */
final class TestWorld {
    static final int AIR = GeometryPalette.AIR_ID;
    static final int SOLID = GeometryPalette.SOLID_ID;
    /** A thin wall down the middle of x: two cells, west (0) and east (1). */
    static final int DIVIDER;
    /** An open door that is a zone boundary on every face, so its cell is a zone of its own (GEO-007). */
    static final int DOOR;
    /**
     * An open door whose east face is its zone boundary, as a door is across the face it closes
     * against (GEO-007): it joins the room west of it, and gas crosses east without the zones meeting.
     */
    static final int DOOR_EAST;

    static final GeometryPalette PALETTE;

    static {
        GeometryPalette.Builder b = GeometryPalette.builder();
        BitSet wall = new BitSet(GeometryDeriver.VOXELS);
        GeometryDeriver.fill(wall, 7, 0, 0, 9, 16, 16);
        DIVIDER = b.intern(GeometryDeriver.derive(wall));
        DOOR = b.intern(Geometry.declared(Set.of(), Geometry.FULL_VOLUME, Geometry.FLAG_ZONE_BOUNDARY));
        DOOR_EAST = b.intern(Geometry.AIR.withFlags(Geometry.boundaryFlag(Face.EAST)));
        PALETTE = b.build();
    }

    static final GasRegistry REGISTRY = GasRegistry.of(List.of(gas("test:nitrogen", 0.028, 20.8)));
    static final double ROOM_T = 293.15;
    static final GasConfig CONFIG = GasConfig.DEFAULT.withLog(message -> {
        throw new AssertionError("unexpected engine warning: " + message);
    });

    private final Map<Long, short[]> ids = new HashMap<>();
    private final Map<Long, SectionSnapshot> snapshots = new HashMap<>();
    private final int background;

    TestWorld() {
        this(SOLID);
    }

    TestWorld(int background) {
        this.background = background;
    }

    /** Sets every block of a box, corners inclusive, and makes the sections around it known. */
    TestWorld box(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
        for (int sx = (x0 - 1) >> 4; sx <= (x1 + 1) >> 4; sx++) {
            for (int sy = (y0 - 1) >> 4; sy <= (y1 + 1) >> 4; sy++) {
                for (int sz = (z0 - 1) >> 4; sz <= (z1 + 1) >> 4; sz++) {
                    section(SectionKeys.section(sx, sy, sz));
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    long key = SectionKeys.sectionOfBlock(x, y, z);
                    section(key)[SectionKeys.index(x & 15, y & 15, z & 15)] = (short) id;
                    snapshots.remove(key);
                }
            }
        }
        return this;
    }

    TestWorld set(int x, int y, int z, int id) {
        return box(x, y, z, x, y, z, id);
    }

    /** Makes a section unknown, as if its chunk were unloaded or not yet copied. */
    TestWorld forget(long section) {
        ids.remove(section);
        snapshots.remove(section);
        return this;
    }

    /** A copy of a section's ids, to restore later with {@link #restore}. */
    short[] save(long section) {
        return ids.get(section).clone();
    }

    TestWorld restore(long section, short[] saved) {
        ids.put(section, saved.clone());
        snapshots.remove(section);
        return this;
    }

    boolean has(long section) {
        return ids.containsKey(section);
    }

    private short[] section(long key) {
        return ids.computeIfAbsent(key, k -> {
            short[] a = new short[SectionSnapshot.SIZE];
            java.util.Arrays.fill(a, (short) background);
            return a;
        });
    }

    /** The snapshot of a section, or null if it is unknown. */
    SectionSnapshot snapshot(long key) {
        SectionSnapshot s = snapshots.get(key);
        if (s == null) {
            short[] a = ids.get(key);
            if (a == null) {
                return null;
            }
            s = SectionSnapshot.of(key, 0, 0, a);
            snapshots.put(key, s);
        }
        return s;
    }

    SectionTopology topology() {
        return new SectionTopology(PALETTE, this::snapshot);
    }

    SectionTopology topology(int minY, int maxY) {
        return new SectionTopology(PALETTE, this::snapshot, minY, maxY);
    }

    static long cell(int x, int y, int z) {
        return CellIds.of(x, y, z);
    }

    // ---- Gas ----------------------------------------------------------------

    static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(5), molarMass, heatCapacity, Optional.empty());
    }

    /** Nitrogen at a pressure in kPa, per 1,000 L. */
    static GasMixture air(double kPa) {
        double n = kPa * Geometry.FULL_VOLUME / (GasConstants.R * ROOM_T);
        GasMixture m = new GasMixture(REGISTRY, CONFIG, Geometry.FULL_VOLUME, ROOM_T);
        m.setMoles(0, n);
        return m;
    }

    static ZoneGraph graph(SectionTopology topology, GasMixture ambient) {
        EngineLog log = CONFIG.log();
        ConservationLedger ledger = new ConservationLedger(
                REGISTRY, CONFIG.pruneEpsilon(), ConservationLedger.DEFAULT_RELATIVE_TOLERANCE, log);
        return new ZoneGraph(REGISTRY, CONFIG, topology, ambient, ExchangeSettings.DEFAULT, ledger);
    }

    /** The moles per litre of the ambient mixture. */
    static double perLitre(GasMixture m) {
        return m.totalMoles() / m.volume();
    }

    static Face[] faces() {
        return Face.values();
    }
}
