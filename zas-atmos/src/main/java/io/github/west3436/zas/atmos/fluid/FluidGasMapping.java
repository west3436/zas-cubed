package io.github.west3436.zas.atmos.fluid;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.Millibuckets;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * What the fluids of one fluid tag are in ZAS³ (COMPAT-140): the gas they are, and the temperature
 * they arrive at (COMPAT-145). Ids only, so it names no fluid of any mod's.
 *
 * <p><b>Temperature.</b> A fluid tank has no temperature of its own, so the gas is given one on the
 * way in: the fluid type's, if {@link #fluidTemperature}, as for a mod's several steams at 373 K, 383
 * K and so on; else {@link #temperature} if it is set; else standard air's, 293.15 K, since most
 * mods leave their fluid types at NeoForge's default. Then it is raised to {@link #minTemperature}
 * if it is below it. Steam is the case that needs that: it arrives at 373.15 K or more.
 *
 * <p><b>The way back.</b> A gas leaves as a fluid only once it is at least as hot as that fluid's
 * {@linkplain #leastTemperature least temperature}, so cold water vapour never becomes steam.
 *
 * <p>Immutable; any thread.
 *
 * @param tag the fluid tag's id, such as {@code c:oxygen}
 * @param gas the gas its fluids are
 * @param temperature the temperature its fluids arrive at, in K, or {@link Double#NaN} for none;
 *     ignored if {@code fluidTemperature}
 * @param fluidTemperature whether its fluids arrive at their fluid type's temperature
 * @param minTemperature the least temperature its fluids arrive at, in K; 0 for no minimum
 * @param oneWay whether its fluids become the gas, but the gas never becomes them
 */
public record FluidGasMapping(
        ResourceLocation tag,
        ResourceLocation gas,
        double temperature,
        boolean fluidTemperature,
        double minTemperature,
        boolean oneWay) {

    /**
     * Checks the arguments.
     *
     * @throws IllegalArgumentException if a temperature is negative or not finite; the message
     *     starts with the datapack field at fault
     * @throws NullPointerException if the tag or the gas is null
     */
    public FluidGasMapping {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(gas, "gas");
        if (!Double.isNaN(temperature) && (!Double.isFinite(temperature) || temperature <= 0)) {
            throw new IllegalArgumentException("temperature: must be finite and above 0, got " + temperature);
        }
        if (!Double.isFinite(minTemperature) || minTemperature < 0) {
            throw new IllegalArgumentException(
                    "min_temperature: must be finite and not negative, got " + minTemperature);
        }
    }

    /**
     * A tag that is one gas both ways, at 293.15 K.
     *
     * @param tag the tag's id
     * @param gas the gas's id
     * @return the mapping
     */
    public static FluidGasMapping of(ResourceLocation tag, ResourceLocation gas) {
        return new FluidGasMapping(tag, gas, Double.NaN, false, 0, false);
    }

    /**
     * The temperature a fluid of the tag arrives at.
     *
     * @param fluidTypeTemperature its fluid type's temperature, in K
     * @return that, a set temperature or 293.15 K, as the mapping says, raised to {@link
     *     #minTemperature}
     */
    public double entryTemperature(double fluidTypeTemperature) {
        double base;
        if (fluidTemperature && fluidTypeTemperature > 0 && Double.isFinite(fluidTypeTemperature)) {
            base = fluidTypeTemperature;
        } else if (!Double.isNaN(temperature)) {
            base = temperature;
        } else {
            base = Millibuckets.STANDARD_TEMPERATURE;
        }
        return Math.max(base, minTemperature);
    }

    /**
     * How hot gas must be to leave as a fluid of the tag: the fluid's own temperature, if the
     * mapping gives one, raised to {@link #minTemperature}; for a mapping that gives none, just
     * {@code minTemperature}.
     *
     * @param fluidTypeTemperature the fluid type's temperature, in K
     * @return the least temperature, in K
     */
    public double leastTemperature(double fluidTypeTemperature) {
        return fluidTemperature || !Double.isNaN(temperature) ? entryTemperature(fluidTypeTemperature) : minTemperature;
    }

    /**
     * The gas some millibuckets of a fluid of the tag are.
     *
     * @param millibuckets the amount, not negative
     * @param molesPerMillibucket the unit factor, from {@link Millibuckets}
     * @param fluidTypeTemperature the fluid type's temperature, in K
     * @return the gas, at {@link #entryTemperature}; {@link GasParcel#EMPTY} for none
     * @throws IllegalArgumentException if the amount is negative
     */
    public GasParcel gas(long millibuckets, double molesPerMillibucket, double fluidTypeTemperature) {
        double moles = Millibuckets.moles(millibuckets, molesPerMillibucket);
        return moles > 0 ? new GasParcel(Map.of(gas, moles), entryTemperature(fluidTypeTemperature)) : GasParcel.EMPTY;
    }
}
