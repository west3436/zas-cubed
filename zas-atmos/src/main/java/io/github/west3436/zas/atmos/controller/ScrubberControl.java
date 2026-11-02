package io.github.west3436.zas.atmos.controller;

/**
 * A device an area air controller commands as a scrubber (CONT-041, CONT-042, CONT-079). The
 * scrubber (CONT-015) implements it by mapping the order onto its own mode and gas selection.
 */
public interface ScrubberControl {
    /**
     * Applies the controller's order. Called once per atmos tick by each controller that controls
     * the device, on the server thread; the same order may come again every tick.
     */
    void scrubberOrder(ScrubberOrder order);
}
