package io.github.west3436.zas.engine.gas;

import static io.github.west3436.zas.engine.gas.TestGases.mixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CORE-006. Each pair differs in one quantity, just inside or just outside one
 * tolerance, while the other quantities stay within theirs. The second mixture
 * is the smaller one, so relative differences are taken against the first.
 */
class GasEquivalenceTest {
    private static final double RT_300 = GasConstants.R * 300; // kPa·L/mol at 300 K

    static Stream<Arguments> pairs() {
        return Stream.of(
                // Temperature, absolute (4 K beats 1.2 % of 300 K = 3.6 K).
                Arguments.of(
                        "temperature abs inside", mixture(1000, 300, 40, 0, 0), mixture(1000, 296.1, 40, 0, 0), true),
                Arguments.of(
                        "temperature abs outside", mixture(1000, 300, 40, 0, 0), mixture(1000, 295.9, 40, 0, 0), false),
                // Temperature, relative (1.2 % of 1000 K = 12 K beats 4 K).
                Arguments.of(
                        "temperature rel inside", mixture(1000, 1000, 40, 0, 0), mixture(1000, 988.1, 40, 0, 0), true),
                Arguments.of(
                        "temperature rel outside",
                        mixture(1000, 1000, 40, 0, 0),
                        mixture(1000, 987.9, 40, 0, 0),
                        false),
                // Pressure, absolute: about 1 kPa, where 5 % is 0.05 kPa; densities stay far below 0.0021 mol/L apart.
                Arguments.of(
                        "pressure abs inside",
                        mixture(1000, 300, 0.4, 0, 0),
                        mixture(1000, 300, 0.4 - 0.099 * 1000 / RT_300, 0, 0),
                        true),
                Arguments.of(
                        "pressure abs outside",
                        mixture(1000, 300, 0.4, 0, 0),
                        mixture(1000, 300, 0.4 - 0.101 * 1000 / RT_300, 0, 0),
                        false),
                // Pressure, relative: about 97 kPa spread over three gases, each moving less than 0.0021 mol/L.
                Arguments.of("pressure rel inside", mixture(1000, 300, 13, 13, 13), scaled(13, 0.049), true),
                Arguments.of("pressure rel outside", mixture(1000, 300, 13, 13, 13), scaled(13, 0.051), false),
                // Density, absolute: alpha at 0.01 mol/L (5 % is 0.0005); beta compensates so pressure is unchanged.
                Arguments.of(
                        "density abs inside",
                        mixture(1000, 300, 10, 200, 0),
                        mixture(1000, 300, 10 - 2.09, 200 + 2.09, 0),
                        true),
                Arguments.of(
                        "density abs outside",
                        mixture(1000, 300, 10, 200, 0),
                        mixture(1000, 300, 10 - 2.11, 200 + 2.11, 0),
                        false),
                // Density, relative: alpha at 0.1 mol/L (5 % is 0.005, beating 0.0021).
                Arguments.of(
                        "density rel inside",
                        mixture(1000, 300, 100, 200, 0),
                        mixture(1000, 300, 100 - 4.9, 200 + 4.9, 0),
                        true),
                Arguments.of(
                        "density rel outside",
                        mixture(1000, 300, 100, 200, 0),
                        mixture(1000, 300, 100 - 5.1, 200 + 5.1, 0),
                        false));
    }

    private static GasMixture scaled(double each, double reduction) {
        double n = each * (1 - reduction);
        return mixture(1000, 300, n, n, n);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    void core_006_equivalence_test(String name, GasMixture first, GasMixture second, boolean equivalent) {
        assertEquals(equivalent, first.isEquivalent(second), name);
        assertEquals(equivalent, second.isEquivalent(first), name + " (symmetric)");
    }

    @Test
    void core_006_identical_mixtures_are_equivalent() {
        assertTrue(mixture(1000, 300, 10, 5, 2).isEquivalent(mixture(1000, 300, 10, 5, 2)));
        assertTrue(new GasMixture(TestGases.REGISTRY, 1000, 300)
                .isEquivalent(new GasMixture(TestGases.REGISTRY, 1000, 300)));
    }

    @Test
    void core_006_configured_override() {
        GasMixture first = mixture(1000, 300, 40, 0, 0);
        GasMixture second = mixture(1000, 295.9, 40, 0, 0);
        assertFalse(first.isEquivalent(second));

        EquivalenceTolerances looser = new EquivalenceTolerances(10, 0.012, 0.1, 0.05, 0.0021, 0.05);
        assertTrue(first.isEquivalent(second, looser));

        // Through the config, as the default for the overload without tolerances.
        GasConfig config = GasConfig.DEFAULT.withEquivalence(looser);
        assertTrue(mixture(config, 1000, 300, 40, 0, 0).isEquivalent(mixture(config, 1000, 295.9, 40, 0, 0)));

        EquivalenceTolerances stricter = new EquivalenceTolerances(1, 0.001, 0.1, 0.05, 0.0021, 0.05);
        assertFalse(first.isEquivalent(mixture(1000, 298, 40, 0, 0), stricter));
        assertTrue(first.isEquivalent(mixture(1000, 298, 40, 0, 0)));
    }
}
