package io.github.west3436.zas.compat.mekanism.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.west3436.zas.compat.mekanism.Venting;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.network.to_server.PacketDropperUse;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Releases what a gauge dropper empties out of a machine's tank (MEK-020): a sneak-click with the
 * dropper on a tank's gauge in a window.
 *
 * <p>Wraps the call of {@code IChemicalTank.setEmpty} in the private {@code
 * PacketDropperUse#handleChemicalTank}, the only one in the method. The wrapper reads what the tank
 * holds, lets Mekanism empty it, and hands what left to {@link Venting}.
 *
 * <p><b>Radiation is Mekanism's here</b> (MEK-023): just before the call, Mekanism radiates the
 * whole of what the tank holds. So this releases the gas and never radiates.
 *
 * <p><b>Where it leaves.</b> At the machine's block. For a multiblock, Mekanism names the middle of
 * the structure, which is inside its shell, so the gas leaves instead from the outside of the block
 * whose window the player has open, which is part of the shell. The cap counts for the block
 * Mekanism names, so for a multiblock as a whole.
 *
 * <p>No public hook serves: the packet is handled inside Mekanism, which posts no event for it.
 */
@Mixin(value = PacketDropperUse.class, remap = false)
abstract class GaugeDropperDumpMixin {
    @Shadow
    @Final
    private BlockPos pos;

    @WrapOperation(
            method = "handleChemicalTank",
            at = @At(value = "INVOKE", target = "Lmekanism/api/chemical/IChemicalTank;setEmpty()V"))
    private void zas$releaseDumped(
            IChemicalTank tank,
            Operation<Void> original,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos source) {
        ChemicalStack held = tank.getStack().copy();
        original.call(tank);
        long gone = held.getAmount() - tank.getStored();
        if (gone > 0) {
            Venting.release(
                    level,
                    source,
                    Vec3.atCenterOf(zas$outlet(level, source)),
                    held.copyWithAmount(gone),
                    Venting.Radiation.BY_MEKANISM);
        }
    }

    /** The block the gas leaves at: the machine's, or the one outside a multiblock's open block. */
    private BlockPos zas$outlet(Level level, BlockPos source) {
        BlockPos open = pos;
        if (open.equals(source)) {
            return source;
        }
        if (level.getBlockEntity(open) instanceof TileEntityMultiblock<?> shell
                && shell.getMultiblock().isFormed()) {
            Direction out = shell.getMultiblock().getBounds().getSide(open);
            if (out != null) {
                return open.relative(out);
            }
        }
        return open;
    }
}
