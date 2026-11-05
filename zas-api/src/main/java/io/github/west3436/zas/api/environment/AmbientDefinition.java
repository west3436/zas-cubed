package io.github.west3436.zas.api.environment;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * A dimension's ambient atmosphere as an {@link AmbientProvider} gives it (ENV-004): partial
 * pressures per gas and a temperature. With no gas it is a vacuum (ENV-003), whose temperature is
 * the radiative background (HEAT-004) and what an exposed entity feels (LIFE-011).
 *
 * <p>The record is immutable: {@link #partialPressures()} is an unmodifiable copy sorted by id,
 * holding only positive amounts. Every method may be called from any thread.
 *
 * @param partialPressures partial pressure in kPa per gas id; copied, and entries of zero dropped
 * @param temperature the temperature in kelvin, finite and not negative; above 0 if it holds gas
 */
@AnyThread
public record AmbientDefinition(Map<ResourceLocation, Double> partialPressures, double temperature) {
    /** The cosmic background temperature, in kelvin: a vacuum's default (ENV-003). */
    public static final double COSMIC_BACKGROUND = 2.7;

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if a partial pressure is negative or not finite, the
     *     temperature is negative or not finite, or the ambient holds gas at 0 K
     * @throws NullPointerException if the map, a key or a value is null
     */
    public AmbientDefinition {
        Objects.requireNonNull(partialPressures, "partialPressures");
        TreeMap<ResourceLocation, Double> copy = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> e : partialPressures.entrySet()) {
            ResourceLocation gas = Objects.requireNonNull(e.getKey(), "gas id");
            double p = Objects.requireNonNull(e.getValue(), "partial pressure of " + gas);
            if (!Double.isFinite(p) || p < 0) {
                throw new IllegalArgumentException("partial pressure of " + gas + " must be finite and >= 0: " + p);
            }
            if (p > 0) {
                copy.put(gas, p);
            }
        }
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
        if (temperature == 0 && !copy.isEmpty()) {
            throw new IllegalArgumentException("an ambient holding gas needs a temperature above 0 K");
        }
        partialPressures = Collections.unmodifiableMap(copy);
    }

    /**
     * A vacuum at the cosmic background temperature (ENV-003).
     *
     * <p>Thread: any.
     *
     * @return no gas at {@link #COSMIC_BACKGROUND}
     */
    @AnyThread
    public static AmbientDefinition vacuum() {
        return vacuum(COSMIC_BACKGROUND);
    }

    /**
     * A vacuum at a temperature (ENV-003, D-52).
     *
     * <p>Thread: any.
     *
     * @param temperature the temperature in kelvin, finite and not negative
     * @return no gas at that temperature
     * @throws IllegalArgumentException if the temperature is negative or not finite
     */
    @AnyThread
    public static AmbientDefinition vacuum(double temperature) {
        return new AmbientDefinition(Map.of(), temperature);
    }

    /**
     * Standard air's pressure and composition (ENV-011) at a temperature.
     *
     * <p>Thread: any.
     *
     * @param temperature the temperature in kelvin, finite and above 0
     * @return 101.325 kPa of 79 % N₂ and 21 % O₂ at that temperature
     * @throws IllegalArgumentException if the temperature isn't finite and above 0
     */
    @AnyThread
    public static AmbientDefinition standardAir(double temperature) {
        return new AmbientDefinition(
                Map.of(
                        StandardAir.NITROGEN,
                        StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
                        StandardAir.OXYGEN,
                        StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION),
                temperature);
    }

    /**
     * Whether the ambient holds no gas (ENV-003).
     *
     * <p>Thread: any.
     *
     * @return true if {@link #partialPressures()} is empty
     */
    @AnyThread
    public boolean isVacuum() {
        return partialPressures.isEmpty();
    }

    /**
     * The total pressure.
     *
     * <p>Thread: any.
     *
     * @return the sum of {@link #partialPressures()}, in kPa
     */
    @AnyThread
    public double pressure() {
        double total = 0;
        for (double p : partialPressures.values()) {
            total += p;
        }
        return total;
    }
}
