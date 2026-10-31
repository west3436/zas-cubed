package io.github.west3436.zas.engine;

import java.util.Arrays;

/**
 * An immutable 16×16 bit mask over one face of a block position. Bit {@code (u, v)} stands for
 * the 1/16 × 1/16 square at that position on the face; the axes are given in {@link Face}.
 */
public final class FaceMask {
    /** The number of bits on a side. */
    public static final int SIZE = 16;

    /** The number of bits in a mask. */
    public static final int BITS = SIZE * SIZE;

    public static final FaceMask EMPTY = new FaceMask(new long[4]);
    public static final FaceMask FULL = new FaceMask(new long[] {-1L, -1L, -1L, -1L});

    private final long[] words;

    private FaceMask(long[] words) {
        this.words = words;
    }

    /** A mask from four words, bit {@code v * 16 + u} of the concatenated words being {@code (u, v)}. */
    public static FaceMask of(long w0, long w1, long w2, long w3) {
        return canonical(new long[] {w0, w1, w2, w3});
    }

    /** The mask with bit {@code (u, v)} set wherever {@code u0 <= u < u1} and {@code v0 <= v < v1}. */
    public static FaceMask rect(int u0, int v0, int u1, int v1) {
        long[] w = new long[4];
        for (int v = v0; v < v1; v++) {
            for (int u = u0; u < u1; u++) {
                int bit = index(u, v);
                w[bit >>> 6] |= 1L << bit;
            }
        }
        return canonical(w);
    }

    /** The bit index of {@code (u, v)}: {@code v * 16 + u}. */
    public static int index(int u, int v) {
        if (u < 0 || u >= SIZE || v < 0 || v >= SIZE) {
            throw new IndexOutOfBoundsException("(" + u + ", " + v + ")");
        }
        return v * SIZE + u;
    }

    static FaceMask canonical(long[] w) {
        if ((w[0] | w[1] | w[2] | w[3]) == 0) {
            return EMPTY;
        }
        if ((w[0] & w[1] & w[2] & w[3]) == -1L) {
            return FULL;
        }
        return new FaceMask(w);
    }

    public boolean get(int u, int v) {
        int bit = index(u, v);
        return (words[bit >>> 6] & (1L << bit)) != 0;
    }

    /** Word {@code i} (0 to 3) of the mask. */
    public long word(int i) {
        return words[i];
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public boolean isFull() {
        return this == FULL;
    }

    /** The number of set bits. */
    public int cardinality() {
        return Long.bitCount(words[0]) + Long.bitCount(words[1]) + Long.bitCount(words[2]) + Long.bitCount(words[3]);
    }

    public FaceMask or(FaceMask o) {
        return canonical(
                new long[] {words[0] | o.words[0], words[1] | o.words[1], words[2] | o.words[2], words[3] | o.words[3]
                });
    }

    public FaceMask and(FaceMask o) {
        return canonical(
                new long[] {words[0] & o.words[0], words[1] & o.words[1], words[2] & o.words[2], words[3] & o.words[3]
                });
    }

    public FaceMask not() {
        return canonical(new long[] {~words[0], ~words[1], ~words[2], ~words[3]});
    }

    /** Whether this and {@code o} share a set bit. */
    public boolean intersects(FaceMask o) {
        return (words[0] & o.words[0]) != 0
                || (words[1] & o.words[1]) != 0
                || (words[2] & o.words[2]) != 0
                || (words[3] & o.words[3]) != 0;
    }

    /** Whether {@code a | b} covers the whole face, without allocating. */
    public static boolean unionIsFull(FaceMask a, FaceMask b) {
        return ((a.words[0] | b.words[0])
                        & (a.words[1] | b.words[1])
                        & (a.words[2] | b.words[2])
                        & (a.words[3] | b.words[3]))
                == -1L;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FaceMask m && Arrays.equals(words, m.words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        return isEmpty() ? "FaceMask[empty]" : isFull() ? "FaceMask[full]" : "FaceMask[" + cardinality() + " bits]";
    }
}
