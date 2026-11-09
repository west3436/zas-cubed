package io.github.west3436.zas.s10;

import io.github.west3436.zas.engine.scheduler.DimensionEngine;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/**
 * Retained-size accounting of the station's atmos heap (NFR-004), counted as the engine bench's
 * {@code HeapAccounting} counts it: the object graph is walked from the engine and the pipeline,
 * each object counted once at its HotSpot shallow size and charged to the first part that reaches
 * it, parts in the same order and with the same finer parts. In the game it then goes on to what
 * the platform holds: the dimension's {@code DimensionAtmos}, the mods' static tables, the mods'
 * block entities in the station (the devices, pipes and their networks) and the mods' attachments
 * on its entities.
 *
 * <p>The engine's parts hold only the engine's objects. The callbacks the platform gives the engine
 * lead back to the platform's state: the profiler's phase listener to the profiler, and the log,
 * which names the dimension, to the {@code DimensionAtmos} and through it to the pipeline and the
 * engine again. The engine bench's callbacks lead to its own classes, which it doesn't walk; here
 * the walk of the engine's parts doesn't go into the mods' objects outside the engine either, and
 * the platform's parts count them. Otherwise the dimension's pending operations, the pipeline's
 * tables and the undo point were charged to the zone graph, whose ledger holds the log.
 *
 * <p>A snapshot is charged to the published snapshots however it is reached, as in the engine bench:
 * the section mirror reaches the last tick's first, through the fragile blocks' lookup.
 *
 * <p>Left out, besides what the engine bench leaves out (threads, executors, classes, enum
 * constants, the gas registry and settings, the geometry palette): everything of Minecraft's,
 * NeoForge's or Mojang's, such as levels, block states, registries and entities themselves, except
 * the small value objects the mods keep in their own tables (block and chunk positions, vectors);
 * and the other dimensions' atmos, which a callback holding the service reaches.
 */
@SuppressWarnings({"deprecation", "removal"})
final class AtmosHeap {
    enum Scale {
        CELL,
        ZONE,
        NEITHER
    }

    enum Part {
        MIRROR("Section mirror (geometry ids of mirrored sections)", Scale.CELL, true),
        MEMBERSHIP("Zone membership: per-section palettes and lookups", Scale.CELL, true),
        CELLS("Zone graph: cells, their stored connections and the cell map", Scale.CELL, true),
        ZONES("Zone graph: zones, mixtures and edges", Scale.ZONE, true),
        ZONE_SECTIONS("Zone membership: each zone's sections and chunks", Scale.ZONE, true),
        SNAPSHOT("Published snapshots: zone views and tables", Scale.ZONE, true),
        UNDO("Undo point: mixture copies, components", Scale.ZONE, true),
        HEAT("Heat: each zone's cached heat paths and the resolved reservoirs and pairs", Scale.CELL, true),
        OTHER("Other engine and pipeline state: classifier and fill scratch, queues, outcomes", Scale.NEITHER, true),
        DIMENSION(
                "Platform: the dimension's DimensionAtmos (pending operations, hotspots, profiler)",
                Scale.NEITHER,
                false),
        STATICS(
                "Platform: the mods' static tables (airflow, persistence, device schedules, sync)",
                Scale.NEITHER,
                false),
        DEVICES("Devices: the mods' block entities in the station, with pipe networks", Scale.NEITHER, false),
        ATTACHMENTS("The mods' attachments on the station's players and mobs", Scale.NEITHER, false);

        final String label;
        final Scale scale;
        /** Whether the engine bench counts it too. */
        final boolean engine;

        Part(String label, Scale scale, boolean engine) {
            this.label = label;
            this.scale = scale;
            this.engine = engine;
        }
    }

    private static final sun.misc.Unsafe UNSAFE = Unsafes.U;
    private static final String ENGINE = "io.github.west3436.zas.engine.";
    private static final Set<String> VALUES = Set.of(
            "net.minecraft.core.BlockPos",
            "net.minecraft.core.BlockPos$MutableBlockPos",
            "net.minecraft.world.level.ChunkPos",
            "net.minecraft.core.SectionPos",
            "net.minecraft.world.phys.Vec3");

    /** Static tables of the mods' that hold per-level, per-player or per-device state. */
    static final List<String> STATIC_HOLDERS = List.of(
            "io.github.west3436.zas.airflow.Airflow",
            "io.github.west3436.zas.change.ChangeTracker",
            "io.github.west3436.zas.debug.DebugOverlay",
            "io.github.west3436.zas.environment.ProvidedAmbients",
            "io.github.west3436.zas.fire.Burners",
            "io.github.west3436.zas.geometry.PositionGeometries",
            "io.github.west3436.zas.ignition.IgnitionEvents",
            "io.github.west3436.zas.life.Photosynthesis",
            "io.github.west3436.zas.life.Respiration",
            "io.github.west3436.zas.persistence.AtmosPersistence",
            "io.github.west3436.zas.atmos.device.DeviceActivitySync",
            "io.github.west3436.zas.atmos.device.DeviceTicker");

    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    private final EnumMap<Part, Long> bytes = new EnumMap<>(Part.class);
    private final EnumMap<Part, Map<String, long[]>> byClass = new EnumMap<>(Part.class);
    private final Map<Class<?>, Layout> layouts = new HashMap<>();
    private final IdentityHashMap<Object, Boolean> excluded = new IdentityHashMap<>();
    /** Whether the engine's parts are being walked, which leave the platform's objects alone. */
    private boolean inEngine;
    /** The station's dimension; the walk leaves the others' alone. */
    private DimensionAtmos station;

    AtmosHeap() {
        for (Part p : Part.values()) {
            bytes.put(p, 0L);
        }
    }

    AtmosHeap exclude(Object o) {
        if (o != null) {
            excluded.put(o, Boolean.TRUE);
        }
        return this;
    }

    /** The engine's and pipeline's parts, as the engine bench walks them. On the engine's thread. */
    AtmosHeap engine(DimensionEngine engine, DimensionPipeline pipeline) {
        inEngine = true;
        try {
            walk(engine.mirror(), Part.MIRROR);
            walkField(engine, "membership", Part.MEMBERSHIP);
            walkField(engine, "undoMembership", Part.MEMBERSHIP);
            walkField(engine, "conduction", Part.HEAT);
            walk(engine.graph(), Part.ZONES);
            walk(pipeline.published(), Part.SNAPSHOT);
            walk(engine.lastSnapshot(), Part.SNAPSHOT);
            for (String f : new String[] {"undoMixtures", "undoPinned", "lastComponents", "lastZoneOps"}) {
                walkField(engine, f, Part.UNDO);
            }
            walk(engine, Part.OTHER);
            walk(pipeline.scheduler(), Part.OTHER);
            walk(pipeline, Part.OTHER);
        } finally {
            inEngine = false;
        }
        return this;
    }

    /** The platform's dimension state. Server thread, after {@link #engine}. */
    AtmosHeap dimension(DimensionAtmos d) {
        station = d;
        walk(d, Part.DIMENSION);
        return this;
    }

    /** The static tables. Server thread. */
    AtmosHeap statics() {
        for (String name : STATIC_HOLDERS) {
            Class<?> c;
            try {
                c = Class.forName(name, false, AtmosHeap.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                continue;
            }
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                    continue;
                }
                Object base = UNSAFE.staticFieldBase(f);
                long offset = UNSAFE.staticFieldOffset(f);
                walk(UNSAFE.getObject(base, offset), Part.STATICS);
            }
        }
        return this;
    }

    /** The mods' block entities in some chunks, where loaded. Server thread. */
    AtmosHeap devices(ServerLevel level, Iterable<Long> chunks) {
        for (long c : chunks) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            if (chunk == null) {
                continue;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be.getClass().getName().startsWith(BusTiming.ZAS)) {
                    walk(be, Part.DEVICES);
                }
            }
        }
        return this;
    }

    /** The mods' attachments on some entities. Server thread. */
    @SuppressWarnings("unchecked")
    AtmosHeap attachments(Iterable<Entity> entities) {
        long offset = Unsafes.offset(AttachmentHolder.class, "attachments");
        for (Entity e : entities) {
            Map<Object, Object> map = (Map<Object, Object>) UNSAFE.getObject(e, offset);
            if (map == null) {
                continue;
            }
            for (Object v : map.values()) {
                if (v != null && v.getClass().getName().startsWith(BusTiming.ZAS)) {
                    walk(v, Part.ATTACHMENTS);
                }
            }
        }
        return this;
    }

    /** Charges something to a part, if nothing has reached it yet. */
    AtmosHeap walk(Object root, Part part) {
        if (root == null) {
            return this;
        }
        ArrayDeque<Object> objects = new ArrayDeque<>();
        ArrayDeque<Part> parts = new ArrayDeque<>();
        objects.push(root);
        parts.push(part);
        while (!objects.isEmpty()) {
            Object o = objects.pop();
            Part p = parts.pop();
            if (o == null || seen.containsKey(o) || skip(o)) {
                continue;
            }
            seen.put(o, Boolean.TRUE);
            p = refine(o, p);
            Class<?> c = o.getClass();
            if (c.isArray()) {
                charge(p, c, arraySize(o));
                if (!c.getComponentType().isPrimitive()) {
                    for (Object e : (Object[]) o) {
                        if (e != null) {
                            objects.push(e);
                            parts.push(p);
                        }
                    }
                }
                continue;
            }
            Layout l = layout(c);
            charge(p, c, l.size);
            for (int i = 0; i < l.refs.length; i++) {
                Object v = l.read(o, i);
                if (v != null) {
                    objects.push(v);
                    parts.push(refineField(o, l.refs[i].getName(), p));
                }
            }
        }
        return this;
    }

    private void walkField(Object owner, String field, Part part) {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                walk(UNSAFE.getObject(owner, UNSAFE.objectFieldOffset(f)), part);
                return;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
    }

    private void charge(Part p, Class<?> c, long size) {
        bytes.merge(p, size, Long::sum);
        long[] row = byClass.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(c.getName(), k -> new long[2]);
        row[0]++;
        row[1] += size;
    }

    long bytes(Part p) {
        return bytes.get(p);
    }

    long total(boolean engineOnly) {
        long t = 0;
        for (Part p : Part.values()) {
            if (!engineOnly || p.engine) {
                t += bytes.get(p);
            }
        }
        return t;
    }

    long total(Scale s) {
        long t = 0;
        for (Part p : Part.values()) {
            if (p.engine && p.scale == s) {
                t += bytes.get(p);
            }
        }
        return t;
    }

    /** The classes taking the most bytes in a part, largest first. */
    List<String> topClasses(Part p, int n) {
        List<Map.Entry<String, long[]>> rows =
                new ArrayList<>(byClass.getOrDefault(p, Map.of()).entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, rows.size()); i++) {
            long[] r = rows.get(i).getValue();
            out.add(rows.get(i).getKey() + ": " + r[0] + " objects, " + r[1] + " B");
        }
        return out;
    }

    // ---- What to skip and where to charge ------------------------------------------------

    private boolean skip(Object o) {
        if (excluded.containsKey(o)) {
            return true;
        }
        if (o instanceof Class<?>
                || o instanceof Thread
                || o instanceof ThreadGroup
                || o instanceof ClassLoader
                || o instanceof Enum<?>
                || o instanceof Executor
                || o instanceof java.lang.ref.Reference<?>) {
            return true;
        }
        String n = o.getClass().getName();
        if (n.startsWith("net.minecraft.") || n.startsWith("com.mojang.") || n.startsWith("net.neoforged.")) {
            return !VALUES.contains(n);
        }
        if (inEngine && n.startsWith(BusTiming.ZAS) && !n.startsWith(ENGINE)) {
            return true; // the platform's, reached through a callback it gave the engine
        }
        if (o instanceof DimensionAtmos && o != station) {
            return true; // another dimension's
        }
        return n.startsWith(BusTiming.BENCH)
                || n.equals(ENGINE + "gas.GasRegistry")
                || n.equals(ENGINE + "gas.GasConfig")
                || n.equals(ENGINE + "GeometryPalette")
                || n.equals(ENGINE + "gas.GasDefinition")
                || n.equals("io.github.west3436.zas.geometry.GeometryTable");
    }

    private static Part refine(Object o, Part p) {
        if (!p.engine) {
            return p;
        }
        String n = o.getClass().getName();
        if (n.startsWith(ENGINE + "scheduler.Conduction")) {
            return Part.HEAT;
        }
        if (n.equals(ENGINE + "zone.Cell")
                || n.equals("[L" + ENGINE + "zone.Cell;")
                || n.equals(ENGINE + "zone.LongObjectMap")) {
            return Part.CELLS;
        }
        if (n.startsWith(ENGINE + "classify.") || n.startsWith(ENGINE + "scheduler.EnclosureClassifier")) {
            return Part.OTHER;
        }
        if (n.equals(ENGINE + "zone.ZoneGraph") || n.equals(ENGINE + "zone.Zone")) {
            return Part.ZONES;
        }
        if (n.equals(ENGINE + "scheduler.Membership$ZoneSections")) {
            return Part.ZONE_SECTIONS;
        }
        if (n.equals(ENGINE + "scheduler.AtmosSnapshot")) {
            return Part.SNAPSHOT;
        }
        if (n.equals(ENGINE + "SectionSnapshot")) {
            return Part.MIRROR;
        }
        if (n.equals(ENGINE + "scheduler.SectionMembers")
                || n.equals(ENGINE + "scheduler.MembershipTracker")
                || n.equals(ENGINE + "scheduler.Membership")) {
            return Part.MEMBERSHIP;
        }
        return p;
    }

    private static Part refineField(Object owner, String field, Part p) {
        if (owner.getClass().getName().equals(ENGINE + "scheduler.AtmosSnapshot")
                && (field.equals("positions") || field.equals("zoneOfPosition"))) {
            return Part.MEMBERSHIP;
        }
        return p;
    }

    // ---- Sizes -----------------------------------------------------------------------------

    private static long arraySize(Object a) {
        Class<?> c = a.getClass();
        long n = java.lang.reflect.Array.getLength(a);
        return align(UNSAFE.arrayBaseOffset(c) + n * UNSAFE.arrayIndexScale(c));
    }

    private Layout layout(Class<?> c) {
        return layouts.computeIfAbsent(c, Layout::new);
    }

    private static long align(long size) {
        return (size + 7) & ~7L;
    }

    /** An instance's size and reference fields. */
    private static final class Layout {
        final long size;
        final Field[] refs;
        final long[] offsets; // -1 where read by reflection

        Layout(Class<?> c) {
            List<Field> refList = new ArrayList<>();
            List<Long> offsetList = new ArrayList<>();
            long end = 12;
            long estimate = 12;
            boolean exact = true;
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                boolean viaUnsafe = !k.isRecord() && !k.isHidden() && !c.isHidden() && !c.isRecord();
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    long size = fieldSize(f.getType());
                    estimate += size;
                    long offset = -1;
                    if (viaUnsafe) {
                        offset = UNSAFE.objectFieldOffset(f);
                        end = Math.max(end, offset + size);
                    } else {
                        exact = false;
                    }
                    if (!f.getType().isPrimitive()) {
                        if (offset < 0) {
                            try {
                                f.setAccessible(true);
                            } catch (RuntimeException e) {
                                continue; // counted, not walked
                            }
                        }
                        refList.add(f);
                        offsetList.add(offset);
                    }
                }
            }
            this.size = align(exact ? end : estimate);
            this.refs = refList.toArray(Field[]::new);
            this.offsets = offsetList.stream().mapToLong(Long::longValue).toArray();
        }

        Object read(Object o, int i) {
            if (offsets[i] >= 0) {
                return UNSAFE.getObject(o, offsets[i]);
            }
            try {
                return refs[i].get(o);
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        private static long fieldSize(Class<?> t) {
            if (t == long.class || t == double.class) {
                return 8;
            }
            if (t == int.class || t == float.class) {
                return 4;
            }
            if (t == short.class || t == char.class) {
                return 2;
            }
            if (t == byte.class || t == boolean.class) {
                return 1;
            }
            return 4;
        }
    }
}
