package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.heater.AirConditionerMath;
import io.github.west3436.zas.atmos.heater.HeaterMath;
import java.util.Locale;

/**
 * An area air controller's thermostat (CONT-045, CONT-165): a target temperature for its zone, which
 * its vents heat toward and its scrubbers cool toward, with a dead band so it doesn't flap. Pure, so
 * it is unit-tested.
 *
 * <p>Off by default: a controller with no target moves no heat. With one, it heats once its zone is
 * more than the band below the target and stops at the target; it cools once the zone is more than
 * the band above it, and stops at the target. Heating and cooling never meet, since each stops at the
 * target and the other starts only a band away.
 *
 * <p>"At the target" is within a tenth of the band of it ({@link #STOP_SHARE}): heat that leaks
 * through the walls toward the world's own temperature would otherwise keep a room a hair short of
 * its target, and the thermostat running, for ever.
 */
public final class Thermostat {
    /** The lowest target, K: the air conditioner's lowest setpoint (CONT-019). */
    public static final double MIN_TARGET = AirConditionerMath.MIN_SETPOINT;

    /** The highest target, K: the heater's highest setpoint (CONT-019). */
    public static final double MAX_TARGET = HeaterMath.MAX_SETPOINT;

    /** A target when a preset gives none, K: room temperature, 20 °C. */
    public static final double DEFAULT_TARGET = HeaterMath.DEFAULT_SETPOINT;

    /** The default dead band, K: {@code [controller] thermostat_band_k}, vg's 2 K. */
    public static final double DEFAULT_BAND = 2.0;

    /** How near the target, as a share of the band, heating or cooling stops. */
    public static final double STOP_SHARE = 0.1;

    /** What a thermostat is doing. */
    public enum State {
        /** No target. */
        OFF,
        /** Within the band, or waiting to read its zone. */
        IDLE,
        HEATING,
        COOLING;

        /** The state with an ordinal, or {@link #OFF} for one out of range. */
        public static State of(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
        }

        /** Its name for lang keys and computers. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Thermostat() {}

    /** A target clamped to 73–573 K; NaN stays NaN, which is off. */
    public static double clampTarget(double kelvin) {
        return Double.isNaN(kelvin) ? Double.NaN : Math.max(MIN_TARGET, Math.min(MAX_TARGET, kelvin));
    }

    /**
     * The target a thermostat starts at when it is switched on: the middle of the preset's safe
     * temperatures, min1 to max1, when both are on; else 293.15 K. 293 K for Human.
     */
    public static double startingTarget(ControllerPreset preset) {
        Thresholds t = preset.temperature();
        if (t.min1() == Thresholds.OFF || t.max1() == Thresholds.OFF) {
            return DEFAULT_TARGET;
        }
        return clampTarget((t.min1() + t.max1()) / 2);
    }

    /**
     * What a thermostat does after a reading of its zone.
     *
     * @param was what it was doing
     * @param temperature the zone's temperature, K; NaN when it has no reading, which is idle
     * @param target the target, K; NaN is off
     * @param band the dead band, K, at least 0
     * @return what it does now
     */
    public static State next(State was, double temperature, double target, double band) {
        if (Double.isNaN(target)) {
            return State.OFF;
        }
        if (Double.isNaN(temperature)) {
            return State.IDLE;
        }
        double b = Math.max(0, band);
        double stop = b * STOP_SHARE;
        if (was == State.HEATING && temperature < target - stop) {
            return State.HEATING;
        }
        if (was == State.COOLING && temperature > target + stop) {
            return State.COOLING;
        }
        if (temperature < target - b) {
            return State.HEATING;
        }
        if (temperature > target + b) {
            return State.COOLING;
        }
        return State.IDLE;
    }

    /**
     * The heat one of several devices moves this step: an even share of what brings the zone to the
     * target, but at most what one device moves in a step.
     *
     * @param need what brings the zone to the target, J
     * @param devices how many devices share it
     * @param most the most one device moves in the step, J
     * @return the device's share, J, from 0 to {@code most}
     */
    public static double share(double need, int devices, double most) {
        if (!(need > 0) || devices <= 0 || !(most > 0)) {
            return 0;
        }
        return Math.min(most, need / devices);
    }
}
