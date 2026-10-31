package io.github.west3436.zas.engine;

/**
 * Packed keys for 16×16×16 sections and 16×16 chunk columns, and the deterministic order used
 * wherever sections are listed (CORE-044).
 *
 * <p>A section key packs signed section coordinates as 22 bits of x, 22 bits of z and 20 bits of
 * y, from the high bits down. A chunk key packs chunk x in the low 32 bits and chunk z in the high
 * 32 bits. Both layouts match the ones Minecraft uses, so the platform can pass its own keys
 * straight through.
 */
public final class SectionKeys {
    private SectionKeys() {}

    /** The key of the section with section coordinates {@code (x, y, z)}. */
    public static long section(int x, int y, int z) {
        return ((x & 0x3FFFFFL) << 42) | ((z & 0x3FFFFFL) << 20) | (y & 0xFFFFFL);
    }

    /** The key of the section holding block {@code (x, y, z)}. */
    public static long sectionOfBlock(int x, int y, int z) {
        return section(x >> 4, y >> 4, z >> 4);
    }

    public static int x(long section) {
        return (int) (section >> 42);
    }

    public static int y(long section) {
        return (int) (section << 44 >> 44);
    }

    public static int z(long section) {
        return (int) (section << 22 >> 42);
    }

    /** The key of the chunk column with chunk coordinates {@code (x, z)}. */
    public static long chunk(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }

    public static int chunkX(long chunk) {
        return (int) chunk;
    }

    public static int chunkZ(long chunk) {
        return (int) (chunk >>> 32);
    }

    /** The key of the chunk column holding {@code section}. */
    public static long chunkOf(long section) {
        return chunk(x(section), z(section));
    }

    /** The index of local position {@code (x, y, z)}, each 0–15, within a section: y, z, x order. */
    public static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    /** Orders section keys by x, then z, then y, so a chunk column's sections are adjacent. */
    public static int compare(long a, long b) {
        int c = Integer.compare(x(a), x(b));
        if (c == 0) {
            c = Integer.compare(z(a), z(b));
        }
        return c != 0 ? c : Integer.compare(y(a), y(b));
    }
}
