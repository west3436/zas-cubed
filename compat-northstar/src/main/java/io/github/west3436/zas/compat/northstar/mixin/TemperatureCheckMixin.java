package io.github.west3436.zas.compat.northstar.mixin;

import com.lightning.northstar.world.temperature.NorthstarTemperature;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Northstar's own temperature check on entities (SPACE-011, D-51). Each tick it freezes an
 * entity that isn't insulated below −32 °C and sets one on fire above 300 °C, by Northstar's own
 * temperatures, which know nothing of ZAS³'s zones. With the adapter on, ZAS³'s temperature hazards
 * alone apply (LIFE-011), with Northstar's suits rated as ZAS³ protection. What Northstar's
 * temperature does to blocks is left to it.
 */
@Mixin(value = NorthstarTemperature.class, remap = false)
abstract class TemperatureCheckMixin {
    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private static void zas$zasDecides(LivingEntity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
