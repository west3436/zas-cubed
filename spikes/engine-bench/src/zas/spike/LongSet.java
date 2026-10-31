package zas.spike;

import java.util.Arrays;

/** Open-addressing set of longs. Iteration is by insertion order (CORE-072). */
public final class LongSet {
    private static final long EMPTY = Long.MIN_VALUE;
    private long[] table;
    private long[] order;
    private int size;

    public LongSet(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, expected * 2) - 1) << 1;
        table = new long[cap];
        Arrays.fill(table, EMPTY);
        order = new long[Math.max(16, expected)];
    }

    static int mix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (int) k;
    }

    public boolean add(long k) {
        int mask = table.length - 1;
        int i = mix(k) & mask;
        while (table[i] != EMPTY) {
            if (table[i] == k) {
                return false;
            }
            i = (i + 1) & mask;
        }
        table[i] = k;
        if (size == order.length) {
            order = Arrays.copyOf(order, size * 2);
        }
        order[size++] = k;
        if (size * 2 > table.length) {
            rehash();
        }
        return true;
    }

    public boolean contains(long k) {
        int mask = table.length - 1;
        int i = mix(k) & mask;
        while (table[i] != EMPTY) {
            if (table[i] == k) {
                return true;
            }
            i = (i + 1) & mask;
        }
        return false;
    }

    private void rehash() {
        long[] old = table;
        table = new long[old.length * 2];
        Arrays.fill(table, EMPTY);
        int mask = table.length - 1;
        for (long k : old) {
            if (k != EMPTY) {
                int i = mix(k) & mask;
                while (table[i] != EMPTY) {
                    i = (i + 1) & mask;
                }
                table[i] = k;
            }
        }
    }

    public int size() {
        return size;
    }

    public long get(int i) {
        return order[i];
    }
}
