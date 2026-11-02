package io.github.west3436.zas.engine.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The exact heat steps (HEAT-006, HEAT-012, HEAT-004). */
class HeatMathTest {
    /** HEAT-012: layers in series. */
    @Test
    void heat_012_series() {
        assertEquals(1.7, HeatMath.series(1.7), 1e-15);
        assertEquals(0.85, HeatMath.series(1.7, 1.7), 1e-15);
        assertEquals(1 / (1 / 1.7 + 1 / 0.15 + 1 / 50.0), HeatMath.series(1.7, 0.15, 50), 1e-15);
    }

    /**
     * HEAT-006: random capacities, conductances and periods never cross equilibrium, and a pair
     * conserves its energy.
     */
    @Test
    void heat_006_never_crosses_equilibrium() {
        Random random = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            double c1 = Math.pow(10, -3 + 9 * random.nextDouble());
            double c2 = Math.pow(10, -3 + 9 * random.nextDouble());
            double g = Math.pow(10, -6 + 12 * random.nextDouble());
            double dt = 0.1 + random.nextDouble() * 10;
            double t1 = random.nextDouble() * 2000;
            double t2 = random.nextDouble() * 2000;

            double next = HeatMath.towardReservoirs(t1, c1, g, t2, dt);
            assertTrue(Math.min(t1, t2) <= next && next <= Math.max(t1, t2), "past the reservoirs");

            double e = HeatMath.pairEnergy(t1, c1, t2, c2, g, dt);
            double n1 = t1 - e / c1;
            double n2 = t2 + e / c2;
            double before = t1 - t2;
            double after = n1 - n2;
            assertTrue(
                    before * after >= -1e-9 * Math.max(1, before * before),
                    "the pair crossed: " + before + " -> " + after);
            assertTrue(Math.abs(after) <= Math.abs(before) + 1e-9, "the pair moved apart");
            double energy = c1 * t1 + c2 * t2;
            assertEquals(energy, c1 * n1 + c2 * n2, 1e-9 * Math.abs(energy) + 1e-9, "energy");
        }
    }

    /** HEAT-004: the outer surface temperature balances conduction and radiation. */
    @Test
    void heat_004_surface_temperature_against_bisection() {
        Random random = new Random(4);
        for (int i = 0; i < 2_000; i++) {
            double u = Math.pow(10, -2 + 4 * random.nextDouble());
            double tZone = 10 + random.nextDouble() * 1500;
            double tBg = random.nextDouble() * 400;
            double e = random.nextDouble();
            double ts = HeatMath.surfaceTemperature(u, tZone, tBg, e);
            double expected = bisect(u, tZone, tBg, e);
            assertEquals(expected, ts, 1e-6 * Math.max(1, expected), "u " + u + ", " + tZone + " K to " + tBg + " K");
            double flux = HeatMath.radiativeFlux(u, tZone, tBg, e);
            double radiated = e * HeatMath.STEFAN_BOLTZMANN * (Math.pow(ts, 4) - Math.pow(tBg, 4));
            assertEquals(radiated, flux, 1e-6 * Math.max(1, Math.abs(flux)), "flux");
        }
        // A worked case: a stone wall, a room at 293.15 K, space at 2.7 K, ε 0.9.
        double ts = HeatMath.surfaceTemperature(1.7, 293.15, 2.7, 0.9);
        assertTrue(ts > 200 && ts < 293.15, "surface " + ts);
    }

    private static double bisect(double u, double tZone, double tBg, double e) {
        double lo = Math.min(tZone, tBg);
        double hi = Math.max(tZone, tBg);
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (lo + hi);
            double f = u * (tZone - mid) - e * HeatMath.STEFAN_BOLTZMANN * (Math.pow(mid, 4) - Math.pow(tBg, 4));
            if (f > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }
}
