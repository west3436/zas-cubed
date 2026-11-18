package io.github.west3436.zas.atmos.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * What a comparator reads from this mod's blocks (CONT-174 to CONT-176), on one scale.
 *
 * <ul>
 *   <li><b>A container</b> reads how full it is: 0 only when it holds nothing, otherwise ⌈15 ×
 *       share⌉, so anything at all reads at least 1 and 15 means more than 14/15 full. For gas, full
 *       is the pressure above which the container starts to fail: a canister's or a large tank's
 *       rating, 9,120 kPa, where it ruptures (CONT-101), so 15 is about to fail. A portable pump's or
 *       scrubber's store, which never fails, is full at 50 atm, where its window's bar ends and a
 *       scrubber stalls. A battery is full at its capacity, a water tank at its, and a tank holder
 *       with all its places taken.
 *   <li><b>A reader</b>, the gauge and the air sensor, reads where its dial points: ⌊15 × along +
 *       ½⌋, 0 at rest and 15 at the top of its scale.
 * </ul>
 *
 * <p>A block whose signal comes from what its step reads tells its comparators when that changes,
 * with an {@link Output}.
 */
public final class Comparators {
    /** The strongest signal. */
    public static final int MAX = 15;

    private Comparators() {}

    /**
     * A container's signal for how full it is.
     *
     * @param share what it holds over what it holds when full; above 1 is full
     * @return 0 for nothing (or no number), otherwise 1 to 15
     */
    public static int fill(double share) {
        if (!(share > 0)) {
            return 0;
        }
        return (int) Math.max(1, Math.min(MAX, Math.ceil(MAX * share - 1e-9)));
    }

    /**
     * A gas container's signal: its pressure as a share of the pressure above which it fails.
     *
     * @param kPa its pressure
     * @param limitKPa the pressure above which it fails
     */
    public static int pressure(double kPa, double limitKPa) {
        return fill(kPa / limitKPa);
    }

    /**
     * A reader's signal: where its dial points.
     *
     * @param along 0 at rest to 1 at the top; outside that is clamped
     */
    public static int dial(double along) {
        if (!(along > 0)) {
            return 0;
        }
        return (int) Math.floor(MAX * Math.min(1, along) + 0.5);
    }

    /**
     * The last signal a block told its comparators of, so it tells them again only when it changes.
     * Not saved: after a load the first step tells them, which changes nothing for a comparator that
     * already reads the same.
     */
    public static final class Output {
        private int last = -1;

        /**
         * Tells the comparators around a block if its signal is not the one they were last told of.
         *
         * @param level the block's level, server side
         * @param pos the block
         * @param block the block, as the comparators look for it
         * @param signal its signal now
         */
        public void update(Level level, BlockPos pos, Block block, int signal) {
            if (changes(signal)) {
                level.updateNeighbourForOutputSignal(pos, block);
            }
        }

        /**
         * Whether a signal is not the one last told of, keeping it as told: for a block whose
         * comparators sit at more than one position.
         */
        public boolean changes(int signal) {
            if (signal == last) {
                return false;
            }
            last = signal;
            return true;
        }
    }
}
