package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import java.util.Arrays;

/**
 * An immutable table of cells named by block position ({@link SectionKeys#block}) and sub-cell
 * index (GEO-004), each with a non-negative int value. The published zone table uses it to list
 * the unzoned cells of regions a held breach left unresolved (GEO-026), which its membership
 * palettes don't hold. Its size is that of the held regions; lookups are binary searches.
 */
final class CellTable {
    static final CellTable EMPTY = new CellTable(new long[0], new byte[0], new int[0]);

    private final long[] blocks; // ascending; a block repeats once per sub-cell listed
    private final byte[] subs; // ascending within a block
    private final int[] values;

    private CellTable(long[] blocks, byte[] subs, int[] values) {
        this.blocks = blocks;
        this.subs = subs;
        this.values = values;
    }

    /** Collects entries in any order. */
    static final class Builder {
        private long[] blocks = new long[16];
        private int[] subs = new int[16];
        private int[] values = new int[16];
        private int size;

        Builder add(long block, int sub, int value) {
            if (sub < 0 || sub > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("sub-cell index out of range: " + sub);
            }
            if (value < 0) {
                throw new IllegalArgumentException("negative value: " + value);
            }
            if (size == blocks.length) {
                blocks = Arrays.copyOf(blocks, size * 2);
                subs = Arrays.copyOf(subs, size * 2);
                values = Arrays.copyOf(values, size * 2);
            }
            blocks[size] = block;
            subs[size] = sub;
            values[size] = value;
            size++;
            return this;
        }

        /** The table, sorted by block then sub-cell; for a cell added twice, the first value wins. */
        CellTable build() {
            if (size == 0) {
                return EMPTY;
            }
            Integer[] order = new Integer[size];
            for (int i = 0; i < size; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> {
                int c = Long.compare(blocks[a], blocks[b]);
                return c != 0 ? c : Integer.compare(subs[a], subs[b]);
            });
            long[] bs = new long[size];
            byte[] ss = new byte[size];
            int[] vs = new int[size];
            int n = 0;
            for (int k = 0; k < size; k++) {
                int i = order[k];
                if (n > 0 && bs[n - 1] == blocks[i] && ss[n - 1] == subs[i]) {
                    continue;
                }
                bs[n] = blocks[i];
                ss[n] = (byte) subs[i];
                vs[n] = values[i];
                n++;
            }
            return new CellTable(Arrays.copyOf(bs, n), Arrays.copyOf(ss, n), Arrays.copyOf(vs, n));
        }
    }

    /** The number of cells. */
    int size() {
        return blocks.length;
    }

    /** Whether any cell of a block position is listed. */
    boolean hasBlock(long block) {
        return first(block) >= 0;
    }

    /**
     * The value of cell {@code sub} of a block position, or -1 if it isn't listed. A negative
     * {@code sub} matches any cell of the position, giving the first listed.
     */
    int get(long block, int sub) {
        int i = first(block);
        if (i < 0) {
            return -1;
        }
        if (sub < 0) {
            return values[i];
        }
        for (; i < blocks.length && blocks[i] == block; i++) {
            if (subs[i] == sub) {
                return values[i];
            }
        }
        return -1;
    }

    /** Whether cell {@code sub} of a block position is listed; a negative {@code sub}, any of its cells. */
    boolean contains(long block, int sub) {
        return get(block, sub) >= 0;
    }

    /** The index of the first entry of a block position, or -1. */
    private int first(long block) {
        int i = Arrays.binarySearch(blocks, block);
        if (i < 0) {
            return -1;
        }
        while (i > 0 && blocks[i - 1] == block) {
            i--;
        }
        return i;
    }
}
