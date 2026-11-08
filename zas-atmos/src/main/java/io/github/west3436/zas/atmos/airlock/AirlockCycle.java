package io.github.west3436.zas.atmos.airlock;

/**
 * The rules of an airlock controller's cycle (CONT-139), with no game classes: what its vents do
 * at each step, and which side of an airlock is the chamber.
 */
public final class AirlockCycle {
    /** How close the chamber must come to the far side's pressure, kPa: 5 % of 1 atm (CONT-041). */
    public static final double TOLERANCE_KPA = 0.05 * 101.325;

    /** What a cycle does at a step. */
    public enum Step {
        /** The chamber is above the far side: the vents siphon down to it. */
        SIPHON,
        /** The chamber is below the far side: the vents blow up to it. */
        BLOW,
        /** The chamber is within {@link #TOLERANCE_KPA} of the far side: the airlock opens. */
        DONE
    }

    private AirlockCycle() {}

    /**
     * The step for a chamber at {@code chamberKpa} cycling toward a side at {@code targetKpa}.
     *
     * @param chamberKpa the pressure the air sensor reads
     * @param targetKpa the pressure beyond the airlock being cycled toward
     * @return what the vents do, or that the cycle is done
     */
    public static Step next(double chamberKpa, double targetKpa) {
        if (Math.abs(chamberKpa - targetKpa) <= TOLERANCE_KPA) {
            return Step.DONE;
        }
        return chamberKpa > targetKpa ? Step.SIPHON : Step.BLOW;
    }

    /**
     * Whether an airlock's front is the chamber's side. A side in the air sensor's zone is the
     * chamber, if only one is; otherwise the side nearer the sensor is.
     *
     * @param sensorZone the sensor's zone id, or −1 if it reads no zone
     * @param frontZone the zone id in front of the airlock, or −1
     * @param backZone the zone id behind it, or −1
     * @param frontDistanceSq the squared distance from the sensor to the cell in front
     * @param backDistanceSq the squared distance from the sensor to the cell behind
     */
    public static boolean frontIsChamber(
            long sensorZone, long frontZone, long backZone, double frontDistanceSq, double backDistanceSq) {
        if (sensorZone >= 0) {
            boolean front = frontZone == sensorZone;
            boolean back = backZone == sensorZone;
            if (front != back) {
                return front;
            }
        }
        return frontDistanceSq <= backDistanceSq;
    }
}
