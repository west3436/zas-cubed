package io.github.west3436.zas.engine.fire;

/**
 * Burning carbon, completely or not (FIRE-005, FIRE-025). Fuels burn as carbon, C + O₂ → CO₂ at
 * 394 kJ/mol, so a fuel's power fixes how much carbon it burns per second, and with it the O₂ it
 * uses and the CO₂ it makes. Below an O₂ fraction of 0.18 ({@link FireSettings#incompleteBelowFraction()}) part of the carbon
 * forms CO instead (C + ½O₂ → CO): the share rises linearly from 0 at 0.18 to all of it at the fire
 * threshold (FIRE-030). Each mole of carbon that forms CO uses half a mole less O₂ and releases
 * {@link #CO_OXIDATION_ENTHALPY} less heat, the heat CO would give burning on to CO₂ (FIRE-003). The
 * carbon burned stays the same and the heat falls to match.
 *
 * <p>The share applies to the carbon of any carbon fuel, CH₄ included: {@link #oxygenSaved(double)}
 * and {@link #heatLost(double)} give what to take off a complete burn's O₂ and heat per mole of
 * carbon. Pure; any thread.
 */
public final class CarbonCombustion {
    /** Heat of combustion of carbon to CO₂, J/mol (FIRE-025). */
    public static final double CARBON_ENTHALPY = 394_000;

    /** Heat of combustion of CO to CO₂, J/mol (FIRE-003). */
    public static final double CO_OXIDATION_ENTHALPY = 283_000;

    private CarbonCombustion() {}

    /**
     * The share of burning carbon that forms CO rather than CO₂ (FIRE-005): 0 at or above the onset
     * fraction (0.18 by default), rising linearly to 1 at the fire threshold (0.14), and 1 below it
     * (where only a gas fire at its fuel's autoignition temperature burns, FIRE-035). With a threshold
     * at or above the onset, the share is 0 at and above the threshold.
     *
     * @param oxygenFraction the O₂ mole fraction where the carbon burns
     * @param thresholdFraction the fire threshold's O₂ fraction ({@link
     *     FireSettings#minOxygenFraction()})
     * @param onsetFraction the O₂ fraction below which CO starts to form ({@link
     *     FireSettings#incompleteBelowFraction()})
     * @return the CO share, 0 to 1
     */
    public static double coShare(double oxygenFraction, double thresholdFraction, double onsetFraction) {
        if (!(oxygenFraction < onsetFraction)) {
            return 0;
        }
        if (thresholdFraction >= onsetFraction) {
            return oxygenFraction < thresholdFraction ? 1 : 0;
        }
        double share = (onsetFraction - oxygenFraction) / (onsetFraction - thresholdFraction);
        return Math.min(1, Math.max(0, share));
    }

    /** {@link #coShare(double, double, double)} with a fire's settings. */
    public static double coShare(double oxygenFraction, FireSettings settings) {
        return coShare(oxygenFraction, settings.minOxygenFraction(), settings.incompleteBelowFraction());
    }

    /** O₂ a mole of carbon needs less than when it burns to CO₂, mol: half its CO share. */
    public static double oxygenSaved(double coShare) {
        return coShare / 2;
    }

    /** Heat a mole of carbon releases less than when it burns to CO₂, J: its CO share of 283 kJ. */
    public static double heatLost(double coShare) {
        return coShare * CO_OXIDATION_ENTHALPY;
    }

    /**
     * Carbon burned at a power, as a fuel profile gives it (FIRE-025).
     *
     * @param watts the power the fuel releases burning completely, W
     * @param seconds how long it burns, s
     * @return the carbon burned, mol
     */
    public static double carbonFor(double watts, double seconds) {
        return watts * seconds / CARBON_ENTHALPY;
    }

    /**
     * Burns carbon with a CO share.
     *
     * @param carbon the carbon burned, mol, not negative
     * @param coShare the share forming CO, 0 to 1 ({@link #coShare(double, FireSettings)})
     * @return what it uses, makes and releases
     */
    public static Burn burn(double carbon, double coShare) {
        if (!(carbon >= 0) || !Double.isFinite(carbon)) {
            throw new IllegalArgumentException("carbon must be finite and >= 0: " + carbon);
        }
        if (!(coShare >= 0 && coShare <= 1)) {
            throw new IllegalArgumentException("CO share must be 0 to 1: " + coShare);
        }
        double co = carbon * coShare;
        return new Burn(
                carbon, carbon - co / 2, carbon - co, co, carbon * CARBON_ENTHALPY - co * CO_OXIDATION_ENTHALPY);
    }

    /**
     * Burns carbon at a power for a time where the O₂ fraction is {@code oxygenFraction} (FIRE-005,
     * FIRE-025). It doesn't check the threshold; the caller does ({@link FireSettings#sustains}).
     *
     * @param watts the profile's power, W, burning completely
     * @param seconds how long it burns, s
     * @param oxygenFraction the O₂ fraction where it burns
     * @param threshold the fire threshold and CO onset
     */
    public static Burn burnAt(double watts, double seconds, double oxygenFraction, FireSettings threshold) {
        return burn(carbonFor(watts, seconds), coShare(oxygenFraction, threshold));
    }

    /**
     * What burning carbon uses, makes and releases.
     *
     * @param carbon carbon burned, mol
     * @param oxygen O₂ used, mol
     * @param carbonDioxide CO₂ made, mol
     * @param carbonMonoxide CO made, mol
     * @param heat heat released, J
     */
    public record Burn(double carbon, double oxygen, double carbonDioxide, double carbonMonoxide, double heat) {
        /**
         * This burn scaled by a factor, as when there is too little O₂ for all of it.
         *
         * @param factor 0 to 1
         */
        public Burn scaled(double factor) {
            return new Burn(
                    carbon * factor, oxygen * factor, carbonDioxide * factor, carbonMonoxide * factor, heat * factor);
        }
    }
}
