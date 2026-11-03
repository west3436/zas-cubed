package io.github.west3436.zas.ignition;

import io.github.west3436.zas.api.mutation.SourceId;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where ignition sources meet the hotspot engine (FIRE-014): the geometry table's standing-source
 * predicate and the call that ignites a cell. Both belong to the hotspots work (#42): until it
 * lands, this keeps the predicate and the calls ignite nothing.
 *
 * <p>TODO(#42): forward to {@code Geometries.setIgnitionSources} and the API's {@code
 * Fires.ignite}.
 */
final class EngineLink {
    private static volatile Predicate<BlockState> standing = state -> false;

    private EngineLink() {}

    /**
     * Hands the geometry table the predicate it asks of every block state when it is rebuilt: whether
     * the state is a standing source. Once, at mod construction.
     */
    static void setStandingSources(Predicate<BlockState> predicate) {
        standing = predicate;
    }

    /**
     * Tries to ignite the mixture at {@code pos} or a neighbouring cell of its zone, on the next
     * atmos tick; completes when that tick is applied. Server thread.
     *
     * @param source the ignition source, such as {@code zas:flint_and_steel}
     */
    static CompletableFuture<?> ignite(ServerLevel level, BlockPos pos, SourceId source) {
        return CompletableFuture.completedFuture(null);
    }
}
