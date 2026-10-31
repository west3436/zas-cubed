package io.github.west3436.zas.engine;

import java.util.Arrays;

/**
 * An immutable copy of one section's geometry ids, with the generations of its chunk at the time
 * of the copy (architecture §2.3). A section that holds one geometry throughout is stored as that
 * id alone.
 *
 * <p>Per-position overrides (position-dependent shapes, seal providers) are not carried yet; they
 * arrive with GEO-043 and API-028.
 */
public final class SectionSnapshot {
    /** The number of positions in a section. */
    public static final int SIZE = 4096;

    private final long key;
    private final long lifecycleGeneration;
    private final long blockGeneration;
    private final short[] ids; // null when uniform
    private final int uniformId;

    private SectionSnapshot(long key, long lifecycleGeneration, long blockGeneration, short[] ids, int uniformId) {
        this.key = key;
        this.lifecycleGeneration = lifecycleGeneration;
        this.blockGeneration = blockGeneration;
        this.ids = ids;
        this.uniformId = uniformId;
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
                return new SectionSnapshot(key, lifecycleGeneration, blockGeneration, ids.clone(), -1);
            }
        }
        return uniform(key, lifecycleGeneration, blockGeneration, Short.toUnsignedInt(first));
    }

    /** A snapshot of a section holding geometry {@code id} at every position. */
    public static SectionSnapshot uniform(long key, long lifecycleGeneration, long blockGeneration, int id) {
        if (id < 0 || id > 0xFFFF) {
            throw new IllegalArgumentException("geometry id out of range: " + id);
        }
        return new SectionSnapshot(key, lifecycleGeneration, blockGeneration, null, id);
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
