package io.github.west3436.zas.engine.gas;

/**
 * Tolerances of the mixture equivalence test (CORE-006). A quantity differs
 * only when it is off by more than both its absolute and its relative
 * tolerance. Relative differences are taken against the larger magnitude of
 * the two values.
 *
 * @param temperatureAbsolute K
 * @param temperatureRelative fraction, 0.012 = 1.2 %
 * @param pressureAbsolute kPa
 * @param pressureRelative fraction
 * @param densityAbsolute each gas's molar density, mol/L
 * @param densityRelative fraction
 */
public record EquivalenceTolerances(
        double temperatureAbsolute,
        double temperatureRelative,
        double pressureAbsolute,
        double pressureRelative,
        double densityAbsolute,
        double densityRelative) {

    /** ZAS's tolerances: 4 K or 1.2 %, 0.1 kPa or 5 %, 0.0021 mol/L or 5 %. */
    public static final EquivalenceTolerances DEFAULT = new EquivalenceTolerances(4, 0.012, 0.1, 0.05, 0.0021, 0.05);

    public EquivalenceTolerances {
        requireTolerance("temperatureAbsolute", temperatureAbsolute);
        requireTolerance("temperatureRelative", temperatureRelative);
        requireTolerance("pressureAbsolute", pressureAbsolute);
        requireTolerance("pressureRelative", pressureRelative);
        requireTolerance("densityAbsolute", densityAbsolute);
        requireTolerance("densityRelative", densityRelative);
    }

    private static void requireTolerance(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and not negative, got " + value);
        }
    }

    /** Whether two values differ by more than both tolerances. */
    static boolean differs(double a, double b, double absolute, double relative) {
        double difference = Math.abs(a - b);
        return difference > absolute && difference > relative * Math.max(Math.abs(a), Math.abs(b));
    }
}
