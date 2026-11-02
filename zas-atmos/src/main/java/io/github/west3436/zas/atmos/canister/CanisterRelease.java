package io.github.west3436.zas.atmos.canister;

import io.github.west3436.zas.api.gas.GasMixture;

/**
 * How much a canister's open valve releases in one atmos tick (CONT-084): enough to bring the
 * receiving zone to the release pressure, computed against the zone's real volume, but never so
 * much that the zone ends above the canister.
 */
public final class CanisterRelease {
    /** The lowest release pressure, kPa. */
    public static final double MIN_KPA = 10.0;

    /** The highest release pressure, kPa. */
    public static final double MAX_KPA = 1013.0;

    /** The release pressure a new canister has, kPa: one atmosphere. */
    public static final double DEFAULT_KPA = 101.325;

    private CanisterRelease() {}

    /** A release pressure clamped to {@link #MIN_KPA}–{@link #MAX_KPA}; NaN gives the default. */
    public static double clamp(double kPa) {
        return Double.isNaN(kPa) ? DEFAULT_KPA : Math.max(MIN_KPA, Math.min(MAX_KPA, kPa));
    }

    /**
     * The moles to release.
     *
     * <p>With the canister's network at n_c moles in V_c, and the zone at n_z moles and pressure
     * P_z in V_z: (P_release − P_z)·V_z/(R·T) moles bring the zone to the release pressure, and
     * (n_c·V_z − n_z·V_c)/(V_c + V_z) equalise the two (both at temperature T). The release is the
     * smaller, and nothing when the zone is at or above either.
     *
     * @param canisterMoles n_c
     * @param canisterVolume V_c, L
     * @param zoneMoles n_z
     * @param zoneVolume V_z, L: the zone's, or one cell's for the ambient
     * @param zonePressure P_z, kPa
     * @param temperature T, K: the canister's gas
     * @param release the release pressure, kPa
     * @return the moles to release, at least 0
     */
    public static double moles(
            double canisterMoles,
            double canisterVolume,
            double zoneMoles,
            double zoneVolume,
            double zonePressure,
            double temperature,
            double release) {
        if (canisterMoles <= 0 || temperature <= 0 || zoneVolume <= 0) {
            return 0;
        }
        double toTarget = (release - zonePressure) * zoneVolume / (GasMixture.GAS_CONSTANT * temperature);
        double toEqual = (canisterMoles * zoneVolume - zoneMoles * canisterVolume) / (canisterVolume + zoneVolume);
        return Math.max(0, Math.min(Math.min(toTarget, toEqual), canisterMoles));
    }
}
