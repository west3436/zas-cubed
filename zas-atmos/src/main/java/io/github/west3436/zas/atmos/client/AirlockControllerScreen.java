package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * An airlock controller's window (CONT-153): its state with a Stop button, and the airlock drawn
 * from side to side, the pressure beyond airlock 1, the chamber's at the air sensor, and the pressure
 * beyond airlock 2, with each airlock's door between them, green open, grey closed and red bolted.
 * Under each side, a button cycles toward it; the one it's cycling toward is lit. A part that isn't
 * linked shows a dash and greys out its button, as the server refuses it.
 */
final class AirlockControllerScreen extends TguiScreen<AirlockControllerMenu> {
    private static final int WIDTH = 216;
    private static final int DOOR_GAP = 9;
    private static final int SECTION_HEIGHT = 2 + Ui.BUTTON_ROW + 3 + 2 * Ui.ROW + 2 + Ui.BUTTON_ROW + 2;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + SECTION_HEIGHT;

    AirlockControllerScreen(AirlockControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private static Component text(String key) {
        return Component.translatable("gui.zas_atmos.airlock_controller." + key);
    }

    private boolean powered() {
        return menu.value(AirlockControllerMenu.DATA_POWERED) != 0;
    }

    private boolean linked(int bit) {
        return (menu.value(AirlockControllerMenu.DATA_LINKS) & 1 << bit) != 0;
    }

    /** The airlock it's cycling toward, 0 or 1, or −1. */
    private int cycling() {
        return menu.value(AirlockControllerMenu.DATA_CYCLING) - 1;
    }

    private boolean sensor() {
        return linked(2);
    }

    @Override
    protected int windowHeight() {
        return HEIGHT + DeviceParts.noticesHeight(powered(), false) + (sensor() ? 0 : Ui.NOTICE_HEIGHT + Ui.PAD);
    }

    @Override
    protected int eyeColour() {
        return !powered() ? Ui.BAD : cycling() >= 0 ? Ui.AVERAGE : Ui.GOOD;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = DeviceParts.notices(ui, x, y, w, powered(), false, Optional.empty());
        if (!sensor()) {
            y += ui.notice(x, y, w, Ui.NOTICE, Ui.BLACK, text("no_sensor")) + Ui.PAD;
        }
        ui.section(x, y, w, SECTION_HEIGHT, null);
        int labelX = x + Ui.PAD;
        int row = y + 2;

        // The state, and Stop at its right.
        DeviceParts.label(ui, "state", labelX, row, Ui.BUTTON_HEIGHT);
        DeviceParts.status(
                ui,
                state(),
                cycling() >= 0 ? Ui.AVERAGE : Ui.DIM,
                labelX + DeviceParts.LABEL_WIDTH,
                row,
                Ui.BUTTON_HEIGHT);
        Component stop = text("stop");
        int stopW = ui.buttonWidth(stop);
        ui.button(
                x + w - Ui.PAD - stopW,
                row,
                stopW,
                Ui.BUTTON_HEIGHT,
                stop,
                Tone.DANGER,
                cycling() >= 0 ? () -> press(AirlockControllerMenu.STOP) : null);
        row += Ui.BUTTON_ROW + 3;

        // Side 1 | door 1 | chamber | door 2 | side 2
        int inner = w - 2 * Ui.PAD;
        int col = (inner - 2 * DOOR_GAP) / 3;
        int[] cols = {labelX, labelX + col + DOOR_GAP, labelX + 2 * (col + DOOR_GAP)};
        double[] kpa = {
            menu.pressure(AirlockControllerMenu.DATA_BEYOND),
            menu.pressure(AirlockControllerMenu.DATA_CHAMBER),
            menu.pressure(AirlockControllerMenu.DATA_BEYOND + 2)
        };
        Component[] names = {text("airlock_1"), text("chamber"), text("airlock_2")};
        boolean[] known = {linked(0), sensor(), linked(1)};
        for (int i = 0; i < 3; i++) {
            ui.lineCentred(names[i], cols[i], col, row, Ui.ROW, Ui.LABEL, Face.BODY);
            boolean show = known[i] && !Double.isNaN(kpa[i]);
            ui.lineCentred(
                    Component.literal(show ? Readout.pressure(kpa[i]) : "—"),
                    cols[i],
                    col,
                    row + Ui.ROW,
                    Ui.ROW,
                    show ? Ui.WHITE : Ui.DIM,
                    Face.BODY);
        }
        for (int i = 0; i < 2; i++) {
            int doorX = cols[i] + col + (DOOR_GAP - 3) / 2;
            ui.fill(doorX, row, 3, 2 * Ui.ROW, doorColour(i));
        }
        row += 2 * Ui.ROW + 2;

        // A cycle toward each side.
        boolean ready = powered() && cycling() < 0 && linked(0) && linked(1) && sensor();
        for (int i = 0; i < 2; i++) {
            int index = i;
            int bx = cols[i == 0 ? 0 : 2];
            ui.button(
                    bx,
                    row,
                    col,
                    Ui.BUTTON_HEIGHT,
                    text("cycle"),
                    cycling() == i ? Tone.SELECTED : Tone.DEFAULT,
                    ready ? () -> press(AirlockControllerMenu.CYCLE + index) : null);
        }
    }

    private Component state() {
        if (cycling() < 0) {
            return text("idle");
        }
        return switch (menu.value(AirlockControllerMenu.DATA_STEP)) {
            case 1 -> text("siphoning");
            case 2 -> text("blowing");
            default -> text("cycling");
        };
    }

    private int doorColour(int i) {
        if (!linked(i)) {
            return Ui.DIM;
        }
        int bits = menu.value(AirlockControllerMenu.DATA_DOORS) >> 2 * i;
        if ((bits & 2) != 0) {
            return Ui.BAD;
        }
        return (bits & 1) != 0 ? Ui.GOOD : Ui.LABEL;
    }
}
