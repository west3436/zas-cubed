package io.github.west3436.zas.api;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Map;
import java.util.Optional;
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
 * {@link io.github.west3436.zas.api.mutation.GasMutations} and
 * {@link io.github.west3436.zas.api.life.Breathing}, which check arguments
 * and threads before delegating here, so implementations may assume non-null
 * arguments and, for mutations, the server thread.
 *
 * <p>Methods added after the first ones have a default, so that a provider
 * built against an older API keeps working with a newer one.
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
}
