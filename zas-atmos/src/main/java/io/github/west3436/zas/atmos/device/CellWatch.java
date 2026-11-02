package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Optional;

/**
 * Whether a device can trust its reading of the cell it acts on. A device that exchanges gas with
 * a cell waits {@link #SETTLE_STEPS} atmos ticks after it loads, then acts only on a cell that reads
 * the same state and zone as at its previous step, and is zoned or the ambient.
 *
 * <p>A chunk's zones reach the framework's published state a little after the chunk loads, and
 * until then an enclosed cell could read as the ambient (#153). A device that trusted such a
 * reading would vent a sealed room's gas into the "ambient", or fill it as if it were open. #154
 * fixed that in the framework, which now reads such a cell as unresolved; the wait stays as
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
     * @return true if the wait is over, the cell reads as at the previous step, and it is zoned or
     *     the ambient
     */
    public boolean trust(AtmosphereSample sample) {
        boolean steady = sample.state() == lastState && sample.zone().equals(lastZone);
        lastState = sample.state();
        lastZone = sample.zone();
        return settled
                && steady
                && (sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.AMBIENT);
    }
}
