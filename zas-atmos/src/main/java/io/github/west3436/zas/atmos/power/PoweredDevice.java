package io.github.west3436.zas.atmos.power;

import net.minecraft.world.phys.Vec3;

/**
 * A device that draws FE (CONT-060, CONT-065): implemented by the block entities of vents,
 * scrubbers, pumps and the rest, and found by a provider through {@link
 * io.github.west3436.zas.atmos.device.DeviceTicker#loaded}. The contract is in {@link DevicePower}
 * and architecture §11.3.
 */
public interface PoweredDevice {
    /** The device's power state. */
    DevicePower power();

    /**
     * The point whose zone the device is in, for a provider that powers the parts in its zone
     * (CONT-068): the cell the device samples or acts on, such as the cell in front of a vent's
     * grille.
     */
    Vec3 airPoint();
}
