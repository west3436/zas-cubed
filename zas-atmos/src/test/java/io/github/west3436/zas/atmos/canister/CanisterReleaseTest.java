package io.github.west3436.zas.atmos.canister;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import org.junit.jupiter.api.Test;

/** A canister's release valve (CONT-084): against the zone's real volume. */
class CanisterReleaseTest {
    private static final double R = GasMixture.GAS_CONSTANT;
    private static final double T = 293.15;
    private static final double V = PipeVolumes.CANISTER;

    private static double moles(double kPa, double volume) {
        return kPa * volume / (R * T);
    }

    @Test
    void cont_084_release_brings_the_zone_to_the_release_pressure_by_its_real_volume() {
        double canister = moles(4_559.6, V);
        for (double zoneVolume : new double[] {27_000, 75_000}) {
            double zone = moles(80, zoneVolume);
            double n = CanisterRelease.moles(canister, V, zone, zoneVolume, 80, T, 101.325);
            assertEquals(moles(21.325, zoneVolume), n, 1e-9);
        }
    }

    @Test
    void cont_084_release_never_goes_past_equal_pressure() {
        // A canister barely above a big room, set to fill it far higher: it only equalises.
        double zoneVolume = 75_000;
        double canister = moles(150, V);
        double zone = moles(100, zoneVolume);
        double n = CanisterRelease.moles(canister, V, zone, zoneVolume, 100, T, 1_000);
        double canisterAfter = (canister - n) * R * T / V;
        double zoneAfter = (zone + n) * R * T / zoneVolume;
        assertEquals(canisterAfter, zoneAfter, 1e-9);
    }

    @Test
    void cont_084_nothing_is_released_at_or_above_the_release_pressure() {
        double canister = moles(4_559.6, V);
        assertEquals(0, CanisterRelease.moles(canister, V, moles(101.325, 27_000), 27_000, 101.325, T, 101.325));
        assertEquals(0, CanisterRelease.moles(canister, V, moles(120, 27_000), 27_000, 120, T, 101.325));
        assertEquals(0, CanisterRelease.moles(0, V, 0, 27_000, 0, T, 101.325));
    }

    @Test
    void cont_084_release_pressure_is_clamped_to_10_to_1013_kpa() {
        assertEquals(10, CanisterRelease.clamp(0));
        assertEquals(1_013, CanisterRelease.clamp(5_000));
        assertEquals(500, CanisterRelease.clamp(500));
        assertEquals(CanisterRelease.DEFAULT_KPA, CanisterRelease.clamp(Double.NaN));
    }
}
