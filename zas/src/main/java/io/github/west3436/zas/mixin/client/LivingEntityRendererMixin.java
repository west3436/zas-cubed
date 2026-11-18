package io.github.west3436.zas.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.west3436.zas.client.ClientTumbles;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flung bodies tumble (CLIENT-027): {@link LivingEntityRenderer}'s {@code setupRotations} at HEAD
 * turns the model by the body's tumble ({@link ClientTumbles#apply}) before the body's own yaw, so
 * the turn is about a level axis in the world's frame. Every living renderer, the player's
 * included, runs this first, and only the model is turned: the name tag and the shadow are drawn
 * outside it.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(
            method =
                    "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"))
    private void zas$tumble(
            LivingEntity entity,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale,
            CallbackInfo ci) {
        ClientTumbles.apply(entity, poseStack, partialTick, scale);
    }
}
