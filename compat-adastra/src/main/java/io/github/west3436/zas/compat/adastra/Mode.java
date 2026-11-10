package io.github.west3436.zas.compat.adastra;

import io.github.west3436.zas.api.atmosphere.AtmospherePreset;
import io.github.west3436.zas.api.atmosphere.Atmospheres;

/**
 * How the adapter treats Ad Astra's own breathable areas (SPACE-001). In both modes ZAS³ is the only
 * source of atmospheric harm (SPACE-013).
 */
public enum Mode {
    /** Where an Ad Astra oxygen distributor reaches, outside zones, living things have air (SPACE-017). */
    COOPERATIVE,
    /** Only ZAS³'s atmosphere counts. */
    AUTHORITATIVE;

    /**
     * The mode now: the adapter's setting, or by default Cooperative under the Normal preset and
     * Authoritative under Hardcore (SPACE-004). Read each time, so a change of either applies from
     * the next check. Any thread.
     */
    public static Mode current() {
        AdAstraConfig.ModeSetting setting =
                AdAstraConfig.SPEC.isLoaded() ? AdAstraConfig.MODE.get() : AdAstraConfig.ModeSetting.PRESET;
        return switch (setting) {
            case COOPERATIVE -> COOPERATIVE;
            case AUTHORITATIVE -> AUTHORITATIVE;
            case PRESET -> Atmospheres.preset() == AtmospherePreset.HARDCORE ? AUTHORITATIVE : COOPERATIVE;
        };
    }
}
