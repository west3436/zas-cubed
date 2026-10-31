package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongPredicate;

/**
 * A mutable world of geometry ids standing in for a server level: air by default, with blocks
 * set one by one, dirty-section tracking, chunk loading and lifecycle generations. Hash
 * collections are used for lookups only; drains are sorted.
 */
final class TestWorld implements DimensionPipeline.World {
    static final int AIR = GeometryPalette.AIR_ID;
    static final int SOLID = GeometryPalette.SOLID_ID;
    static final GeometryPalette PALETTE = GeometryPalette.builder().build();
    static final int MIN_SECTION_Y = -4;
    static final int MAX_SECTION_Y = 19;

    private final Map<Long, short[]> sections = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();
    private final Set<Long> unloaded = new HashSet<>();
    private final Map<Long, Long> generations = new HashMap<>();
    private long stamp;
    private final int background;
    Ambient ambient;
    int captures;

    TestWorld(Ambient ambient) {
        this(ambient, AIR);
    }

    TestWorld(Ambient ambient, int background) {
        this.ambient = ambient;
        this.background = background;
    }

    TestWorld set(int x, int y, int z, int id) {
        long key = SectionKeys.sectionOfBlock(x, y, z);
        short[] a = sections.computeIfAbsent(key, k -> {
            short[] s = new short[SectionSnapshot.SIZE];
            Arrays.fill(s, (short) background);
            return s;
        });
        int index = SectionKeys.index(x & 15, y & 15, z & 15);
        if (a[index] != id) {
            a[index] = (short) id;
            dirty.add(key);
        }
        return this;
    }

    TestWorld box(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
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
    TestWorld room(int x0, int y0, int z0, int x1, int y1, int z1) {
        box(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, SOLID);
        return box(x0, y0, z0, x1, y1, z1, AIR);
    }

    void unload(long chunk) {
        unloaded.add(chunk);
        generations.remove(chunk);
    }

    void load(long chunk) {
        unloaded.remove(chunk);
    }

    @Override
    public long[] drainDirty(LongPredicate interest) {
        TreeSet<Long> out = new TreeSet<>(SectionKeys::compare);
        for (long k : dirty) {
            if (interest.test(k) && !unloaded.contains(SectionKeys.chunkOf(k))) {
                out.add(k);
            }
        }
        dirty.clear();
        long[] keys = new long[out.size()];
        int i = 0;
        for (long k : out) {
            keys[i++] = k;
        }
        return keys;
    }

    @Override
    public boolean isLoaded(long chunk) {
        return !unloaded.contains(chunk);
    }

    @Override
    public SectionSource capture(long section) {
        int y = SectionKeys.y(section);
        if (!isLoaded(SectionKeys.chunkOf(section)) || y < MIN_SECTION_Y || y > MAX_SECTION_Y) {
            return null;
        }
        captures++;
        long chunk = SectionKeys.chunkOf(section);
        long gen = lifecycleGeneration(chunk);
        short[] a = sections.get(section);
        SectionSnapshot s = a == null
                ? SectionSnapshot.uniform(section, gen, gen, background)
                : SectionSnapshot.of(section, gen, gen, a);
        return SectionSource.of(s);
    }

    @Override
    public long lifecycleGeneration(long chunk) {
        if (unloaded.contains(chunk)) {
            return 0;
        }
        return generations.computeIfAbsent(chunk, c -> ++stamp);
    }

    @Override
    public Ambient ambient() {
        return ambient;
    }
}
