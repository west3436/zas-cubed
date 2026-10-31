package zas.spike;

/**
 * The engine's snapshot of block geometry: one decoded state array per
 * 16x16x16 section. Sections that are absent are unloaded (GEO-024).
 */
public final class World {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;

    public final LongMap<short[]> sections = new LongMap<>(1024);

    public short[] sectionAt(int x, int y, int z) {
        return sections.get(Pos.section(x, y, z));
    }

    public short[] ensureSection(int x, int y, int z) {
        long k = Pos.section(x, y, z);
        short[] s = sections.get(k);
        if (s == null) {
            s = new short[4096];
            sections.put(k, s);
        }
        return s;
    }

    public void set(int x, int y, int z, short state) {
        ensureSection(x, y, z)[Pos.index(x, y, z)] = state;
    }

    public short get(int x, int y, int z) {
        short[] s = sectionAt(x, y, z);
        return s == null ? Shapes.STONE : s[Pos.index(x, y, z)];
    }

    /** Loads (creates) every section in the box, so fills see them as loaded. */
    public void load(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0 & ~15; x <= x1; x += 16) {
            for (int y = y0 & ~15; y <= y1; y += 16) {
                for (int z = z0 & ~15; z <= z1; z += 16) {
                    ensureSection(x, y, z);
                }
            }
        }
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, short state) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** A hollow box: shell of `wall`, inside air. Inside is (x0+1..x1-1) etc. */
    public void room(int x0, int y0, int z0, int x1, int y1, int z1, short wall) {
        fill(x0, y0, z0, x1, y1, z1, wall);
        fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, Shapes.AIR);
    }
}
