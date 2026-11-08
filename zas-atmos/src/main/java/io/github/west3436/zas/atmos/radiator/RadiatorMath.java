package io.github.west3436.zas.atmos.radiator;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;

/**
 * The heat radiator pipe and heat exchangers move in an atmos tick (HEAT-020 to HEAT-022). Plain
 * arithmetic, so it is unit-tested without a world.
 *
 * <p><b>A network and its radiator pipe.</b> Each segment couples its network's gas to a body of
 * gas around it: its cell's share of its zone or of the open air (HEAT-020), or, in a vacuum's open
 * air, the background it radiates to (HEAT-021). The network's new temperature comes from all of
 * them at once, implicitly ({@link #settle}): the backward step of the heat equation, which lands
 * between the network's temperature and the bodies', so it never passes one of them, however large
 * the step or small the capacities (HEAT-006). Each body then gains {@code a × (T' − T_body)}, and
 * the heat the bodies gain is the heat the network loses.
 */
public final class RadiatorMath {
    /** The Stefan–Boltzmann constant, in W/(m²·K⁴) (CODATA 2018, exact). */
    public static final double STEFAN_BOLTZMANN = 5.670374419e-8;

    /** A segment's conductance to the gas around it, W/K, by default (HEAT-020): a finned tube in still air. */
    public static final double DEFAULT_CONDUCTANCE = 20;

    /** A segment's emissivity by default (HEAT-021), HEAT-004's. */
    public static final double DEFAULT_EMISSIVITY = 0.9;

    /** A segment's radiating area, m² (HEAT-021). */
    public static final double AREA = 1;

    /** One cell's volume, L: a segment exchanges with this much of the gas around it. */
    public static final double CELL_VOLUME = StandardAir.CELL_VOLUME;

    /** Standard air's molar density, mol/L. */
    public static final double STANDARD_DENSITY =
            StandardAir.PRESSURE / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);

    /**
     * The molar density below which gas is too thin to carry heat, mol/L: 1 % of standard air's, as a
     * near-vacuum zone conducts nothing (HEAT-008).
     */
    public static final double NEAR_VACUUM_DENSITY = 0.01 * STANDARD_DENSITY;

    private RadiatorMath() {}

    /**
     * Whether gas is too thin to carry heat to or from a segment (HEAT-008).
     *
     * @param moles its moles
     * @param volume its volume, L
     * @return true below {@link #NEAR_VACUUM_DENSITY}, or with no volume
     */
    public static boolean nearVacuum(double moles, double volume) {
        return !(volume > 0) || !(moles / volume >= NEAR_VACUUM_DENSITY);
    }

    /**
     * The heat capacity of one cell's share of a body of gas: its share by volume, or all of it if
     * it is no bigger than a cell.
     *
     * @param heatCapacity the body's heat capacity, J/K
     * @param volume its volume, L
     * @return the share's, J/K
     */
    public static double cellCapacity(double heatCapacity, double volume) {
        return volume > CELL_VOLUME ? heatCapacity * CELL_VOLUME / volume : heatCapacity;
    }

    /**
     * A segment's coupling to the gas of its cell over one atmos tick (HEAT-020), J/K: its
     * conductance over the step in series with the cell's heat capacity, so a segment can never move
     * more than the cell can take. In dense gas it is the conductance × the step.
     *
     * @param conductance W/K
     * @param seconds the step
     * @param cellCapacity the cell's share's heat capacity, J/K
     * @return the coupling, J/K; 0 for no conductance or no gas
     */
    public static double convection(double conductance, double seconds, double cellCapacity) {
        double g = conductance * seconds;
        if (!(g > 0) || !(cellCapacity > 0)) {
            return 0;
        }
        return g * cellCapacity / (g + cellCapacity);
    }

    /**
     * A segment's coupling to a vacuum's background over one atmos tick (HEAT-021), J/K: εσA(T² +
     * T_bg²)(T + T_bg) × the step, so that coupling × (T − T_bg) is εσA(T⁴ − T_bg⁴) × the step.
     *
     * @param emissivity ε, 0 to 1
     * @param seconds the step
     * @param t the network's temperature, K
     * @param background the background's, K
     * @return the coupling, J/K
     */
    public static double radiation(double emissivity, double seconds, double t, double background) {
        if (!(emissivity > 0)) {
            return 0;
        }
        return emissivity * STEFAN_BOLTZMANN * AREA * (t * t + background * background) * (t + background) * seconds;
    }

    /**
     * A network's temperature after one atmos tick coupled to bodies of gas or backgrounds: (C T + Σ
     * aᵢTᵢ) / (C + Σ aᵢ), which lies between its temperature and theirs.
     *
     * @param heatCapacity the network's, J/K, positive
     * @param t its temperature, K
     * @param couplings Σ aᵢ, J/K
     * @param weighted Σ aᵢTᵢ, J
     * @return its new temperature, K
     */
    public static double settle(double heatCapacity, double t, double couplings, double weighted) {
        if (!(couplings > 0)) {
            return t;
        }
        return (heatCapacity * t + weighted) / (heatCapacity + couplings);
    }

    /**
     * The heat a pair of heat exchangers moves in one atmos tick (HEAT-022), J: what brings their
     * shares to one temperature, ΔT × C₁C₂ / (C₁ + C₂).
     *
     * @param c1 the first exchanger's share's heat capacity, J/K
     * @param t1 its temperature, K
     * @param c2 the second's, J/K
     * @param t2 its temperature, K
     * @return the heat from the first to the second, negative if it flows the other way; 0 if
     *     either has no gas
     */
    public static double pairHeat(double c1, double t1, double c2, double t2) {
        if (!(c1 > 0) || !(c2 > 0) || t1 == t2) {
            return 0;
        }
        return (t1 - t2) * c1 * c2 / (c1 + c2);
    }
}
