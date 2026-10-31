package io.github.west3436.zas.engine.classify;

import io.github.west3436.zas.engine.SectionKeys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * Classifications the worker has not finished, and the operations waiting on them (GEO-028;
 * architecture §4.3). An operation that needs a zone at an unzoned cell, such as gas added in a
 * new sealed room, is {@link #hold held} under that cell until a fill decides the cell's region.
 * Nothing is dropped or reordered: the operations come back in arrival order from {@link
 * #complete}, and the caller applies them to the zone that formed, or sends them to the ambient
 * if the region was open (CORE-053).
 *
 * <p>An entry is either <em>runnable</em>, to be classified in the next tick, or
 * <em>waiting</em> on sections its last fill reached but the worker didn't have (GEO-024). A
 * waiting entry becomes runnable again when they all {@linkplain #sectionsArrived arrive}.
 *
 * <p>Deterministic: entries are kept in the order they were created. Single-threaded, owned by
 * the worker.
 *
 * @param <T> the caller's operation type
 */
public final class PendingClassifications<T> {
    private static final long[] NONE = new long[0];

    private final LinkedHashMap<Long, Entry<T>> entries = new LinkedHashMap<>();

    /** One seed cell waiting for classification, and the operations held on it. */
    public static final class Entry<T> {
        private final long seed;
        private final List<T> ops = new ArrayList<>();
        private final List<T> opsView = Collections.unmodifiableList(ops);
        private long[] missing = NONE;

        private Entry(long seed) {
            this.seed = seed;
        }

        /** The cell to fill from. */
        public long seed() {
            return seed;
        }

        /** The held operations, in arrival order. A read-only view. */
        public List<T> ops() {
            return opsView;
        }

        /** Whether the entry waits on sections. */
        public boolean isWaiting() {
            return missing.length > 0;
        }

        /** The sections it waits on, sorted by {@link SectionKeys#compare}; empty if runnable. */
        public long[] missingSections() {
            return missing.clone();
        }

        @Override
        public String toString() {
            return "Pending[" + CellIds.toString(seed) + ", " + ops.size() + " ops"
                    + (isWaiting() ? ", waiting on " + missing.length + " sections" : "") + "]";
        }
    }

    /**
     * Holds an operation until the classification of {@code seed} completes, creating a runnable
     * entry for it if there is none.
     *
     * @return the entry
     */
    public Entry<T> hold(long seed, T op) {
        Entry<T> e = request(seed);
        e.ops.add(op);
        return e;
    }

    /** The entry for {@code seed}, created runnable and with no operations if there is none. */
    public Entry<T> request(long seed) {
        return entries.computeIfAbsent(seed, Entry::new);
    }

    /** The entry for {@code seed}, or null. */
    public Entry<T> entry(long seed) {
        return entries.get(seed);
    }

    /** Whether operations at {@code seed} must wait: it has an entry. */
    public boolean isPending(long seed) {
        return entries.containsKey(seed);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Every entry, in creation order. */
    public List<Entry<T>> entries() {
        return new ArrayList<>(entries.values());
    }

    /** The entries to classify now, in creation order. */
    public List<Entry<T>> runnable() {
        List<Entry<T>> out = new ArrayList<>();
        for (Entry<T> e : entries.values()) {
            if (!e.isWaiting()) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Makes an entry wait for sections, as an {@link FillResult.Unresolved} result lists them.
     *
     * @throws IllegalArgumentException if the list is empty or the entry isn't held here
     */
    public void await(Entry<T> entry, long[] missingSections) {
        requireHeld(entry);
        if (missingSections.length == 0) {
            throw new IllegalArgumentException("an entry must wait on at least one section");
        }
        entry.missing = sortDistinct(missingSections.clone());
    }

    /**
     * Makes runnable every waiting entry whose missing sections are now all available.
     *
     * @param available whether the worker has a section now
     * @return the entries that became runnable, in creation order
     */
    public List<Entry<T>> sectionsArrived(LongPredicate available) {
        List<Entry<T>> out = new ArrayList<>();
        for (Entry<T> e : entries.values()) {
            if (!e.isWaiting()) {
                continue;
            }
            boolean all = true;
            for (long s : e.missing) {
                if (!available.test(s)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                e.missing = NONE;
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Every section some waiting entry needs, sorted by {@link SectionKeys#compare} without
     * duplicates: what the worker asks the server thread for, or waits for to load.
     */
    public long[] missingSections() {
        int n = 0;
        for (Entry<T> e : entries.values()) {
            n += e.missing.length;
        }
        long[] all = new long[n];
        int i = 0;
        for (Entry<T> e : entries.values()) {
            System.arraycopy(e.missing, 0, all, i, e.missing.length);
            i += e.missing.length;
        }
        return sortDistinct(all);
    }

    /**
     * Ends an entry's wait: removes it and returns its operations in arrival order, to apply now.
     *
     * @throws IllegalArgumentException if the entry isn't held here
     */
    public List<T> complete(Entry<T> entry) {
        requireHeld(entry);
        entries.remove(entry.seed);
        return new ArrayList<>(entry.ops);
    }

    /**
     * Removes every entry whose seed lies in an enclosed region, so their operations can be applied
     * to the zones that formed from it, without classifying each seed again.
     *
     * @return the removed entries, in creation order
     */
    public List<Entry<T>> takeCovered(FillResult.Enclosed region) {
        List<Entry<T>> out = new ArrayList<>();
        for (Iterator<Entry<T>> it = entries.values().iterator(); it.hasNext(); ) {
            Entry<T> e = it.next();
            if (region.contains(e.seed)) {
                out.add(e);
                it.remove();
            }
        }
        return out;
    }

    private void requireHeld(Entry<T> entry) {
        if (entries.get(entry.seed) != entry) {
            throw new IllegalArgumentException(entry + " is not held here");
        }
    }

    /** Sorts by {@link SectionKeys#compare} and drops duplicates, in place; returns the distinct prefix. */
    private static long[] sortDistinct(long[] sections) {
        Long[] boxed = new Long[sections.length];
        for (int i = 0; i < sections.length; i++) {
            boxed[i] = sections[i];
        }
        Arrays.sort(boxed, SectionKeys::compare);
        int n = 0;
        for (Long s : boxed) {
            if (n == 0 || sections[n - 1] != s) {
                sections[n++] = s;
            }
        }
        return n == sections.length ? sections : Arrays.copyOf(sections, n);
    }
}
