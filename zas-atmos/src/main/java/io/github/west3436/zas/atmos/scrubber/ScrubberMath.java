package io.github.west3436.zas.atmos.scrubber;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * What a scrubber takes from the air it samples in one second (CONT-079, CONT-080, CONT-081). It
 * reads only snapshots, so any device that scrubs can use it, whether the air is a zone or a held
 * tank and the buffer is a pipe network or a store of its own.
 *
 * <p>A scrubber samples a volume of the air, which holds that volume's share of each gas, and keeps
 * only the gases it selects. Whatever else the sample held stays where it was, so a scrubber asks
 * for the gases it keeps and nothing else. It stops once its buffer reaches 50 atm, and the sample
 * that would pass that is cut to what reaches it.
 */
public final class ScrubberMath {
    /** The volume a scrubber samples per second, in L (CONT-080). */
    public static final double SAMPLE_LITRES = 500.0;

    /** The volume a scrubber samples per second in panic, in L (CONT-080). */
    public static final double PANIC_LITRES = 1_000.0;

    /** The buffer pressure a scrubber stops at, in kPa: 50 atm (CONT-081). */
    public static final double STALL_KPA = 50 * StandardAir.PRESSURE;

    /** Keeps every gas: siphon and panic keep their whole sample (CONT-079). */
    public static final Predicate<ResourceLocation> EVERY_GAS = gas -> true;

    private ScrubberMath() {}

    /**
     * The share of the air a sample holds: its volume over the air's, and never more than all of it.
     *
     * @param litres the volume sampled, in L
     * @param volume the air's volume, in L
     * @return from 0 to 1
     */
    public static double sampledFraction(double litres, double volume) {
        return litres <= 0 || volume <= 0 ? 0 : Math.min(1, litres / volume);
    }

    /**
     * Whether a buffer has reached the pressure a scrubber stops at (CONT-081).
     *
     * @param buffer the buffer
     * @return true if it is at 50 atm or above
     */
    public static boolean stalled(GasMixture buffer) {
        return buffer.pressure() >= STALL_KPA;
    }

    /**
     * The gas to take from the air in one second.
     *
     * @param air the air sampled, a zone, a cell of the ambient or a tank
     * @param litres the volume sampled this second, in L
     * @param keep which gases the sample keeps: a {@link GasSelection} to scrub, {@link #EVERY_GAS}
     *     to siphon
     * @param buffer where the gas goes, whose pressure stops the scrubber
     * @return the moles to take per gas, sorted by id; empty if there is nothing to take or the
     *     buffer has stalled
     */
    public static Map<ResourceLocation, Double> take(
            GasMixture air, double litres, Predicate<ResourceLocation> keep, GasMixture buffer) {
        TreeMap<ResourceLocation, Double> kept = new TreeMap<>();
        if (stalled(buffer)) {
            return kept;
        }
        double fraction = sampledFraction(litres, air.volume());
        double total = 0;
        for (Map.Entry<ResourceLocation, Double> e : air.moles().entrySet()) {
            if (keep.test(e.getKey())) {
                double n = e.getValue() * fraction;
                if (n > 0) {
                    kept.put(e.getKey(), n);
                    total += n;
                }
            }
        }
        double rt = GasMixture.GAS_CONSTANT * air.temperature();
        if (rt > 0) {
            double room = (STALL_KPA - buffer.pressure()) * buffer.volume() / rt;
            if (total > room) {
                double scale = room / total;
                kept.replaceAll((gas, n) -> n * scale);
            }
        }
        return kept;
    }
}
