package io.github.west3436.zas.mixin;

import io.github.west3436.zas.fire.FuelBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M5 (architecture §7): furnaces, smokers and blast furnaces pause without oxygen (FIRE-027). At
 * the head of {@link AbstractFurnaceBlockEntity#serverTick}, a furnace that burns or has fuel, and
 * whose cell in front fails the fire oxygen threshold (FIRE-030), skips the tick: its burn time,
 * fuel and cooking progress stay as they are, and it resumes when O₂ returns. A furnace without
 * O₂ can't light either. Its O₂ use while it burns is {@link io.github.west3436.zas.fire.Burners}'
 * (FIRE-021).
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Shadow
    int litTime;

    @Shadow
    protected NonNullList<ItemStack> items;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void zas$pauseWithoutOxygen(
            Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        AbstractFurnaceBlockEntityMixin self = (AbstractFurnaceBlockEntityMixin) (Object) furnace;
        if (FuelBlocks.furnacePaused(
                level, pos, state, self.litTime > 0, !self.items.get(1).isEmpty())) {
            ci.cancel();
        }
    }
}
