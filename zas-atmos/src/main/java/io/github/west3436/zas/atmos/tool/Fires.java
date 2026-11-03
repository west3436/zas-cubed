package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.api.ServerThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stand-in for the API's {@code io.github.west3436.zas.api.fire.Fires#extinguish}, with its exact
 * signature, until that is on main. It goes then, and the extinguisher imports the API's class.
 */
final class Fires {
    private static final ResourceLocation GAS_FIRE = ResourceLocation.fromNamespaceAndPath("zas", "gas_fire");

    private Fires() {}

    /**
     * Removes every gas fire and vanilla fire block in the inclusive box from {@code from} to {@code
     * to}.
     *
     * @return how many it removed
     */
    @ServerThread
    static int extinguish(ServerLevel level, BlockPos from, BlockPos to) {
        int removed = 0;
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.FIRE) || GAS_FIRE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                level.removeBlock(p, false);
                removed++;
            }
        }
        return removed;
    }
}
