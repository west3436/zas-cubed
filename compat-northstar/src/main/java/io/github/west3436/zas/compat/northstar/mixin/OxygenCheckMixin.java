package io.github.west3436.zas.compat.northstar.mixin;

import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Northstar's own breathing check on entities (SPACE-011, D-51). That listener to {@link
 * LivingBreatheEvent} deals Northstar's suffocation damage where it finds no oxygen, lets an entity
 * breathe in a sealer's area or from a suit's tank, and drains both. With the adapter on, ZAS³'s
 * verdict alone decides breathing: its zones are where Northstar's sealed areas were, Northstar's
 * suits are its life support, and a suit's tank feeds its internals. Northstar's sealers still
 * work for its blocks and machines.
 */
@Mixin(value = NorthstarOxygen.class, remap = false)
abstract class OxygenCheckMixin {
    @Inject(method = "onBreathe", at = @At("HEAD"), cancellable = true)
    private static void zas$zasDecides(LivingBreatheEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
