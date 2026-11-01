package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.ChunkGenerations;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.DirtySections;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.SectionSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * A headless server level for the benchmarks: geometry ids per section, the platform's change
 * tracking ({@link DirtySections} and {@link ChunkGenerations}, as {@code ChangeTracker} uses
 * them), and a square of loaded chunks. Unlike the test worlds it does the same per-change work as
 * the change hook, so the locality-filter bench (GEO-011) measures the real structures.
 */
final class BenchWorld implements DimensionPipeline.World {
    static final int AIR = GeometryPalette.AIR_ID;
    static final int SOLID = GeometryPalette.SOLID_ID;
    /**
     * An open door in a wall running north to south: gas passes, and its east face is a zone
     * boundary, so it joins the room west of it and the rooms stay separate zones sharing an edge
     * (GEO-007).
     */
    static final int DOOR_EAST;
    /** {@link #DOOR_EAST} closed: its east face sealed, the same faces a boundary (GEO-044). */
    static final int DOOR_EAST_CLOSED;
    /** An open door in a wall running east to west, joining the room north of it. */
    static final int DOOR_SOUTH;

    static final GeometryPalette PALETTE;
    static final int MIN_SECTION_Y = -4;
    static final int MAX_SECTION_Y = 19;

    static {
        GeometryPalette.Builder b = GeometryPalette.builder();
        int east = Geometry.boundaryFlag(Face.EAST);
        DOOR_EAST = b.intern(Geometry.declared(Set.of(), Geometry.FULL_VOLUME, east));
        DOOR_EAST_CLOSED = b.intern(Geometry.declared(Set.of(Face.EAST), Geometry.FULL_VOLUME, east));
        DOOR_SOUTH = b.intern(Geometry.declared(Set.of(), Geometry.FULL_VOLUME, Geometry.boundaryFlag(Face.SOUTH)));
        PALETTE = b.build();
    }

    private final Map<Long, short[]> sections = new HashMap<>();
    private final DirtySections dirty = new DirtySections();
    private final ChunkGenerations generations = new ChunkGenerations();
    private final Set<Long> loaded = new HashSet<>();
    private final int background;
    final Ambient ambient;
    /** Sections captured at batch close. */
    long captures;

    BenchWorld(Ambient ambient, int background) {
        this.ambient = ambient;
        this.background = background;
    }

    /** Loads every chunk with chunk x in [x0, x1] and z in [z0, z1]. */
    BenchWorld loadChunks(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                loadChunk(SectionKeys.chunk(x, z));
            }
        }
        return this;
    }

    void loadChunk(long chunk) {
        if (loaded.add(chunk)) {
            generations.load(chunk);
        }
    }

    int loadedChunks() {
        return loaded.size();
    }

    /** Sets a block, doing the change hook's work if the geometry id changes. */
    BenchWorld set(int x, int y, int z, int id) {
        long key = SectionKeys.sectionOfBlock(x, y, z);
        short[] a = sections.get(key);
        if (a == null) {
            a = new short[SectionSnapshot.SIZE];
            Arrays.fill(a, (short) background);
            sections.put(key, a);
        }
        int index = SectionKeys.index(x & 15, y & 15, z & 15);
        if (a[index] != id) {
            a[index] = (short) id;
            hook(x, y, z);
        }
        return this;
    }

    /**
     * Only the change hook's per-write work, as {@code ChangeTracker} does it for a write whose
     * geometry id changed: a block generation and a dirty bit. For changes to sections nothing ever
     * captures, so storing the block is beside the point.
     */
    void hook(int x, int y, int z) {
        generations.bumpBlock(SectionKeys.chunk(x >> 4, z >> 4));
        dirty.mark(x, y, z);
    }

    BenchWorld box(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, y, z, id);
                }
            }
        }
        return this;
    }

    /** A sealed room: solid walls one block thick around the interior box, which is air. */
    BenchWorld room(int x0, int y0, int z0, int x1, int y1, int z1) {
        box(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, SOLID);
        return box(x0, y0, z0, x1, y1, z1, AIR);
    }

    /** Forgets every pending change, as if they had been drained before the bench began. */
    void clearDirty() {
        dirty.clear();
    }

    int dirtySections() {
        return dirty.sectionCount();
    }

    /** The world's sections as snapshots, for fills that run without the pipeline. */
    SectionSnapshot snapshot(long key) {
        short[] a = sections.get(key);
        return a == null ? SectionSnapshot.uniform(key, 1, 1, background) : SectionSnapshot.of(key, 1, 1, a);
    }

    @Override
    public long[] drainDirty(LongPredicate interest) {
        List<DirtySection> drained = dirty.drain(interest);
        long[] keys = new long[drained.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = drained.get(i).key();
        }
        return keys;
    }

    @Override
    public boolean isLoaded(long chunk) {
        return loaded.contains(chunk);
    }

    @Override
    public SectionSource capture(long section) {
        int y = SectionKeys.y(section);
        long chunk = SectionKeys.chunkOf(section);
        if (!isLoaded(chunk) || y < MIN_SECTION_Y || y > MAX_SECTION_Y) {
            return null;
        }
        captures++;
        long life = generations.lifecycle(chunk);
        long block = generations.block(chunk);
        short[] a = sections.get(section);
        // SectionSnapshot.of copies the array on this thread, standing in for the platform's copy
        // of the chunk's palette container (spike S5: 0.3 µs per section in game).
        SectionSnapshot s = a == null
                ? SectionSnapshot.uniform(section, life, block, background)
                : SectionSnapshot.of(section, life, block, a);
        return SectionSource.of(s);
    }

    @Override
    public long lifecycleGeneration(long chunk) {
        return generations.lifecycle(chunk);
    }

    @Override
    public Ambient ambient() {
        return ambient;
    }
}
