package io.github.west3436.zas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.life.ClientBreath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The air bar while the local player's air drains for lack of O₂ ({@link
 * ClientBreath#drainsForOxygen}): grey bubbles in place of vanilla's, so it doesn't read as
 * drowning. It takes vanilla's air layer's place, drawn where and as that layer draws it. In
 * breathable air, and with the eyes in a fluid it drowns in, vanilla's layer draws as before.
 */
final class HypoxicAirBar {
    private static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "hud/air_hypoxic");
    private static final ResourceLocation AIR_BURSTING =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "hud/air_hypoxic_bursting");

    private HypoxicAirBar() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(HypoxicAirBar::onLayer);
    }

    private static void onLayer(RenderGuiLayerEvent.Pre e) {
        if (!e.getName().equals(VanillaGuiLayers.AIR_LEVEL)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // Vanilla draws the camera's player's air; spectating another player leaves it to vanilla.
        if (player == null || mc.getCameraEntity() != player
                || !ClientBreath.LOCAL.drainsForOxygen(ClientBreath.eyesInFluid(player))) {
            return;
        }
        e.setCanceled(true);
        if (mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
            draw(e.getGuiGraphics(), mc.gui, player);
        }
    }

    /** As vanilla's {@code Gui.renderAirLevel}, with the grey sprites. */
    private static void draw(GuiGraphics g, Gui gui, LocalPlayer player) {
        int max = player.getMaxAirSupply();
        int air = Math.min(player.getAirSupply(), max);
        if (!player.isEyeInFluid(FluidTags.WATER) && air >= max) {
            return;
        }
        int right = g.guiWidth() / 2 + 91;
        int y = g.guiHeight() - gui.rightHeight;
        int full = Mth.ceil((air - 2) * 10.0 / max);
        int bursting = Mth.ceil(air * 10.0 / max) - full;
        RenderSystem.enableBlend();
        for (int i = 0; i < full + bursting; i++) {
            g.blitSprite(i < full ? AIR : AIR_BURSTING, right - i * 8 - 9, y, 9, 9);
        }
        RenderSystem.disableBlend();
        gui.rightHeight += 10;
    }
}
