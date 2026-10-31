package zas.spike;

import java.util.Arrays;

/** Open-addressing long-to-object map, no removal. Iteration is by insertion order. */
public final class LongMap<V> {
    private static final long EMPTY = Long.MIN_VALUE;
    private long[] keys;
    private Object[] vals;
    private long[] order;
    private int size;

    public LongMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, expected * 2) - 1) << 1;
        keys = new long[cap];
        Arrays.fill(keys, EMPTY);
        vals = new Object[cap];
        order = new long[Math.max(16, expected)];
    }

    @SuppressWarnings("unchecked")
    public V get(long k) {
        int mask = keys.length - 1;
        int i = LongSet.mix(k) & mask;
        while (keys[i] != EMPTY) {
            if (keys[i] == k) {
                return (V) vals[i];
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    public void put(long k, V v) {
        int mask = keys.length - 1;
        int i = LongSet.mix(k) & mask;
        while (keys[i] != EMPTY) {
            if (keys[i] == k) {
                vals[i] = v;
                return;
            }
            i = (i + 1) & mask;
        }
        keys[i] = k;
        vals[i] = v;
        if (size == order.length) {
            order = Arrays.copyOf(order, size * 2);
        }
        order[size++] = k;
        if (size * 2 > keys.length) {
            rehash();
        }
    }

    private void rehash() {
        long[] ok = keys;
        Object[] ov = vals;
        keys = new long[ok.length * 2];
        Arrays.fill(keys, EMPTY);
        vals = new Object[ok.length * 2];
        int mask = keys.length - 1;
        for (int j = 0; j < ok.length; j++) {
            if (ok[j] != EMPTY) {
                int i = LongSet.mix(ok[j]) & mask;
                while (keys[i] != EMPTY) {
                    i = (i + 1) & mask;
                }
                keys[i] = ok[j];
                vals[i] = ov[j];
            }
        }
    }

    public int size() {
        return size;
    }

    public long keyAt(int i) {
        return order[i];
    }
}
