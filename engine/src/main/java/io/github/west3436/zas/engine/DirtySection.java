package io.github.west3436.zas.engine;

import java.util.function.IntConsumer;

/**
 * One section's dirty positions, as drained from {@link DirtySections}: a 4096-bit set indexed by
 * {@link SectionKeys#index(int, int, int)}. Immutable once drained.
 */
public final class DirtySection {
    private final long key;
    private final long[] bits;
    private final int count;

    DirtySection(long key, long[] bits) {
        this.key = key;
        this.bits = bits;
        int n = 0;
        for (long w : bits) {
            n += Long.bitCount(w);
        }
        this.count = n;
    }

    /** The section key ({@link SectionKeys}). */
    public long key() {
        return key;
    }

    public int sectionX() {
        return SectionKeys.x(key);
    }

    public int sectionY() {
        return SectionKeys.y(key);
    }

    public int sectionZ() {
        return SectionKeys.z(key);
    }

    /** The number of dirty positions. */
    public int count() {
        return count;
    }

    /** Whether the position with section index {@code index} is dirty. */
    public boolean isDirty(int index) {
        return (bits[index >>> 6] & (1L << index)) != 0;
    }

    /** Whether local position {@code (x, y, z)}, each 0–15, is dirty. */
    public boolean isDirty(int x, int y, int z) {
        return isDirty(SectionKeys.index(x, y, z));
    }

    /** Calls {@code action} with each dirty section index, in ascending order. */
    public void forEachIndex(IntConsumer action) {
        for (int w = 0; w < bits.length; w++) {
            long word = bits[w];
            while (word != 0) {
                int bit = Long.numberOfTrailingZeros(word);
                action.accept((w << 6) | bit);
                word &= word - 1;
            }
        }
    }

    @Override
    public String toString() {
        return "DirtySection[" + sectionX() + ", " + sectionY() + ", " + sectionZ() + "; " + count + " dirty]";
    }
}
