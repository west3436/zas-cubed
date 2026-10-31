package io.github.west3436.zas.engine.gas;

import java.util.Arrays;
import java.util.Objects;

/**
 * A gas mixture: moles per gas, a temperature in K and a volume in L
 * (CORE-001). Total moles and pressure are derived from that state.
 *
 * <p>Moles are a dense array indexed by the {@link GasRegistry}'s gas indices,
 * so every loop runs in index order and results don't depend on hash order
 * (architecture §3.5). Mixtures are mutable and not thread-safe; the atmos
 * worker owns them.
 *
 * <p><b>Invariants,</b> held after every operation:
 *
 * <ul>
 *   <li>Every stored value is finite (CORE-063). An operation whose inputs are
 *       invalid, or whose result would not be finite, changes nothing, is
 *       logged through {@link GasConfig#log()} and reports the rejection in its
 *       return value.
 *   <li>No gas amount is negative (CORE-061), and none is below
 *       {@link GasConfig#pruneEpsilon()} unless it is zero (CORE-007). Pruned
 *       moles are counted, so a conservation ledger can attribute them
 *       ({@link #drainPruned(double[])}).
 *   <li>The volume is positive and the temperature is not negative.
 * </ul>
 */
public final class GasMixture {
    private final GasRegistry registry;
    private final GasConfig config;
    private final double[] moles;
    private final double[] pruned;
    private double temperature;
    private double volume;
    private double totalMoles;
    /** Σ cᵢnᵢ, before the floor. */
    private double rawHeatCapacity;

    /**
     * An empty mixture.
     *
     * @throws IllegalArgumentException if the volume isn't finite and positive or
     *     the temperature isn't finite and non-negative
     */
    public GasMixture(GasRegistry registry, GasConfig config, double volume, double temperature) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        if (!validVolume(volume)) {
            throw new IllegalArgumentException("volume must be finite and positive, got " + volume);
        }
        if (!validTemperature(temperature)) {
            throw new IllegalArgumentException("temperature must be finite and not negative, got " + temperature);
        }
        this.moles = new double[registry.size()];
        this.pruned = new double[registry.size()];
        this.volume = volume;
        this.temperature = temperature;
    }

    /** An empty mixture with the {@linkplain GasConfig#DEFAULT default settings}. */
    public GasMixture(GasRegistry registry, double volume, double temperature) {
        this(registry, GasConfig.DEFAULT, volume, temperature);
    }

    // ---- State ------------------------------------------------------------

    public GasRegistry registry() {
        return registry;
    }

    public GasConfig config() {
        return config;
    }

    /** Temperature, in K. */
    public double temperature() {
        return temperature;
    }

    /** Volume, in L. */
    public double volume() {
        return volume;
    }

    /** Moles of the gas at a registry index. */
    public double moles(int gas) {
        return moles[gas];
    }

    /** Total moles over all gases. */
    public double totalMoles() {
        return totalMoles;
    }

    /** Whether the mixture holds no gas. */
    public boolean isEmpty() {
        return totalMoles == 0;
    }

    /** Pressure, in kPa: P = nRT/V. */
    public double pressure() {
        return totalMoles * GasConstants.R * temperature / volume;
    }

    /** Partial pressure of one gas, in kPa. */
    public double partialPressure(int gas) {
        return moles[gas] * GasConstants.R * temperature / volume;
    }

    /** Molar density of one gas, in mol/L. */
    public double molarDensity(int gas) {
        return moles[gas] / volume;
    }

    /**
     * Heat capacity, in J/K: Σ cᵢnᵢ over gases (CORE-003), but never less than
     * {@link GasConfig#heatCapacityFloor()} (CORE-009).
     */
    public double heatCapacity() {
        return Math.max(rawHeatCapacity, config.heatCapacityFloor());
    }

    /** Thermal energy, in J: Σ cᵢnᵢ times T, without the floor. */
    public double thermalEnergy() {
        return rawHeatCapacity * temperature;
    }

    // ---- Setting state ------------------------------------------------------

    /** Sets the moles of one gas. Returns false, changing nothing, if rejected. */
    public boolean setMoles(int gas, double amount) {
        if (!validAmount(amount)) {
            return reject("setMoles", "amount " + amount + " of " + gasId(gas));
        }
        double old = moles[gas];
        moles[gas] = amount;
        if (!stateFinite(temperature, volume)) {
            moles[gas] = old;
            return reject("setMoles", "amount " + amount + " of " + gasId(gas) + " overflows");
        }
        pruneAndRecount();
        return true;
    }

    /** Sets the temperature. Returns false, changing nothing, if rejected. */
    public boolean setTemperature(double kelvin) {
        if (!validTemperature(kelvin) || !finiteDerived(totalMoles, rawHeatCapacity, kelvin, volume)) {
            return reject("setTemperature", "temperature " + kelvin);
        }
        temperature = kelvin;
        return true;
    }

    /**
     * Sets the volume, keeping moles and temperature, so pressure changes.
     * Returns false, changing nothing, if rejected.
     */
    public boolean setVolume(double litres) {
        if (!validVolume(litres) || !finiteDerived(totalMoles, rawHeatCapacity, temperature, litres)) {
            return reject("setVolume", "volume " + litres);
        }
        volume = litres;
        return true;
    }

    /** Removes all gas, keeping temperature and volume. */
    public void clear() {
        Arrays.fill(moles, 0);
        totalMoles = 0;
        rawHeatCapacity = 0;
    }

    /** A copy of this mixture's state; its pruned-moles count starts at zero. */
    public GasMixture copy() {
        GasMixture copy = new GasMixture(registry, config, volume, temperature);
        System.arraycopy(moles, 0, copy.moles, 0, moles.length);
        copy.totalMoles = totalMoles;
        copy.rawHeatCapacity = rawHeatCapacity;
        return copy;
    }

    /** Overwrites this mixture's moles, temperature and volume with another's. */
    public void copyFrom(GasMixture other) {
        requireSameRegistry(other);
        System.arraycopy(other.moles, 0, moles, 0, moles.length);
        temperature = other.temperature;
        volume = other.volume;
        totalMoles = other.totalMoles;
        rawHeatCapacity = other.rawHeatCapacity;
    }

    // ---- Adding gas ---------------------------------------------------------

    /**
     * Adds another mixture's gas to this one; the other is unchanged. Each gas's
     * moles add (CORE-008) and total thermal energy is conserved, so the
     * temperature becomes the heat-capacity-weighted mean (CORE-002). The volume
     * is unchanged. Returns false, changing nothing, if rejected.
     */
    public boolean merge(GasMixture other) {
        requireSameRegistry(other);
        double capacity = 0;
        for (int i = 0; i < moles.length; i++) {
            double sum = moles[i] + other.moles[i];
            if (!Double.isFinite(sum)) {
                return reject("merge", "moles of " + gasId(i) + " overflow");
            }
            capacity += registry.molarHeatCapacity(i) * sum;
        }
        double mergedTemperature =
                mixedTemperature(rawHeatCapacity, temperature, other.rawHeatCapacity, other.temperature);
        if (!Double.isFinite(capacity) || !validTemperature(mergedTemperature)) {
            return reject("merge", "result is not finite");
        }
        double total = 0;
        for (int i = 0; i < moles.length; i++) {
            total += moles[i] + other.moles[i];
        }
        if (!finiteDerived(total, capacity, mergedTemperature, volume)) {
            return reject("merge", "pressure or thermal energy overflows");
        }
        for (int i = 0; i < moles.length; i++) {
            moles[i] += other.moles[i];
        }
        temperature = mergedTemperature;
        pruneAndRecount();
        return true;
    }

    /**
     * Adds moles of one gas at this mixture's temperature. Returns false,
     * changing nothing, if rejected.
     */
    public boolean addMoles(int gas, double amount) {
        return addMoles(gas, amount, temperature);
    }

    /**
     * Adds moles of one gas at its own temperature, conserving thermal energy as
     * {@link #merge} does. Returns false, changing nothing, if rejected.
     */
    public boolean addMoles(int gas, double amount, double gasTemperature) {
        if (!validAmount(amount) || !validTemperature(gasTemperature)) {
            return reject("addMoles", "amount " + amount + " of " + gasId(gas) + " at " + gasTemperature + " K");
        }
        double sum = moles[gas] + amount;
        double addedCapacity = registry.molarHeatCapacity(gas) * amount;
        double mixed = mixedTemperature(rawHeatCapacity, temperature, addedCapacity, gasTemperature);
        if (!Double.isFinite(sum)
                || !validTemperature(mixed)
                || !finiteDerived(totalMoles + amount, rawHeatCapacity + addedCapacity, mixed, volume)) {
            return reject("addMoles", "amount " + amount + " of " + gasId(gas) + " overflows");
        }
        moles[gas] = sum;
        temperature = mixed;
        pruneAndRecount();
        return true;
    }

    // ---- Removing gas (CORE-004, CORE-060, CORE-061) ------------------------

    /**
     * Removes a fraction of every gas. The fraction is clamped to [0, 1]. The
     * parcel has this mixture's temperature and the share of its volume that the
     * fraction takes. A rejected call (a NaN fraction) returns an empty parcel.
     */
    public GasMixture removeFraction(double fraction) {
        if (Double.isNaN(fraction)) {
            reject("removeFraction", "fraction NaN");
            return emptyParcel(volume);
        }
        double f = Math.min(1, Math.max(0, fraction));
        return removeFractionInto(f, volume * f);
    }

    /**
     * Removes moles of the whole mixture, in proportion to its composition. Asking
     * for more than the mixture holds removes all of it. A rejected call
     * returns an empty parcel.
     */
    public GasMixture removeMoles(double amount) {
        if (!validAmount(amount)) {
            reject("removeMoles", "amount " + amount);
            return emptyParcel(volume);
        }
        if (totalMoles == 0) {
            return emptyParcel(volume);
        }
        double f = Math.min(1, amount / totalMoles);
        return removeFractionInto(f, volume * f);
    }

    /**
     * Removes the gas in a volume of this mixture: the fraction {@code litres /
     * volume()}, clamped to the whole mixture. The parcel's volume is the
     * volume removed. A rejected call returns an empty parcel.
     */
    public GasMixture removeVolume(double litres) {
        if (!validAmount(litres)) {
            reject("removeVolume", "volume " + litres);
            return emptyParcel(volume);
        }
        double taken = Math.min(litres, volume);
        return removeFractionInto(taken / volume, taken);
    }

    /**
     * Removes moles of one gas, clamped to what is present. Returns the moles
     * removed, which leave at this mixture's temperature; 0 if rejected.
     */
    public double removeGas(int gas, double amount) {
        if (!validAmount(amount)) {
            reject("removeGas", "amount " + amount + " of " + gasId(gas));
            return 0;
        }
        double removed = Math.min(amount, moles[gas]);
        moles[gas] = removed == moles[gas] ? 0 : moles[gas] - removed;
        pruneAndRecount();
        return removed;
    }

    private GasMixture removeFractionInto(double f, double parcelVolume) {
        GasMixture parcel = emptyParcel(parcelVolume);
        if (f == 0) {
            return parcel;
        }
        for (int i = 0; i < moles.length; i++) {
            // f <= 1, so the product never exceeds moles[i] and the rest is never negative.
            double removed = f == 1 ? moles[i] : moles[i] * f;
            parcel.moles[i] = removed;
            moles[i] = Math.max(0, moles[i] - removed);
        }
        pruneAndRecount();
        parcel.pruneAndRecount();
        return parcel;
    }

    private GasMixture emptyParcel(double parcelVolume) {
        // A vanishing share of the volume would make an unusable mixture; an
        // empty or near-empty parcel keeps the source volume instead.
        return new GasMixture(registry, config, parcelVolume >= Double.MIN_NORMAL ? parcelVolume : volume, temperature);
    }

    // ---- Thermal energy (CORE-005, CORE-062) --------------------------------

    /**
     * Adds thermal energy, raising the temperature by ΔE/C. Returns the energy
     * added: the request, or 0 if rejected (a negative or non-finite request, or
     * a temperature that would overflow).
     */
    public double addThermalEnergy(double joules) {
        if (!validAmount(joules)) {
            reject("addThermalEnergy", "energy " + joules);
            return 0;
        }
        double raised = temperature + joules / heatCapacity();
        if (!validTemperature(raised) || !finiteDerived(totalMoles, rawHeatCapacity, raised, volume)) {
            reject("addThermalEnergy", "energy " + joules + " J makes the temperature overflow");
            return 0;
        }
        temperature = raised;
        return joules;
    }

    /**
     * Removes thermal energy, lowering the temperature by ΔE/C but never below
     * {@link GasConfig#temperatureFloor()}. Returns the energy actually removed;
     * 0 if rejected or already at or below the floor.
     */
    public double removeThermalEnergy(double joules) {
        if (!validAmount(joules)) {
            reject("removeThermalEnergy", "energy " + joules);
            return 0;
        }
        double floor = config.temperatureFloor();
        if (temperature <= floor) {
            return 0;
        }
        double capacity = heatCapacity();
        double available = (temperature - floor) * capacity;
        if (joules >= available) {
            temperature = floor;
            return available;
        }
        temperature = Math.max(floor, temperature - joules / capacity);
        return joules;
    }

    // ---- Equivalence (CORE-006) ---------------------------------------------

    /** The equivalence test with this mixture's configured tolerances. */
    public boolean isEquivalent(GasMixture other) {
        return isEquivalent(other, config.equivalence());
    }

    /**
     * Whether two mixtures are equivalent: no quantity (temperature, pressure,
     * each gas's molar density) differs by more than both its absolute and its
     * relative tolerance.
     */
    public boolean isEquivalent(GasMixture other, EquivalenceTolerances tolerances) {
        requireSameRegistry(other);
        if (EquivalenceTolerances.differs(
                temperature, other.temperature, tolerances.temperatureAbsolute(), tolerances.temperatureRelative())) {
            return false;
        }
        if (EquivalenceTolerances.differs(
                pressure(), other.pressure(), tolerances.pressureAbsolute(), tolerances.pressureRelative())) {
            return false;
        }
        for (int i = 0; i < moles.length; i++) {
            if (EquivalenceTolerances.differs(
                    molarDensity(i),
                    other.molarDensity(i),
                    tolerances.densityAbsolute(),
                    tolerances.densityRelative())) {
                return false;
            }
        }
        return true;
    }

    // ---- Pruning (CORE-007) -------------------------------------------------

    /**
     * Adds the moles pruned since the last drain, per gas, into {@code sink}
     * (indexed like the registry) and resets the count.
     */
    public void drainPruned(double[] sink) {
        for (int i = 0; i < pruned.length; i++) {
            sink[i] += pruned[i];
            pruned[i] = 0;
        }
    }

    private void pruneAndRecount() {
        double epsilon = config.pruneEpsilon();
        double total = 0;
        double capacity = 0;
        for (int i = 0; i < moles.length; i++) {
            double n = moles[i];
            if (n != 0 && n < epsilon) {
                pruned[i] += n;
                moles[i] = 0;
                continue;
            }
            total += n;
            capacity += registry.molarHeatCapacity(i) * n;
        }
        totalMoles = total;
        rawHeatCapacity = capacity;
    }

    // ---- Helpers ------------------------------------------------------------

    private static double mixedTemperature(
            double capacityA, double temperatureA, double capacityB, double temperatureB) {
        double capacity = capacityA + capacityB;
        if (capacity <= 0) {
            return capacityB > 0 ? temperatureB : temperatureA;
        }
        return (capacityA * temperatureA + capacityB * temperatureB) / capacity;
    }

    /** Whether the current moles, with this temperature and volume, give finite derived values. */
    private boolean stateFinite(double kelvin, double litres) {
        double total = 0;
        double capacity = 0;
        for (int i = 0; i < moles.length; i++) {
            total += moles[i];
            capacity += registry.molarHeatCapacity(i) * moles[i];
        }
        return finiteDerived(total, capacity, kelvin, litres);
    }

    /** Whether total moles, heat capacity, thermal energy and pressure are all finite. */
    private static boolean finiteDerived(double total, double capacity, double kelvin, double litres) {
        return Double.isFinite(total)
                && Double.isFinite(capacity)
                && Double.isFinite(capacity * kelvin)
                && Double.isFinite(total * GasConstants.R * kelvin / litres);
    }

    private static boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }

    private static boolean validTemperature(double kelvin) {
        return Double.isFinite(kelvin) && kelvin >= 0;
    }

    private static boolean validVolume(double litres) {
        return Double.isFinite(litres) && litres > 0;
    }

    private void requireSameRegistry(GasMixture other) {
        if (other.registry != registry && !other.registry.equals(registry)) {
            throw new IllegalArgumentException("mixtures from different gas registries");
        }
    }

    private String gasId(int gas) {
        return registry.definition(gas).id();
    }

    private boolean reject(String operation, String detail) {
        config.log()
                .warn("Rejected GasMixture." + operation + ": " + detail
                        + " (would store a non-finite or invalid value)");
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GasMixture[T=")
                .append(temperature)
                .append(" K, V=")
                .append(volume)
                .append(" L");
        for (int i = 0; i < moles.length; i++) {
            if (moles[i] != 0) {
                sb.append(", ").append(gasId(i)).append('=').append(moles[i]);
            }
        }
        return sb.append(']').toString();
    }
}
