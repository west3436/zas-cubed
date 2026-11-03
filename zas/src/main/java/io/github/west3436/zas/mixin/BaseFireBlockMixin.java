package io.github.west3436.zas.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.west3436.zas.fire.FireAir;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M3 (architecture §7): no ignition without oxygen (FIRE-020, FIRE-030). {@link
 * BaseFireBlock#canBePlacedAt} is how flint and steel, fire charges and dispensers ask whether fire
 * can go somewhere; where the cell fails the fire oxygen threshold, in a zone or the ambient, the
 * answer is no. On a client, which has no simulation, it is unchanged; the server's answer wins.
 */
@Mixin(BaseFireBlock.class)
abstract class BaseFireBlockMixin {
    @ModifyReturnValue(method = "canBePlacedAt", at = @At("RETURN"))
    private static boolean zas$needsOxygen(
            boolean placeable, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return placeable && FireAir.sustains(level, pos);
    }
}
