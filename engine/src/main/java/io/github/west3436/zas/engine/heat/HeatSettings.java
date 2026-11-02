package io.github.west3436.zas.engine.heat;

import io.github.west3436.zas.engine.gas.GasConstants;

/**
 * The settings of conduction and radiation (HEAT-001 to HEAT-015), carried by every batch so a
 * config change applies from a batch boundary.
 *
 * @param enabled whether zones conduct and radiate heat at all
 * @param fallbackTransmittance U, in W/(m²·K) per block-thick layer, of a block with none defined
 *     (HEAT-015); default 1.7, as stone
 * @param maxThickness how many blocks a face's far side is looked for through (HEAT-011); default 3
 * @param nearVacuumDensity the molar density, in mol/L, below which a zone neither conducts nor
 *     radiates (HEAT-008); default 1 % of standard air's
 * @param emissivity the emissivity of a wall's outer surface facing a vacuum (HEAT-004); default 0.9
 */
public record HeatSettings(
        boolean enabled, double fallbackTransmittance, int maxThickness, double nearVacuumDensity, double emissivity) {
    /** Standard air's molar density, in mol/L: 101.325 kPa at 293.15 K (ENV-011). */
    public static final double STANDARD_AIR_DENSITY = 101.325 / (GasConstants.R * 293.15);

    /** The defaults: on, U 1.7, 3 blocks, 1 % of standard air, ε 0.9. */
    public static final HeatSettings DEFAULT = new HeatSettings(true, 1.7, 3, 0.01 * STANDARD_AIR_DENSITY, 0.9);

    /** No conduction or radiation. */
    public static final HeatSettings OFF = DEFAULT.withEnabled(false);

    public HeatSettings {
        if (!(fallbackTransmittance > 0) || !Double.isFinite(fallbackTransmittance)) {
            throw new IllegalArgumentException("fallback transmittance must be positive: " + fallbackTransmittance);
        }
        if (maxThickness < 1 || maxThickness > 16) {
            throw new IllegalArgumentException("wall thickness must be 1 to 16 blocks: " + maxThickness);
        }
        if (!(nearVacuumDensity >= 0) || !Double.isFinite(nearVacuumDensity)) {
            throw new IllegalArgumentException("near-vacuum density must be finite and >= 0: " + nearVacuumDensity);
        }
        if (!(emissivity >= 0 && emissivity <= 1)) {
            throw new IllegalArgumentException("emissivity must be 0 to 1: " + emissivity);
        }
    }

    public HeatSettings withEnabled(boolean on) {
        return new HeatSettings(on, fallbackTransmittance, maxThickness, nearVacuumDensity, emissivity);
    }

    public HeatSettings withFallbackTransmittance(double u) {
        return new HeatSettings(enabled, u, maxThickness, nearVacuumDensity, emissivity);
    }

    public HeatSettings withMaxThickness(int blocks) {
        return new HeatSettings(enabled, fallbackTransmittance, blocks, nearVacuumDensity, emissivity);
    }

    public HeatSettings withNearVacuumDensity(double molPerLitre) {
        return new HeatSettings(enabled, fallbackTransmittance, maxThickness, molPerLitre, emissivity);
    }

    public HeatSettings withEmissivity(double e) {
        return new HeatSettings(enabled, fallbackTransmittance, maxThickness, nearVacuumDensity, e);
    }

    /**
     * Whether a change from {@code other} to these settings changes which links each face has or
     * their conductance, so the cached links must be rebuilt.
     */
    public boolean changesLinks(HeatSettings other) {
        return other.fallbackTransmittance != fallbackTransmittance || other.maxThickness != maxThickness;
    }
}
