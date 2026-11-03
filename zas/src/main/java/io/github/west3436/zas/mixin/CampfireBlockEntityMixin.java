package io.github.west3436.zas.mixin;

import io.github.west3436.zas.fire.FuelBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M6 (architecture §7): campfires go out without oxygen (FIRE-020, FIRE-027). At the head of
 * {@link CampfireBlockEntity#cookTick}, which runs while a campfire is lit, a campfire whose cell
 * fails the fire oxygen threshold (FIRE-030) is put out as a shovel does. A campfire has no fuel to
 * keep, so going out is its pause; it can be lit again once O₂ returns. Lighting one without O₂
 * isn't stopped ({@code CampfireBlock#canLight} has no position), but it goes out on its next tick.
 * Its O₂ use while it burns is {@link io.github.west3436.zas.fire.Burners}' (FIRE-021).
 */
@Mixin(CampfireBlockEntity.class)
abstract class CampfireBlockEntityMixin {
    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private static void zas$goOutWithoutOxygen(
            Level level, BlockPos pos, BlockState state, CampfireBlockEntity campfire, CallbackInfo ci) {
        if (FuelBlocks.campfireOut(level, pos, state)) {
            ci.cancel();
        }
    }
}
