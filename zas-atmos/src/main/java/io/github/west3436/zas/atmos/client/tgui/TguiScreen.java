package io.github.west3436.zas.atmos.client.tgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * A device window in the TGUI look (D-76). It draws the window, then has the screen draw its content
 * with {@link Ui} each frame. A control sends only a button id, which the server applies and checks
 * (CONT-074).
 */
public abstract class TguiScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    /** The section around the player's inventory, above its first row and in all. */
    private static final int INVENTORY_ABOVE = 3;

    private static final int INVENTORY_HEIGHT = 80;

    private final Ui ui = new Ui();

    protected TguiScreen(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
    }

    /** How much window a player's inventory at {@code y} needs: from the window's top to its own end. */
    protected static int heightWithInventory(int y) {
        return y - INVENTORY_ABOVE + INVENTORY_HEIGHT + Ui.PAD;
    }

    /** Draws the window's content in a box inside its title bar and padding. */
    protected abstract void draw(Ui ui, int x, int y, int w, int h);

    /** Draws what goes over the slots' items, in the same frame as {@link #draw}. */
    protected void drawOver(Ui ui) {}

    /** The status eye's colour: green while the player can act, amber while the device can't. */
    protected int eyeColour() {
        return Ui.GOOD;
    }

    /** The window's height this frame, for one whose notices come and go. A window with slots keeps its own. */
    protected int windowHeight() {
        return imageHeight;
    }

    /** Sends a button id to the server. */
    protected final void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** The section around the player's inventory, which starts at {@code inventoryY} in the window, and every slot's frame. */
    protected final void slots(Ui ui, int inventoryY) {
        ui.section(
                leftPos + Ui.PAD,
                topPos + inventoryY - INVENTORY_ABOVE,
                imageWidth - 2 * Ui.PAD,
                INVENTORY_HEIGHT,
                null);
        for (Slot slot : menu.slots) {
            ui.slot(leftPos + slot.x, topPos + slot.y);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int h = windowHeight();
        if (h != imageHeight) {
            imageHeight = h;
            topPos = (height - h) / 2;
        }
        ui.begin(
                graphics,
                font,
                mouseX,
                mouseY,
                minecraft == null ? 1 : (int) minecraft.getWindow().getGuiScale());
        ui.window(leftPos, topPos, imageWidth, imageHeight, title, eyeColour(), this::onClose);
        draw(
                ui,
                leftPos + Ui.PAD,
                topPos + Ui.TITLE_HEIGHT + Ui.PAD,
                imageWidth - 2 * Ui.PAD,
                imageHeight - Ui.TITLE_HEIGHT - 2 * Ui.PAD);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The window draws its own title, and a section its own text.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawOver(ui);
        graphics.pose().popPose();
        ui.drawTooltip(width);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ui.click(mouseX, mouseY)) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
