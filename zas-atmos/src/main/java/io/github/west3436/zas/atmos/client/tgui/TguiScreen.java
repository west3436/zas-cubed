package io.github.west3436.zas.atmos.client.tgui;

import io.github.west3436.zas.atmos.label.LabelPayload;
import io.github.west3436.zas.atmos.label.LabelledMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A device window in the TGUI look (D-76). It draws the window, then has the screen draw its content
 * with {@link Ui} each frame. A control sends only a button id, which the server applies and checks
 * (CONT-074).
 *
 * <p>The window of a device that takes a label ({@link LabelledMenu}) shows the label in its title
 * bar, where a player can type one ({@link TitleLabel}). That is sent as text, which the server
 * cleans and checks as it checks a button ({@link LabelPayload}).
 */
public abstract class TguiScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    /** The section around the player's inventory, above its first row and in all. */
    private static final int INVENTORY_ABOVE = 3;

    private static final int INVENTORY_HEIGHT = 80;

    private final Ui ui = new Ui();

    /** The title bar of a device that takes a label; null for any other window. */
    private final @Nullable TitleLabel label;

    protected TguiScreen(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
        label = menu instanceof LabelledMenu
                ? new TitleLabel(
                        title, text -> PacketDistributor.sendToServer(new LabelPayload(menu.containerId, text)))
                : null;
    }

    /** Whether the player may label the device: a spectator may look, and nothing more. */
    private boolean mayLabel() {
        return minecraft != null && minecraft.player != null && !minecraft.player.isSpectator();
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

    /**
     * The section around the player's inventory, which starts at {@code inventoryY} in the window, and
     * the frame of every slot that is shown.
     */
    protected final void slots(Ui ui, int inventoryY) {
        ui.section(
                leftPos + Ui.PAD,
                topPos + inventoryY - INVENTORY_ABOVE,
                imageWidth - 2 * Ui.PAD,
                INVENTORY_HEIGHT,
                null);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ui.slot(leftPos + slot.x, topPos + slot.y);
            }
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
        if (label == null) {
            ui.window(leftPos, topPos, imageWidth, imageHeight, title, eyeColour(), this::onClose);
        } else {
            ui.frame(leftPos, topPos, imageWidth, imageHeight, eyeColour(), this::onClose);
            label.draw(ui, leftPos, topPos, imageWidth, mayLabel());
        }
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

    /** While a label is being typed the keys are its own: none closes the window or moves an item. */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (label != null && label.editing()) {
            label.key(key, () -> minecraft == null ? "" : minecraft.keyboardHandler.getClipboard());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (label != null && label.editing()) {
            label.type(character);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (label != null && label.editing() && !label.over(mouseX, mouseY)) {
            // A click anywhere else keeps what was typed, then does what it would have.
            label.keep();
        }
        if (button == 0 && ui.click(mouseX, mouseY)) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
