package io.github.west3436.zas.api;

import io.github.west3436.zas.api.atmosphere.AtmospherePreset;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The provider used while ZAS³ is absent: a vanilla, breathable world (D-17,
 * API-009). Every point is ambient standard air, no zone exists,
 * mutations complete at once with nothing moved, pins complete at once with
 * nothing pinned, and breathing follows
 * vanilla's rules, so being underwater still counts (API-014). Standard
 * air's gases are the only gases, grouped as ZAS³'s shipped tags group them
 * and with the heat capacities ZAS³ ships for them. The atmos period is
 * CORE-041's default, 1 s.
 */
final class FallbackProvider implements ZasApiProvider {
    static final FallbackProvider INSTANCE = new FallbackProvider();

    private static final AtmosphereSample AIR = AtmosphereSample.ambient(StandardAir.mixture(StandardAir.CELL_VOLUME));
    private static final CompletableFuture<MutationResult> ABSENT =
            CompletableFuture.completedFuture(MutationResult.ABSENT);
    private static final Set<ResourceLocation> OXIDIZERS = Set.of(StandardAir.OXYGEN);

    /** Standard air's gases, in id order. Also {@link ZasApiProvider#definedGases}'s default. */
    static final Set<ResourceLocation> STANDARD_AIR_GASES =
            Collections.unmodifiableSet(new TreeSet<>(Set.of(StandardAir.NITROGEN, StandardAir.OXYGEN)));

    /** N₂'s C<sub>v</sub> at 298.15 K, as ZAS³ ships it (CORE-015), in J/(mol·K). */
    static final double NITROGEN_HEAT_CAPACITY = 20.81;

    /** O₂'s C<sub>v</sub> at 298.15 K, as ZAS³ ships it (CORE-015), in J/(mol·K). */
    static final double OXYGEN_HEAT_CAPACITY = 21.06;

    private FallbackProvider() {}

    @Override
    public boolean isReal() {
        return false;
    }

    @Override
    public AtmosphereSample sample(Level level, Vec3 point) {
        return AIR;
    }

    @Override
    public AtmosphereSample sample(Level level, BlockPos pos) {
        return AIR;
    }

    @Override
    public Optional<ZoneInfo> zone(Level level, ZoneId id) {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<MutationResult> addGas(ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
        // A completed future can't be completed again, and its result is
        // immutable, so one instance serves every caller.
        return ABSENT.copy();
    }

    @Override
    public CompletableFuture<MutationResult> removeGas(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
        return ABSENT.copy();
    }

    @Override
    public CompletableFuture<PinResult> pin(ServerLevel level, Vec3 point, SourceId owner) {
        return CompletableFuture.completedFuture(PinResult.ABSENT);
    }

    @Override
    public CompletableFuture<Void> unpin(ServerLevel level, SourceId owner) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Set<ResourceLocation> gasesInTag(GasTag tag) {
        return standardAirGases(tag);
    }

    /**
     * Standard air's gases in a tag, as ZAS³'s tags group them: only oxygen is in a shipped tag.
     * Also {@link ZasApiProvider#gasesInTag}'s default.
     */
    static Set<ResourceLocation> standardAirGases(GasTag tag) {
        return tag.equals(GasTags.OXIDIZER) ? OXIDIZERS : Set.of();
    }

    @Override
    public Breathability breathability(LivingEntity entity) {
        return Breathing.vanilla(entity);
    }

    @Override
    public OptionalDouble molarHeatCapacity(ResourceLocation gas) {
        return standardAirHeatCapacity(gas);
    }

    /**
     * Standard air's gases' heat capacities as ZAS³ ships them; nothing for any other gas. Also
     * {@link ZasApiProvider#molarHeatCapacity}'s default.
     */
    static OptionalDouble standardAirHeatCapacity(ResourceLocation gas) {
        if (gas.equals(StandardAir.NITROGEN)) {
            return OptionalDouble.of(NITROGEN_HEAT_CAPACITY);
        }
        if (gas.equals(StandardAir.OXYGEN)) {
            return OptionalDouble.of(OXYGEN_HEAT_CAPACITY);
        }
        return OptionalDouble.empty();
    }

    @Override
    public Set<ResourceLocation> definedGases() {
        return STANDARD_AIR_GASES;
    }

    @Override
    public Optional<String> gasTranslationKey(ResourceLocation gas) {
        return Optional.empty();
    }

    @Override
    public int atmosPeriodTicks() {
        return Atmospheres.DEFAULT_PERIOD_TICKS;
    }

    @Override
    public boolean containerExplosionsBreakBlocks() {
        return false;
    }

    @Override
    public AtmospherePreset preset() {
        return AtmospherePreset.NORMAL;
    }

    /** No zone exists without ZAS³, so none touches anything. */
    @Override
    public boolean zoneTouches(Level level, BlockPos pos) {
        return false;
    }

    @Override
    public double nearVacuumDensity() {
        return Atmospheres.DEFAULT_NEAR_VACUUM_DENSITY;
    }
}
