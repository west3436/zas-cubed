package io.github.west3436.zas.engine.heat;

/**
 * The exact steps conduction and radiation take (HEAT-006, HEAT-012, HEAT-004). Pure functions.
 *
 * <p>Heat flow through a path of conductance G (W/K) is G·ΔT. Over a tick of {@code dt} seconds the
 * steps below integrate that flow exactly rather than with one explicit step, so a step never
 * overshoots equilibrium whatever the period, heat capacities or conductances (HEAT-006):
 *
 * <ul>
 *   <li>A body of heat capacity C coupled to fixed-temperature reservoirs with total conductance
 *       G moves toward their conductance-weighted mean T_eq: T' = T_eq + (T − T_eq)·e^(−G·dt/C).
 *   <li>Two bodies coupled by G close their difference: ΔT' = ΔT·e^(−G·(1/C₁ + 1/C₂)·dt), and the
 *       energy moved conserves C₁T₁ + C₂T₂.
 * </ul>
 */
public final class HeatMath {
    /** The Stefan–Boltzmann constant, in W/(m²·K⁴) (CODATA 2018, exact). */
    public static final double STEFAN_BOLTZMANN = 5.670374419e-8;

    private HeatMath() {}

    /**
     * The series transmittance of layers: 1/U = Σ 1/Uᵢ (HEAT-012).
     *
     * @param transmittances each layer's U, positive
     * @return the path's U in W/(m²·K)
     */
    public static double series(double... transmittances) {
        double r = 0;
        for (double u : transmittances) {
            r += 1 / u;
        }
        return 1 / r;
    }

    /**
     * A body's temperature after a tick coupled to reservoirs.
     *
     * @param t its temperature, K
     * @param c its heat capacity, J/K, positive
     * @param g the reservoirs' total conductance, W/K, not negative
     * @param teq their conductance-weighted mean temperature, K
     * @param dt the tick, s
     * @return the new temperature, between {@code t} and {@code teq}
     */
    public static double towardReservoirs(double t, double c, double g, double teq, double dt) {
        if (!(g > 0) || !(c > 0)) {
            return t;
        }
        return teq + (t - teq) * Math.exp(-g * dt / c);
    }

    /**
     * The energy two bodies exchange in a tick through a conductance.
     *
     * @param ta the first body's temperature, K
     * @param ca its heat capacity, J/K, positive
     * @param tb the second body's temperature, K
     * @param cb its heat capacity, J/K, positive
     * @param g the conductance between them, W/K
     * @param dt the tick, s
     * @return the energy, J, that flows from the first to the second (negative if the other way);
     *     never enough to reverse their difference
     */
    public static double pairEnergy(double ta, double ca, double tb, double cb, double g, double dt) {
        if (!(g > 0) || !(ca > 0) || !(cb > 0)) {
            return 0;
        }
        double inv = 1 / ca + 1 / cb;
        double dT = ta - tb;
        return dT * -Math.expm1(-g * inv * dt) / inv;
    }

    /**
     * The outer surface temperature of a wall that conducts heat from a zone and radiates it to a
     * vacuum (HEAT-004): where U·(T_zone − T_s) = εσ(T_s⁴ − T_bg⁴). The left side falls and the
     * right side rises with T_s, so the root between T_bg and T_zone is unique; it is found by
     * Newton steps kept inside a shrinking bracket.
     *
     * @param u the wall's U, W/(m²·K), positive
     * @param tZone the zone's temperature, K
     * @param tBackground the vacuum's temperature, K
     * @param emissivity ε, 0 to 1
     * @return T_s in K
     */
    public static double surfaceTemperature(double u, double tZone, double tBackground, double emissivity) {
        double es = emissivity * STEFAN_BOLTZMANN;
        if (tZone == tBackground || !(es > 0)) {
            return tZone;
        }
        double lo = Math.min(tZone, tBackground);
        double hi = Math.max(tZone, tBackground);
        double bg4 = tBackground * tBackground * tBackground * tBackground;
        double ts = 0.5 * (lo + hi);
        for (int i = 0; i < 100; i++) {
            double ts3 = ts * ts * ts;
            double f = u * (tZone - ts) - es * (ts3 * ts - bg4);
            if (f > 0) {
                lo = ts;
            } else if (f < 0) {
                hi = ts;
            } else {
                return ts;
            }
            double df = -u - 4 * es * ts3;
            double next = ts - f / df;
            if (!(next > lo && next < hi)) {
                next = 0.5 * (lo + hi);
            }
            if (Math.abs(next - ts) < 1e-12 * Math.max(1, ts)) {
                return next;
            }
            ts = next;
        }
        return ts;
    }

    /**
     * The heat flux, in W/m², from a zone through a wall of transmittance U to a vacuum at
     * {@code tBackground} (HEAT-004): conduction through the wall in series with radiation from
     * its outer surface. Positive when the zone is warmer.
     */
    public static double radiativeFlux(double u, double tZone, double tBackground, double emissivity) {
        return u * (tZone - surfaceTemperature(u, tZone, tBackground, emissivity));
    }
}
