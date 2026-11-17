package io.github.west3436.zas.compat.ae2;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

/**
 * How AE2 draws a gas in its windows and on a storage monitor's face: the icon JEI and EMI show for
 * it, in the gas's colour, with its name and formula on hover (COMPAT-070). No art of its own. Client
 * only: {@link Ae2Compat} starts it only on a client.
 */
final class GasKeyRendering implements AEKeyRenderHandler<GasKey> {
    /** The colour of a gas this client has no definition of. */
    private static final int UNKNOWN_COLOUR = 0xA0A0A0;

    private GasKeyRendering() {}

    static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) ->
                event.enqueueWork(() -> AEKeyRendering.register(GasKeyType.TYPE, GasKey.class, new GasKeyRendering())));
    }

    /** The gas as the viewers list it, or a grey one with its id for a gas this client doesn't know. */
    private static GasStack stack(GasKey key) {
        String id = key.gas().toString();
        GasEntry gas = ViewerData.gas(id)
                .orElseGet(() -> new GasEntry(
                        id,
                        "gas." + key.gas().getNamespace() + "." + key.gas().getPath(),
                        "",
                        Double.NaN,
                        Double.NaN,
                        UNKNOWN_COLOUR,
                        List.of()));
        return GasStack.of(gas);
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, GasKey key) {
        // One of it, so the viewers' drawing adds no amount: AE2 writes its own.
        ViewerDraw.gas(graphics, stack(key), x, y);
    }

    @Override
    public void drawOnBlockFace(
            PoseStack poseStack, MultiBufferSource buffers, GasKey key, float scale, int light, Level level) {
        int colour = stack(key).gas().colour();
        int r = colour >> 16 & 0xFF;
        int g = colour >> 8 & 0xFF;
        int b = colour & 0xFF;
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);
        float half = (scale - 0.05f) / 2;
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.text(ViewerDraw.GAS));
        buffer.addVertex(pose, -half, -half, 0)
                .setColor(r, g, b, 255)
                .setUv(0, 1)
                .setLight(light);
        buffer.addVertex(pose, half, -half, 0)
                .setColor(r, g, b, 255)
                .setUv(1, 1)
                .setLight(light);
        buffer.addVertex(pose, half, half, 0).setColor(r, g, b, 255).setUv(1, 0).setLight(light);
        buffer.addVertex(pose, -half, half, 0)
                .setColor(r, g, b, 255)
                .setUv(0, 0)
                .setLight(light);
        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(GasKey key) {
        return key.getDisplayName();
    }

    @Override
    public List<Component> getTooltip(GasKey key) {
        return ViewerDraw.tooltip(stack(key), Minecraft.getInstance().options.advancedItemTooltips);
    }
}
