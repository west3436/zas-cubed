package io.github.west3436.zas.compat.mekanism;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * The chemicals that are gases, and the gases that are chemicals (MEK-010): one set of {@link
 * ChemicalMapping}s, looked up from either side. It is the allow-list too: a chemical with no
 * mapping never becomes a gas, and a gas no mapping leads back from never becomes a chemical.
 *
 * <p><b>Gas to chemical.</b> Only a mapping that goes {@linkplain ChemicalMapping#bothWays() both
 * ways} leads back. Where several do for one gas, the gas's temperature picks: the one with the
 * highest {@link ChemicalMapping#minTemperature} the gas has reached, or, if it has reached none,
 * the one with the lowest. So water vapour leaves as steam at 373.15 K and above and as water
 * vapour below. Two with the same least temperature are {@linkplain #ties() a tie}, which the
 * lower chemical id wins.
 *
 * <p>Ids only, so it names nothing of Mekanism's. Immutable; any thread.
 */
public final class ChemicalMap {
    /** A map of nothing. */
    public static final ChemicalMap EMPTY = new ChemicalMap(List.of());

    /** Hottest first, then by chemical id: the order {@link #forGas} tries them in. */
    private static final Comparator<ChemicalMapping> HOTTEST_FIRST = Comparator.comparingDouble(
                    ChemicalMapping::minTemperature)
            .reversed()
            .thenComparing(ChemicalMapping::chemical);

    private final Map<ResourceLocation, ChemicalMapping> byChemical;
    private final Map<ResourceLocation, List<ChemicalMapping>> byGas;

    private ChemicalMap(Collection<ChemicalMapping> mappings) {
        TreeMap<ResourceLocation, ChemicalMapping> chemicals = new TreeMap<>();
        TreeMap<ResourceLocation, List<ChemicalMapping>> gases = new TreeMap<>();
        for (ChemicalMapping mapping : mappings) {
            if (chemicals.put(mapping.chemical(), mapping) != null) {
                throw new IllegalArgumentException("Two mappings for " + mapping.chemical());
            }
            if (mapping.bothWays()) {
                ResourceLocation gas = mapping.gases().keySet().iterator().next();
                gases.computeIfAbsent(gas, g -> new ArrayList<>()).add(mapping);
            }
        }
        gases.replaceAll((gas, list) -> {
            list.sort(HOTTEST_FIRST);
            return List.copyOf(list);
        });
        byChemical = Collections.unmodifiableMap(chemicals);
        byGas = Collections.unmodifiableMap(gases);
    }

    /**
     * A map of some mappings.
     *
     * @param mappings the mappings, one for a chemical at most
     * @return the map
     * @throws IllegalArgumentException if two are for one chemical
     */
    public static ChemicalMap of(Collection<ChemicalMapping> mappings) {
        return mappings.isEmpty() ? EMPTY : new ChemicalMap(mappings);
    }

    /**
     * What a chemical is in ZAS³.
     *
     * @param chemical the chemical's id
     * @return its mapping, or empty if it isn't mapped, so it never becomes a gas
     */
    public Optional<ChemicalMapping> ofChemical(ResourceLocation chemical) {
        return Optional.ofNullable(byChemical.get(chemical));
    }

    /**
     * The chemical a gas is in Mekanism.
     *
     * @param gas the gas's id
     * @param temperature the gas's temperature, in K
     * @return the mapping that leads back from the gas at that temperature, or empty if none does,
     *     so the gas never becomes a chemical
     */
    public Optional<ChemicalMapping> forGas(ResourceLocation gas, double temperature) {
        List<ChemicalMapping> candidates = byGas.get(gas);
        if (candidates == null) {
            return Optional.empty();
        }
        for (ChemicalMapping candidate : candidates) {
            if (candidate.minTemperature() <= temperature) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    /**
     * Every mapping, in the order of the chemicals' ids.
     *
     * @return the mappings, unmodifiable
     */
    public Collection<ChemicalMapping> mappings() {
        return byChemical.values();
    }

    /**
     * The mapped chemicals.
     *
     * @return their ids, sorted and unmodifiable
     */
    public Set<ResourceLocation> chemicals() {
        return byChemical.keySet();
    }

    /**
     * The gases that can become a chemical.
     *
     * @return their ids, sorted and unmodifiable
     */
    public Set<ResourceLocation> gases() {
        return byGas.keySet();
    }

    /**
     * The mappings that pass a test, as a map of their own.
     *
     * @param keep the test
     * @return the map of those it passes
     */
    public ChemicalMap filter(Predicate<ChemicalMapping> keep) {
        return of(byChemical.values().stream().filter(keep).toList());
    }

    /**
     * The gases that two chemicals lead back from at the same least temperature, so that only the
     * one with the lower id is ever chosen. A datapack settles one by marking the other {@code
     * "one_way": true}.
     *
     * @return for each such gas, the chemicals in the order they are tried, with the tied ones next
     *     to each other; empty if there are none
     */
    public Map<ResourceLocation, List<ResourceLocation>> ties() {
        TreeMap<ResourceLocation, List<ResourceLocation>> ties = new TreeMap<>();
        byGas.forEach((gas, candidates) -> {
            for (int i = 1; i < candidates.size(); i++) {
                if (candidates.get(i).minTemperature() == candidates.get(i - 1).minTemperature()) {
                    ties.put(
                            gas,
                            candidates.stream().map(ChemicalMapping::chemical).toList());
                    return;
                }
            }
        });
        return ties;
    }
}
