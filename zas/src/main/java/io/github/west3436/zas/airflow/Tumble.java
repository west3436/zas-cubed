package io.github.west3436.zas.airflow;

/**
 * One body's tumble as its client draws it (CLIENT-027): end over end about the level axis across
 * its motion, faster as it moves faster. When the tumble ends it turns on or back to upright by the
 * shorter way, quickly but not at once. Platform-free, so the turn can be tested without a client.
 * Client thread.
 *
 * <p>The axis follows the body's level motion, eased so that a change of course doesn't jerk the
 * model. A body that has only ever moved straight up or down turns about the axis across the way
 * it faces.
 */
public final class Tumble {
    /** Degrees turned per block travelled. */
    public static final double DEGREES_PER_BLOCK = 50;

    /** The slowest turn while flung, degrees per tick: a body lifted slowly still turns. */
    public static final double MIN_RATE = 8;

    /** The fastest turn, degrees per tick: faster than this reads as a blur. */
    public static final double MAX_RATE = 40;

    /** How fast it turns to upright once the tumble ends, degrees per tick: at most 5 ticks. */
    public static final double SETTLE_RATE = 36;

    /** The level speed below which the motion says nothing about the axis, blocks per tick. */
    static final double AXIS_SPEED = 0.02;

    /** The share of the way to a new axis it moves each tick. */
    static final double AXIS_EASE = 0.25;

    /** The share of the way to a new speed it moves each tick, to smooth uneven moves. */
    static final double SPEED_EASE = 0.5;

    private double angle;
    private double prevAngle;
    private double axisX;
    private double axisZ;
    private boolean hasAxis;
    private double speed;

    /**
     * One tick of the body.
     *
     * @param flung whether it is flung now
     * @param dx what it moved since the last tick, blocks
     * @param dy see {@code dx}
     * @param dz see {@code dx}
     * @param facingX the level direction it faces, for an axis when its motion gives none
     * @param facingZ see {@code facingX}
     * @return whether it is still turned or flung; once false it is upright and can be dropped
     */
    public boolean tick(boolean flung, double dx, double dy, double dz, double facingX, double facingZ) {
        if (angle >= 360) {
            angle -= 360;
            prevAngle -= 360;
        }
        prevAngle = angle;
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        speed += (moved - speed) * SPEED_EASE;
        double level = Math.hypot(dx, dz);
        if (level > AXIS_SPEED) {
            steer(dz / level, -dx / level);
        } else if (!hasAxis) {
            double f = Math.hypot(facingX, facingZ);
            if (f > 0) {
                steer(facingZ / f, -facingX / f);
            } else {
                steer(1, 0);
            }
        }
        if (flung) {
            angle += rate(speed);
            return true;
        }
        double target = angle < 180 ? 0 : 360;
        double left = target - angle;
        angle += Math.copySign(Math.min(SETTLE_RATE, Math.abs(left)), left);
        // Upright at 360 is kept for a tick, to draw the last step forwards, and is 0 from the next.
        return angle != 0 || prevAngle != 0;
    }

    /** The turn while flung, degrees per tick, at a speed in blocks per tick. */
    public static double rate(double speed) {
        return Math.clamp(speed * DEGREES_PER_BLOCK, MIN_RATE, MAX_RATE);
    }

    /** The angle turned at a point between the last tick and this one, degrees. */
    public double angle(float partialTick) {
        return prevAngle + (angle - prevAngle) * partialTick;
    }

    /**
     * The level axis it turns about: across its motion, so that a positive angle tips its top the
     * way it moves.
     */
    public double axisX() {
        return axisX;
    }

    /** See {@link #axisX}. */
    public double axisZ() {
        return axisZ;
    }

    /** The body's speed, eased, blocks per tick. */
    public double speed() {
        return speed;
    }

    private void steer(double x, double z) {
        if (!hasAxis) {
            axisX = x;
            axisZ = z;
            hasAxis = true;
            return;
        }
        double nx = axisX + (x - axisX) * AXIS_EASE;
        double nz = axisZ + (z - axisZ) * AXIS_EASE;
        double len = Math.hypot(nx, nz);
        if (len < 1e-6) {
            // A reversal: the eased axis passed through nothing, so take the new one.
            axisX = x;
            axisZ = z;
        } else {
            axisX = nx / len;
            axisZ = nz / len;
        }
    }
}
