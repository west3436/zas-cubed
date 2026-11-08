package io.github.west3436.zas.s10.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.west3436.zas.atmos.device.DeviceSchedule;
import io.github.west3436.zas.s10.BenchHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The S-10 bench's timing of each device's step (NFR-001), by device type, inside the device
 * ticker's listener: devices are most of the server thread's atmos work, so the report says which
 * ones. Wraps the one call that steps a due device; only in the bench's own mod, which never ships.
 */
@Mixin(DeviceSchedule.class)
abstract class DeviceStepMixin {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lio/github/west3436/zas/atmos/device/DeviceSchedule$Member;step()V"))
    private void zas_s10$time(DeviceSchedule.Member member, Operation<Void> original) {
        long t = BenchHooks.enterDevice();
        try {
            original.call(member);
        } finally {
            BenchHooks.exitDevice(member, t);
        }
    }
}
