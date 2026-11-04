package io.github.west3436.zas.atmos.filter;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pump.PumpFlow;
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
 *   <li>the slice whose part for the output brings the output to the target, as a pressure pump
 *       works that out ({@link PumpFlow#molesToTarget}): with the output's network's volume counted
 *       once, and allowing for the temperature it settles at;
 *   <li>the same for the side outlet.
 * </ul>
 *
 * <p>Gas moves at the input's temperature and mixes into each outlet conserving thermal energy, as
 * everything added to a {@link GasStore} does (CONT-008).
 */
public final class FilterMath {
    private FilterMath() {}

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
        if (!(in.totalMoles() > 0) || !(in.temperature() > 0) || !(seconds > 0)) {
            return 0;
        }
        double kpa = PumpFlow.clampTarget(target);
        if (out.pressure() >= kpa || side.pressure() >= kpa) {
            return 0;
        }
        Map<ResourceLocation, Double> toSide = new TreeMap<>();
        Map<ResourceLocation, Double> toOut = new TreeMap<>();
        in.moles().forEach((gas, n) -> (selected.test(gas) ? toSide : toOut).put(gas, n));
        double rate = PumpFlow.molesInVolume(PumpFlow.MAX_RATE * seconds, PumpFlow.Side.of(in));
        return Math.min(rate, Math.min(sliceToTarget(toOut, in, out, kpa), sliceToTarget(toSide, in, side, kpa)));
    }

    /**
     * The slice whose part for an outlet brings that outlet to the target.
     *
     * @param part the gases of the input that go to the outlet
     * @return the slice's moles; infinite if none of the input goes to the outlet
     */
    private static double sliceToTarget(
            Map<ResourceLocation, Double> part, GasStore in, GasStore outlet, double target) {
        double moles = 0;
        for (double n : part.values()) {
            moles += n;
        }
        if (!(moles > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        PumpFlow.Side gas = new PumpFlow.Side(moles, GasProperties.heatCapacity(part), in.temperature(), in.volume());
        return PumpFlow.molesToTarget(target, gas, PumpFlow.Side.of(outlet)) * in.totalMoles() / moles;
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
