package io.github.west3436.zas.atmos.power;

import io.github.west3436.ss13.api.power.PoweredDevice;
import net.minecraft.world.phys.Vec3;

/**
 * A ZAS³ device that draws FE (CONT-060, CONT-065): SS13 Framework's {@link PoweredDevice}, with the
 * point an area air controller finds it by. Implemented by the block entities of vents, scrubbers,
 * pumps and the rest. The contract, its lease and its claims are the framework's {@link
 * io.github.west3436.ss13.api.power.DevicePower}; architecture §11.3.
 */
public interface PoweredPart extends PoweredDevice {
    /**
     * The point whose zone the device is in, for a provider that powers the parts in its zone
     * (CONT-068): the cell the device samples or acts on, such as the cell in front of a vent's
     * grille.
     */
    Vec3 airPoint();
}
