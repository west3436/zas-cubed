package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.inline.SidePortMenu;
import io.github.west3436.zas.atmos.mixer.MixerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A mixer's window (CONT-077): its strip with two inputs, its state and target, and one bar split
 * between their shares. Input 1 is the port at its back, and input 2 the port on its side.
 */
final class MixerScreen extends InlineScreen<MixerMenu> {
    private static final int SPLIT = 9;
    private static final int SETTINGS = 2 + STATE_ROW + Ui.BUTTON_ROW + SPLIT + 2 + Ui.BUTTON_ROW + 1;

    MixerScreen(MixerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SIDE_STRIP + Ui.PAD + SETTINGS);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = noPower(ui, x, y, w);
        sideStrip(ui, menu, x, y, w, DeviceParts.text("input_1"), DeviceParts.text("input_2"), true);
        y += SIDE_STRIP + Ui.PAD;
        ui.section(x, y, w, SETTINGS, null);
        int row = y + 2;
        stateRow(ui, x, row, w);
        row += STATE_ROW;
        targetRow(ui, x, row, w, menu.value(SidePortMenu.DATA_TARGET));
        row += Ui.BUTTON_ROW;

        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;
        int back = menu.value(MixerMenu.DATA_BACK_PERCENT);
        ui.split(
                valueX,
                row,
                valueW,
                SPLIT,
                back / 100.0,
                Ui.BLUE,
                Ui.TEAL_EDGE,
                DeviceParts.text("share", DeviceParts.text("input_1"), back),
                DeviceParts.text("share", DeviceParts.text("input_2"), 100 - back));
        row += SPLIT + 2;
        DeviceParts.label(ui, "mix", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                valueW,
                DeviceParts.text("percent", back),
                Ui.WHITE,
                DeviceParts.SMALL_STEPS,
                "%",
                i -> press(MixerMenu.MIX_DOWN_BIG + i),
                DeviceParts.text("default"),
                () -> press(MixerMenu.MIX_DEFAULT));
    }
}
