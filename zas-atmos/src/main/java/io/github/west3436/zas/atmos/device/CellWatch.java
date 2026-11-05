package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Optional;

/**
 * Whether a device can trust its reading of the cell it acts on. A device that exchanges gas with
 * a cell waits {@link #SETTLE_STEPS} atmos ticks after it loads, then acts only on a cell that reads
 * the same state and zone as at its previous step, and is zoned, paused or the ambient.
 *
 * <p>A paused zone's reading is its last-known state, which is real and doesn't change (GEO-035).
 * A device acts on it as on any zone: what it sends is voided and what it draws is supplied
 * (GEO-036, GEO-038), so a pipe network keeps running and no pressure builds up behind the zone
 * (D-43).
 *
 * <p>An unresolved cell's reading is only a guess, so a device holds there, as a breather's check
 * does (LIFE-048). A chunk's zones reach the framework's published state a little after the chunk
 * loads, and until then an enclosed cell could read as the ambient (#153). A device that trusted
 * such a reading would vent a sealed room's gas into the "ambient", or fill it as if it were open.
 * #154 fixed that in the framework, which now reads such a cell as unresolved; the wait stays as
 * defence in depth.
 */
public final class CellWatch {
    /** The atmos ticks a device waits after it loads before it acts. */
    public static final int SETTLE_STEPS = 3;

    private int settling = SETTLE_STEPS;
    private boolean settled;
    private AtmosphereState lastState;
    private Optional<ZoneId> lastZone = Optional.empty();

    /** Starts waiting again, as when the device loads. */
    public void reset() {
        settling = SETTLE_STEPS;
        settled = false;
        lastState = null;
        lastZone = Optional.empty();
    }

    /**
     * Counts one atmos tick of the wait; call it once per step, whether or not the device reads its
     * cell. The device acts from the step after the {@link #SETTLE_STEPS}th.
     */
    public void step() {
        settled = settling == 0;
        if (settling > 0) {
            settling--;
        }
    }

    /**
     * Records a reading of the cell and says whether to act on it.
     *
     * @param sample the cell's reading this step
     * @return true if the wait is over, the cell reads as at the previous step, and it is zoned,
     *     paused or the ambient
     */
    public boolean trust(AtmosphereSample sample) {
        boolean steady = steady(sample);
        lastState = sample.state();
        lastZone = sample.zone();
        return settled && steady && actsOn(sample);
    }

    /**
     * What {@link #trust} would say of this reading at the next step, recording nothing.
     *
     * @param sample the cell's reading now
     * @return true if the wait will be over, and the cell reads as at the last step and is zoned,
     *     paused or the ambient
     */
    public boolean wouldTrust(AtmosphereSample sample) {
        return settling == 0 && steady(sample) && actsOn(sample);
    }

    private boolean steady(AtmosphereSample sample) {
        return sample.state() == lastState && sample.zone().equals(lastZone);
    }

    private static boolean actsOn(AtmosphereSample sample) {
        return sample.state() != AtmosphereState.UNRESOLVED;
    }
}
