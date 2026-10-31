package io.github.west3436.zas.engine;

import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A dimension's ambient atmosphere, the unsimulated reservoir outside every zone (ENV-001;
 * architecture §2.3). The {@code Batch} carries one per dimension.
 *
 * <p>Plain, immutable data: partial pressures by gas id and a temperature. Gases are named by
 * id, not by registry index, so an ambient stays valid whatever registry the engine runs with;
 * {@link #fill(GasRegistry, double)} turns it into a mixture of any volume. An ambient with no
 * gas is a vacuum (ENV-003).
 *
 * @param partialPressures partial pressure in kPa per gas id; copied, sorted by id, and entries
 *     of zero dropped
 * @param temperature the temperature in K
 */
public record Ambient(SortedMap<String, Double> partialPressures, double temperature) {
    /** The cosmic background temperature, in K: a vacuum's default (ENV-003). */
    public static final double COSMIC_BACKGROUND = 2.7;

    /** The id of nitrogen in standard air. */
    public static final String NITROGEN = "zas:nitrogen";

    /** The id of oxygen in standard air. */
    public static final String OXYGEN = "zas:oxygen";

    /**
     * Standard Overworld air: 101.325 kPa, 293.15 K, 79 % N₂ and 21 % O₂ by moles. The fallback
     * for a dimension with no ambient from any source (ENV-011).
     */
    public static final Ambient STANDARD_AIR = new Ambient(
            new TreeMap<>(
                    Map.of(NITROGEN, 0.79 * GasConstants.ONE_ATMOSPHERE, OXYGEN, 0.21 * GasConstants.ONE_ATMOSPHERE)),
            293.15);

    /** A vacuum at the cosmic background temperature (ENV-003). */
    public static final Ambient VACUUM = vacuum(COSMIC_BACKGROUND);

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if a partial pressure is negative or not finite, the
     *     temperature is negative or not finite, or the ambient holds gas at 0 K
     * @throws NullPointerException if the map, a key or a value is null
     */
    public Ambient {
        Objects.requireNonNull(partialPressures, "partialPressures");
        TreeMap<String, Double> copy = new TreeMap<>();
        partialPressures.forEach((id, p) -> {
            Objects.requireNonNull(id, "gas id");
            Objects.requireNonNull(p, "partial pressure of " + id);
            if (!Double.isFinite(p) || p < 0) {
                throw new IllegalArgumentException("partial pressure of " + id + " must be finite and >= 0: " + p);
            }
            if (p > 0) {
                copy.put(id, p);
            }
        });
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
        if (temperature == 0 && !copy.isEmpty()) {
            throw new IllegalArgumentException("an ambient holding gas needs a temperature above 0 K");
        }
        partialPressures = Collections.unmodifiableSortedMap(copy);
    }

    /** A vacuum at a temperature (ENV-003, D-52). */
    public static Ambient vacuum(double temperature) {
        return new Ambient(new TreeMap<>(), temperature);
    }

    /**
     * An ambient given as moles per 1,000 L at a temperature (ENV-001).
     *
     * @throws IllegalArgumentException as for the constructor, or if an amount is negative or
     *     not finite
     */
    public static Ambient ofMolesPerCubicMetre(Map<String, Double> molesPer1000L, double temperature) {
        TreeMap<String, Double> pressures = new TreeMap<>();
        molesPer1000L.forEach((id, n) -> {
            if (n == null || !Double.isFinite(n) || n < 0) {
                throw new IllegalArgumentException("moles of " + id + " must be finite and >= 0: " + n);
            }
            pressures.put(id, n * GasConstants.R * temperature / 1000.0);
        });
        return new Ambient(pressures, temperature);
    }

    /** Whether the ambient holds no gas (ENV-003). */
    public boolean isVacuum() {
        return partialPressures.isEmpty();
    }

    /** The total pressure, in kPa. */
    public double pressure() {
        double total = 0;
        for (double p : partialPressures.values()) {
            total += p;
        }
        return total;
    }

    /** The partial pressure of one gas, in kPa; 0 if the ambient holds none. */
    public double partialPressure(String gas) {
        return partialPressures.getOrDefault(gas, 0.0);
    }

    /** The moles of one gas in a volume in litres: n = pV/RT. */
    public double moles(String gas, double volume) {
        double p = partialPressure(gas);
        return p == 0 ? 0 : p * volume / (GasConstants.R * temperature);
    }

    /** The gas ids this ambient holds that a registry lacks, in id order. */
    public List<String> missingGases(GasRegistry registry) {
        List<String> missing = new ArrayList<>();
        for (String id : partialPressures.keySet()) {
            if (registry.indexOf(id) < 0) {
                missing.add(id);
            }
        }
        return missing;
    }

    /**
     * A new mixture of this ambient filling a volume.
     *
     * @param registry the registry the mixture indexes gases by
     * @param volume the volume in litres, finite and positive
     * @throws IllegalArgumentException if the registry lacks a gas this ambient holds, or the
     *     volume is out of range
     */
    public GasMixture fill(GasRegistry registry, double volume) {
        List<String> missing = missingGases(registry);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("the gas registry lacks " + missing);
        }
        GasMixture mixture = new GasMixture(registry, volume, temperature);
        for (String id : partialPressures.keySet()) {
            mixture.setMoles(registry.indexOf(id), moles(id, volume));
        }
        return mixture;
    }
}
