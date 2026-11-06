package io.github.west3436.zas.atmos.client.tgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** A window in the TGUI look (D-76) with no menu behind it: what {@link TguiScreen} is for a device. */
public abstract class TguiWindow extends Screen {
    private final Ui ui = new Ui();
    private final int windowWidth;

    protected TguiWindow(Component title, int width) {
        super(title);
        this.windowWidth = width;
    }

    /** The window's title this frame. */
    protected Component windowTitle() {
        return title;
    }

    /** The window's height this frame. */
    protected abstract int windowHeight();

    /** Draws the window's content in a box inside its title bar and padding. */
    protected abstract void draw(Ui ui, int x, int y, int w, int h);

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int h = windowHeight();
        int left = (width - windowWidth) / 2;
        int top = (height - h) / 2;
        ui.begin(
                graphics,
                font,
                mouseX,
                mouseY,
                minecraft == null ? 1 : (int) minecraft.getWindow().getGuiScale());
        ui.window(left, top, windowWidth, h, windowTitle(), Ui.GOOD, this::onClose);
        draw(
                ui,
                left + Ui.PAD,
                top + Ui.TITLE_HEIGHT + Ui.PAD,
                windowWidth - 2 * Ui.PAD,
                h - Ui.TITLE_HEIGHT - 2 * Ui.PAD);
        ui.drawTooltip(width);
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
