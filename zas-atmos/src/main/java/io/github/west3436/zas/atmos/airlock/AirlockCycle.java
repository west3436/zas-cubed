package io.github.west3436.zas.atmos.airlock;

/**
 * The rules of an airlock controller's cycle (CONT-139, CONT-140), with no game classes: what its
 * vents do at each step, which side of an airlock is the chamber, which part of the airlock each
 * air sensor reads, and which way a press of a sensor cycles.
 */
public final class AirlockCycle {
    /** The role of a sensor beyond airlock 1; beyond airlock 2 is 1, the airlock's own index. */
    public static final int SIDE_1 = 0;

    /** The role of a sensor beyond airlock 2. */
    public static final int SIDE_2 = 1;

    /** The role of the chamber's sensor. */
    public static final int CHAMBER = 2;

    /** No role: a sensor whose role can't be worked out, as with an airlock missing. */
    public static final int NONE = -1;

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
     * @param chamberKpa the pressure the chamber's sensor reads
     * @param targetKpa the pressure the sensor beyond the airlock being cycled toward reads
     * @return what the vents do, or that the cycle is done
     */
    public static Step next(double chamberKpa, double targetKpa) {
        if (Math.abs(chamberKpa - targetKpa) <= TOLERANCE_KPA) {
            return Step.DONE;
        }
        return chamberKpa > targetKpa ? Step.SIPHON : Step.BLOW;
    }

    /**
     * Whether an airlock's front is the chamber's side. The chamber is the zone both airlocks
     * border: a side whose zone one of the other airlock's sides has is the chamber, if only one
     * is. Otherwise, as when both airlocks open onto one room, the side nearer the other airlock
     * is.
     *
     * @param frontZone the zone id in front of the airlock, or −1 if it reads no zone
     * @param backZone the zone id behind it, or −1
     * @param otherFront the zone id in front of the other airlock, or −1
     * @param otherBack the zone id behind the other airlock, or −1
     * @param frontDistanceSq the squared distance from the other airlock to the cell in front
     * @param backDistanceSq the squared distance from the other airlock to the cell behind
     */
    public static boolean frontIsChamber(
            long frontZone,
            long backZone,
            long otherFront,
            long otherBack,
            double frontDistanceSq,
            double backDistanceSq) {
        boolean front = frontZone >= 0 && (frontZone == otherFront || frontZone == otherBack);
        boolean back = backZone >= 0 && (backZone == otherFront || backZone == otherBack);
        if (front != back) {
            return front;
        }
        return frontDistanceSq <= backDistanceSq;
    }

    /**
     * A linked sensor's role, from its zone: the chamber's ({@link #CHAMBER}) if it's in the zone
     * on the chamber's side of an airlock, or beyond airlock 1 or 2 ({@link #SIDE_1}, {@link
     * #SIDE_2}) if it's in the zone past it. Where its zone fits none, as in open air or a vacuum,
     * or more than one, as when both airlocks open onto one room, where it is decides among them:
     * past the airlock whose far side it's nearer, by the most, or the chamber's if it's past
     * neither.
     *
     * @param sensorZone the sensor's zone id, or −1 if it reads no zone
     * @param chamberZones the zone id on the chamber's side of airlock 1 and of airlock 2, or −1
     * @param farZones the zone id on the far side of each, or −1
     * @param past how much nearer the sensor is to each airlock's far side than to its chamber
     *     side, as a difference of squared distances; positive where it's past that airlock
     * @return {@link #CHAMBER}, {@link #SIDE_1} or {@link #SIDE_2}
     */
    public static int role(long sensorZone, long[] chamberZones, long[] farZones, double[] past) {
        boolean[] fits = new boolean[3];
        int count = 0;
        if (sensorZone >= 0) {
            fits[CHAMBER] = sensorZone == chamberZones[0] || sensorZone == chamberZones[1];
            fits[SIDE_1] = sensorZone == farZones[0];
            fits[SIDE_2] = sensorZone == farZones[1];
            for (boolean f : fits) {
                count += f ? 1 : 0;
            }
        }
        int where = past[0] <= 0 && past[1] <= 0 ? CHAMBER : past[0] >= past[1] ? SIDE_1 : SIDE_2;
        if (count == 0 || fits[where]) {
            return where;
        }
        for (int r : new int[] {CHAMBER, SIDE_1, SIDE_2}) {
            if (fits[r]) {
                return r; // its zone fits one role only, or several that where it is doesn't pick
            }
        }
        return where;
    }

    /**
     * The airlock a press of a linked sensor cycles toward (CONT-140): the one opposite the side
     * the airlock is accessing. That's the side whose airlock alone is unbolted, or else the one
     * opened last. With neither, it's away from the pressed sensor's own side, or toward airlock 1
     * from the chamber's sensor.
     *
     * @param bolted whether airlock 1 and airlock 2 are bolted
     * @param lastOpened the airlock opened last, 0 or 1, or −1 if none has been
     * @param pressed the pressed sensor's role
     * @return the airlock to cycle toward, 0 or 1
     */
    public static int pressTarget(boolean[] bolted, int lastOpened, int pressed) {
        int accessing = bolted[0] != bolted[1] ? (bolted[0] ? 1 : 0) : lastOpened;
        if (accessing == 0 || accessing == 1) {
            return 1 - accessing;
        }
        return pressed == SIDE_1 || pressed == SIDE_2 ? 1 - pressed : 0;
    }
}
