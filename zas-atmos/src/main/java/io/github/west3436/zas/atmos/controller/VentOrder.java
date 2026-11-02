package io.github.west3436.zas.atmos.controller;

/**
 * What an area air controller tells its vents (CONT-041): off, or blow until the zone reaches a
 * pressure, its external bound in kPa (CONT-014).
 *
 * @param on whether the vent runs
 * @param targetKpa the pressure to blow to, 0 to 5,066 kPa; 0 when off
 */
public record VentOrder(boolean on, double targetKpa) {
    /** The vent is off. */
    public static final VentOrder OFF = new VentOrder(false, 0);

    public VentOrder {
        if (!Double.isFinite(targetKpa)) {
            throw new IllegalArgumentException("target must be finite: " + targetKpa);
        }
        targetKpa = on ? Math.max(0, Math.min(Thresholds.MAX, targetKpa)) : 0;
    }

    /** Blow to a pressure. */
    public static VentOrder blow(double targetKpa) {
        return new VentOrder(true, targetKpa);
    }
}
