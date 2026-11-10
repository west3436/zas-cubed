package io.github.west3436.zas.compat.mekanism.mixin;

import io.github.west3436.zas.compat.mekanism.Venting;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.content.network.ChemicalNetwork;
import mekanism.common.content.network.transmitter.PressurizedTube;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases what a pressurized tube network loses when one of its tubes is broken (MEK-020).
 *
 * <p>Injects at the head of the protected {@code ChemicalNetwork#disperse}, which Mekanism calls
 * with the tube that went and what the tubes left can't hold: all of it when the last tube of a
 * network goes, and otherwise what the remaining tubes have no room for. It hands that to {@link
 * Venting}, which releases it where the tube was.
 *
 * <p><b>Radiation is Mekanism's here</b> (MEK-023): {@code disperse} itself radiates the whole of
 * what it is given. So this releases the gas and never radiates.
 *
 * <p><b>Only for a tube that is gone.</b> Mekanism also calls {@code disperse} when it saves a
 * network holding more than its tubes' capacity, which a lowered capacity setting can cause; the
 * chemical then stays in the network, so releasing it would make gas out of nothing. A tube that is
 * unloaded with its chunk takes its share with it first, so nothing is dispersed for it.
 *
 * <p>No public hook serves: Mekanism posts no event when a network loses chemical, and {@code
 * disperse} is the one method every such loss passes through with a position.
 */
@Mixin(value = ChemicalNetwork.class, remap = false)
abstract class BrokenTubeMixin {
    @Inject(method = "disperse", at = @At("HEAD"))
    private void zas$releaseDispersed(PressurizedTube tube, ChemicalStack lost, CallbackInfo ci) {
        if (tube.isValid()) {
            return;
        }
        BlockPos pos = tube.getBlockPos();
        Venting.release(tube.getLevel(), pos, Vec3.atCenterOf(pos), lost, Venting.Radiation.BY_MEKANISM);
    }
}
