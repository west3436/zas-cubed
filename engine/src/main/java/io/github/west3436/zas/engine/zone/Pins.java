package io.github.west3436.zas.engine.zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The owners of a zone's pins (API-017, D-64), as an immutable list of owner ids, sorted and
 * without repeats. A zone with any owner is pinned. The lists are shared between zones, views and
 * saved shares, so they are never changed in place: each change makes a new list.
 */
public final class Pins {
    /**
     * The owner of a pin that has none: a pin saved by format versions 2 and 3, which stored only
     * whether a zone was pinned, or one set by {@link ZoneGraph#setPinned}.
     */
    public static final String ANONYMOUS = "zas:pinned";

    /** No pins. */
    public static final List<String> NONE = List.of();

    /** The anonymous pin alone. */
    public static final List<String> ANONYMOUS_ONLY = List.of(ANONYMOUS);

    private Pins() {}

    /**
     * Owner ids as a pin list: sorted, without repeats.
     *
     * @throws NullPointerException if an owner is null
     * @throws IllegalArgumentException if an owner is empty
     */
    public static List<String> of(Collection<String> owners) {
        if (owners.isEmpty()) {
            return NONE;
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String o : owners) {
            sorted.add(check(o));
        }
        return List.copyOf(sorted);
    }

    /** A pin list with {@code owner} added; the same list if it already has it. */
    public static List<String> with(List<String> pins, String owner) {
        check(owner);
        if (pins.contains(owner)) {
            return pins;
        }
        List<String> out = new ArrayList<>(pins.size() + 1);
        out.addAll(pins);
        out.add(owner);
        out.sort(null);
        return List.copyOf(out);
    }

    /** A pin list without {@code owner}; the same list if it doesn't have it. */
    public static List<String> without(List<String> pins, String owner) {
        if (!pins.contains(owner)) {
            return pins;
        }
        if (pins.size() == 1) {
            return NONE;
        }
        List<String> out = new ArrayList<>(pins);
        out.remove(owner);
        return List.copyOf(out);
    }

    /** The owners of either list. */
    public static List<String> union(List<String> a, List<String> b) {
        if (b.isEmpty() || a.equals(b)) {
            return a;
        }
        if (a.isEmpty()) {
            return b;
        }
        TreeSet<String> out = new TreeSet<>(a);
        out.addAll(b);
        return out.size() == a.size() ? a : List.copyOf(out);
    }

    private static String check(String owner) {
        if (owner.isEmpty()) {
            throw new IllegalArgumentException("a pin owner can't be empty");
        }
        return owner;
    }
}
