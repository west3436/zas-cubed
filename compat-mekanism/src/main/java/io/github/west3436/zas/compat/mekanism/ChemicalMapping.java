package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.gas.GasParcel;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * What one Mekanism chemical is in ZAS³ (MEK-010): the gas it becomes, or the gases it splits
 * into, and the least temperature it arrives at. Ids only, so it names nothing of Mekanism's.
 *
 * <p>Mekanism's chemicals carry no temperature, so one is given on the way in: that of wherever
 * the chemical comes from, raised to {@link #minTemperature} if it is below it. Steam is the case
 * that needs it: it arrives at 373.15 K or more.
 *
 * <p>Immutable; any thread.
 *
 * @param chemical the chemical's id in Mekanism's registry, such as {@code mekanism:oxygen}
 * @param gases the mole fraction of each gas the chemical is, by gas id; each above 0, adding up
 *     to 1. One entry for most chemicals; fusion fuel has two, half deuterium and half tritium
 * @param minTemperature the least temperature the gas arrives at, in K; 0 for no minimum
 * @param oneWay whether the chemical becomes its gas but the gas never becomes this chemical. A
 *     chemical that splits into several gases is one-way whatever this says
 */
public record ChemicalMapping(
        ResourceLocation chemical, Map<ResourceLocation, Double> gases, double minTemperature, boolean oneWay) {
    /** How far from 1 the fractions may add up to. */
    private static final double FRACTION_TOLERANCE = 1e-6;

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if there is no gas, a fraction isn't above 0, the fractions
     *     don't add up to 1, or the temperature is negative or not finite. The message starts with
     *     the datapack field at fault
     * @throws NullPointerException if the chemical, the map, a key or a value is null
     */
    public ChemicalMapping {
        Objects.requireNonNull(chemical, "chemical");
        Objects.requireNonNull(gases, "gases");
        TreeMap<ResourceLocation, Double> copy = new TreeMap<>();
        double total = 0;
        for (Map.Entry<ResourceLocation, Double> e : gases.entrySet()) {
            ResourceLocation gas = Objects.requireNonNull(e.getKey(), "gas id");
            double fraction = Objects.requireNonNull(e.getValue(), "fraction of " + gas);
            if (!Double.isFinite(fraction) || fraction <= 0) {
                throw new IllegalArgumentException("gases: " + gas + ": must be finite and above 0, got " + fraction);
            }
            copy.put(gas, fraction);
            total += fraction;
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("gases: must name at least one gas");
        }
        if (Math.abs(total - 1) > FRACTION_TOLERANCE) {
            throw new IllegalArgumentException("gases: the fractions must add up to 1, got " + total);
        }
        if (!Double.isFinite(minTemperature) || minTemperature < 0) {
            throw new IllegalArgumentException(
                    "min_temperature: must be finite and not negative, got " + minTemperature);
        }
        gases = Collections.unmodifiableMap(copy);
    }

    /**
     * A chemical that is one gas, both ways, with no least temperature.
     *
     * @param chemical the chemical's id
     * @param gas the gas's id
     * @return the mapping
     */
    public static ChemicalMapping of(ResourceLocation chemical, ResourceLocation gas) {
        return new ChemicalMapping(chemical, Map.of(gas, 1.0), 0, false);
    }

    /**
     * Whether gas can become this chemical: it is one gas, and isn't one-way.
     *
     * @return true if {@link ChemicalMap#forGas} may answer with it
     */
    public boolean bothWays() {
        return !oneWay && gases.size() == 1;
    }

    /**
     * The temperature the chemical arrives at.
     *
     * @param sourceTemperature the temperature of wherever it comes from, in K
     * @return that temperature, or {@link #minTemperature} if that is higher
     */
    public double entryTemperature(double sourceTemperature) {
        return Math.max(sourceTemperature, minTemperature);
    }

    /**
     * The gas an amount of the chemical is.
     *
     * @param millibuckets the amount, not negative
     * @param molesPerMillibucket the unit factor, from {@link ChemicalUnits#molesPerMillibucket}
     * @param sourceTemperature the temperature of wherever it comes from, in K, finite and not
     *     negative
     * @return its gases, at {@link #entryTemperature}; {@link GasParcel#EMPTY} for none
     * @throws IllegalArgumentException if the amount is negative
     */
    public GasParcel gas(long millibuckets, double molesPerMillibucket, double sourceTemperature) {
        double moles = ChemicalUnits.moles(millibuckets, molesPerMillibucket);
        if (!(moles > 0)) {
            return GasParcel.EMPTY;
        }
        TreeMap<ResourceLocation, Double> out = new TreeMap<>();
        gases.forEach((gas, fraction) -> out.put(gas, moles * fraction));
        return new GasParcel(out, entryTemperature(sourceTemperature));
    }
}
