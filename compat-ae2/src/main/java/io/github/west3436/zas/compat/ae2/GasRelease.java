package io.github.west3436.zas.compat.ae2;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Where gas goes that has left one store and that nothing will take (COMPAT-074): into the air where
 * it is. It is the last resort, for gas a block gave and won't take back, or a fraction of a unit a
 * block took less of than it said it would, so gas the adapter has in hand is never deleted.
 */
final class GasRelease {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Who releases it, as the framework's ledger records it. */
    static final SourceId SOURCE = SourceId.of("zas", "ae2");

    private GasRelease() {}

    /**
     * Releases gas at a block. Server thread only.
     *
     * @param level the level
     * @param pos the block
     * @param gas the gas; it leaves the caller now
     * @param what what the gas is, for the debug log
     */
    static void release(ServerLevel level, BlockPos pos, GasParcel gas, String what) {
        if (gas.isEmpty()) {
            return;
        }
        LOGGER.debug("Releasing {} at {}: {}", what, pos, gas);
        GasMutations.add(level, Vec3.atCenterOf(pos), gas, SOURCE);
    }
}
