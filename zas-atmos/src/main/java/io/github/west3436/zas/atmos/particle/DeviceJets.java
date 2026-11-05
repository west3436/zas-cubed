package io.github.west3436.zas.atmos.particle;

import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentMode;

/**
 * What a client shows for a vent or a scrubber (CLIENT-019), from what the server already tells it: how
 * hard it is working, in the device activity table (DATA-018), and what it is doing, in its block state.
 * So these particles need no sync of their own.
 *
 * <p>Nothing shows below {@link DeviceActivity#WORKING}, so a trickle stays quiet. A vent's way follows
 * its mode, since a vent can siphon as well as blow. A scrubber only ever draws gas in.
 */
public final class DeviceJets {
    /**
     * How far out of a block's middle its particles start, in blocks: just in front of its grille, which
     * is the whole face of a full block.
     */
    public static final double MOUTH = 0.55;

    private DeviceJets() {}

    /**
     * The amount of a vent's {@link GasJetKind#DEVICE} jet.
     *
     * @param mode what its block state says it is doing
     * @param level its activity level
     * @return its level, positive blowing out and negative drawing in; 0 for no jet
     */
    public static double vent(VentMode mode, int level) {
        if (level < DeviceActivity.WORKING) {
            return 0;
        }
        return switch (mode) {
            case OFF -> 0;
            case BLOW -> level;
            case SIPHON -> -level;
        };
    }

    /**
     * The amount of a scrubber's {@link GasJetKind#DEVICE} jet.
     *
     * @param mode what its block state says it is doing
     * @param level its activity level
     * @return its level, negative as it draws in; 0 for no jet
     */
    public static double scrubber(ScrubberMode mode, int level) {
        if (level < DeviceActivity.WORKING) {
            return 0;
        }
        return switch (mode) {
            case OFF -> 0;
            case SCRUB, SIPHON, PANIC -> -level;
        };
    }
}
