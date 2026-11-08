package io.github.west3436.zas.s10.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.west3436.zas.s10.BenchHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The S-10 bench's timing of the mods' block entity tickers (NFR-001), such as the generator's and
 * the fire alarm's. Wraps the one call that ticks a block entity in a loaded chunk; a block entity
 * of another mod or of Minecraft runs untimed. Only in the bench's own mod, which never ships.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BlockEntityTickMixin {
    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void zas_s10$time(
            BlockEntityTicker<BlockEntity> ticker,
            Level level,
            BlockPos pos,
            BlockState state,
            BlockEntity be,
            Operation<Void> original) {
        long t = BenchHooks.enterBlockEntity(be);
        try {
            original.call(ticker, level, pos, state, be);
        } finally {
            BenchHooks.exitBlockEntity(be, t);
        }
    }
}
