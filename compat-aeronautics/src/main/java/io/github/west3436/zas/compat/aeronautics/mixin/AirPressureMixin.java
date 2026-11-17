package io.github.west3436.zas.compat.aeronautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import io.github.west3436.zas.compat.aeronautics.LiftFactors;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Multiplies the air pressure Sable gives a point by its dimension's lift factor (COMPAT-080):
 * {@code DimensionPhysicsData.getAirPressure} is what Create Aeronautics' balloons, wings,
 * propellers, burners and steam vents, Sable's floating blocks and Simulated's altitude sensor
 * read. Sable's own value is always worked out first, and the factor is 1 wherever the adapter
 * leaves it alone (COMPAT-082, COMPAT-083).
 *
 * <p>No public hook serves: Sable's {@code api} package has no pressure, and the one way to change
 * it without a mixin, {@code DimensionPhysicsData.putPhysics}, replaces entries in a table that a
 * {@code /reload} clears, that the client side refills from Sable's own packet, and that in single
 * player the client and the integrated server share. This one return value, the same in every
 * Sable from 1.2 on, is simpler to keep right; it has its own GameTests (COMPAT-003).
 */
@Mixin(value = DimensionPhysicsData.class, remap = false)
abstract class AirPressureMixin {
    @ModifyReturnValue(method = "getAirPressure", at = @At("RETURN"))
    private static double zas$followTheAmbient(double sables, Level level, Vector3dc position) {
        return sables * LiftFactors.of(level);
    }
}
