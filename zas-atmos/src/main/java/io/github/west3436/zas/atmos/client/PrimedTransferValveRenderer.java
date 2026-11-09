package io.github.west3436.zas.atmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a primed TTV with its block models: no tanks, one or two, with the timer
 * running or the valve open. Each tank's faces carry a tint index, 1 for the first tank and 2 for the
 * second, and are tinted by the tank's kind.
 */
final class PrimedTransferValveRenderer extends EntityRenderer<PrimedTransferValve> {
    private static final String[] TANKS = {"", "_one", "_two"};
    private static final Direction[] FACES = Direction.values();

    /** Each model, which the client entry point has the game load: [tanks][open]. */
    static final ModelResourceLocation[][] MODELS = models();

    PrimedTransferValveRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.4f;
    }

    private static ModelResourceLocation[][] models() {
        ModelResourceLocation[][] models = new ModelResourceLocation[TANKS.length][2];
        for (int tanks = 0; tanks < TANKS.length; tanks++) {
            for (int open = 0; open < 2; open++) {
                String path = "block/tank_transfer_valve" + TANKS[tanks] + (open == 1 ? "_open" : "_timer");
                models[tanks][open] = ModelResourceLocation.standalone(ZasAtmosMod.id(path));
            }
        }
        return models;
    }

    @Override
    public void render(
            PrimedTransferValve valve,
            float yaw,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        ItemStack first = valve.firstTank();
        ItemStack second = valve.secondTank();
        int tanks = (first.isEmpty() ? 0 : 1) + (second.isEmpty() ? 0 : 1);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODELS[tanks][valve.valveOpen() ? 1 : 0]);
        // A lone tank is drawn as the first, whichever place it fills.
        int tintOne = TransferValveTint.of(first.isEmpty() ? second : first);
        int tintTwo = TransferValveTint.of(second);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-valve.getYRot()));
        pose.translate(-0.5f, 0, -0.5f);
        VertexConsumer out = buffers.getBuffer(Sheets.cutoutBlockSheet());
        RandomSource random = RandomSource.create(42);
        for (Direction face : FACES) {
            quads(model.getQuads(null, face, random, ModelData.EMPTY, null), pose, out, tintOne, tintTwo, light);
        }
        quads(model.getQuads(null, null, random, ModelData.EMPTY, null), pose, out, tintOne, tintTwo, light);
        pose.popPose();
        super.render(valve, yaw, partialTick, pose, buffers, light);
    }

    private static void quads(
            List<BakedQuad> quads, PoseStack pose, VertexConsumer out, int tintOne, int tintTwo, int light) {
        for (BakedQuad quad : quads) {
            int tint = switch (quad.getTintIndex()) {
                case 1 -> tintOne;
                case 2 -> tintTwo;
                default -> 0xFFFFFF;
            };
            out.putBulkData(
                    pose.last(),
                    quad,
                    (tint >> 16 & 0xFF) / 255f,
                    (tint >> 8 & 0xFF) / 255f,
                    (tint & 0xFF) / 255f,
                    1f,
                    light,
                    OverlayTexture.NO_OVERLAY);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PrimedTransferValve valve) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
