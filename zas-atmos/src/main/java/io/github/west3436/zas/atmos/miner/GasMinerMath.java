package io.github.west3436.zas.atmos.miner;

import io.github.west3436.zas.api.gas.GasMixture;

/**
 * How much gas a gas miner adds in one step (CONT-180, CONT-181).
 *
 * <p>Gas at temperature T added to a room of volume V raises its pressure by n·R·T / V when every gas
 * has the same molar heat capacity: the mixture's temperature changes, but its n·T sum just adds. So
 * the moles that bring a room from P to the target are (P_target − P)·V / (R·T). Where heat capacities
 * differ the result is a little off, and the next step makes up the rest; the miner never takes gas
 * away, so a step that overshoots stays there.
 */
public final class GasMinerMath {
    private GasMinerMath() {}

    /**
     * The moles to add.
     *
     * @param roomKpa the room's pressure, kPa
     * @param roomLitres the room's volume, L
     * @param targetKpa the pressure to fill to, kPa
     * @param temperature the temperature of the gas added, K
     * @param most the most it may add in this step, mol
     * @return the moles, 0 to {@code most}; 0 if the room is at or above the target
     */
    public static double moles(double roomKpa, double roomLitres, double targetKpa, double temperature, double most) {
        if (!(roomLitres > 0) || !(temperature > 0) || !(roomKpa < targetKpa)) {
            return 0;
        }
        double n = (targetKpa - roomKpa) * roomLitres / (GasMixture.GAS_CONSTANT * temperature);
        return Math.max(0, Math.min(most, n));
    }
}
