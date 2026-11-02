package io.github.west3436.zas.life;

import net.minecraft.world.entity.LivingEntity;

/**
 * What an entity's protection does to the pressure and temperature it is exposed to: pressure
 * protection (LIFE-020) and thermal protection (LIFE-025). {@link EnvironmentHazards} asks it for
 * the factors at the exposure's pressure and temperature. An entity's comes from what it wears and
 * its own life support ({@link WornProtection}).
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
     * pressure, 0 to 1: LIFE-020's (1 − c⁵).
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

    /** Whether any protection is rated for a pressure in kPa ({@link io.github.west3436.zas.api.life.Protection#PRESSURE}). */
    default boolean protectsAt(double pressure) {
        return pressureFactor(pressure) < 1;
    }

    /** Whether any protection's thermal range holds a temperature in K ({@link io.github.west3436.zas.api.life.Protection#THERMAL}). */
    default boolean protectsFrom(double temperature) {
        return thermalFactor(temperature) < 1;
    }

    /** An entity's shielding where it is now. Server thread. */
    static Shielding of(LivingEntity entity) {
        return WornProtection.of(entity);
    }
}
