package io.github.west3436.zas.compat.pneumaticcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import org.junit.jupiter.api.Test;

/** PneumaticCraft's air as moles (Q-24) and how much a leak gives a tick (COMPAT-011, COMPAT-012). */
class AirUnitsTest {
    @Test
    void compat_012_a_millilitre_at_one_bar_and_293_kelvin_is_its_moles_by_the_gas_law() {
        // n = pV / RT for 1 mL at 100 kPa: 100 kPa × 0.001 L / (8.314462618 × 293.15)
        assertEquals(4.1028e-5, AirUnits.DEFAULT_MOLES_PER_ML, 1e-9);
        assertEquals(100.0 * 0.001 / (8.314462618 * 293.15), AirUnits.DEFAULT_MOLES_PER_ML, 1e-15);
        // A full Pneumatic Chestplate, 28,000 mL at 10 bar, is about 11.5 mol.
        assertEquals(11.488, 280_000 * AirUnits.DEFAULT_MOLES_PER_ML, 1e-3);
    }

    @Test
    void compat_011_its_air_is_standard_air() {
        GasParcel air = AirUnits.standardAir(2.0);
        assertEquals(2.0, air.totalMoles(), 1e-12);
        assertEquals(2.0 * 0.79, air.moles().get(StandardAir.NITROGEN), 1e-12);
        assertEquals(2.0 * 0.21, air.moles().get(StandardAir.OXYGEN), 1e-12);
        assertEquals(293.15, air.temperature(), 1e-12);
        assertEquals(GasParcel.EMPTY, AirUnits.standardAir(0));
        assertEquals(GasParcel.EMPTY, AirUnits.standardAir(-1));
        assertEquals(GasParcel.EMPTY, AirUnits.standardAir(Double.NaN));
    }

    @Test
    void compat_012_a_leak_gives_forty_millilitres_a_bar_and_twenty_more_each_tick() {
        assertEquals(20, AirUnits.leakPerTick(0f, 1000));
        assertEquals(60, AirUnits.leakPerTick(1f, 1000));
        assertEquals(180, AirUnits.leakPerTick(4f, 100_000));
        // Cut to a whole number in float arithmetic, as PneumaticCraft does.
        assertEquals((int) (2.37f * 40) + 20, AirUnits.leakPerTick(2.37f, 100_000));
    }

    @Test
    void compat_012_a_leak_never_gives_more_than_there_is() {
        assertEquals(15, AirUnits.leakPerTick(0.01f, 15));
        assertEquals(0, AirUnits.leakPerTick(3f, 0));
        // A vacuum holds air below 0, and leaks inward: nothing comes out.
        assertEquals(0, AirUnits.leakPerTick(-0.5f, -100));
    }
}
