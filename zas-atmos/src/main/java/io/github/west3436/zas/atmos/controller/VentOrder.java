package io.github.west3436.zas.atmos.controller;

import net.minecraft.core.BlockPos;

/**
 * What an area air controller tells its vents (CONT-041): off, blow until the zone reaches a pressure,
 * or siphon until it falls to one, that pressure being the external bound in kPa (CONT-014), and who
 * sent it.
 *
 * <p>The modes' orders ({@link ControllerMode#vent}) have no sender; a controller adds its own
 * position with {@link #from} before it gives one, so a vent knows its controller from the first
 * order, for its GUI (CONT-074).
 *
 * @param mode what the vent does
 * @param targetKpa the pressure to blow or siphon to, 0 to 5,066 kPa; 0 when off
 * @param sender where the controller that gave the order is; null if it isn't known
 */
public record VentOrder(Mode mode, double targetKpa, BlockPos sender) {
    /** A vent's modes as a controller sets them. */
    public enum Mode {
        OFF,
        BLOW,
        SIPHON
    }

    /** The vent is off. */
    public static final VentOrder OFF = new VentOrder(Mode.OFF, 0, null);

    public VentOrder {
        if (!Double.isFinite(targetKpa)) {
            throw new IllegalArgumentException("target must be finite: " + targetKpa);
        }
        targetKpa = mode != Mode.OFF ? Math.max(0, Math.min(Thresholds.MAX, targetKpa)) : 0;
        sender = sender == null ? null : sender.immutable();
    }

    /** Whether the vent runs. */
    public boolean on() {
        return mode != Mode.OFF;
    }

    /** Blow to a pressure. */
    public static VentOrder blow(double targetKpa) {
        return new VentOrder(Mode.BLOW, targetKpa, null);
    }

    /** Siphon down to a pressure. */
    public static VentOrder siphon(double targetKpa) {
        return new VentOrder(Mode.SIPHON, targetKpa, null);
    }

    /** The same order, sent by the controller at a position. */
    public VentOrder from(BlockPos controller) {
        return new VentOrder(mode, targetKpa, controller);
    }
}
