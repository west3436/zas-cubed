package io.github.west3436.zas.atmos.gas;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A mutable gas mixture held by the content mod: a pipe network, a node's saved share, a device's
 * store. Moles per gas id (sorted, so iteration is deterministic), a temperature in K and a volume
 * in L; pressures in kPa, as in the API.
 *
 * <p><b>Mixing temperatures.</b> Gas added to a store mixes conserving thermal energy (CONT-008,
 * CORE-003): the store takes the heat-capacity-weighted mean temperature, with each gas's molar
 * heat capacity from the API ({@link GasProperties}, architecture §11.4).
 *
 * <p>Server thread only; not thread-safe.
 */
public final class GasStore {
    /** Amounts below this many moles are dropped, so rounding never leaves a trace of a gas. */
    public static final double EPSILON = 1e-12;

    private static final String TEMPERATURE = "T";
    private static final String MOLES = "Moles";

    private final TreeMap<ResourceLocation, Double> moles = new TreeMap<>();
    private double temperature;
    private double volume;
    private @Nullable Runnable watcher;

    /**
     * An empty store.
     *
     * @param volume its volume in L, positive
     */
    public GasStore(double volume) {
        setVolume(volume);
    }

    /**
     * Has a watcher told, after the change, each time the moles or the temperature change. A store has
     * at most one. Reading the gas, or a call that changes nothing, doesn't tell it.
     *
     * @param watcher the watcher, or null for none
     */
    public void watch(@Nullable Runnable watcher) {
        this.watcher = watcher;
    }

    private void changed() {
        if (watcher != null) {
            watcher.run();
        }
    }

    /** The volume in L. */
    public double volume() {
        return volume;
    }

    /**
     * Changes the volume, keeping the moles.
     *
     * @param volume the volume in L, finite and positive
     */
    public void setVolume(double volume) {
        if (!(volume > 0) || !Double.isFinite(volume)) {
            throw new IllegalArgumentException("volume must be finite and > 0 L: " + volume);
        }
        this.volume = volume;
    }

    /** The temperature in K; that of the last gas added while empty, or 0 if none ever was. */
    public double temperature() {
        return temperature;
    }

    /** Sets the temperature in K. */
    public void setTemperature(double temperature) {
        if (!(temperature >= 0) || !Double.isFinite(temperature)) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
        if (temperature != this.temperature) {
            this.temperature = temperature;
            changed();
        }
    }

    /** The moles per gas, unmodifiable and sorted by id. */
    public Map<ResourceLocation, Double> moles() {
        return Collections.unmodifiableMap(moles);
    }

    /** The moles of one gas. */
    public double moles(ResourceLocation gas) {
        return moles.getOrDefault(gas, 0.0);
    }

    /** The total moles. */
    public double totalMoles() {
        double total = 0;
        for (double n : moles.values()) {
            total += n;
        }
        return total;
    }

    /** The heat capacity in J/K: Σ moles × molar heat capacity (CORE-003), 0 when empty. */
    public double heatCapacity() {
        return GasProperties.heatCapacity(moles);
    }

    /** The thermal energy in J: the heat capacity times the temperature. */
    public double thermalEnergy() {
        return heatCapacity() * temperature;
    }

    /** Whether the store holds no gas. */
    public boolean isEmpty() {
        return moles.isEmpty();
    }

    /** The pressure in kPa, nRT/V. */
    public double pressure() {
        return totalMoles() * GasMixture.GAS_CONSTANT * temperature / volume;
    }

    /** The partial pressure of one gas, in kPa. */
    public double partialPressure(ResourceLocation gas) {
        return moles(gas) * GasMixture.GAS_CONSTANT * temperature / volume;
    }

    /** An immutable snapshot of the store. */
    public GasMixture mixture() {
        return new GasMixture(moles, temperature, volume);
    }

    /**
     * Adds gas, conserving thermal energy.
     *
     * @param gas the gas
     */
    public void add(GasParcel gas) {
        add(gas.moles(), gas.temperature());
    }

    /**
     * Adds gas, conserving thermal energy: the store takes the heat-capacity-weighted mean of its
     * temperature and the gas's, or the gas's own if the store is empty.
     *
     * @param add moles per gas, none negative
     * @param at their temperature in K
     */
    public void add(Map<ResourceLocation, Double> add, double at) {
        double added = GasProperties.heatCapacity(add);
        if (!(added > 0)) {
            return;
        }
        temperature = GasProperties.mixedTemperature(heatCapacity(), temperature, added, at);
        add.forEach((gas, n) -> {
            if (n > 0) {
                moles.merge(gas, n, Double::sum);
            }
        });
        changed();
    }

    /** Adds all of another store's gas to this one and empties it. */
    public void merge(GasStore other) {
        add(other.drain());
    }

    /**
     * Takes up to the given moles of each gas.
     *
     * @param want moles wanted per gas
     * @return what was taken, at this store's temperature
     */
    public GasParcel take(Map<ResourceLocation, Double> want) {
        TreeMap<ResourceLocation, Double> taken = new TreeMap<>();
        want.forEach((gas, n) -> {
            double have = moles(gas);
            double t = Math.min(have, Math.max(0, n));
            if (t > 0) {
                taken.put(gas, t);
                set(gas, have - t);
            }
        });
        if (!taken.isEmpty()) {
            changed();
        }
        return new GasParcel(taken, temperature);
    }

    /**
     * Takes a fraction of every gas.
     *
     * @param fraction from 0 to 1; clamped
     * @return what was taken, at this store's temperature
     */
    public GasParcel takeFraction(double fraction) {
        GasParcel part = fraction(fraction);
        part.moles().forEach((gas, n) -> set(gas, moles(gas) - n));
        if (!part.isEmpty()) {
            changed();
        }
        return part;
    }

    /**
     * A fraction of every gas, without taking it.
     *
     * @param fraction from 0 to 1; clamped
     * @return the gas, at this store's temperature
     */
    public GasParcel fraction(double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        TreeMap<ResourceLocation, Double> part = new TreeMap<>();
        moles.forEach((gas, n) -> {
            if (n * f > 0) {
                part.put(gas, f >= 1 ? n : n * f);
            }
        });
        return new GasParcel(part, temperature);
    }

    /** Takes all the gas, leaving the store empty at the same temperature. */
    public GasParcel drain() {
        GasParcel all = new GasParcel(moles, temperature);
        moles.clear();
        if (!all.isEmpty()) {
            changed();
        }
        return all;
    }

    /** Replaces the contents with a parcel. */
    public void set(GasParcel gas) {
        moles.clear();
        temperature = gas.temperature();
        gas.moles().forEach(moles::put);
        changed();
    }

    private void set(ResourceLocation gas, double n) {
        if (n > EPSILON) {
            moles.put(gas, n);
        } else {
            moles.remove(gas);
        }
    }

    /**
     * Writes a parcel as the content mod saves gas: its temperature and moles per gas id.
     *
     * @param gas the gas
     * @return a tag for {@link #parcel(CompoundTag)}
     */
    public static CompoundTag save(GasParcel gas) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TEMPERATURE, gas.temperature());
        CompoundTag m = new CompoundTag();
        gas.moles().forEach((id, n) -> m.putDouble(id.toString(), n));
        tag.put(MOLES, m);
        return tag;
    }

    /**
     * Reads a parcel written by {@link #save}. Unknown or invalid entries are skipped.
     *
     * @param tag the tag
     * @return the gas
     */
    public static GasParcel parcel(CompoundTag tag) {
        TreeMap<ResourceLocation, Double> m = new TreeMap<>();
        CompoundTag molesTag = tag.getCompound(MOLES);
        for (String key : molesTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            double n = molesTag.getDouble(key);
            if (id != null && n > 0 && Double.isFinite(n) && molesTag.contains(key, Tag.TAG_ANY_NUMERIC)) {
                m.put(id, n);
            }
        }
        double t = tag.getDouble(TEMPERATURE);
        return new GasParcel(m, Double.isFinite(t) && t >= 0 ? t : 0);
    }

    @Override
    public String toString() {
        return "GasStore[" + moles + ", " + temperature + " K, " + volume + " L]";
    }
}
