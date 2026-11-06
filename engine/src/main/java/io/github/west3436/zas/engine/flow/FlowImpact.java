package io.github.west3436.zas.engine.flow;

/**
 * What an entity airflow throws against a block takes (FLOW-005): vanilla's fly-into-wall formula,
 * from the horizontal speed the block took off it. Immutable; any thread.
 */
public final class FlowImpact {
    /** The speed in blocks per tick that must be lost for an impact to do any damage. */
    public static final double LEAST_SPEED_LOST = 0.3;

    private FlowImpact() {}

    /**
     * The horizontal speed a move at (vx, vz) loses when a block stops it along an axis.
     *
     * @param vx its speed along x, in blocks per tick
     * @param vz its speed along z, in blocks per tick
     * @param stoppedX whether a block stops it along x
     * @param stoppedZ whether a block stops it along z
     * @return the speed lost, in blocks per tick
     */
    public static double speedLost(double vx, double vz, boolean stoppedX, boolean stoppedZ) {
        return Math.hypot(vx, vz) - Math.hypot(stoppedX ? 0 : vx, stoppedZ ? 0 : vz);
    }

    /**
     * The damage of an impact, before armor and the hazard's scale: speed lost × 10 − 3, and never
     * below 0.
     *
     * @param speedLost the speed lost, in blocks per tick
     * @return the damage in HP
     */
    public static double damage(double speedLost) {
        return Math.max(0, (speedLost - LEAST_SPEED_LOST) * 10);
    }
}
