package io.github.west3436.zas.api;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The service behind the API. ZAS³ installs its implementation with
 * {@link ZasApi#install}; until then, and when ZAS³ is absent, a built-in
 * fallback answers with a vanilla, breathable world (D-17).
 *
 * <p><b>Consumers don't call or implement this.</b> They use
 * {@link io.github.west3436.zas.api.atmosphere.Atmospheres} and
 * {@link io.github.west3436.zas.api.mutation.GasMutations}, which check
 * arguments and threads before delegating here, so implementations may assume
 * non-null arguments and, for mutations, the server thread.
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
     * The gases in a gas tag (CORE-012), for
     * {@link io.github.west3436.zas.api.gas.GasTags}.
     *
     * <p>Thread: any.
     *
     * @param tag the tag
     * @return the ids of its gases, immutable; empty if there is no such tag
     */
    @AnyThread
    Set<ResourceLocation> gasesInTag(GasTag tag);
}
