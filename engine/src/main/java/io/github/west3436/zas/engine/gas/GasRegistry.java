package io.github.west3436.zas.engine.gas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable set of gas definitions with dense indices (CORE-010).
 *
 * <p>Gases are ordered by id, so index {@code i} names the same gas for the
 * same set of definitions however they were loaded. {@link GasMixture} stores
 * moles in arrays indexed this way. Indices are only meaningful within one
 * registry: a different set of definitions can renumber them.
 */
public final class GasRegistry {
    /** A registry with no gases. */
    public static final GasRegistry EMPTY = new GasRegistry(List.of());

    private final List<GasDefinition> definitions;
    private final Map<String, Integer> indexById;
    private final double[] molarHeatCapacities;

    private GasRegistry(List<GasDefinition> sorted) {
        this.definitions = List.copyOf(sorted);
        // Lookup only; never iterated (architecture §3.5).
        this.indexById = new HashMap<>();
        this.molarHeatCapacities = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            indexById.put(sorted.get(i).id(), i);
            molarHeatCapacities[i] = sorted.get(i).molarHeatCapacity();
        }
    }

    /**
     * Builds a registry from definitions in any order.
     *
     * @throws IllegalArgumentException if two definitions share an id
     */
    public static GasRegistry of(Collection<GasDefinition> definitions) {
        List<GasDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparing(GasDefinition::id));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).id().equals(sorted.get(i - 1).id())) {
                throw new IllegalArgumentException(
                        "duplicate gas id " + sorted.get(i).id());
            }
        }
        return new GasRegistry(sorted);
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

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof GasRegistry other && definitions.equals(other.definitions));
    }

    @Override
    public int hashCode() {
        return definitions.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GasRegistry[");
        for (int i = 0; i < definitions.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(definitions.get(i).id());
        }
        return sb.append(']').toString();
    }
}
