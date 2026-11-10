package io.github.west3436.zas.compat.mekanism.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.west3436.zas.compat.mekanism.Venting;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.item.ItemGaugeDropper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Releases what a gauge dropper holds when its player empties it (MEK-020): a sneak-use of the
 * dropper in the hand.
 *
 * <p>Wraps the call of {@code IChemicalHandler.setChemicalInTank} in {@code ItemGaugeDropper#use},
 * the only one in the method, which sets the dropper's tank to nothing. The wrapper reads what the
 * dropper holds, lets Mekanism empty it, and hands what left to {@link Venting}, which releases it
 * at the player's head. The cap counts for the block the player stands in. Mekanism radiates
 * nothing here.
 *
 * <p>No public hook serves: emptying the dropper sets its tank directly and posts no event, and
 * NeoForge's item-use event fires before the item's own code, so it can't see what was emptied.
 */
@Mixin(value = ItemGaugeDropper.class, remap = false)
abstract class GaugeDropperEmptyMixin {
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lmekanism/api/chemical/IChemicalHandler;setChemicalInTank(ILmekanism/api/chemical/ChemicalStack;)V"))
    private void zas$releaseEmptied(
            IChemicalHandler handler,
            int tank,
            ChemicalStack stack,
            Operation<Void> original,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) Player player) {
        ChemicalStack held = handler.getChemicalInTank(tank).copy();
        original.call(handler, tank, stack);
        long gone = held.getAmount() - handler.getChemicalInTank(tank).getAmount();
        if (gone > 0) {
            Venting.release(
                    level,
                    player.blockPosition(),
                    player.getEyePosition(),
                    held.copyWithAmount(gone),
                    Venting.Radiation.BY_VENTING);
        }
    }
}
