package io.github.west3436.zas.compat.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** ME storage's whole millimoles and ZAS³'s moles (COMPAT-074): rounding never makes gas. */
class GasUnitsTest {
    @Test
    void compat_074_a_unit_is_a_millimole() {
        assertEquals(1.0, GasUnits.moles(1000));
        assertEquals(0.001, GasUnits.moles(1));
        assertEquals(0.0, GasUnits.moles(0));
        assertThrows(IllegalArgumentException.class, () -> GasUnits.moles(-1));
    }

    @Test
    void compat_074_whole_units_round_down() {
        assertEquals(1000, GasUnits.whole(1.0));
        assertEquals(1000, GasUnits.whole(1.0009));
        assertEquals(0, GasUnits.whole(0.0009));
        assertEquals(0, GasUnits.whole(0));
        assertEquals(0, GasUnits.whole(-1));
        assertEquals(0, GasUnits.whole(Double.NaN));
        assertEquals(0, GasUnits.whole(Double.POSITIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, GasUnits.whole(1e300));
    }

    @Test
    void compat_074_covering_rounds_up() {
        assertEquals(1000, GasUnits.covering(1.0));
        assertEquals(1001, GasUnits.covering(1.0009));
        assertEquals(1, GasUnits.covering(1e-9));
        assertEquals(0, GasUnits.covering(0));
        assertEquals(0, GasUnits.covering(-1));
    }

    /** Units turned into moles and back come out as they went in, to the last unit. */
    @Test
    void compat_074_units_survive_the_round_trip() {
        for (long units = 0; units < 200_000; units++) {
            assertEquals(units, GasUnits.whole(GasUnits.moles(units)), "units " + units);
            assertEquals(units, GasUnits.covering(GasUnits.moles(units)), "units " + units);
        }
        Random random = new Random(368);
        for (int i = 0; i < 100_000; i++) {
            long units = random.nextLong(1L << 50);
            assertEquals(units, GasUnits.whole(GasUnits.moles(units)), "units " + units);
        }
    }

    /**
     * Whatever the amount, the whole units hold no more than it to the last bit, and one more unit
     * would hold more; the covering units hold at least it, and one fewer would hold less.
     */
    @Test
    void compat_074_rounding_never_makes_gas() {
        Random random = new Random(74);
        for (int i = 0; i < 200_000; i++) {
            double moles = random.nextDouble() * Math.pow(10, random.nextInt(-6, 9));
            long whole = GasUnits.whole(moles);
            assertTrue(GasUnits.moles(whole) <= moles, moles + " made " + whole);
            assertTrue(GasUnits.moles(whole + 1) > moles, moles + " left a whole unit: " + whole);
            long covering = GasUnits.covering(moles);
            assertTrue(GasUnits.moles(covering) >= moles, moles + " covered by " + covering);
            assertTrue(covering == 0 || GasUnits.moles(covering - 1) < moles, moles + " covered twice: " + covering);
        }
    }
}
