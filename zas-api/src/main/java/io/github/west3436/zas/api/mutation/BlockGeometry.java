package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Telling ZAS³ about changes to a block's gas geometry that it can't see for itself (GEO-014).
 *
 * <p>Every block state written to a loaded chunk reaches ZAS³ without help (GEO-010). What it can't
 * see is a change that writes no block state: a shape driven by block-entity data, or an {@link
 * io.github.west3436.zas.api.capability.ISealProvider} whose answer changed.
 *
 * <p><b>Threads:</b> server thread only, and it throws {@link IllegalStateException} otherwise
 * (API-045). Without ZAS³ installed it does nothing: no zones exist (D-17).
 */
public final class BlockGeometry {
    private BlockGeometry() {}

    /**
     * Makes ZAS³ work out a position's gas geometry again. The change reaches zones at the next
     * atmos tick. A position in a chunk that isn't loaded is ignored, since nothing there is held.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param pos the block position
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static void markDirty(ServerLevel level, BlockPos pos) {
        ServerThreads.require(level, "BlockGeometry.markDirty");
        ZasApi.provider().markDirty(level, Objects.requireNonNull(pos, "pos"));
    }
}
