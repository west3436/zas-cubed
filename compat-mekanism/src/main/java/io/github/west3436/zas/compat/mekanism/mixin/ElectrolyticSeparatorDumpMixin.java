package io.github.west3436.zas.compat.mekanism.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.west3436.zas.compat.mekanism.Venting;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Releases what an electrolytic separator sheds from an output set to Dumping or Dumping Excess
 * (MEK-020): most often the oxygen of a separator kept for its hydrogen.
 *
 * <p>Wraps the two calls of {@code IChemicalTank.shrinkStack} in the private {@code
 * TileEntityElectrolyticSeparator#handleTank}, one for each mode and the only two in the method,
 * which Mekanism runs for each output every tick. The wrapper hands the amount that really left to
 * {@link Venting}, which releases it at the separator's block. Mekanism radiates nothing here.
 *
 * <p>No public hook serves: Mekanism has no event, API or capability for a machine deleting its
 * output. The block entity shrinks its own tanks, and nothing outside it is told.
 */
@Mixin(value = TileEntityElectrolyticSeparator.class, remap = false)
abstract class ElectrolyticSeparatorDumpMixin {
    @WrapOperation(
            method = "handleTank",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lmekanism/api/chemical/IChemicalTank;shrinkStack(JLmekanism/api/Action;)J"))
    private long zas$releaseDumped(IChemicalTank tank, long amount, Action action, Operation<Long> original) {
        ChemicalStack held = tank.getStack().copy();
        long shed = original.call(tank, amount, action);
        if (shed > 0 && action.execute()) {
            BlockEntity self = (BlockEntity) (Object) this;
            BlockPos pos = self.getBlockPos();
            Venting.release(
                    self.getLevel(),
                    pos,
                    Vec3.atCenterOf(pos),
                    held.copyWithAmount(shed),
                    Venting.Radiation.BY_VENTING);
        }
        return shed;
    }
}
