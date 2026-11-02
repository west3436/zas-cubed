package io.github.west3436.zas.atmos.controller;

/**
 * A device an area air controller commands as a vent (CONT-041, CONT-042). The vent (CONT-014)
 * implements it by mapping the order onto its own settings: {@link VentOrder#OFF} to off, and
 * {@link VentOrder#blow} to blowing with that external bound.
 */
public interface VentControl {
    /**
     * Applies the controller's order. Called once per atmos tick by each controller that controls
     * the device, on the server thread; the same order may come again every tick.
     */
    void ventOrder(VentOrder order);
}
