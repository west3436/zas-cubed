package io.github.west3436.zas.api.gas;

import io.github.west3436.zas.api.AnyThread;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * An immutable snapshot of a gas mixture: moles per gas, a temperature and a
 * volume, with the total moles and pressures derived from them (CORE-001,
 * API-046).
 *
 * <p>Units: moles, kelvin and litres; pressures are in kilopascals
 * (P = nRT/V, with V in litres, gives kPa). Gases are identified by their
 * registry id. Every method may be called from any thread.
 *
 * @param moles moles per gas id; copied, sorted by id, and entries of zero
 *     dropped
 * @param temperature the temperature in kelvin, finite and not negative
 * @param volume the volume in litres, finite and positive
 */
@AnyThread
public record GasMixture(Map<ResourceLocation, Double> moles, double temperature, double volume) {
    /** The molar gas constant R, in J/(mol·K). */
    public static final double GAS_CONSTANT = 8.314462618;

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if an amount, the temperature or the
     *     volume is out of range
     * @throws NullPointerException if the map, a key or a value is null
     */
    public GasMixture {
        moles = GasParcel.validMoles(moles);
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
        if (!Double.isFinite(volume) || volume <= 0) {
            throw new IllegalArgumentException("volume must be finite and > 0 L: " + volume);
        }
    }

    /**
     * The moles of one gas.
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return its moles, or 0 if the mixture holds none
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
     * The total pressure, nRT/V.
     *
     * <p>Thread: any.
     *
     * @return the pressure in kPa
     */
    @AnyThread
    public double pressure() {
        return totalMoles() * GAS_CONSTANT * temperature / volume;
    }

    /**
     * The partial pressure of one gas, n<sub>gas</sub>RT/V.
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return its partial pressure in kPa, or 0 if the mixture holds none
     */
    @AnyThread
    public double partialPressure(ResourceLocation gas) {
        return moles(gas) * GAS_CONSTANT * temperature / volume;
    }

    /**
     * The partial pressure of every gas the mixture holds.
     *
     * <p>Thread: any.
     *
     * @return an unmodifiable map from gas id to kPa, sorted by id
     */
    @AnyThread
    public Map<ResourceLocation, Double> partialPressures() {
        TreeMap<ResourceLocation, Double> out = new TreeMap<>();
        moles.forEach((gas, n) -> out.put(gas, n * GAS_CONSTANT * temperature / volume));
        return Collections.unmodifiableMap(out);
    }

    /**
     * The composition: each gas's mole fraction.
     *
     * <p>Thread: any.
     *
     * @return an unmodifiable map from gas id to a fraction in (0, 1], sorted
     *     by id; empty for an empty mixture
     */
    @AnyThread
    public Map<ResourceLocation, Double> composition() {
        double total = totalMoles();
        TreeMap<ResourceLocation, Double> out = new TreeMap<>();
        if (total > 0) {
            moles.forEach((gas, n) -> out.put(gas, n / total));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The gas in this mixture as a parcel, without the volume.
     *
     * <p>Thread: any.
     *
     * @return a parcel with the same moles and temperature
     */
    @AnyThread
    public GasParcel parcel() {
        return new GasParcel(moles, temperature);
    }

    /**
     * Whether the mixture holds no gas (a vacuum).
     *
     * <p>Thread: any.
     *
     * @return true if {@link #moles()} is empty
     */
    @AnyThread
    public boolean isEmpty() {
        return moles.isEmpty();
    }
}
