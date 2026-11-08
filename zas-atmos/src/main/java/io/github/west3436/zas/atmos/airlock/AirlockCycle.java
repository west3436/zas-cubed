package io.github.west3436.zas.atmos.airlock;

/**
 * The rules of an airlock controller (CONT-138 to CONT-140), with no game classes: what its vents do
 * at each step of a cycle, which way a press of a sensor cycles, and the slot it guesses for an
 * airlock or a sensor linked with no slot armed.
 *
 * <p>For the guess, each place is read as a key: its zone's id, {@link #OPEN} where it's open to the
 * dimension's ambient (open air, or a vacuum in space), or {@link #UNKNOWN} where it reads nothing
 * yet.
 */
public final class AirlockCycle {
    /** The inner door, and the role of the sensor past it. */
    public static final int INNER = 0;

    /** The outer door, and the role of the sensor past it. */
    public static final int OUTER = 1;

    /** The role of the chamber's sensor. */
    public static final int CHAMBER = 2;

    /** No guess. */
    public static final int NONE = -1;

    /** A place open to the dimension's ambient: open air, or a vacuum in space. */
    public static final long OPEN = -2;

    /** A place that reads nothing yet, or isn't known. */
    public static final long UNKNOWN = -1;

    /** Which side of a door is the chamber's: its front. */
    public static final int FRONT = 0;

    /** Which side of a door is the chamber's: its back. */
    public static final int BACK = 1;

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
     * @param targetKpa the pressure the sensor past the door being cycled toward reads
     * @return what the vents do, or that the cycle is done
     */
    public static Step next(double chamberKpa, double targetKpa) {
        if (Math.abs(chamberKpa - targetKpa) <= TOLERANCE_KPA) {
            return Step.DONE;
        }
        return chamberKpa > targetKpa ? Step.SIPHON : Step.BLOW;
    }

    /**
     * The door a press of a linked sensor cycles toward (CONT-140): the one opposite the side the
     * airlock is accessing. That's the side whose door alone is unbolted, or else the one opened last.
     * With neither, it's away from the pressed sensor's own side, or toward the inner door from the
     * chamber's sensor.
     *
     * @param bolted whether the inner and the outer door are bolted
     * @param lastOpened the door opened last, {@link #INNER} or {@link #OUTER}, or −1 if none has been
     * @param pressed the pressed sensor's role: {@link #INNER}, {@link #OUTER} or {@link #CHAMBER}
     * @return the door to cycle toward, {@link #INNER} or {@link #OUTER}
     */
    public static int pressTarget(boolean[] bolted, int lastOpened, int pressed) {
        int accessing = bolted[0] != bolted[1] ? (bolted[0] ? OUTER : INNER) : lastOpened;
        if (accessing == INNER || accessing == OUTER) {
            return 1 - accessing;
        }
        return pressed == INNER || pressed == OUTER ? 1 - pressed : INNER;
    }

    // ---- The guess at link time (CONT-138) ------------------------------------------------

    /**
     * Which side of a door is the chamber's. A chamber is a zone, so with one side open and the other
     * a zone, it's the zone. Otherwise it's the zone the other door also borders, if one side alone
     * is.
     *
     * @param front the key in front of the door
     * @param back the key behind it
     * @param otherFront the key in front of the other door, or {@link #UNKNOWN} with no other door
     * @param otherBack the key behind the other door, or {@link #UNKNOWN}
     * @return {@link #FRONT}, {@link #BACK} or {@link #NONE}
     */
    public static int chamberSide(long front, long back, long otherFront, long otherBack) {
        if (front >= 0 && back == OPEN) {
            return FRONT;
        }
        if (back >= 0 && front == OPEN) {
            return BACK;
        }
        boolean f = front >= 0 && (front == otherFront || front == otherBack);
        boolean b = back >= 0 && (back == otherFront || back == otherBack);
        return f == b ? NONE : f ? FRONT : BACK;
    }

    /**
     * Which door a door is: the outer door if its far side is open, the inner door if the other door's
     * is, and otherwise the outer door if its far side's pressure is lower than the other door's by
     * more than {@link #TOLERANCE_KPA}, the inner door if higher.
     *
     * @param far the key on the door's far side, or {@link #UNKNOWN} if which side is the chamber's
     *     isn't known
     * @param farKpa the pressure there
     * @param otherFar the key on the other door's far side, or {@link #UNKNOWN} with no other door
     * @param otherFarKpa the pressure there
     * @return {@link #INNER}, {@link #OUTER} or {@link #NONE}, as when both open onto one place
     */
    public static int doorGuess(long far, double farKpa, long otherFar, double otherFarKpa) {
        if (far == UNKNOWN || far == otherFar) {
            return NONE;
        }
        if (far == OPEN) {
            return OUTER;
        }
        if (otherFar == OPEN) {
            return INNER;
        }
        if (otherFar == UNKNOWN || Math.abs(farKpa - otherFarKpa) <= TOLERANCE_KPA) {
            return NONE;
        }
        return farKpa < otherFarKpa ? OUTER : INNER;
    }

    /**
     * A sensor's role, from where it is: the chamber's if it's in the zone on a door's chamber side,
     * or the inner or outer side's if it's in that door's far side, open counting as one place.
     *
     * @param sensor the key where the sensor is
     * @param chamber the key on the chamber side of the inner and of the outer door, {@link #UNKNOWN}
     *     where it isn't known
     * @param far the key on the far side of each, {@link #UNKNOWN} where it isn't known
     * @return {@link #INNER}, {@link #OUTER}, {@link #CHAMBER}, or {@link #NONE} if it fits none or
     *     more than one
     */
    public static int sensorGuess(long sensor, long[] chamber, long[] far) {
        if (sensor == UNKNOWN) {
            return NONE;
        }
        boolean[] fits = {sensor == far[INNER], sensor == far[OUTER], sensor == chamber[0] || sensor == chamber[1]};
        int role = NONE;
        for (int r = 0; r < fits.length; r++) {
            if (fits[r]) {
                if (role != NONE) {
                    return NONE;
                }
                role = r;
            }
        }
        return role;
    }
}
