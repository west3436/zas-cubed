package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * What a portable scrubber takes from the air in one second (CONT-018). It scrubs as a wall scrubber
 * does ({@link ScrubberMath}, with the same stop at 50 atm), but samples more of the air and is
 * limited in what it moves: the gases it keeps from {@link #SAMPLE_LITRES} of the air, up to the
 * moles of {@link #MOVE_LITRES} of it.
 */
public final class PortableScrubberMath {
    /** The volume it samples per second, L: vg's 5,000 L per 2 s tick (D-20). */
    public static final double SAMPLE_LITRES = 2_500;

    /** The most it moves per second, as the moles of this volume of the air, L: vg's 300 L per 2 s tick (D-20). */
    public static final double MOVE_LITRES = 150;

    private PortableScrubberMath() {}

    /**
     * The gas to take from the air in one second.
     *
     * @param air the air: a zone or a held tank
     * @param keep the gases it keeps
     * @param store its store, whose pressure stops it
     * @return the moles to take per gas, sorted by id; empty if there is nothing to take or the store
     *     has stalled
     */
    public static Map<ResourceLocation, Double> take(
            GasMixture air, Predicate<ResourceLocation> keep, GasMixture store) {
        TreeMap<ResourceLocation, Double> take = new TreeMap<>(ScrubberMath.take(air, SAMPLE_LITRES, keep, store));
        double total = take.values().stream().mapToDouble(Double::doubleValue).sum();
        double most = air.totalMoles() * ScrubberMath.sampledFraction(MOVE_LITRES, air.volume());
        if (total > most) {
            double scale = most / total;
            take.replaceAll((gas, n) -> n * scale);
        }
        return take;
    }
}
