package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.teg.TegMenu;
import io.github.west3436.zas.atmos.teg.TegStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A TEG core's window (CONT-159): its state and switch, and its redstone setting (CONT-170); a table
 * of the hot and the cold stream, each with its temperature and pressure in and out; then what it
 * makes and what it has stored.
 */
public final class TegScreen extends TguiScreen<TegMenu> {
    private static final int WIDTH = 216;
    private static final int TABLE = 3 * Ui.ROW + 2;
    private static final int STATE = 2 + 2 * Ui.BUTTON_ROW;
    private static final int POWER = 2 + 2 * Ui.ROW + 1;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + STATE + Ui.PAD + TABLE + Ui.PAD + POWER;
    private static final int SIDE_WIDTH = 30;

    public TegScreen(TegMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected int eyeColour() {
        return menu.status() == TegStatus.RUNNING ? Ui.GOOD : Ui.AVERAGE;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        int labelX = x + Ui.PAD;
        ui.section(x, y, w, STATE, null);
        int row = y + 1;
        DeviceParts.label(ui, "state", labelX, row, Ui.BUTTON_HEIGHT);
        TegStatus status = menu.status();
        boolean held = menu.on() && RedstoneControl.holds(menu.redstone());
        DeviceParts.status(
                ui,
                held ? DeviceParts.held() : Component.translatable(status.key()),
                held ? Ui.AVERAGE : colour(status),
                labelX + DeviceParts.LABEL_WIDTH,
                row,
                Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(ui, x + w - Ui.PAD, row, menu.on(), () -> press(TegMenu.TOGGLE));
        DeviceParts.redstone(ui, x, row + Ui.BUTTON_ROW, w, menu.redstone(), this::press);
        y += STATE + Ui.PAD;

        ui.section(x, y, w, TABLE, null);
        int col = (w - SIDE_WIDTH - 2 * Ui.PAD) / 2;
        int inX = labelX + SIDE_WIDTH;
        int outX = inX + col;
        row = y + 1;
        ui.lineCentred(DeviceParts.text("in"), inX, col, row, Ui.ROW, Ui.LABEL, Face.BODY);
        ui.lineCentred(DeviceParts.text("out"), outX, col, row, Ui.ROW, Ui.LABEL, Face.BODY);
        for (int side = 0; side < 2; side++) {
            row += Ui.ROW;
            ui.line(
                    DeviceParts.text(side == 0 ? "hot" : "cold"),
                    labelX,
                    row,
                    Ui.ROW,
                    side == 0 ? Ui.ORANGE : Ui.BLUE,
                    Face.BOLD);
            cell(ui, inX, col, row, menu.temperature(side, true), menu.pressure(side, true));
            cell(ui, outX, col, row, menu.temperature(side, false), menu.pressure(side, false));
        }
        y += TABLE + Ui.PAD;

        ui.section(x, y, w, POWER, null);
        row = y + 1;
        DeviceParts.label(ui, "output", labelX, row, Ui.ROW);
        ui.line(
                DeviceParts.text("fe_per_tick", menu.power()),
                labelX + DeviceParts.LABEL_WIDTH,
                row,
                Ui.ROW,
                menu.power() > 0 ? Ui.GOOD : Ui.DIM,
                Face.BOLD);
        row += Ui.ROW;
        DeviceParts.label(ui, "stored", labelX, row, Ui.ROW - 1);
        int barX = labelX + DeviceParts.LABEL_WIDTH;
        DeviceParts.charge(ui, barX, row, x + w - Ui.PAD - barX, Ui.ROW - 1, menu.energy(), menu.capacity());
    }

    /** A stream's temperature and pressure, or a dash with no gas. */
    private static void cell(Ui ui, int x, int w, int row, int kelvin, int kpa) {
        Component text = kelvin <= 0
                ? Component.literal("—")
                : Component.literal(Readout.temperature(kelvin) + "  " + Readout.pressure(kpa));
        ui.lineCentred(text, x, w, row, Ui.ROW, Ui.WHITE, Face.BODY);
    }

    private static int colour(TegStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case RUNNING -> Ui.GOOD;
            case FULL, NO_DIFFERENCE -> Ui.LABEL;
            case NO_CIRCULATORS, NO_FLOW -> Ui.AVERAGE;
        };
    }
}
