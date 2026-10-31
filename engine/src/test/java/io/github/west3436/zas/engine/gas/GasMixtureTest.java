package io.github.west3436.zas.engine.gas;

import static io.github.west3436.zas.engine.gas.TestGases.A;
import static io.github.west3436.zas.engine.gas.TestGases.B;
import static io.github.west3436.zas.engine.gas.TestGases.G;
import static io.github.west3436.zas.engine.gas.TestGases.mixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GasMixtureTest {
    private static final double EPS = 1e-9;

    @Test
    void core_001_gas_mixture_state() {
        GasMixture m = mixture(1000, 300, 10, 5, 0);
        assertEquals(1000, m.volume());
        assertEquals(300, m.temperature());
        assertEquals(10, m.moles(A));
        assertEquals(5, m.moles(B));
        assertEquals(0, m.moles(G));
        assertEquals(15, m.totalMoles(), EPS);
        // P = nRT/V = 15 × 8.314462618 × 300 / 1000
        assertEquals(37.415081781, m.pressure(), EPS);
        assertEquals(10 * 8.314462618 * 300 / 1000, m.partialPressure(A), EPS);
        assertEquals(0.01, m.molarDensity(A), EPS);

        // Standard conditions: 1 atm in 1000 L at 293.15 K is n = PV/RT ≈ 41.5718 mol.
        GasMixture air = mixture(1000, 293.15, 0.21 * 41.5718, 0.79 * 41.5718, 0);
        assertEquals(101.325, air.pressure(), 1e-2);

        // Changing volume changes pressure, not moles.
        assertTrue(m.setVolume(500));
        assertEquals(2 * 37.415081781, m.pressure(), EPS);
        assertEquals(15, m.totalMoles(), EPS);
    }

    @Test
    void core_002_energy_conserving_merge() {
        // C₁ = 10 × 20 = 200 J/K; C₂ = 5 × 30 + 2 × 12.5 = 175 J/K.
        GasMixture a = mixture(1000, 300, 10, 0, 0);
        GasMixture b = mixture(500, 400, 0, 5, 2);
        double energyBefore = a.thermalEnergy() + b.thermalEnergy();
        assertEquals(200 * 300 + 175 * 400, energyBefore, EPS);

        assertTrue(a.merge(b));
        assertEquals(energyBefore, a.thermalEnergy(), 1e-9 * energyBefore);
        assertEquals((200 * 300 + 175 * 400) / 375.0, a.temperature(), EPS);
        assertEquals(1000, a.volume(), "merge keeps the receiver's volume");

        // Merging with an empty mixture of another temperature changes nothing.
        GasMixture c = mixture(1000, 300, 10, 0, 0);
        assertTrue(c.merge(new GasMixture(TestGases.REGISTRY, 1000, 5000)));
        assertEquals(300, c.temperature(), EPS);

        // Merging into an empty mixture takes the other's temperature.
        GasMixture empty = new GasMixture(TestGases.REGISTRY, 1000, 20);
        assertTrue(empty.merge(b));
        assertEquals(400, empty.temperature(), EPS);
    }

    @Test
    void core_002_add_moles_at_own_temperature_conserves_energy() {
        GasMixture m = mixture(1000, 300, 10, 0, 0); // 200 J/K
        double before = m.thermalEnergy() + 30 * 4 * 500; // 4 mol of beta at 500 K
        assertTrue(m.addMoles(B, 4, 500));
        assertEquals(before, m.thermalEnergy(), 1e-9 * before);
        assertEquals((200 * 300 + 120 * 500) / 320.0, m.temperature(), EPS);
    }

    @Test
    void core_008_mole_conserving_merge() {
        GasMixture a = mixture(1000, 300, 10, 3, 0);
        GasMixture b = mixture(250, 250, 1.5, 0, 7);
        assertTrue(a.merge(b));
        assertEquals(11.5, a.moles(A), EPS);
        assertEquals(3, a.moles(B), EPS);
        assertEquals(7, a.moles(G), EPS);
        assertEquals(21.5, a.totalMoles(), EPS);
        // The source is unchanged.
        assertEquals(1.5, b.moles(A));
        assertEquals(7, b.moles(G));
    }

    @Test
    void core_003_heat_capacity() {
        assertEquals(10 * 20 + 5 * 30 + 2 * 12.5, mixture(1000, 300, 10, 5, 2).heatCapacity(), EPS);
        assertEquals(0.5 * 20 + 0.25 * 12.5, mixture(1000, 300, 0.5, 0, 0.25).heatCapacity(), EPS);
    }

    @Test
    void core_009_heat_capacity_floor() {
        GasMixture empty = new GasMixture(TestGases.REGISTRY, 1000, 300);
        assertEquals(0.0003, empty.heatCapacity());
        assertEquals(1, empty.addThermalEnergy(1));
        assertTrue(Double.isFinite(empty.temperature()));
        assertEquals(300 + 1 / 0.0003, empty.temperature(), 1e-6);

        // A tiny mixture below the floor reports the floor too.
        GasMixture tiny = mixture(1000, 300, 1e-6, 0, 0); // 2e-5 J/K
        assertEquals(0.0003, tiny.heatCapacity());
    }

    @Test
    void core_004_removal_operations() {
        // By moles: 5 of 20 mol is a quarter of each gas.
        GasMixture m = mixture(1000, 300, 10, 6, 4);
        GasMixture parcel = m.removeMoles(5);
        assertParcel(parcel, 2.5, 1.5, 1);
        assertParcel(m, 7.5, 4.5, 3);
        assertEquals(250, parcel.volume(), EPS);

        // By fraction.
        m = mixture(1000, 300, 10, 6, 4);
        parcel = m.removeFraction(0.5);
        assertParcel(parcel, 5, 3, 2);
        assertParcel(m, 5, 3, 2);

        // By volume: 250 L of 1000 L is a quarter; the parcel has the source's pressure.
        m = mixture(1000, 300, 10, 6, 4);
        double pressure = m.pressure();
        parcel = m.removeVolume(250);
        assertParcel(parcel, 2.5, 1.5, 1);
        assertParcel(m, 7.5, 4.5, 3);
        assertEquals(250, parcel.volume(), EPS);
        assertEquals(pressure, parcel.pressure(), 1e-9 * pressure);
        assertEquals(1000, m.volume(), "the source keeps its volume");

        // By moles of one gas.
        m = mixture(1000, 300, 10, 6, 4);
        assertEquals(2, m.removeGas(B, 2), EPS);
        assertParcel(m, 10, 4, 4);
    }

    @Test
    void core_060_removed_parcel_temperature() {
        GasMixture m = mixture(1000, 321.5, 10, 6, 4);
        assertEquals(321.5, m.removeMoles(3).temperature());
        assertEquals(321.5, m.removeFraction(0.2).temperature());
        assertEquals(321.5, m.removeVolume(100).temperature());
        assertEquals(321.5, m.temperature(), "removal doesn't change the source's temperature");
    }

    @Test
    void core_005_thermal_energy_changes() {
        GasMixture m = mixture(1000, 300, 10, 0, 0); // C = 200 J/K
        assertEquals(2000, m.addThermalEnergy(2000));
        assertEquals(310, m.temperature(), EPS); // ΔT = 2000 / 200
        assertEquals(1000, m.removeThermalEnergy(1000));
        assertEquals(305, m.temperature(), EPS);

        GasMixture mixed = mixture(1000, 300, 10, 5, 2); // C = 375 J/K
        mixed.addThermalEnergy(750);
        assertEquals(302, mixed.temperature(), EPS);
    }

    @Test
    void core_062_temperature_floor() {
        GasMixture m = mixture(1000, 300, 10, 0, 0); // C = 200 J/K
        assertEquals((300 - 2.7) * 200, m.removeThermalEnergy(1e9), EPS);
        assertEquals(2.7, m.temperature());
        assertEquals(0, m.removeThermalEnergy(10), "nothing more to remove at the floor");
        assertEquals(2.7, m.temperature());

        GasMixture configured = mixture(GasConfig.DEFAULT.withTemperatureFloor(50), 1000, 300, 10, 0, 0);
        assertEquals((300 - 50) * 200, configured.removeThermalEnergy(1e9), EPS);
        assertEquals(50, configured.temperature());

        // A removal that stays above the floor removes exactly what was asked.
        GasMixture partial = mixture(1000, 300, 10, 0, 0);
        assertEquals(100, partial.removeThermalEnergy(100));
        assertEquals(299.5, partial.temperature(), EPS);
    }

    @Test
    void core_007_pruning() {
        GasMixture m = new GasMixture(TestGases.REGISTRY, 1000, 300);
        assertTrue(m.setMoles(A, 5e-10)); // below the default 1e-9 mol
        assertTrue(m.setMoles(B, 2e-9)); // above it
        assertEquals(0, m.moles(A));
        assertEquals(2e-9, m.moles(B));
        assertEquals(2e-9, m.totalMoles());

        double[] pruned = new double[TestGases.REGISTRY.size()];
        m.drainPruned(pruned);
        assertEquals(5e-10, pruned[A], "pruned moles are counted for the ledger");
        m.drainPruned(pruned);
        assertEquals(5e-10, pruned[A], "draining resets the count");

        // A configured epsilon; a removal leaving less than it is pruned.
        GasMixture coarse = mixture(GasConfig.DEFAULT.withPruneEpsilon(0.01), 1000, 300, 1, 1, 0);
        coarse.removeGas(A, 0.995);
        assertEquals(0, coarse.moles(A));
        coarse.removeGas(B, 0.98);
        assertEquals(0.02, coarse.moles(B), EPS);

        // Each side of a removal is pruned: a tiny parcel share vanishes.
        GasMixture source = mixture(1000, 300, 10, 1e-6, 0);
        GasMixture parcel = source.removeFraction(1e-4); // 1e-10 mol of beta
        assertEquals(0, parcel.moles(B));
        assertEquals(1e-3, parcel.moles(A), EPS);
    }

    private static void assertParcel(GasMixture m, double alpha, double beta, double gamma) {
        assertEquals(alpha, m.moles(A), EPS, "alpha");
        assertEquals(beta, m.moles(B), EPS, "beta");
        assertEquals(gamma, m.moles(G), EPS, "gamma");
        assertEquals(alpha + beta + gamma, m.totalMoles(), EPS, "total");
    }
}
