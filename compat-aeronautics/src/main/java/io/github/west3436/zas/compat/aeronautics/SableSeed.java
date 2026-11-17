package io.github.west3436.zas.compat.aeronautics;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Ambients seeded from Sable's {@code dimension_physics} data (SPACE-020's Sable part, which
 * COMPAT-082 rests on). A dimension that a data pack entry gives a {@code base_pressure} is seeded
 * with standard air's mix at that many times 101.325 kPa, at 293.15 K; a {@code base_pressure} of 0
 * or less is a vacuum (ENV-003). Sable's data has no temperature.
 *
 * <p>As every seed, it applies only where no ZAS³ data pack definition and no provider gives the
 * dimension's ambient (D-45), so the Overworld, the Nether and the End keep ZAS³'s own. A dimension
 * with no entry, or whose entry leaves {@code base_pressure} at Sable's default, isn't covered: the
 * default describes no planet.
 *
 * <p>Lift there is Sable's pressure as it is (COMPAT-082): {@link LiftFactors} sees that this seed
 * gave the ambient, through {@code Ambients.seedOf}, and leaves the factor at 1.
 *
 * <p>{@code DimensionPhysicsData} is outside Sable's {@code api} package, so this has its own
 * GameTest (COMPAT-003).
 */
public final class SableSeed {
    /** The id the seed is registered under. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "sable_dimension_physics");

    private SableSeed() {}

    /**
     * The seed's answer for a level.
     *
     * @param level the dimension's level
     * @return the seeded ambient, or null if Sable's data doesn't cover the dimension or the setting
     *     is off
     */
    @Nullable
    public static AmbientDefinition seed(ServerLevel level) {
        if (!AeroSettings.seedFromDimensionPhysics()) {
            return null;
        }
        DimensionPhysics physics = DimensionPhysicsData.of(level);
        // of() falls back to the default it builds for the level, which is no data pack's entry.
        if (physics == null || physics == DimensionPhysicsData.getDefault(level)) {
            return null;
        }
        Optional<Double> base = physics.basePressure();
        return base.isPresent() ? ambient(base.get()) : null;
    }

    /**
     * The ambient a {@code base_pressure} seeds.
     *
     * @param basePressure Sable's multiplier of its own standard pressure
     * @return a vacuum for 0 or less or not a number, else standard air's mix at that many times
     *     101.325 kPa and 293.15 K
     */
    public static AmbientDefinition ambient(double basePressure) {
        if (!(basePressure > 0) || !Double.isFinite(basePressure)) {
            return AmbientDefinition.vacuum();
        }
        double total = StandardAir.PRESSURE * basePressure;
        return new AmbientDefinition(
                Map.of(
                        StandardAir.NITROGEN, total * StandardAir.NITROGEN_FRACTION,
                        StandardAir.OXYGEN, total * StandardAir.OXYGEN_FRACTION),
                StandardAir.TEMPERATURE);
    }
}
