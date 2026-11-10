package io.github.west3436.zas.api.gas;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ZasApi;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Gases' thermal properties (CORE-003, API-051), so that a mod holding its own gas, such as a pipe
 * network or a tank, can mix gas at different temperatures as ZAS³ does: conserving thermal
 * energy, which for a gas at a fixed volume is its heat capacity times its temperature.
 *
 * <p>A gas's molar heat capacity comes from its datapack definition (CORE-010): C<sub>v</sub>, in
 * J/(mol·K). A gas the server doesn't define, such as one a removed datapack left in a saved
 * tank, uses {@link #DEFAULT_MOLAR_HEAT_CAPACITY}, so mixing never fails.
 *
 * <p>Queries answer for the server running in this JVM, or else for the server the client is
 * connected to. Without ZAS³ the only gases are those of {@link StandardAir}, with the heat
 * capacities ZAS³ ships for them.
 *
 * <p>Thread: any. Results are immutable.
 */
public final class GasProperties {
    /**
     * The molar heat capacity used for a gas the server doesn't define: 20.8 J/(mol·K), about
     * (5/2)R, a diatomic gas such as N₂ or O₂ near room temperature.
     */
    public static final double DEFAULT_MOLAR_HEAT_CAPACITY = 20.8;

    private GasProperties() {}

    /**
     * Whether the server defines a gas (CORE-010): one of its datapacks holds a definition for the
     * id. A mod that takes gas ids from data of its own, such as a table of another mod's
     * substances and the gases they are, can check an id with it before it puts such gas into the
     * world.
     *
     * <p>Without ZAS³ the only gases defined are those of {@link StandardAir}.
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return true if the gas is defined
     */
    @AnyThread
    public static boolean isDefined(ResourceLocation gas) {
        return ZasApi.provider()
                .molarHeatCapacity(Objects.requireNonNull(gas, "gas"))
                .isPresent();
    }

    /**
     * A gas's molar heat capacity at constant volume.
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return C<sub>v</sub> in J/(mol·K), positive; {@link #DEFAULT_MOLAR_HEAT_CAPACITY} for a gas
     *     the server doesn't define
     */
    @AnyThread
    public static double molarHeatCapacity(ResourceLocation gas) {
        OptionalDouble c = ZasApi.provider().molarHeatCapacity(Objects.requireNonNull(gas, "gas"));
        return c.isPresent() && c.getAsDouble() > 0 && Double.isFinite(c.getAsDouble())
                ? c.getAsDouble()
                : DEFAULT_MOLAR_HEAT_CAPACITY;
    }

    /**
     * The heat capacity of an amount of gas: Σ moles × molar heat capacity (CORE-003).
     *
     * <p>Thread: any.
     *
     * @param moles moles per gas id, none negative
     * @return the heat capacity in J/K; 0 for no gas
     */
    @AnyThread
    public static double heatCapacity(Map<ResourceLocation, Double> moles) {
        double c = 0;
        for (Map.Entry<ResourceLocation, Double> e : moles.entrySet()) {
            double n = e.getValue();
            if (n > 0) {
                c += n * molarHeatCapacity(e.getKey());
            }
        }
        return c;
    }

    /**
     * The heat capacity of a parcel of gas.
     *
     * <p>Thread: any.
     *
     * @param gas the gas
     * @return the heat capacity in J/K; 0 for an empty parcel
     */
    @AnyThread
    public static double heatCapacity(GasParcel gas) {
        return heatCapacity(gas.moles());
    }

    /**
     * Two parcels mixed together, conserving thermal energy: the moles add, and the temperature is
     * the heat-capacity-weighted mean, (C₁T₁ + C₂T₂)/(C₁ + C₂). An empty parcel adds nothing, so
     * mixing with one returns the other's temperature.
     *
     * <p>Thread: any.
     *
     * @param a one parcel
     * @param b the other
     * @return the mixture's gas, sorted by id
     */
    @AnyThread
    public static GasParcel mix(GasParcel a, GasParcel b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (b.isEmpty()) {
            return a;
        }
        if (a.isEmpty()) {
            return b;
        }
        double t = mixedTemperature(heatCapacity(a), a.temperature(), heatCapacity(b), b.temperature());
        TreeMap<ResourceLocation, Double> moles = new TreeMap<>(a.moles());
        b.moles().forEach((gas, n) -> moles.merge(gas, n, Double::sum));
        return new GasParcel(moles, t);
    }

    /**
     * The temperature two bodies of gas settle at when mixed, conserving thermal energy:
     * (C₁T₁ + C₂T₂)/(C₁ + C₂). Equal temperatures give that temperature exactly, and a body with
     * no heat capacity doesn't change the other's.
     *
     * <p>Thread: any.
     *
     * @param c1 the first body's heat capacity in J/K, not negative
     * @param t1 its temperature in K
     * @param c2 the second body's heat capacity in J/K, not negative
     * @param t2 its temperature in K
     * @return the mixed temperature in K; {@code t1} if both capacities are 0
     */
    @AnyThread
    public static double mixedTemperature(double c1, double t1, double c2, double t2) {
        if (t1 == t2 || !(c2 > 0)) {
            return t1;
        }
        if (!(c1 > 0)) {
            return t2;
        }
        return (c1 * t1 + c2 * t2) / (c1 + c2);
    }
}
