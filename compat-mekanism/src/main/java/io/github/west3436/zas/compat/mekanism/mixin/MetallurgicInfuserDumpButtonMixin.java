package io.github.west3436.zas.compat.mekanism.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.west3436.zas.compat.mekanism.Venting;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Releases what a metallurgic infuser's dump button empties (MEK-020). An infuser holds infuse
 * types, which no shipped mapping makes a gas, so this releases nothing unless a datapack maps one:
 * the chemical map decides what is a gas, not the hook.
 *
 * <p>Wraps the call of {@code IChemicalTank.setEmpty} in {@code TileEntityMetallurgicInfuser#dump},
 * which both the dump button's packet and the infuser's computer method {@code dumpInfuseType} run.
 * The wrapper reads what the tank holds, lets Mekanism empty it, and hands what left to {@link
 * Venting}, which releases it at the infuser's block. Mekanism radiates nothing here.
 *
 * <p>No public hook serves: Mekanism has no event, API or capability for a machine deleting its
 * contents. The block entity empties its own tank, and nothing outside it is told.
 */
@Mixin(value = TileEntityMetallurgicInfuser.class, remap = false)
abstract class MetallurgicInfuserDumpButtonMixin {
    @WrapOperation(
            method = "dump",
            at = @At(value = "INVOKE", target = "Lmekanism/api/chemical/IChemicalTank;setEmpty()V"))
    private void zas$releaseDumped(IChemicalTank tank, Operation<Void> original) {
        ChemicalStack held = tank.getStack().copy();
        original.call(tank);
        long gone = held.getAmount() - tank.getStored();
        if (gone > 0) {
            BlockEntity self = (BlockEntity) (Object) this;
            BlockPos pos = self.getBlockPos();
            Venting.release(
                    self.getLevel(),
                    pos,
                    Vec3.atCenterOf(pos),
                    held.copyWithAmount(gone),
                    Venting.Radiation.BY_VENTING);
        }
    }
}
