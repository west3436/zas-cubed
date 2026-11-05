package io.github.west3436.zas.engine;

import java.util.Arrays;
import java.util.function.ToIntFunction;

/**
 * An immutable copy of one section's geometry ids, with the generations of its chunk at the time
 * of the copy (architecture §2.3). A section that holds one geometry throughout is stored as that
 * id alone.
 *
 * <p><b>Overrides.</b> A position whose geometry isn't its block state's (a position-dependent
 * shape, GEO-043, or a seal provider's answer, API-023) comes as an override: the geometry itself,
 * since it has no id until the engine gives it one. {@link #resolved} is the snapshot the engine
 * keeps, with those positions' ids in place and no overrides left. {@link #geometryId} of a position
 * with an override not yet resolved gives its block state's id.
 */
public final class SectionSnapshot {
    /** The number of positions in a section. */
    public static final int SIZE = 4096;

    private final long key;
    private final long lifecycleGeneration;
    private final long blockGeneration;
    private final short[] ids; // null when uniform
    private final int uniformId;
    private final int[] overrideIndices; // ascending
    private final Geometry[] overrides;

    private static final int[] NO_INDICES = new int[0];
    private static final Geometry[] NO_OVERRIDES = new Geometry[0];

    private SectionSnapshot(
            long key,
            long lifecycleGeneration,
            long blockGeneration,
            short[] ids,
            int uniformId,
            int[] overrideIndices,
            Geometry[] overrides) {
        this.key = key;
        this.lifecycleGeneration = lifecycleGeneration;
        this.blockGeneration = blockGeneration;
        this.ids = ids;
        this.uniformId = uniformId;
        this.overrideIndices = overrideIndices;
        this.overrides = overrides;
    }

    /**
     * A snapshot from {@link #SIZE} geometry ids indexed by {@link SectionKeys#index(int, int,
     * int)}, each 0–65535 stored as an unsigned short. The array is copied.
     */
    public static SectionSnapshot of(long key, long lifecycleGeneration, long blockGeneration, short[] ids) {
        if (ids.length != SIZE) {
            throw new IllegalArgumentException("expected " + SIZE + " ids, got " + ids.length);
        }
        short first = ids[0];
        for (short id : ids) {
            if (id != first) {
                return new SectionSnapshot(
                        key, lifecycleGeneration, blockGeneration, ids.clone(), -1, NO_INDICES, NO_OVERRIDES);
            }
        }
        return uniform(key, lifecycleGeneration, blockGeneration, Short.toUnsignedInt(first));
    }

    /** A snapshot of a section holding geometry {@code id} at every position. */
    public static SectionSnapshot uniform(long key, long lifecycleGeneration, long blockGeneration, int id) {
        if (id < 0 || id > 0xFFFF) {
            throw new IllegalArgumentException("geometry id out of range: " + id);
        }
        return new SectionSnapshot(key, lifecycleGeneration, blockGeneration, null, id, NO_INDICES, NO_OVERRIDES);
    }

    /**
     * A snapshot from {@link #SIZE} geometry ids and the geometries of single positions. The arrays
     * are copied.
     *
     * @param overrideIndices section indices, ascending, without duplicates
     * @param overrides the geometry at each of them
     */
    public static SectionSnapshot withOverrides(
            long key,
            long lifecycleGeneration,
            long blockGeneration,
            short[] ids,
            int[] overrideIndices,
            Geometry[] overrides) {
        if (ids.length != SIZE) {
            throw new IllegalArgumentException("expected " + SIZE + " ids, got " + ids.length);
        }
        if (overrideIndices.length != overrides.length) {
            throw new IllegalArgumentException(
                    overrideIndices.length + " override positions, " + overrides.length + " geometries");
        }
        if (overrides.length == 0) {
            return of(key, lifecycleGeneration, blockGeneration, ids);
        }
        return new SectionSnapshot(
                key, lifecycleGeneration, blockGeneration, ids.clone(), -1, overrideIndices.clone(), overrides.clone());
    }

    /** Whether any position's geometry comes as an override that isn't resolved yet. */
    public boolean hasOverrides() {
        return overrides.length > 0;
    }

    /**
     * This snapshot with each override's id in place.
     *
     * @param intern gives a geometry's id, or -1 if it can't have one, when its position keeps its
     *     block state's id
     * @return a snapshot with no overrides; this one if it has none
     */
    public SectionSnapshot resolved(ToIntFunction<Geometry> intern) {
        if (overrides.length == 0) {
            return this;
        }
        short[] out = ids.clone();
        for (int i = 0; i < overrides.length; i++) {
            int id = intern.applyAsInt(overrides[i]);
            if (id >= 0) {
                out[overrideIndices[i]] = (short) id;
            }
        }
        return of(key, lifecycleGeneration, blockGeneration, out);
    }

    /** The section key ({@link SectionKeys}). */
    public long key() {
        return key;
    }

    /** The lifecycle generation of the section's chunk ({@link ChunkGenerations}). */
    public long lifecycleGeneration() {
        return lifecycleGeneration;
    }

    /** The block generation of the section's chunk ({@link ChunkGenerations}). */
    public long blockGeneration() {
        return blockGeneration;
    }

    /** Whether every position has the same geometry. */
    public boolean isUniform() {
        return ids == null;
    }

    /** The geometry id at section index {@code index}. */
    public int geometryId(int index) {
        return ids == null ? uniformId : Short.toUnsignedInt(ids[index]);
    }

    /** The geometry id at local position {@code (x, y, z)}, each 0–15. */
    public int geometryId(int x, int y, int z) {
        return geometryId(SectionKeys.index(x, y, z));
    }

    /** A copy of all {@link #SIZE} geometry ids, as unsigned shorts. */
    public short[] geometryIds() {
        if (ids != null) {
            return ids.clone();
        }
        short[] out = new short[SIZE];
        Arrays.fill(out, (short) uniformId);
        return out;
    }
}
