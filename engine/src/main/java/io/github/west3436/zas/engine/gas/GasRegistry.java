package io.github.west3436.zas.engine.gas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An immutable set of gas definitions with dense indices (CORE-010), and the gas tags that group
 * them (CORE-012).
 *
 * <p>Gases are ordered by id, so index {@code i} names the same gas for the
 * same set of definitions however they were loaded. {@link GasMixture} stores
 * moles in arrays indexed this way. Indices are only meaningful within one
 * registry: a different set of definitions can renumber them.
 *
 * <p>A tag is an id, {@code namespace:path} like a gas id, naming a set of this registry's gases,
 * such as {@code zas:toxic}. Datapacks define them; the registry holds them resolved.
 */
public final class GasRegistry {
    /** A registry with no gases. */
    public static final GasRegistry EMPTY = new GasRegistry(List.of(), Map.of());

    private final List<GasDefinition> definitions;
    private final Map<String, Integer> indexById;
    private final double[] molarHeatCapacities;
    /** Tag id to its gas ids, both sorted. */
    private final Map<String, List<String>> tags;
    /** Tag id to membership by gas index. Lookup only; never iterated (architecture §3.5). */
    private final Map<String, boolean[]> membership;
    /** Per gas index, the ids of its tags, sorted. */
    private final List<List<String>> tagsByGas;

    private GasRegistry(List<GasDefinition> sorted, Map<String, List<String>> sortedTags) {
        this.definitions = List.copyOf(sorted);
        // Lookup only; never iterated (architecture §3.5).
        this.indexById = new HashMap<>();
        this.molarHeatCapacities = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            indexById.put(sorted.get(i).id(), i);
            molarHeatCapacities[i] = sorted.get(i).molarHeatCapacity();
        }
        this.tags = Collections.unmodifiableMap(sortedTags);
        this.membership = new HashMap<>();
        List<List<String>> byGas = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            byGas.add(new ArrayList<>());
        }
        sortedTags.forEach((tag, gases) -> {
            boolean[] in = new boolean[sorted.size()];
            for (String gas : gases) {
                int index = indexById.get(gas);
                in[index] = true;
                byGas.get(index).add(tag); // tags are visited in sorted order
            }
            membership.put(tag, in);
        });
        this.tagsByGas = byGas.stream().map(List::copyOf).toList();
    }

    /**
     * Builds a registry without tags from definitions in any order.
     *
     * @throws IllegalArgumentException if two definitions share an id
     */
    public static GasRegistry of(Collection<GasDefinition> definitions) {
        return of(definitions, Map.of());
    }

    /**
     * Builds a registry from definitions and tags in any order.
     *
     * @param definitions the gases
     * @param tags each tag's id and the ids of its gases; a tag may be empty
     * @throws IllegalArgumentException if two definitions share an id, a tag id isn't {@code
     *     namespace:path}, or a tag names a gas that isn't among the definitions
     */
    public static GasRegistry of(
            Collection<GasDefinition> definitions, Map<String, ? extends Collection<String>> tags) {
        List<GasDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparing(GasDefinition::id));
        TreeSet<String> ids = new TreeSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && sorted.get(i).id().equals(sorted.get(i - 1).id())) {
                throw new IllegalArgumentException(
                        "duplicate gas id " + sorted.get(i).id());
            }
            ids.add(sorted.get(i).id());
        }
        Map<String, List<String>> sortedTags = new TreeMap<>();
        tags.forEach((tag, gases) -> {
            if (!GasDefinition.isValidId(tag)) {
                throw new IllegalArgumentException("gas tag id must be namespace:path, got '" + tag + "'");
            }
            TreeSet<String> members = new TreeSet<>(gases);
            for (String gas : members) {
                if (!ids.contains(gas)) {
                    throw new IllegalArgumentException("gas tag " + tag + " names " + gas + ", which isn't defined");
                }
            }
            sortedTags.put(tag, List.copyOf(members));
        });
        return new GasRegistry(sorted, sortedTags);
    }

    /** The number of gases; indices run from 0 to {@code size() - 1}. */
    public int size() {
        return definitions.size();
    }

    /** The definition at an index. */
    public GasDefinition definition(int index) {
        return definitions.get(index);
    }

    /** All definitions, in index order. */
    public List<GasDefinition> definitions() {
        return definitions;
    }

    /** The index of a gas id, or -1 if this registry doesn't hold it. */
    public int indexOf(String id) {
        Integer index = indexById.get(id);
        return index == null ? -1 : index;
    }

    /** The definition with an id, if this registry holds it. */
    public Optional<GasDefinition> find(String id) {
        int index = indexOf(id);
        return index < 0 ? Optional.empty() : Optional.of(definitions.get(index));
    }

    /** The molar heat capacity, in J/(mol·K), of the gas at an index. */
    public double molarHeatCapacity(int index) {
        return molarHeatCapacities[index];
    }

    /** Every tag, by id in id order, with the ids of its gases in id order (CORE-012). */
    public Map<String, List<String>> tags() {
        return tags;
    }

    /** The ids of a tag's gases, in id order; empty if there is no such tag. */
    public List<String> gasesIn(String tag) {
        return tags.getOrDefault(tag, List.of());
    }

    /** Whether the gas at an index is in a tag; false if there is no such tag. */
    public boolean isIn(int index, String tag) {
        boolean[] in = membership.get(tag);
        return in != null && in[index];
    }

    /** The ids of the tags the gas at an index is in, in id order. */
    public List<String> tagsOf(int index) {
        return tagsByGas.get(index);
    }

    @Override
    public boolean equals(Object o) {
        return o == this
                || (o instanceof GasRegistry other && definitions.equals(other.definitions) && tags.equals(other.tags));
    }

    @Override
    public int hashCode() {
        return 31 * definitions.hashCode() + tags.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GasRegistry[");
        for (int i = 0; i < definitions.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(definitions.get(i).id());
        }
        if (!tags.isEmpty()) {
            sb.append("; tags");
            tags.keySet().forEach(t -> sb.append(" #").append(t));
        }
        return sb.append(']').toString();
    }
}
