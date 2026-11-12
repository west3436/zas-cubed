package io.github.west3436.zas.atmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gauge.GaugeBlock;
import io.github.west3436.zas.atmos.gauge.GaugeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Draws a gauge's needle over its dial, which the block's model draws. The needle turns about the
 * dial's middle, from pointing left at rest, over the top, to pointing right at the top of the scale,
 * as the analyzer's gauge does, and sweeps to each new step the server sends ({@link
 * GaugeBlockEntity#shown}).
 *
 * <p>It is drawn in the frame of the gauge's model on a floor, turned as the block state turns the
 * model ({@link #xRotation}, {@link #yRotation}), from a strip of the gauge's texture.
 */
final class GaugeRenderer implements BlockEntityRenderer<GaugeBlockEntity> {
    /** The texture holding the needle's strip. */
    static final ResourceLocation TEXTURE = ZasAtmosMod.id("block/gauge");

    private static final float PX = 1 / 16f;

    /** The dial's face above the floor, and a hair over it so the needle never fights it. */
    private static final float FACE_Y = 2 * PX + 0.002f;

    /** The needle's reach past the middle, its tail behind it, and its width, in pixels. */
    private static final float REACH = 4.5f;

    private static final float TAIL = 1f;
    private static final float WIDTH = 1f;

    /** The needle's strip in the texture: 6 pixels long, 1 high, from the left of the bottom row. */
    private static final float STRIP_U0 = 0;

    private static final float STRIP_U1 = 6;
    private static final float STRIP_V0 = 15;
    private static final float STRIP_V1 = 16;

    GaugeRenderer(BlockEntityRendererProvider.Context context) {}

    /** The model's turn about x, as its block state file has it: on a floor 0, on a wall 90, on a ceiling 180. */
    static int xRotation(BlockState state) {
        return switch (state.getValue(GaugeBlock.FACE)) {
            case FLOOR -> 0;
            case WALL -> 90;
            case CEILING -> 180;
        };
    }

    /**
     * The model's turn about y: toward its facing, and on a floor half a turn more, since the model's
     * top is to the south and a floor gauge's top faces away from the player who placed it.
     */
    static int yRotation(BlockState state) {
        // A block state file turns a model clockwise from the north; a direction's yaw counts from the south.
        int facing = (int) state.getValue(GaugeBlock.FACING).toYRot() + 180;
        return Math.floorMod(state.getValue(GaugeBlock.FACE) == AttachFace.FLOOR ? facing + 180 : facing, 360);
    }

    @Override
    public void render(
            GaugeBlockEntity gauge,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        BlockState state = gauge.getBlockState();
        if (!(state.getBlock() instanceof GaugeBlock)) {
            return;
        }
        float along = gauge.shown();
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(TEXTURE);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(BlockModelRotation.by(xRotation(state), yRotation(state))
                .getRotation()
                .getLeftRotation());
        pose.translate(0, FACE_Y - 0.5f, 0);
        // At rest it points east, the dial's left in the model's frame; it turns over the south, the top.
        pose.mulPose(Axis.YP.rotation((float) (-Math.PI * along)));
        float x0 = -TAIL * PX;
        float x1 = REACH * PX;
        float z0 = -WIDTH / 2 * PX;
        float z1 = WIDTH / 2 * PX;
        float u0 = sprite.getU(STRIP_U0 / 16f);
        float u1 = sprite.getU(STRIP_U1 / 16f);
        float v0 = sprite.getV(STRIP_V0 / 16f);
        float v1 = sprite.getV(STRIP_V1 / 16f);
        PoseStack.Pose last = pose.last();
        vertex(out, last, x0, z0, u0, v0, light, overlay);
        vertex(out, last, x0, z1, u0, v1, light, overlay);
        vertex(out, last, x1, z1, u1, v1, light, overlay);
        vertex(out, last, x1, z0, u1, v0, light, overlay);
        pose.popPose();
    }

    private static void vertex(
            VertexConsumer out, PoseStack.Pose pose, float x, float z, float u, float v, int light, int overlay) {
        out.addVertex(pose, x, 0, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }
}
