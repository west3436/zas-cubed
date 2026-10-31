package io.github.west3436.zas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.west3436.zas.change.ChangeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M1, the change hook (architecture §4.2 and §7; GEO-010, GEO-011, GEO-012).
 *
 * <p>Wraps the section write inside {@link LevelChunk#setBlockState(BlockPos, BlockState,
 * boolean)}, the one place every block write into a loaded chunk passes, including mods that
 * write to chunks directly. The wrapper sees the old and new state whatever the method returns
 * afterwards: it returns {@code null} if the block was replaced again during {@code onRemove},
 * which an injection at RETURN would miss. It only hands the change to {@link ChangeTracker},
 * which does constant work.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    @WrapOperation(
            method =
                    "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState zas$trackBlockWrite(
            LevelChunkSection section,
            int x,
            int y,
            int z,
            BlockState state,
            Operation<BlockState> original,
            @Local(argsOnly = true) BlockPos pos) {
        BlockState old = original.call(section, x, y, z, state);
        if (old != state) {
            ChangeTracker.onBlockWrite((LevelChunk) (Object) this, pos, old, state);
        }
        return old;
    }
}
