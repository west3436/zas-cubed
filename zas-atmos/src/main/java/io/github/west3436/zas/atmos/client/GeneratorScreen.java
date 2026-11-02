package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.power.GeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The generator's screen: its fuel slot, how long the burning fuel lasts, and its stored FE. It has
 * no texture yet and is drawn with flat panels and slot frames.
 */
public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final int BACKGROUND = 0xF0202428;
    private static final int SLOT = 0xFF14171A;
    private static final int BAR = 0xFF30353B;
    private static final int FLAME = 0xFFE89A3C;
    private static final int ENERGY = 0xFFD8302F;
    private static final int TEXT = 0xFFE0E0E0;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
        for (Slot slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, SLOT);
        }
        int bx = leftPos + GeneratorMenu.FUEL_X + 24;
        int by = topPos + GeneratorMenu.FUEL_Y;
        g.fill(bx, by, bx + 60, by + 6, BAR);
        if (menu.burnTotal() > 0) {
            g.fill(bx, by, bx + 60 * menu.burnLeft() / menu.burnTotal(), by + 6, FLAME);
        }
        g.fill(bx, by + 10, bx + 60, by + 16, BAR);
        if (menu.capacity() > 0) {
            g.fill(bx, by + 10, bx + 60 * menu.energy() / menu.capacity(), by + 16, ENERGY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        g.drawString(
                font,
                Component.translatable("gui.zas_atmos.generator.burn", (menu.burnLeft() + 19) / 20),
                8,
                58,
                TEXT,
                false);
        g.drawString(
                font,
                Component.translatable("gui.zas_atmos.generator.energy", menu.energy(), menu.capacity()),
                8,
                68,
                TEXT,
                false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
