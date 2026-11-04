package io.github.west3436.zas.engine.fire;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The fire oxygen threshold (FIRE-030). */
class FireSettingsTest {
    private static final GasRegistry REGISTRY = GasRegistry.of(List.of(
            new GasDefinition("zas:nitrogen", "gas.zas.nitrogen", "N₂", 0.028014, 20.8, Optional.empty()),
            new GasDefinition("zas:oxygen", "gas.zas.oxygen", "O₂", 0.031998, 21.06, Optional.empty())));
    private static final int N2 = REGISTRY.indexOf("zas:nitrogen");
    private static final int O2 = REGISTRY.indexOf("zas:oxygen");
    private static final FireSettings FIRE = FireSettings.DEFAULT;

    /** FIRE-030: the defaults are 0.14 and 10 kPa. */
    @Test
    void fire_030_defaults() {
        assertTrue(FIRE.minOxygenFraction() == 0.14
                && FIRE.minOxygenKpa() == 10.0
                && FIRE.incompleteBelowFraction() == 0.18);
    }

    /** FIRE-030: the fraction limit, with plenty of O₂ pressure. */
    @Test
    void fire_030_fraction_limit() {
        assertTrue(FIRE.sustains(0.21, 21.3));
        assertTrue(FIRE.sustains(0.14, 50));
        assertFalse(FIRE.sustains(0.1399, 50));
        assertFalse(FIRE.sustains(0.10, 1000), "a high-pressure but O₂-poor mix doesn't burn");
    }

    /** FIRE-030: the partial-pressure limit, with a high O₂ fraction (the Hardcore End). */
    @Test
    void fire_030_partial_pressure_limit() {
        assertTrue(FIRE.sustains(1.0, 10));
        assertFalse(FIRE.sustains(1.0, 9.99), "pure but thin O₂ doesn't burn");
        assertFalse(FIRE.sustains(0.21, 0.21 * 30), "air at 30 kPa doesn't burn");
    }

    /** FIRE-030: both limits are configurable. */
    @Test
    void fire_030_configurable() {
        FireSettings loose = FIRE.withMinOxygenFraction(0.05).withMinOxygenKpa(1);
        assertTrue(loose.sustains(0.10, 2));
        assertFalse(FIRE.sustains(0.10, 2));
        assertFalse(FIRE.withMinOxygenKpa(25).sustains(0.21, 21.3));
        assertThrows(IllegalArgumentException.class, () -> new FireSettings(1.5, 10, 0.18));
        assertThrows(IllegalArgumentException.class, () -> new FireSettings(0.14, -1, 0.18));
        assertThrows(IllegalArgumentException.class, () -> new FireSettings(Double.NaN, 10, 0.18));
    }

    /** FIRE-030: from a mixture, by fraction and by partial pressure. */
    @Test
    void fire_030_mixture() {
        assertTrue(FIRE.sustains(mixture(0.79, 0.21, 1.0)), "air");
        assertFalse(FIRE.sustains(mixture(0.87, 0.13, 1.0)), "13 % O₂");
        assertFalse(FIRE.sustains(mixture(0.0, 1.0, 0.09)), "O₂ at 9 kPa");
        assertTrue(FIRE.sustains(mixture(0.0, 1.0, 0.11)), "O₂ at 11 kPa");
        assertFalse(FIRE.sustains(new GasMixture(REGISTRY, 2500, 293.15)), "vacuum");
    }

    /** FIRE-030: from partial pressures by index, as a published zone or an ambient gives them. */
    @Test
    void fire_030_partial_pressures() {
        double[] air = new double[REGISTRY.size()];
        air[N2] = 80;
        air[O2] = 21.3;
        assertTrue(FIRE.sustains(REGISTRY, air));
        air[O2] = 9;
        assertFalse(FIRE.sustains(REGISTRY, air));
        assertFalse(FIRE.sustains(REGISTRY, new double[REGISTRY.size()]), "vacuum");
        GasRegistry noOxygen = GasRegistry.of(List.of(REGISTRY.definition(N2)));
        assertFalse(FIRE.sustains(noOxygen, new double[] {100}));
    }

    /** FIRE-035: a fire at autoignition needs the partial pressure, whatever the fraction. */
    @Test
    void fire_035_sustains_autoignited_needs_only_the_floor() {
        assertFalse(FIRE.sustains(mixture(0.87, 0.13, 1.0)), "13 % O₂ fails FIRE-030");
        assertTrue(FIRE.sustainsAutoignited(mixture(0.87, 0.13, 1.0)), "13 % O₂ at 13 kPa");
        assertTrue(FIRE.sustainsAutoignited(mixture(0.99, 0.01, 11)), "1 % O₂ at 11 kPa");
        assertFalse(FIRE.sustainsAutoignited(mixture(0.0, 1.0, 0.09)), "O₂ at 9 kPa");
        assertFalse(
                FIRE.withMinOxygenKpa(25).sustainsAutoignited(mixture(0.79, 0.21, 1.0)), "the floor is configurable");
    }

    /** FIRE-035: no gas, no O₂ in the gas, or no O₂ in the registry is no fire, even with a floor of 0. */
    @Test
    void fire_035_sustains_autoignited_needs_some_oxygen() {
        FireSettings noFloor = FIRE.withMinOxygenKpa(0);
        assertFalse(noFloor.sustainsAutoignited(new GasMixture(REGISTRY, 2500, 293.15)), "vacuum");
        assertFalse(noFloor.sustainsAutoignited(mixture(1.0, 0.0, 1.0)), "pure N₂");
        GasMixture noOxygen = new GasMixture(GasRegistry.of(List.of(REGISTRY.definition(N2))), 2500, 293.15);
        noOxygen.setMoles(0, 100);
        assertFalse(noFloor.sustainsAutoignited(noOxygen), "a registry without O₂");
    }

    /** A 2,500 L mixture at 293.15 K with these mole fractions and a total pressure in atm. */
    private static GasMixture mixture(double n2, double o2, double atm) {
        GasMixture m = new GasMixture(REGISTRY, 2500, 293.15);
        double moles = atm * 101.325 * 2500 / (8.314462618 * 293.15);
        m.setMoles(N2, n2 * moles);
        m.setMoles(O2, o2 * moles);
        return m;
    }
}
