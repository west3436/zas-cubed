package io.github.west3436.zas.life;

import net.minecraft.world.entity.LivingEntity;

/**
 * What an entity's protection does to the pressure and temperature it is exposed to: the hook that
 * pressure protection (LIFE-020) and thermal protection (LIFE-025) fill in. {@link
 * EnvironmentHazards} asks it for the factors at the exposure's pressure and temperature.
 *
 * <p>Until worn protection is built (#36), every entity is {@link #NONE unshielded}: the whole
 * pressure difference reaches it, and temperature damage is dealt in full.
 */
public interface Shielding {
    /** No protection: both factors are 1. */
    Shielding NONE = new Shielding() {
        @Override
        public double pressureFactor(double pressure) {
            return 1;
        }

        @Override
        public double thermalFactor(double temperature) {
            return 1;
        }
    };

    /**
     * The fraction of the pressure difference |P − 101.325 kPa| that still reaches the entity at a
     * pressure, 0 to 1: LIFE-020's (1 − c⁵), with the helmetless floor.
     *
     * @param pressure the pressure around it in kPa
     */
    double pressureFactor(double pressure);

    /**
     * The fraction of temperature damage dealt at a temperature, 0 to 1: LIFE-025's (1 − c).
     *
     * @param temperature the exposure temperature in K
     */
    double thermalFactor(double temperature);

    /** An entity's shielding where it is now. Server thread. */
    static Shielding of(LivingEntity entity) {
        return NONE;
    }
}
