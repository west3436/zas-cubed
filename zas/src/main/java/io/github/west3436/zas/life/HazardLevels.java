package io.github.west3436.zas.life;

import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.zone.DissolveGuard;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The hazard profiles (LIFE-004) as the gas levels that keep a sealed room's zone from dissolving
 * into the open air (CORE-052).
 *
 * <p>A zone that matches the open air within the engine's tolerances dissolves, and its gas goes.
 * Those tolerances allow about 5 kPa of a gas, and chlorine hurts from 0.001 kPa, so without these
 * levels a room that would hurt whoever walked in turned back into open air a minute after it was
 * left. Each gas with a profile gets a level at every threshold's partial pressure, and one at
 * {@link #KEPT_FRACTION} of its lowest, so that a room isn't lost just under the level the HUD
 * warns at. A zone stays while its gas and the open air's lie on different sides of any of them.
 *
 * <p>The levels follow the profiles: a {@code /reload} that changes a profile changes them from
 * the next atmos tick.
 */
public final class HazardLevels {
    /** The share of a gas's lowest threshold from which a sealed room holding it is kept. */
    public static final double KEPT_FRACTION = 0.5;

    /** The last guard compiled, with what it was compiled from. */
    private record Compiled(Map<ResourceLocation, HazardProfile> profiles, GasRegistry registry, DissolveGuard guard) {}

    private static volatile Compiled last;

    private HazardLevels() {}

    /**
     * The levels of the profiles now loaded, for an engine's gases. Compiled once for each set of
     * profiles and registry, so calling it every atmos tick costs two comparisons. Any thread.
     */
    public static DissolveGuard guard(GasRegistry registry) {
        Map<ResourceLocation, HazardProfile> profiles = HazardProfiles.all();
        Compiled compiled = last;
        // The profiles' map is replaced, never changed, so the same map is the same profiles.
        if (compiled == null || compiled.profiles != profiles || compiled.registry != registry) {
            compiled = new Compiled(profiles, registry, compile(profiles, registry));
            last = compiled;
        }
        return compiled.guard;
    }

    /** The levels of some profiles, for the gases a registry defines; a profile for any other gas gives none. */
    static DissolveGuard compile(Map<ResourceLocation, HazardProfile> profiles, GasRegistry registry) {
        DissolveGuard.Builder guard = DissolveGuard.builder(registry);
        profiles.forEach((gas, profile) -> {
            int index = registry.indexOf(gas.toString());
            List<HazardProfile.Threshold> thresholds = profile.thresholds();
            if (index < 0 || thresholds.isEmpty()) {
                return;
            }
            // The thresholds are in order of partial pressure, so the first is the lowest.
            guard.level(index, KEPT_FRACTION * thresholds.get(0).partialPressure(), false);
            for (HazardProfile.Threshold threshold : thresholds) {
                guard.level(index, threshold.partialPressure(), threshold.above());
            }
        });
        return guard.build();
    }
}
