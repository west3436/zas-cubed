package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.heater.ThermoCoil;

/**
 * A device an area air controller's thermostat moves heat through (CONT-045, CONT-166, CONT-167): a
 * vent heats the gas it blows, and a scrubber cools the gas it handles. Each has a {@link ThermoCoil}
 * that the controller powering it orders heat from once per atmos tick, after paying for its parts,
 * and pays for as it orders.
 */
public interface ThermostatControl {
    /** The device's coil. */
    ThermoCoil coil();

    /**
     * Whether the device would move heat now if it were powered: a vent blowing, a scrubber in any
     * mode but off. Asked after the controller's mode order, which sets that.
     */
    boolean thermostatReady();
}
