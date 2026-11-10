package io.github.west3436.zas.api;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Ignition;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The service behind the API. ZAS³ installs its implementation with
 * {@link ZasApi#install}; until then, and when ZAS³ is absent, a built-in
 * fallback answers with a vanilla, breathable world (D-17).
 *
 * <p><b>Consumers don't call or implement this.</b> They use
 * {@link io.github.west3436.zas.api.atmosphere.Atmospheres},
 * {@link io.github.west3436.zas.api.mutation.GasMutations},
 * {@link io.github.west3436.zas.api.mutation.HeatMutations},
 * {@link io.github.west3436.zas.api.mutation.ZonePins} and
 * {@link io.github.west3436.zas.api.life.Breathing}, which check arguments
 * and threads before delegating here, so implementations may assume non-null
 * arguments and, for mutations, the server thread.
 *
 * <p>A method added in a later API version needs a default, so that a provider
 * built against an older API keeps working when a newer one is loaded
 * (architecture §6.2).
 */
public interface ZasApiProvider {
    /**
     * Whether this is a real implementation rather than the fallback.
     *
     * <p>Thread: any.
     *
     * @return true for ZAS³'s implementation
     */
    @AnyThread
    boolean isReal();

    /**
     * The atmosphere at an exact point, from the last applied atmos tick.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param point the point, resolved as described in
     *     {@link io.github.west3436.zas.api.atmosphere.Atmospheres}
     * @return an immutable sample
     */
    @AnyThread
    AtmosphereSample sample(Level level, Vec3 point);

    /**
     * The atmosphere at a block position's largest cell, from the last applied
     * atmos tick.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param pos the block position
     * @return an immutable sample
     */
    @AnyThread
    AtmosphereSample sample(Level level, BlockPos pos);

    /**
     * A zone by id, from the last applied atmos tick.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param id the zone id
     * @return an immutable zone snapshot, or empty if there is no such zone
     */
    @AnyThread
    Optional<ZoneInfo> zone(Level level, ZoneId id);

    /**
     * Queues an addition of gas.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param point the point
     * @param gas the gas offered
     * @param source who is adding it
     * @return a future that completes on the server thread once the change is
     *     applied
     */
    @ServerThread
    CompletableFuture<MutationResult> addGas(ServerLevel level, Vec3 point, GasParcel gas, SourceId source);

    /**
     * Queues a removal of gas.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param point the point
     * @param moles the moles wanted per gas, already checked and copied
     * @param source who is removing it
     * @return a future that completes on the server thread, with the gas
     *     delivered, once the change is applied
     */
    @ServerThread
    CompletableFuture<MutationResult> removeGas(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source);

    /**
     * Queues an addition of heat (API-013).
     *
     * <p>The default is the fallback's answer: {@link HeatResult#ABSENT} at once. It is there so
     * that a provider built against an API from before this method still answers when a newer API
     * is loaded (architecture §6.2).
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param point the point
     * @param joules the energy offered, in J, already checked
     * @param source who is adding it
     * @return a future that completes on the server thread once the change is applied
     */
    @ServerThread
    default CompletableFuture<HeatResult> addHeat(ServerLevel level, Vec3 point, double joules, SourceId source) {
        return CompletableFuture.completedFuture(HeatResult.ABSENT);
    }

    /**
     * Queues a removal of heat (API-013).
     *
     * <p>The default is the fallback's answer, as for {@link #addHeat}.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param point the point
     * @param joules the energy wanted, in J, already checked
     * @param source who is removing it
     * @return a future that completes on the server thread, with the heat delivered, once the
     *     change is applied
     */
    @ServerThread
    default CompletableFuture<HeatResult> removeHeat(ServerLevel level, Vec3 point, double joules, SourceId source) {
        return CompletableFuture.completedFuture(HeatResult.ABSENT);
    }

    /**
     * Queues a pin of the zone at a point (API-017, API-050), forming one if the point's region is
     * enclosed and unzoned.
     *
     * <p>The default is the fallback's answer: {@link PinResult#ABSENT} at once. It is there so that
     * a provider built against an API from before this method still answers when a consumer nests
     * a newer API, which FML then loads instead (architecture §6.2).
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param point the point
     * @param owner who holds the pin
     * @return a future that completes on the server thread once the pin is applied
     */
    @ServerThread
    default CompletableFuture<PinResult> pin(ServerLevel level, Vec3 point, SourceId owner) {
        return CompletableFuture.completedFuture(PinResult.ABSENT);
    }

    /**
     * Queues the removal of an owner's pins from every zone of a level (API-017).
     *
     * <p>The default is the fallback's answer: done at once, since there are no zones. It is there
     * for the same reason as {@link #pin}'s.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param owner whose pins to remove
     * @return a future that completes on the server thread once the pins are removed
     */
    @ServerThread
    default CompletableFuture<Void> unpin(ServerLevel level, SourceId owner) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * The gases in a gas tag (CORE-012), for
     * {@link io.github.west3436.zas.api.gas.GasTags}.
     *
     * <p>The default is the fallback's answer: standard air's gases as ZAS³'s shipped tags group
     * them, which puts oxygen in {@link io.github.west3436.zas.api.gas.GasTags#OXIDIZER} and
     * nothing in any other tag. It is there so that a provider built against an API from before
     * this method still answers when a consumer nests a newer API, which FML then loads instead
     * (architecture §6.2).
     *
     * <p>Thread: any.
     *
     * @param tag the tag
     * @return the ids of its gases, immutable; empty if there is no such tag
     */
    @AnyThread
    default Set<ResourceLocation> gasesInTag(GasTag tag) {
        return FallbackProvider.standardAirGases(tag);
    }

    /**
     * Whether an entity can breathe where it is, and whether it is protected (API-014).
     *
     * <p>The default is vanilla's verdict ({@link Breathing#vanilla}). It is there so that a
     * provider built against an API from before this method still answers when a consumer nests
     * a newer API, which FML then loads instead (architecture §6.2).
     *
     * <p>Thread: the main thread of the entity's logical side; for a server-side entity the caller
     * has checked.
     *
     * @param entity the entity, server or client side
     * @return the verdict
     */
    @LevelThread
    default Breathability breathability(LivingEntity entity) {
        return Breathing.vanilla(entity);
    }

    /**
     * The O₂ partial pressure below which a breathing entity runs out of air (LIFE-003), for
     * {@link Breathing#oxygenMinimum()}.
     *
     * <p>The default is LIFE-003's default, {@link Breathing#DEFAULT_OXYGEN_MINIMUM}, which is
     * also the fallback's answer. It is there so that a provider built against an API from before
     * this method still answers when a newer API is loaded (architecture §6.2).
     *
     * <p>Thread: any.
     *
     * @return the minimum in kPa
     */
    @AnyThread
    default double oxygenMinimum() {
        return Breathing.DEFAULT_OXYGEN_MINIMUM;
    }

    /**
     * A gas's molar heat capacity at constant volume (CORE-003, API-051), for
     * {@link io.github.west3436.zas.api.gas.GasProperties}.
     *
     * <p>The default is the fallback's answer: the heat capacities ZAS³ ships for standard air's
     * gases, and nothing for any other gas. It is there so that a provider built against an API
     * from before this method still answers when a newer API is loaded (architecture §6.2).
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return C<sub>v</sub> in J/(mol·K), or empty if the gas isn't defined
     */
    @AnyThread
    default OptionalDouble molarHeatCapacity(ResourceLocation gas) {
        return FallbackProvider.standardAirHeatCapacity(gas);
    }

    /**
     * Every gas the server defines (CORE-010, API-051), for
     * {@link io.github.west3436.zas.api.gas.GasProperties#defined()}.
     *
     * <p>The default is the fallback's answer: standard air's gases. It is there for the same
     * reason as {@link #molarHeatCapacity}'s.
     *
     * <p>Thread: any.
     *
     * @return the gas ids, immutable, in id order
     */
    @AnyThread
    default Set<ResourceLocation> definedGases() {
        return FallbackProvider.STANDARD_AIR_GASES;
    }

    /**
     * The translation key a gas's definition names it by (CORE-010, API-051), for
     * {@link io.github.west3436.zas.api.gas.GasProperties#translationKey}.
     *
     * <p>The default is the fallback's answer: none, so the caller uses the key's usual form. It
     * is there for the same reason as {@link #molarHeatCapacity}'s.
     *
     * <p>Thread: any.
     *
     * @param gas the gas id
     * @return the key, or empty if the gas isn't defined
     */
    @AnyThread
    default Optional<String> gasTranslationKey(ResourceLocation gas) {
        return Optional.empty();
    }

    /**
     * The atmos tick period in game ticks (CORE-041, API-052), for
     * {@link Atmospheres#periodTicks()}.
     *
     * <p>The default is the fallback's answer, CORE-041's default of 20 (1 s). It is there for the
     * same reason as {@link #molarHeatCapacity}'s.
     *
     * <p>Thread: any.
     *
     * @return the period, at least 2
     */
    @AnyThread
    default int atmosPeriodTicks() {
        return Atmospheres.DEFAULT_PERIOD_TICKS;
    }

    /**
     * Whether explosions of gas containers break blocks (API-059, CONT-107), for {@link
     * Atmospheres#containerExplosionsBreakBlocks()}.
     *
     * <p>The default is the fallback's answer, false. It is there for the same reason as {@link
     * #molarHeatCapacity}'s.
     *
     * <p>Thread: any.
     *
     * @return true if a container's explosion should break blocks
     */
    @AnyThread
    default boolean containerExplosionsBreakBlocks() {
        return false;
    }

    /**
     * The molar density below which a zone neither conducts nor radiates (API-064, HEAT-008), in
     * mol/L, for {@link Atmospheres#nearVacuumDensity()}.
     *
     * <p>The default is the fallback's answer, {@link Atmospheres#DEFAULT_NEAR_VACUUM_DENSITY}. It is
     * there for the same reason as {@link #molarHeatCapacity}'s.
     *
     * <p>Thread: any.
     *
     * @return the density, 0 or more
     */
    @AnyThread
    default double nearVacuumDensity() {
        return Atmospheres.DEFAULT_NEAR_VACUUM_DENSITY;
    }

    /**
     * Applies the server's reactions to a standalone mixture (API-019, FIRE-031), for {@link
     * io.github.west3436.zas.api.reaction.Reactions#applyStandalone}.
     *
     * <p>The default is the fallback's answer: the mixture unchanged, as there are no reactions. It
     * is there for the same reason as {@link #molarHeatCapacity}'s.
     *
     * <p>Thread: any.
     *
     * @param mixture the gas
     * @param seconds the time step, already checked
     * @param ignition whether an ignition source acts on the gas
     * @return the gas after the step
     */
    @AnyThread
    default GasMixture applyStandalone(GasMixture mixture, double seconds, boolean ignition) {
        return mixture;
    }

    /**
     * The hotspots of a zone, in ascending order of {@link BlockPos#asLong} (API-055).
     *
     * <p>The default is the fallback's answer: none, as nothing burns. It is there so that a
     * provider built against an API from before this method still answers when a newer API is
     * loaded (architecture §6.2).
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param zone the zone
     * @return an immutable list
     */
    @AnyThread
    default List<BlockPos> hotspots(Level level, ZoneId zone) {
        return List.of();
    }

    /**
     * Whether a zone has a hotspot (API-055).
     *
     * <p>The default is the fallback's answer, as for {@link #hotspots}.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param zone the zone
     * @return true if it burns
     */
    @AnyThread
    default boolean isBurning(Level level, ZoneId zone) {
        return false;
    }

    /**
     * Whether a position is a hotspot (API-055).
     *
     * <p>The default is the fallback's answer, as for {@link #hotspots}.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param pos the position
     * @return true if a fire burns gas there
     */
    @AnyThread
    default boolean isHotspot(Level level, BlockPos pos) {
        return false;
    }

    /**
     * Queues an ignition at a position (API-056).
     *
     * <p>The default is the fallback's answer: {@link Ignition#ABSENT} at once. It is there for the
     * same reason as {@link #hotspots}'s.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param pos the position, immutable
     * @param source what ignites
     * @return a future that completes on the server thread once the ignition is applied
     */
    @ServerThread
    default CompletableFuture<Ignition> ignite(ServerLevel level, BlockPos pos, SourceId source) {
        return CompletableFuture.completedFuture(Ignition.ABSENT);
    }

    /**
     * Puts out the fire blocks in a box, now (API-057).
     *
     * <p>The default is the fallback's answer: nothing, as no fire is ZAS³'s. It is there for the
     * same reason as {@link #hotspots}'s.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param from one corner, immutable
     * @param to the opposite corner, immutable; the box is already checked
     * @return how many fire blocks were removed
     */
    @ServerThread
    default int extinguish(ServerLevel level, BlockPos from, BlockPos to) {
        return 0;
    }

    /**
     * Makes the implementation work out a position's gas geometry again (GEO-014).
     *
     * <p>The default is the fallback's answer: nothing, as there are no zones. It is there for the
     * same reason as {@link #hotspots}'s.
     *
     * <p>Thread: server thread only; the caller has checked.
     *
     * @param level the level
     * @param pos the block position
     */
    @ServerThread
    default void markDirty(ServerLevel level, BlockPos pos) {}
}
