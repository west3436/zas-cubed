package io.github.west3436.zas.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.west3436.zas.fire.FireAir;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M2 and M4 (architecture §7): vanilla fire needs oxygen (FIRE-020, FIRE-030).
 *
 * <ul>
 *   <li><b>M2</b>, {@link FireBlock}'s scheduled tick at HEAD: a fire whose cell fails the fire
 *       oxygen threshold, in a zone or, outside zones, the dimension's ambient, goes out before it
 *       ages, spreads or burns anything. Its O₂ use is {@link io.github.west3436.zas.fire.Burners}',
 *       not this tick's (FIRE-022).
 *   <li><b>M4</b>, NeoForge's {@code checkBurnOut}: a fire doesn't burn away a neighbouring block
 *       whose own cell fails the threshold, though the fire itself has O₂. The block's side facing
 *       the fire resolves to its own cell, or for a full block to the fire's, so a solid block the
 *       fire touches still burns.
 * </ul>
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @Inject(
            method =
                    "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void zas$goOutWithoutOxygen(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!FireAir.sustains(level, pos)) {
            level.removeBlock(pos, false);
            ci.cancel();
        }
    }

    @WrapMethod(
            method =
                    "checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V")
    private void zas$burnOnlyWithOxygen(
            Level level,
            BlockPos pos,
            int chance,
            RandomSource random,
            int age,
            Direction face,
            Operation<Void> original) {
        if (FireAir.sustains(level, FireAir.front(pos, face))) {
            original.call(level, pos, chance, random, age, face);
        }
    }
}
