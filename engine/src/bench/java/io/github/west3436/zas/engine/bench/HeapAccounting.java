package io.github.west3436.zas.engine.bench;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Retained-size accounting of one dimension's atmos heap (NFR-004): walks the object graph from
 * the engine and the pipeline and adds up each object's shallow size, counting every object once
 * and charging it to the part that reaches it first.
 *
 * <p><b>Shallow sizes</b> are HotSpot's own layout, read through {@code sun.misc.Unsafe}: an
 * instance ends at its last field's offset plus that field's size, an array at its base offset
 * plus its elements, each rounded up to 8 bytes (so they assume compressed oops and class
 * pointers, as with any heap under 32 GB). Records and hidden classes (lambdas), whose offsets
 * Unsafe won't give, are estimated as a 12-byte header plus their fields, rounded the same way.
 *
 * <p><b>Parts</b> are walked in a fixed order, so an object shared between parts (a section of the
 * zone membership that both the worker and the published snapshot hold) is charged to the first:
 * the section mirror, the zone membership, the zone graph, the published snapshots, the undo point,
 * then everything else the engine and pipeline hold. Inside a part, some types are charged to a
 * finer part: the graph's cells (with their stored connections and the cell map) apart from its
 * zones, mixtures and edges, and each zone's list of sections apart from the section palettes.
 *
 * <p><b>Left out:</b> what isn't atmos state or is shared by every dimension: the bench's
 * stand-in world, threads and executors, classes, enum constants, the gas registry, the mixture
 * settings and the geometry palette.
 */
@SuppressWarnings({"deprecation", "removal"}) // Unsafe's field offsets: the bench's only way to HotSpot's layout
final class HeapAccounting {
    /** What a part of the heap scales with. */
    enum Scale {
        CELL,
        ZONE,
        NEITHER
    }

    enum Part {
        MIRROR("Section mirror (geometry ids of mirrored sections)", Scale.CELL),
        MEMBERSHIP("Zone membership: per-section palettes and lookups", Scale.CELL),
        CELLS("Zone graph: cells, their stored connections and the cell map", Scale.CELL),
        ZONES("Zone graph: zones, mixtures and edges", Scale.ZONE),
        ZONE_SECTIONS("Zone membership: each zone's sections and chunks", Scale.ZONE),
        SNAPSHOT("Published snapshots: zone views and tables", Scale.ZONE),
        UNDO("Undo point: mixture copies, components", Scale.ZONE),
        OTHER("Other engine and pipeline state: classifier and fill scratch, queues, outcomes", Scale.NEITHER);

        final String label;
        final Scale scale;

        Part(String label, Scale scale) {
            this.label = label;
            this.scale = scale;
        }
    }

    private static final sun.misc.Unsafe UNSAFE = unsafe();
    private static final String ENGINE = "io.github.west3436.zas.engine.";

    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    private final EnumMap<Part, Long> bytes = new EnumMap<>(Part.class);
    private final EnumMap<Part, Map<String, long[]>> byClass = new EnumMap<>(Part.class);
    private final Map<Class<?>, Layout> layouts = new HashMap<>();
    private final IdentityHashMap<Object, Boolean> excluded = new IdentityHashMap<>();

    HeapAccounting() {
        for (Part p : Part.values()) {
            bytes.put(p, 0L);
        }
    }

    /** Never walks into this object (a stand-in for the platform). */
    HeapAccounting exclude(Object o) {
        if (o != null) {
            excluded.put(o, Boolean.TRUE);
        }
        return this;
    }

    /** Charges everything newly reachable from {@code root} to {@code part}, or to a finer part. */
    HeapAccounting walk(Object root, Part part) {
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

    private void charge(Part p, Class<?> c, long size) {
        bytes.merge(p, size, Long::sum);
        byClass.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(c.getName(), k -> new long[2]);
        long[] row = byClass.get(p).get(c.getName());
        row[0]++;
        row[1] += size;
    }

    /** The classes taking the most bytes in a part, largest first: "name: count objects, bytes". */
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

    /** Charges what a field of {@code owner} reaches; nothing if the field doesn't exist. */
    HeapAccounting walkField(Object owner, String field, Part part) {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return walk(f.get(owner), part);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return this;
    }

    /** One dimension's atmos heap, as the harness holds it between ticks. */
    static HeapAccounting of(Harness h) {
        HeapAccounting a = new HeapAccounting().exclude(h.world);
        a.walk(h.engine.mirror(), Part.MIRROR);
        a.walkField(h.engine, "membership", Part.MEMBERSHIP);
        a.walkField(h.engine, "undoMembership", Part.MEMBERSHIP);
        a.walk(h.engine.graph(), Part.ZONES);
        a.walk(h.pipeline.published(), Part.SNAPSHOT);
        a.walk(h.engine.lastSnapshot(), Part.SNAPSHOT);
        for (String f : new String[] {"undoMixtures", "undoPinned", "lastComponents", "lastZoneOps"}) {
            a.walkField(h.engine, f, Part.UNDO);
        }
        a.walk(h.engine, Part.OTHER);
        a.walk(h.scheduler, Part.OTHER);
        a.walk(h.pipeline, Part.OTHER);
        return a;
    }

    long bytes(Part p) {
        return bytes.get(p);
    }

    long total() {
        long t = 0;
        for (long b : bytes.values()) {
            t += b;
        }
        return t;
    }

    long total(Scale s) {
        long t = 0;
        for (Part p : Part.values()) {
            if (p.scale == s) {
                t += bytes.get(p);
            }
        }
        return t;
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
        return n.startsWith(ENGINE + "bench.")
                || n.equals(ENGINE + "gas.GasRegistry")
                || n.equals(ENGINE + "gas.GasConfig")
                || n.equals(ENGINE + "GeometryPalette")
                || n.equals(ENGINE + "gas.GasDefinition");
    }

    private static Part refine(Object o, Part p) {
        String n = o.getClass().getName();
        if (n.equals(ENGINE + "zone.Cell")
                || n.equals("[L" + ENGINE + "zone.Cell;")
                || n.equals(ENGINE + "zone.LongObjectMap")) {
            return Part.CELLS; // cells, each zone's cell array and the cell map
        }
        if (n.startsWith(ENGINE + "classify.") || n.startsWith(ENGINE + "scheduler.EnclosureClassifier")) {
            return Part.OTHER; // the classifier and its fill's scratch, however reached (the graph's topology)
        }
        if (n.equals(ENGINE + "zone.ZoneGraph") || n.equals(ENGINE + "zone.Zone")) {
            return Part.ZONES; // however reached (the membership's volume lookup holds the graph)
        }
        if (n.equals(ENGINE + "scheduler.Membership$ZoneSections")) {
            return Part.ZONE_SECTIONS;
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
        // The position tables of a snapshot built without palettes (before #106) are membership.
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
        Layout l = layouts.get(c);
        if (l == null) {
            l = new Layout(c);
            layouts.put(c, l);
        }
        return l;
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
                                continue; // a JDK hidden class: counted, not walked
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
            return 4; // a compressed reference
        }
    }

    private static sun.misc.Unsafe unsafe() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("heap accounting needs sun.misc.Unsafe", e);
        }
    }
}
