package io.github.west3436.zas.engine.gas;

import static io.github.west3436.zas.engine.gas.TestGases.A;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class GasMixtureFuzzTest {
    private static final GasRegistry REG = TestGases.REGISTRY;
    private static final int N = REG.size();

    @Test
    void core_061_removal_never_goes_negative() {
        Random random = new Random(61);
        for (int round = 0; round < 20_000; round++) {
            GasMixture m = randomMixture(random, GasConfig.DEFAULT);
            double[] before = moles(m);
            double total = m.totalMoles();
            // Ask for up to three times what is present.
            double ask = random.nextDouble() * 3;
            GasMixture parcel = null;
            double removedGas = 0;
            int gas = random.nextInt(N);
            switch (random.nextInt(4)) {
                case 0 -> parcel = m.removeMoles(ask * total);
                case 1 -> parcel = m.removeFraction(ask - 1); // also below 0 and above 1
                case 2 -> parcel = m.removeVolume(ask * m.volume());
                default -> removedGas = m.removeGas(gas, ask * before[gas]);
            }
            for (int i = 0; i < N; i++) {
                assertTrue(m.moles(i) >= 0, "source went negative in round " + round);
                double removed = parcel != null ? parcel.moles(i) : (i == gas ? removedGas : 0);
                assertTrue(removed >= 0, "parcel went negative in round " + round);
                assertTrue(removed <= before[i], "removed more than present in round " + round);
            }
            assertTrue(m.totalMoles() >= 0);
        }

        // An over-ask removes exactly what is present.
        GasMixture m = TestGases.mixture(1000, 300, 10, 6, 4);
        GasMixture all = m.removeMoles(1e6);
        assertEquals(20, all.totalMoles(), 1e-12);
        assertTrue(m.isEmpty());
        GasMixture n = TestGases.mixture(1000, 300, 10, 6, 4);
        assertEquals(10, n.removeGas(A, 50));
        assertEquals(0, n.moles(A));
    }

    @Test
    void core_063_no_non_finite_values() {
        double[] extremes = {
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.MAX_VALUE,
            1e300,
            1e200,
            -1,
            -Double.MIN_VALUE,
            0,
            Double.MIN_VALUE,
            1e-300,
            1,
            1e6
        };
        Random random = new Random(63);
        TestGases.RecordingLog log = new TestGases.RecordingLog();
        GasConfig config = GasConfig.DEFAULT.withLog(log);
        int rejections = 0;
        for (int round = 0; round < 50_000; round++) {
            GasMixture m =
                    random.nextInt(4) == 0 ? new GasMixture(REG, config, 1000, 300) : randomMixture(random, config);
            if (random.nextInt(8) == 0) {
                m.setMoles(random.nextInt(N), 1e300); // start some rounds near overflow
            }
            double x = random.nextBoolean() ? extremes[random.nextInt(extremes.length)] : random.nextDouble() * 1e3;
            int gas = random.nextInt(N);
            GasMixture other = randomMixture(random, config);
            other.setMoles(gas, Math.abs(x) <= Double.MAX_VALUE ? Math.abs(x) : 1);
            double[] snapshot = snapshot(m);
            int logged = log.messages.size();
            Boolean accepted = switch (random.nextInt(12)) {
                case 0 -> m.setMoles(gas, x);
                case 1 -> m.setTemperature(x);
                case 2 -> m.setVolume(x);
                case 3 -> m.addMoles(gas, x);
                case 4 -> m.addMoles(gas, 1, x);
                case 5 -> {
                    m.addThermalEnergy(x);
                    yield null;
                }
                case 6 -> {
                    m.removeThermalEnergy(x);
                    yield null;
                }
                case 7 -> {
                    assertFinite(m.removeFraction(x));
                    yield null;
                }
                case 8 -> {
                    assertFinite(m.removeMoles(x));
                    yield null;
                }
                case 9 -> {
                    assertFinite(m.removeVolume(x));
                    yield null;
                }
                case 10 -> {
                    m.removeGas(gas, x);
                    yield null;
                }
                default -> m.merge(other);
            };
            assertFinite(m);
            boolean rejected = log.messages.size() > logged;
            if (rejected) {
                rejections++;
                assertEquals(logged + 1, log.messages.size(), "one log line per rejection");
                assertArrayEquals(snapshot, snapshot(m), "a rejected operation changed state in round " + round);
            }
            if (accepted != null) {
                assertEquals(!accepted, rejected, "a rejection is both reported and logged, in round " + round);
            }
        }
        assertTrue(rejections > 1000, "the fuzz exercised rejections: " + rejections);

        // The cases named in the Verify plan, explicitly.
        assertThrows(IllegalArgumentException.class, () -> new GasMixture(REG, config, 0, 300));
        GasMixture m = new GasMixture(REG, config, 1000, 300);
        int logged = log.messages.size();
        assertFalse(m.setVolume(0));
        assertEquals(0, m.addThermalEnergy(Double.MAX_VALUE), "a huge energy on the floor heat capacity overflows");
        assertEquals(300, m.temperature());
        assertEquals(logged + 2, log.messages.size());
        assertTrue(log.messages.get(logged).contains("setVolume"));
        assertTrue(log.messages.get(logged + 1).contains("addThermalEnergy"));
    }

    private static GasMixture randomMixture(Random random, GasConfig config) {
        GasMixture m = new GasMixture(REG, config, 1 + random.nextDouble() * 5000, random.nextDouble() * 2000);
        for (int i = 0; i < N; i++) {
            if (random.nextInt(3) > 0) {
                m.setMoles(i, random.nextDouble() * 100);
            }
        }
        return m;
    }

    private static double[] moles(GasMixture m) {
        double[] out = new double[N];
        for (int i = 0; i < N; i++) {
            out[i] = m.moles(i);
        }
        return out;
    }

    private static double[] snapshot(GasMixture m) {
        double[] out = new double[N + 2];
        System.arraycopy(moles(m), 0, out, 0, N);
        out[N] = m.temperature();
        out[N + 1] = m.volume();
        return out;
    }

    private static void assertFinite(GasMixture m) {
        for (int i = 0; i < N; i++) {
            assertTrue(Double.isFinite(m.moles(i)) && m.moles(i) >= 0, "moles " + m);
        }
        assertTrue(Double.isFinite(m.temperature()) && m.temperature() >= 0, "temperature " + m);
        assertTrue(Double.isFinite(m.volume()) && m.volume() > 0, "volume " + m);
        assertTrue(Double.isFinite(m.totalMoles()), "total " + m);
        assertTrue(Double.isFinite(m.pressure()), "pressure " + m);
        assertTrue(Double.isFinite(m.heatCapacity()), "heat capacity " + m);
        assertTrue(Double.isFinite(m.thermalEnergy()), "thermal energy " + m);
    }
}
