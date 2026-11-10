package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The mB to mol factor (MEK-002, Q-19). */
class ChemicalUnitsTest {
    private static final double DEFAULT = ChemicalUnits.molesPerMillibucket(1.0);

    /** At the default, 1 mB is 1 L of standard air's pressure and temperature, about 0.04157 mol. */
    @Test
    void mek_002_one_millibucket_is_a_litre_of_gas_at_standard_airs_pressure_and_temperature() {
        assertEquals(1.0, ChemicalUnits.DEFAULT_LITRES_PER_MILLIBUCKET);
        assertEquals(0.04157, DEFAULT, 0.000005);
        assertEquals(101.325 / (8.314462618 * 293.15), DEFAULT, 1e-15);
        // The factor's conditions and constant are the API's own.
        assertEquals(StandardAir.PRESSURE, ChemicalUnits.STANDARD_PRESSURE);
        assertEquals(StandardAir.TEMPERATURE, ChemicalUnits.STANDARD_TEMPERATURE);
        assertEquals(GasMixture.GAS_CONSTANT, ChemicalUnits.GAS_CONSTANT);
    }

    /** 1,000 mB fill one block, a 1,000 L cell, at one atmosphere of standard air's temperature. */
    @Test
    void mek_002_a_thousand_millibuckets_fill_one_block_at_one_atmosphere() {
        double moles = ChemicalUnits.moles(1000, DEFAULT);
        GasMixture cell =
                new GasMixture(Map.of(StandardAir.OXYGEN, moles), StandardAir.TEMPERATURE, StandardAir.CELL_VOLUME);
        assertEquals(StandardAir.PRESSURE, cell.pressure(), 1e-9);
        assertEquals(StandardAir.mixture(StandardAir.CELL_VOLUME).totalMoles(), moles, 1e-9);
    }

    /** The factor is one number for every chemical, and the amount scales with it. */
    @Test
    void mek_002_the_factor_scales_every_amount() {
        assertEquals(2 * DEFAULT, ChemicalUnits.molesPerMillibucket(2.0), 1e-15);
        assertEquals(DEFAULT / 4, ChemicalUnits.molesPerMillibucket(0.25), 1e-15);
        assertEquals(0, ChemicalUnits.moles(0, DEFAULT));
        assertEquals(250 * DEFAULT, ChemicalUnits.moles(250, DEFAULT), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> ChemicalUnits.moles(-1, DEFAULT));
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ChemicalUnits.molesPerMillibucket(bad));
        }
    }

    /** Gas makes whole millibuckets, rounded down, so converting never makes gas. */
    @Test
    void mek_002_gas_makes_whole_millibuckets_rounded_down() {
        assertEquals(0, ChemicalUnits.wholeMillibuckets(0.999 * DEFAULT, DEFAULT));
        assertEquals(1, ChemicalUnits.wholeMillibuckets(1.5 * DEFAULT, DEFAULT));
        assertEquals(41, ChemicalUnits.wholeMillibuckets(41.9 * DEFAULT, DEFAULT));
        assertEquals(0, ChemicalUnits.wholeMillibuckets(0, DEFAULT));
        assertEquals(0, ChemicalUnits.wholeMillibuckets(-3, DEFAULT));
        assertEquals(0, ChemicalUnits.wholeMillibuckets(Double.NaN, DEFAULT));
        assertEquals(0, ChemicalUnits.wholeMillibuckets(Double.POSITIVE_INFINITY, DEFAULT));
        assertEquals(0, ChemicalUnits.wholeMillibuckets(1, 0));
    }

    /**
     * What the whole millibuckets hold is never more than the gas, to the last bit, and less than
     * one millibucket stays behind: converting makes no gas and strands none.
     */
    @Test
    void mek_002_rounding_down_never_makes_gas_and_leaves_less_than_a_millibucket() {
        java.util.Random random = new java.util.Random(20261109);
        for (double litres : new double[] {1.0, 0.001, 0.3, 1.0 / 3, 22.414, 1000.0}) {
            double factor = ChemicalUnits.molesPerMillibucket(litres);
            for (int i = 0; i < 20_000; i++) {
                // Amounts from a fraction of a millibucket to 10^12 of them, many of them a hair
                // to either side of a whole number.
                double scale = Math.pow(10, random.nextInt(13));
                double moles = (i % 2 == 0 ? random.nextDouble() * scale : Math.rint(random.nextDouble() * scale))
                        * factor
                        * (1 + (random.nextInt(5) - 2) * Math.ulp(1.0));
                long whole = ChemicalUnits.wholeMillibuckets(moles, factor);
                double held = ChemicalUnits.moles(whole, factor);
                assertTrue(held <= moles, moles + " mol made " + held + " mol of chemical");
                assertTrue(
                        ChemicalUnits.moles(whole + 1, factor) > moles,
                        moles + " mol left a whole millibucket behind at " + litres + " L/mB");
            }
        }
    }

    /** Millibuckets turned into moles and back come out as they went in, at any factor. */
    @Test
    void mek_002_millibuckets_survive_the_round_trip() {
        long[] amounts = {1, 2, 3, 7, 10, 99, 1000, 1001, 4096, 64_000, 8_192_000, 123_456_789, 1_000_000_000_000L};
        for (double litres : new double[] {1.0, 0.001, 0.3, 1.0 / 3, 22.414, 1000.0}) {
            double factor = ChemicalUnits.molesPerMillibucket(litres);
            for (long amount : amounts) {
                assertEquals(
                        amount,
                        ChemicalUnits.wholeMillibuckets(ChemicalUnits.moles(amount, factor), factor),
                        amount + " mB at " + litres + " L/mB");
            }
        }
    }
}
