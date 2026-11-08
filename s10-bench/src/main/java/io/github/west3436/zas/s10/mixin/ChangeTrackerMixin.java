package io.github.west3436.zas.s10.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.s10.BenchHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The S-10 bench's timing of the M1 change hook (NFR-001): every block write into a loaded chunk
 * reaches {@link ChangeTracker#onBlockWrite}, so its time is atmos work wherever the write came
 * from. Only in the bench's own mod, which never ships.
 */
@Mixin(ChangeTracker.class)
abstract class ChangeTrackerMixin {
    @WrapMethod(method = "onBlockWrite")
    private static void zas_s10$time(
            LevelChunk chunk, BlockPos pos, BlockState old, BlockState now, Operation<Void> original) {
        long t = BenchHooks.enterBlockWrite();
        try {
            original.call(chunk, pos, old, now);
        } finally {
            BenchHooks.exitBlockWrite(t);
        }
    }
}
