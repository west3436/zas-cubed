package io.github.west3436.zas.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import io.github.west3436.zas.compat.sable.SableMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Carries the rooms along when Sable moves blocks (COMPAT-100): {@code
 * SubLevelAssemblyHelper.moveBlocks} is what turns blocks into a sub-level (every assembly, the
 * {@code /sable assemble} commands among them), splits one, and puts its blocks back into the world
 * when a mod such as Create Aeronautics takes it apart. The move is wrapped in ZAS³'s relocation API:
 * begun before the first block changes, completed once the last is in place.
 *
 * <p>No public hook serves: Sable has no event around a move. Its observers hear of a new sub-level
 * before its blocks move, but not which blocks will. This wraps one public static method of Sable's
 * {@code api} package, the same in every Sable 2 build. COMPAT-100's GameTests fail without it.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
abstract class MoveBlocksMixin {
    @WrapMethod(method = "moveBlocks")
    private static void zas$carryTheAir(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            Operation<Void> original) {
        SableMoves.around(level, transform, blocks, () -> original.call(level, transform, blocks));
    }
}
