package io.github.west3436.zas.atmos.filter;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * A filter's step (CONT-013): it takes an even slice of its input, sends the selected gases to the
 * side outlet and the rest to the output, and stops when either outlet reaches its target. Nothing
 * here touches the world, so it is unit-tested.
 *
 * <p><b>Moles.</b> A slice is the least of:
 *
 * <ul>
 *   <li>100 L/s at the input's conditions, the same limit as a pressure pump (CONT-076);
 *   <li>the slice that brings the output to the target: if a part p of the slice goes there, that
 *       is ΔP·V/(R·T<sub>in</sub>)/p moles, with V the output's network's volume, counted once (as
 *       CONT-011 does);
 *   <li>the same for the side outlet.
 * </ul>
 *
 * <p>Gas moves at the input's temperature and mixes into each outlet conserving thermal energy, as
 * everything added to a {@link GasStore} does (CONT-008). The target is exact when the gases' heat
 * capacities match; otherwise an outlet can land a little off it, and the next step corrects a
 * shortfall.
 */
public final class FilterMath {
    /** The highest target, kPa (CONT-013). */
    public static final double MAX_TARGET = 4_500;

    /** A new filter's target, kPa: one atmosphere. */
    public static final double DEFAULT_TARGET = 101.325;

    /** The most a filter takes from its input per second, L, at the input's conditions. */
    public static final double LITRES_PER_SECOND = 100;

    private FilterMath() {}

    /** A target clamped to 0–4,500 kPa; NaN is 0. */
    public static double clampTarget(double kpa) {
        return Double.isNaN(kpa) ? 0 : Math.max(0, Math.min(MAX_TARGET, kpa));
    }

    /**
     * The moles a filter takes from its input this step.
     *
     * @param in the input's gas
     * @param out the output's gas
     * @param side the side outlet's gas
     * @param selected whether a gas leaves by the side outlet
     * @param target the target, kPa, clamped to 0–4,500
     * @param seconds the step's length, s
     * @return the moles, from 0 to all the input holds; 0 once either outlet is at the target
     */
    public static double moles(
            GasStore in,
            GasStore out,
            GasStore side,
            Predicate<ResourceLocation> selected,
            double target,
            double seconds) {
        double total = in.totalMoles();
        double temperature = in.temperature();
        if (!(total > 0) || !(temperature > 0) || !(seconds > 0)) {
            return 0;
        }
        double kpa = clampTarget(target);
        if (out.pressure() >= kpa || side.pressure() >= kpa) {
            return 0;
        }
        double chosen = 0;
        for (Map.Entry<ResourceLocation, Double> e : in.moles().entrySet()) {
            if (selected.test(e.getKey())) {
                chosen += e.getValue();
            }
        }
        double rate = total * Math.min(1, LITRES_PER_SECOND * seconds / in.volume());
        double byOut = room(out, kpa, temperature, (total - chosen) / total);
        double bySide = room(side, kpa, temperature, chosen / total);
        return Math.min(rate, Math.min(byOut, bySide));
    }

    /**
     * The slice that brings an outlet to the target, when a part of the slice goes to it.
     *
     * @return the slice's moles; infinite if none of the slice goes to the outlet
     */
    private static double room(GasStore outlet, double target, double temperature, double part) {
        if (!(part > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        return (target - outlet.pressure()) * outlet.volume() / (GasMixture.GAS_CONSTANT * temperature) / part;
    }

    /**
     * Moves an even slice of the input to the outlets: the selected gases to the side outlet and the
     * rest to the output, at the input's temperature. Any two of the stores may be one network: the
     * gas leaves before any arrives, so none is lost or counted twice.
     *
     * @param moles the slice's moles, as {@link #moles} gives
     * @param in the input's gas
     * @param out the output's gas
     * @param side the side outlet's gas
     * @param selected whether a gas leaves by the side outlet
     */
    public static void move(
            double moles, GasStore in, GasStore out, GasStore side, Predicate<ResourceLocation> selected) {
        double total = in.totalMoles();
        if (!(moles > 0) || !(total > 0)) {
            return;
        }
        GasParcel slice = in.takeFraction(moles / total);
        Map<ResourceLocation, Double> toSide = new TreeMap<>();
        Map<ResourceLocation, Double> toOut = new TreeMap<>();
        slice.moles().forEach((gas, n) -> (selected.test(gas) ? toSide : toOut).put(gas, n));
        side.add(toSide, slice.temperature());
        out.add(toOut, slice.temperature());
    }
}
