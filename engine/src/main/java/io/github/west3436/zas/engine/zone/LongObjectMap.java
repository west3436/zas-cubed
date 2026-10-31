package io.github.west3436.zas.engine.zone;

import java.util.Arrays;

/**
 * An open-addressing map from {@code long} to objects, with no boxing. It has no
 * iteration on purpose: nothing may depend on its hash order (architecture §3.5).
 */
final class LongObjectMap<V> {
    private static final float LOAD = 0.5f;

    private long[] keys;
    private Object[] values;
    private int size;
    private int mask;

    LongObjectMap() {
        this(16);
    }

    LongObjectMap(int expected) {
        int capacity = Integer.highestOneBit(Math.max(4, (int) (expected / LOAD)) - 1) << 1;
        keys = new long[capacity];
        values = new Object[capacity];
        mask = capacity - 1;
    }

    int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    V get(long key) {
        int i = slot(key);
        while (values[i] != null) {
            if (keys[i] == key) {
                return (V) values[i];
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    void put(long key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("null value");
        }
        int i = slot(key);
        while (values[i] != null) {
            if (keys[i] == key) {
                values[i] = value;
                return;
            }
            i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = value;
        if (++size > keys.length * LOAD) {
            grow();
        }
    }

    void remove(long key) {
        int i = slot(key);
        while (values[i] != null) {
            if (keys[i] == key) {
                values[i] = null;
                size--;
                // Backward-shift deletion keeps probe chains intact without tombstones.
                int gap = i;
                int j = (i + 1) & mask;
                while (values[j] != null) {
                    int home = slot(keys[j]);
                    if (((j - home) & mask) >= ((j - gap) & mask)) {
                        keys[gap] = keys[j];
                        values[gap] = values[j];
                        values[j] = null;
                        gap = j;
                    }
                    j = (j + 1) & mask;
                }
                return;
            }
            i = (i + 1) & mask;
        }
    }

    void clear() {
        Arrays.fill(values, null);
        size = 0;
    }

    private int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    private void grow() {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        keys = new long[oldKeys.length * 2];
        values = new Object[oldValues.length * 2];
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldValues[i] != null) {
                @SuppressWarnings("unchecked")
                V v = (V) oldValues[i];
                put(oldKeys[i], v);
            }
        }
    }
}
