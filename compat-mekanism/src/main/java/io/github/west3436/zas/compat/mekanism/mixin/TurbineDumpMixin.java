package io.github.west3436.zas.compat.mekanism.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.west3436.zas.compat.mekanism.Venting;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.generators.common.content.turbine.TurbineMultiblockData;
import mekanism.generators.common.content.turbine.TurbineMultiblockData.VentData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Releases the steam an industrial turbine sheds when it is set to Dumping or Dumping Excess
 * (MEK-020).
 *
 * <p>Wraps the two calls of {@code IChemicalTank.shrinkStack} that follow the first read of {@code
 * dumpMode} in {@code TurbineMultiblockData#tick}, one for each mode. The call before them is the
 * steam the turbine uses, which isn't voided and isn't wrapped; the slice, rather than a count of
 * calls, is what keeps it out. The wrapper hands the amount that really left to {@link Venting}.
 * Mekanism radiates nothing here.
 *
 * <p><b>Where it leaves.</b> A turbine is a closed shell, and its middle is inside it, so the steam
 * leaves from the block outside one of its turbine vents, a different vent each tick. The cap
 * counts for the turbine as a whole. A turbine can't form without a vent.
 *
 * <p>No public hook serves: Mekanism has no event, API or capability for a multiblock deleting
 * its contents, and its vents are a private list.
 *
 * <p>The target is Mekanism Generators', so {@link MekanismMixinPlugin} applies this only when
 * that mod is installed.
 */
@Mixin(value = TurbineMultiblockData.class, remap = false)
abstract class TurbineDumpMixin {
    @Shadow
    private List<VentData> ventData;

    @WrapOperation(
            method = "tick",
            slice =
                    @Slice(
                            from =
                                    @At(
                                            value = "FIELD",
                                            target =
                                                    "Lmekanism/generators/common/content/turbine/TurbineMultiblockData;dumpMode:Lmekanism/common/tile/TileEntityChemicalTank$GasMode;")),
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lmekanism/api/chemical/IChemicalTank;shrinkStack(JLmekanism/api/Action;)J"))
    private long zas$releaseDumped(
            IChemicalTank tank,
            long amount,
            Action action,
            Operation<Long> original,
            @Local(argsOnly = true) Level level) {
        ChemicalStack held = tank.getStack().copy();
        long shed = original.call(tank, amount, action);
        List<VentData> vents = ventData;
        if (shed > 0 && action.execute() && !vents.isEmpty()) {
            VentData vent = vents.get((int) Math.floorMod(level.getGameTime(), (long) vents.size()));
            BlockPos outside =
                    vent.side() == null ? vent.location() : vent.location().relative(vent.side());
            Venting.release(
                    level,
                    vents.get(0).location(),
                    Vec3.atCenterOf(outside),
                    held.copyWithAmount(shed),
                    Venting.Radiation.BY_VENTING);
        }
        return shed;
    }
}
