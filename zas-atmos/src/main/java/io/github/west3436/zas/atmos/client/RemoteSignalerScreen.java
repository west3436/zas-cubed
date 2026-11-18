package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.ttv.RemoteSignalerMenu;
import io.github.west3436.zas.atmos.ttv.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A held remote signaler's window (CONT-185): its frequency and code, Default and Send. */
final class RemoteSignalerScreen extends TguiScreen<RemoteSignalerMenu> {
    private static final int WIDTH = 176;
    private static final int SECTION = 2 + 3 * Ui.BUTTON_ROW + 1;
    private static final String[] FREQUENCY_STEPS = {"−1", "−0.1", "+0.1", "+1"};

    RemoteSignalerScreen(RemoteSignalerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, Ui.TITLE_HEIGHT + 2 * Ui.PAD + SECTION);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ui.section(x, y, w, SECTION, null);
        Signal signal = menu.signal();
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;
        int row = y + 2;

        DeviceParts.label(ui, "frequency", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                valueW,
                Component.literal(signal.frequencyText()),
                Ui.WHITE,
                FREQUENCY_STEPS,
                "",
                i -> press(RemoteSignalerMenu.FREQUENCY_DOWN_10 + i),
                null,
                null);
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, "code", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                valueW,
                Component.literal(Integer.toString(signal.code())),
                Ui.WHITE,
                DeviceParts.SMALL_STEPS,
                "",
                i -> press(RemoteSignalerMenu.CODE_DOWN_10 + i),
                null,
                null);
        row += Ui.BUTTON_ROW;

        Component send = DeviceParts.text("send");
        int sendWidth = ui.buttonWidth(send);
        ui.button(
                x + w - Ui.PAD - sendWidth,
                row,
                sendWidth,
                Ui.BUTTON_HEIGHT,
                send,
                Tone.SELECTED,
                () -> press(RemoteSignalerMenu.SEND));
        Component reset = DeviceParts.text("default");
        ui.button(
                valueX,
                row,
                ui.buttonWidth(reset),
                Ui.BUTTON_HEIGHT,
                reset,
                Tone.DEFAULT,
                () -> press(RemoteSignalerMenu.DEFAULT));
    }
}
