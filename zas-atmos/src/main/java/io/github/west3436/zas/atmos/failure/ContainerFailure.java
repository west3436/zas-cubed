package io.github.west3436.zas.atmos.failure;

/**
 * What a gas container does at a pressure: the rule behind tank failure (CONT-038) and canister
 * rupture and explosion (CONT-085, CONT-101, CONT-102), with the explosion's power capped (CONT-109).
 * It is plain arithmetic, so it is tested without the game; {@link TankFailure} applies it to a tank.
 *
 * <p>A pressure is a verdict and nothing more: a container that goes from below its rating to above
 * its explosion threshold in one tick explodes, without rupturing first. Every threshold is
 * exclusive, so a container fails above it, not at it.
 */
public final class ContainerFailure {
    /** One atmosphere, kPa. */
    public static final double ATM_KPA = 101.325;

    /** A tank leaks above this, kPa: 30 atm. */
    public static final double TANK_LEAK_KPA = 30 * ATM_KPA;

    /** A tank ruptures above this, kPa: 40 atm. */
    public static final double TANK_RUPTURE_KPA = 40 * ATM_KPA;

    /** A tank explodes above this, kPa: 50 atm. */
    public static final double TANK_EXPLODE_KPA = 50 * ATM_KPA;

    /** A canister ruptures above its rating, kPa: CONT-101's 9,120 kPa, its 90 atm rounded up. */
    public static final double CANISTER_RATED_KPA = 9120.0;

    /** A canister explodes above 150 % of its rating, kPa: CONT-102's 13,680 kPa, its 135 atm. */
    public static final double CANISTER_EXPLODE_KPA = 13680.0;

    /** A canister ruptures above this temperature, K (CONT-085). */
    public static final double CANISTER_MAX_K = 1273.0;

    /** The pressure above the explosion threshold that makes one point of power, kPa: 10 atm. */
    public static final double POWER_SCALE_KPA = 10 * ATM_KPA;

    /** The default cap on an explosion's power (CONT-109). TNT is 4. */
    public static final double DEFAULT_POWER_CAP = 16.0;

    /** The share of its gas a leaking tank loses each atmos tick, at most. */
    public static final double LEAK_FRACTION = 0.1;

    /** What a container does. */
    public enum Kind {
        /** Nothing: it holds. */
        NONE,
        /** It loses some gas and stays whole. Only tanks leak. */
        LEAK,
        /** It is destroyed, and all its gas goes into its cell. */
        RUPTURE,
        /** As a rupture, and a vanilla explosion of {@link Verdict#power()}. */
        EXPLODE
    }

    /**
     * What a container does at a pressure.
     *
     * @param kind what it does
     * @param power the vanilla explosion's power, already capped; 0 unless it explodes
     */
    public record Verdict(Kind kind, double power) {
        public static final Verdict NONE = new Verdict(Kind.NONE, 0);
        public static final Verdict LEAK = new Verdict(Kind.LEAK, 0);
        public static final Verdict RUPTURE = new Verdict(Kind.RUPTURE, 0);

        /** Whether the container is destroyed. */
        public boolean destroys() {
            return kind == Kind.RUPTURE || kind == Kind.EXPLODE;
        }
    }

    private ContainerFailure() {}

    /**
     * A tank's verdict (CONT-038): it leaks above 30 atm, ruptures above 40 atm, and explodes above
     * 50 atm with power (P − 50 atm) / 10 atm.
     *
     * @param kPa its pressure
     * @param powerCap the most power an explosion can have (CONT-109)
     * @return what it does
     */
    public static Verdict tank(double kPa, double powerCap) {
        if (kPa > TANK_EXPLODE_KPA) {
            return new Verdict(Kind.EXPLODE, power(kPa, TANK_EXPLODE_KPA, powerCap));
        }
        if (kPa > TANK_RUPTURE_KPA) {
            return Verdict.RUPTURE;
        }
        return kPa > TANK_LEAK_KPA ? Verdict.LEAK : Verdict.NONE;
    }

    /**
     * A canister's verdict (CONT-085, CONT-101, CONT-102): it explodes above 135 atm with power
     * (P − 135 atm) / 10 atm, and ruptures above its 90 atm rating or above 1,273 K.
     *
     * @param kPa its pressure
     * @param kelvin the hotter of its gas and the air around it
     * @param powerCap the most power an explosion can have (CONT-109)
     * @return what it does; a canister never just leaks
     */
    public static Verdict canister(double kPa, double kelvin, double powerCap) {
        if (kPa > CANISTER_EXPLODE_KPA) {
            return new Verdict(Kind.EXPLODE, power(kPa, CANISTER_EXPLODE_KPA, powerCap));
        }
        return kPa > CANISTER_RATED_KPA || kelvin > CANISTER_MAX_K ? Verdict.RUPTURE : Verdict.NONE;
    }

    /**
     * The moles a leaking tank loses in one atmos tick: {@link #LEAK_FRACTION} of its gas, but no
     * more than brings it down to the pressure it stops leaking at.
     *
     * @param moles the moles it holds
     * @param kPa its pressure
     * @return the moles to let out, 0 at or below {@link #TANK_LEAK_KPA}
     */
    public static double leakMoles(double moles, double kPa) {
        if (!(kPa > TANK_LEAK_KPA) || !(moles > 0)) {
            return 0;
        }
        return moles * Math.min(LEAK_FRACTION, 1 - TANK_LEAK_KPA / kPa);
    }

    private static double power(double kPa, double threshold, double cap) {
        return Math.max(0, Math.min(cap, (kPa - threshold) / POWER_SCALE_KPA));
    }
}
