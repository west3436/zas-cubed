package io.github.west3436.zas.engine.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Burning carbon (FIRE-005, FIRE-025). */
class CarbonCombustionTest {
    private static final double EPS = 1e-12;
    private static final FireSettings FIRE = FireSettings.DEFAULT;

    /** FIRE-005: half the carbon forms CO at an O₂ fraction of 0.16. */
    @Test
    void fire_005_half_the_carbon_forms_co_at_0_16() {
        assertEquals(0.5, CarbonCombustion.coShare(0.16, FIRE), EPS);
        CarbonCombustion.Burn burn = CarbonCombustion.burn(1.0, CarbonCombustion.coShare(0.16, FIRE));
        assertEquals(0.5, burn.carbonMonoxide(), EPS);
        assertEquals(0.5, burn.carbonDioxide(), EPS);
        assertEquals(0.75, burn.oxygen(), EPS, "C + ½O₂ for the CO half");
        assertEquals(394_000 - 0.5 * 283_000, burn.heat(), 1e-6, "heat reduced to match");
    }

    /** FIRE-005: none at or above 0.18, all of it at the threshold, linear between. */
    @Test
    void fire_005_share_is_linear_from_0_18_to_the_threshold() {
        assertEquals(0, CarbonCombustion.coShare(0.21, FIRE), EPS);
        assertEquals(0, CarbonCombustion.coShare(0.18, FIRE), EPS);
        assertEquals(0.25, CarbonCombustion.coShare(0.17, FIRE), EPS);
        assertEquals(0.75, CarbonCombustion.coShare(0.15, FIRE), EPS);
        assertEquals(1, CarbonCombustion.coShare(0.14, FIRE), EPS);
        assertEquals(1, CarbonCombustion.coShare(0.05, FIRE), EPS);
        // The end follows a configured threshold.
        assertEquals(0.5, CarbonCombustion.coShare(0.15, FIRE.withMinOxygenFraction(0.12)), EPS);
        assertEquals(0, CarbonCombustion.coShare(0.19, FIRE.withMinOxygenFraction(0.18)), EPS);
        assertEquals(1, CarbonCombustion.coShare(0.17, FIRE.withMinOxygenFraction(0.18)), EPS);
        // And a configured onset.
        assertEquals(0.5, CarbonCombustion.coShare(0.17, FIRE.withIncompleteBelowFraction(0.20)), EPS);
        assertEquals(0.5, FIRE.coShare(0.16), EPS);
    }

    /** FIRE-005: what to take off any carbon fuel's complete burn, per mole of carbon. */
    @Test
    void fire_005_reductions_per_mole_of_carbon() {
        assertEquals(0.25, CarbonCombustion.oxygenSaved(0.5), EPS);
        assertEquals(141_500, CarbonCombustion.heatLost(0.5), 1e-9);
        assertEquals(0, CarbonCombustion.heatLost(0), EPS);
    }

    /** FIRE-025: 5 kW of carbon uses about 0.0127 mol of O₂ per second and makes as much CO₂. */
    @Test
    void fire_025_five_kilowatts_uses_0_0127_mol_o2_per_second() {
        CarbonCombustion.Burn burn = CarbonCombustion.burnAt(5000, 1, 0.21, FIRE);
        assertEquals(0.0127, burn.oxygen(), 0.00005);
        assertEquals(5000.0 / 394_000, burn.oxygen(), EPS);
        assertEquals(burn.oxygen(), burn.carbonDioxide(), EPS);
        assertEquals(0, burn.carbonMonoxide(), EPS);
        assertEquals(5000, burn.heat(), 1e-9);
        CarbonCombustion.Burn tenSeconds = CarbonCombustion.burnAt(10_000, 10, 0.21, FIRE);
        assertEquals(20 * burn.oxygen(), tenSeconds.oxygen(), EPS);
    }

    /** A burn conserves carbon and oxygen atoms, and scales. */
    @Test
    void burn_conserves_atoms_and_scales() {
        CarbonCombustion.Burn burn = CarbonCombustion.burn(2.0, 0.3);
        assertEquals(burn.carbon(), burn.carbonDioxide() + burn.carbonMonoxide(), EPS);
        assertEquals(2 * burn.oxygen(), 2 * burn.carbonDioxide() + burn.carbonMonoxide(), EPS);
        CarbonCombustion.Burn half = burn.scaled(0.5);
        assertEquals(burn.heat() / 2, half.heat(), 1e-9);
        assertEquals(burn.oxygen() / 2, half.oxygen(), EPS);
        assertThrows(IllegalArgumentException.class, () -> CarbonCombustion.burn(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> CarbonCombustion.burn(1, 1.5));
    }
}
