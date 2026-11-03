package io.github.west3436.zas.engine.reaction;

/**
 * How fast a reaction goes (FIRE-001, CORE-046). It is first order in its limiting reactant: in a
 * step of {@code dt} seconds it goes {@code 1 - e^(-k·dt)} of the way to using that reactant up, so
 * the result doesn't depend on how the time is split into steps of the same conditions, and no
 * period can overshoot. {@code k} follows Arrhenius's law from a reference temperature, {@code k(T) =
 * perSecond · e^(-Ea/R · (1/T - 1/Tref))}, at the mixture's temperature at the start of the step.
 *
 * @param perSecond k at the reference temperature, 1/s
 * @param activationEnergy Ea, J/mol; 0 makes the rate the same at every temperature
 * @param referenceTemperature Tref, K
 */
public record ReactionRate(double perSecond, double activationEnergy, double referenceTemperature) {
    /** R in J/(mol·K) (CODATA 2018, exact). */
    private static final double R = 8.314462618;

    public ReactionRate {
        if (!Double.isFinite(perSecond) || perSecond <= 0) {
            throw new IllegalArgumentException("rate: per_second must be finite and positive, got " + perSecond);
        }
        if (!Double.isFinite(activationEnergy) || activationEnergy < 0) {
            throw new IllegalArgumentException(
                    "rate: activation_energy must be finite and not negative, got " + activationEnergy);
        }
        if (!Double.isFinite(referenceTemperature) || referenceTemperature <= 0) {
            throw new IllegalArgumentException(
                    "rate: reference_temperature must be finite and positive, got " + referenceTemperature);
        }
    }

    /** A rate that doesn't depend on temperature. */
    public static ReactionRate constant(double perSecond) {
        return new ReactionRate(perSecond, 0, 298.15);
    }

    /** k at a temperature, 1/s; 0 at 0 K for a positive activation energy. */
    public double at(double temperature) {
        if (activationEnergy == 0) {
            return perSecond;
        }
        if (temperature <= 0) {
            return 0;
        }
        return perSecond * Math.exp(-activationEnergy / R * (1 / temperature - 1 / referenceTemperature));
    }

    /** The share of the way to completion a step goes: {@code 1 - e^(-k(T)·seconds)}. */
    public double fraction(double temperature, double seconds) {
        return -Math.expm1(-at(temperature) * seconds);
    }
}
