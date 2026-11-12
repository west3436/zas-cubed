package io.github.west3436.zas.atmos.tank;

import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The blocks that store gas of their own on a pipe network: a canister and a large gas tank. Each is
 * a network node, rated at the same pressure, and is read as its own share of its network's gas.
 */
public final class GasVessels {
    private GasVessels() {}

    /**
     * The vessel at a position: a canister there, or the large tank either of whose halves is there.
     *
     * @param level the level
     * @param pos a position
     * @return its block entity, which for a tank is on its lower half, or null if there is none
     */
    public static @Nullable PipeNodeEntity at(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof CanisterBlockEntity canister ? canister : LargeTankBlock.entity(level, pos);
    }
}
