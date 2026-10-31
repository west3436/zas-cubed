package io.github.west3436.zas.api;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The provider used while ZAS³ is absent: a vanilla, breathable world (D-17,
 * API-009). Every point is ambient standard air, no zone exists, and
 * mutations complete at once with nothing moved.
 */
final class FallbackProvider implements ZasApiProvider {
    static final FallbackProvider INSTANCE = new FallbackProvider();

    private static final AtmosphereSample AIR = AtmosphereSample.ambient(StandardAir.mixture(StandardAir.CELL_VOLUME));
    private static final CompletableFuture<MutationResult> ABSENT =
            CompletableFuture.completedFuture(MutationResult.ABSENT);

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
}
