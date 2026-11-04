package io.github.west3436.zas.engine.fire;

import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;

/**
 * The fire oxygen threshold (FIRE-030) and where incomplete combustion starts (FIRE-005). Any fire,
 * whether a hotspot, vanilla fire or a lit fuel block, burns only where the O₂ fraction is at least
 * {@code minOxygenFraction} <em>and</em> the O₂ partial pressure is at least {@code minOxygenKpa}.
 * The pressure floor replaces ZAS's one mole per tile, so nothing burns in a thin atmosphere such
 * as the Hardcore End however rich in O₂ it is. A gas fire at or above its fuel's autoignition
 * temperature needs only the pressure floor ({@link #sustainsAutoignited}, FIRE-035). Below an O₂
 * fraction of {@code incompleteBelowFraction}, part of a fuel's carbon forms CO ({@link
 * CarbonCombustion#coShare}). Pure; any thread.
 *
 * @param minOxygenFraction the least O₂ mole fraction a fire needs, 0 to 1; default 0.14
 * @param minOxygenKpa the least O₂ partial pressure a fire needs, kPa, not negative; default 10
 * @param incompleteBelowFraction the O₂ fraction below which carbon starts to form CO, 0 to 1;
 *     default 0.18
 */
public record FireSettings(double minOxygenFraction, double minOxygenKpa, double incompleteBelowFraction) {
    /** The registry id of O₂. */
    public static final String OXYGEN = "zas:oxygen";

    /** FIRE-030's and FIRE-005's defaults: 0.14, 10 kPa and 0.18. */
    public static final FireSettings DEFAULT = new FireSettings(0.14, 10.0, 0.18);

    public FireSettings {
        if (!(minOxygenFraction >= 0 && minOxygenFraction <= 1)) {
            throw new IllegalArgumentException("O2 fraction must be 0 to 1: " + minOxygenFraction);
        }
        if (!(minOxygenKpa >= 0) || !Double.isFinite(minOxygenKpa)) {
            throw new IllegalArgumentException("O2 partial pressure must be finite and >= 0: " + minOxygenKpa);
        }
        if (!(incompleteBelowFraction >= 0 && incompleteBelowFraction <= 1)) {
            throw new IllegalArgumentException("CO onset fraction must be 0 to 1: " + incompleteBelowFraction);
        }
    }

    /**
     * Whether a fire can burn in gas with this much O₂ (FIRE-030).
     *
     * @param oxygenFraction the O₂ mole fraction, 0 to 1
     * @param oxygenKpa the O₂ partial pressure, kPa
     * @return true when both are at or above their minimums
     */
    public boolean sustains(double oxygenFraction, double oxygenKpa) {
        return oxygenFraction >= minOxygenFraction && oxygenKpa >= minOxygenKpa;
    }

    /**
     * Whether a fire can burn in a mixture (FIRE-030). An empty mixture, or one in a registry
     * without O₂, can't sustain one.
     */
    public boolean sustains(GasMixture mixture) {
        int oxygen = mixture.registry().indexOf(OXYGEN);
        if (oxygen < 0 || mixture.isEmpty()) {
            return false;
        }
        return sustains(mixture.moles(oxygen) / mixture.totalMoles(), mixture.partialPressure(oxygen));
    }

    /**
     * Whether a gas fire at or above its fuel's autoignition temperature can burn in a mixture
     * (FIRE-035): it needs the O₂ partial pressure, whatever the fraction. An empty mixture, one in a
     * registry without O₂, or one with no O₂ in it, can't sustain one. Every other fire uses {@link
     * #sustains(GasMixture)}.
     */
    public boolean sustainsAutoignited(GasMixture mixture) {
        int oxygen = mixture.registry().indexOf(OXYGEN);
        if (oxygen < 0 || mixture.isEmpty() || !(mixture.moles(oxygen) > 0)) {
            return false;
        }
        return mixture.partialPressure(oxygen) >= minOxygenKpa;
    }

    /**
     * Whether a fire can burn in gas given as partial pressures by registry index, as a published
     * zone or an ambient gives them (FIRE-030, FIRE-020). Empty gas, or a registry without O₂,
     * can't sustain one.
     *
     * @param registry the registry the indices refer to
     * @param partialPressures each gas's partial pressure, kPa, by registry index
     */
    public boolean sustains(GasRegistry registry, double[] partialPressures) {
        int oxygen = registry.indexOf(OXYGEN);
        if (oxygen < 0 || oxygen >= partialPressures.length) {
            return false;
        }
        double total = 0;
        for (double p : partialPressures) {
            total += p;
        }
        if (!(total > 0)) {
            return false;
        }
        return sustains(partialPressures[oxygen] / total, partialPressures[oxygen]);
    }

    /**
     * The share of burning carbon that forms CO at an O₂ fraction (FIRE-005); {@link
     * CarbonCombustion#coShare(double, FireSettings)}.
     */
    public double coShare(double oxygenFraction) {
        return CarbonCombustion.coShare(oxygenFraction, this);
    }

    public FireSettings withMinOxygenFraction(double fraction) {
        return new FireSettings(fraction, minOxygenKpa, incompleteBelowFraction);
    }

    public FireSettings withMinOxygenKpa(double kpa) {
        return new FireSettings(minOxygenFraction, kpa, incompleteBelowFraction);
    }

    public FireSettings withIncompleteBelowFraction(double fraction) {
        return new FireSettings(minOxygenFraction, minOxygenKpa, fraction);
    }
}
