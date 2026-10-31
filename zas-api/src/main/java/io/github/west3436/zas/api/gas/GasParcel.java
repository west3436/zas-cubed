package io.github.west3436.zas.api.gas;

import io.github.west3436.zas.api.AnyThread;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * An amount of gas with no volume of its own: moles per gas and a temperature.
 * It is what a mutation adds, and what a removal delivers (API-012, API-016).
 *
 * <p>Gases are identified by their registry id, such as {@code zas:oxygen}.
 * The record is immutable: {@link #moles()} is an unmodifiable copy sorted by
 * id, holding only positive amounts (API-046). Every method may be called from
 * any thread.
 *
 * @param moles moles per gas id; copied, and entries of zero dropped
 * @param temperature the temperature in kelvin, finite and not negative
 */
@AnyThread
public record GasParcel(Map<ResourceLocation, Double> moles, double temperature) {
    /** A parcel holding no gas, at 0 K. */
    public static final GasParcel EMPTY = new GasParcel(Map.of(), 0.0);

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if an amount or the temperature is
     *     negative or not finite
     * @throws NullPointerException if the map, a key or a value is null
     */
    public GasParcel {
        moles = validMoles(moles);
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
    }

    /**
     * The moles of one gas.
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return its moles, or 0 if the parcel holds none
     */
    @AnyThread
    public double moles(ResourceLocation gas) {
        return moles.getOrDefault(gas, 0.0);
    }

    /**
     * The total moles of every gas.
     *
     * <p>Thread: any.
     *
     * @return the sum of {@link #moles()}
     */
    @AnyThread
    public double totalMoles() {
        double total = 0;
        for (double n : moles.values()) {
            total += n;
        }
        return total;
    }

    /**
     * Whether the parcel holds no gas.
     *
     * <p>Thread: any.
     *
     * @return true if {@link #moles()} is empty
     */
    @AnyThread
    public boolean isEmpty() {
        return moles.isEmpty();
    }

    /**
     * Returns an unmodifiable copy of a moles-per-gas map, sorted by gas id,
     * after checking every amount. Entries of zero are dropped.
     *
     * <p>Thread: any.
     *
     * @param moles moles per gas id
     * @return the checked copy
     * @throws IllegalArgumentException if an amount is negative or not finite
     * @throws NullPointerException if the map, a key or a value is null
     */
    @AnyThread
    public static Map<ResourceLocation, Double> validMoles(Map<ResourceLocation, Double> moles) {
        Objects.requireNonNull(moles, "moles");
        TreeMap<ResourceLocation, Double> copy = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> e : moles.entrySet()) {
            ResourceLocation gas = Objects.requireNonNull(e.getKey(), "gas id");
            double n = Objects.requireNonNull(e.getValue(), "moles of " + gas);
            if (!Double.isFinite(n) || n < 0) {
                throw new IllegalArgumentException("moles of " + gas + " must be finite and >= 0: " + n);
            }
            if (n > 0) {
                copy.put(gas, n);
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
