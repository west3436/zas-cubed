package io.github.west3436.zas.atmos.controller;

import net.minecraft.core.BlockPos;

/**
 * What an area air controller tells its vents (CONT-041): off, or blow until the zone reaches a
 * pressure, its external bound in kPa (CONT-014), and who sent it.
 *
 * <p>The modes' orders ({@link ControllerMode#vent}) have no sender; a controller adds its own
 * position with {@link #from} before it gives one, so a vent knows its controller from the first
 * order, for its GUI (CONT-074).
 *
 * @param on whether the vent runs
 * @param targetKpa the pressure to blow to, 0 to 5,066 kPa; 0 when off
 * @param sender where the controller that gave the order is; null if it isn't known
 */
public record VentOrder(boolean on, double targetKpa, BlockPos sender) {
    /** The vent is off. */
    public static final VentOrder OFF = new VentOrder(false, 0);

    public VentOrder {
        if (!Double.isFinite(targetKpa)) {
            throw new IllegalArgumentException("target must be finite: " + targetKpa);
        }
        targetKpa = on ? Math.max(0, Math.min(Thresholds.MAX, targetKpa)) : 0;
        sender = sender == null ? null : sender.immutable();
    }

    /** An order with no sender. */
    public VentOrder(boolean on, double targetKpa) {
        this(on, targetKpa, null);
    }

    /** Blow to a pressure. */
    public static VentOrder blow(double targetKpa) {
        return new VentOrder(true, targetKpa);
    }

    /** The same order, sent by the controller at a position. */
    public VentOrder from(BlockPos controller) {
        return new VentOrder(on, targetKpa, controller);
    }
}
