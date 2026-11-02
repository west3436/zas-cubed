package io.github.west3436.zas.atmos.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A plain device GUI: lines of text over a flat panel and a row or two of buttons, with no
 * textures. A button sends only its id; the server applies and checks it (CONT-074).
 */
abstract class DeviceScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    private static final int PANEL = 0xF0202020;
    private static final int BORDER = 0xFF808080;
    private static final int TEXT = 0xFFE0E0E0;

    DeviceScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 200;
        imageHeight = 110;
    }

    /** The lines of state to show, from the menu's synced values. */
    abstract List<Component> lines();

    /** Adds a button that sends an id to the server. */
    void button(int x, int y, int width, Component label, int id) {
        addRenderableWidget(Button.builder(label, b -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                    }
                })
                .bounds(leftPos + x, topPos + y, width, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, TEXT, false);
        int y = 20;
        for (Component line : lines()) {
            graphics.drawString(font, line, 8, y, TEXT, false);
            y += 11;
        }
    }
}
