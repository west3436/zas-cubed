package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.turbine.TurbineMenu;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A gas turbine's window (CONT-163): its state and switch; the shaft's speed as a bar; the chamber's
 * pressure and temperature; then what it makes and what it has stored.
 */
public final class TurbineScreen extends TguiScreen<TurbineMenu> {
    private static final int WIDTH = 216;
    private static final int STATE = 2 + Ui.BUTTON_ROW;
    private static final int READINGS = 2 + 4 * Ui.ROW + 1;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + STATE + Ui.PAD + READINGS;

    public TurbineScreen(TurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected int eyeColour() {
        return menu.status() == TurbineStatus.RUNNING ? Ui.GOOD : Ui.AVERAGE;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = x + w - Ui.PAD - valueX;
        ui.section(x, y, w, STATE, null);
        int row = y + 1;
        DeviceParts.label(ui, "state", labelX, row, Ui.BUTTON_HEIGHT);
        TurbineStatus status = menu.status();
        DeviceParts.status(ui, Component.translatable(status.key()), colour(status), valueX, row, Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(ui, x + w - Ui.PAD, row, menu.on(), () -> press(TurbineMenu.TOGGLE));
        y += STATE + Ui.PAD;

        ui.section(x, y, w, READINGS, null);
        row = y + 1;
        int rpm = menu.value(TurbineMenu.DATA_RPM);
        int top = Math.max(1, menu.value(TurbineMenu.DATA_TOP_RPM));
        DeviceParts.label(ui, "speed", labelX, row, Ui.ROW - 1);
        ui.bar(
                valueX,
                row,
                valueW,
                Ui.ROW - 1,
                (double) rpm / top,
                Ui.BLUE,
                Ui.BLUE_EDGE,
                DeviceParts.text("rpm", rpm));
        row += Ui.ROW;
        DeviceParts.label(ui, "chamber", labelX, row, Ui.ROW);
        int kpa = menu.value(TurbineMenu.DATA_CHAMBER_PRESSURE);
        int kelvin = menu.value(TurbineMenu.DATA_CHAMBER_TEMPERATURE);
        ui.line(
                Component.literal(kelvin <= 0 ? "—" : Readout.pressure(kpa) + "  " + Readout.temperature(kelvin)),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        row += Ui.ROW;
        int power = menu.value(TurbineMenu.DATA_POWER);
        DeviceParts.label(ui, "output", labelX, row, Ui.ROW);
        ui.line(DeviceParts.text("fe_per_tick", power), valueX, row, Ui.ROW, power > 0 ? Ui.GOOD : Ui.DIM, Face.BOLD);
        row += Ui.ROW;
        DeviceParts.label(ui, "stored", labelX, row, Ui.ROW - 1);
        DeviceParts.charge(ui, valueX, row, valueW, Ui.ROW - 1, menu.energy(), menu.capacity());
    }

    private static int colour(TurbineStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case RUNNING -> Ui.GOOD;
            case IDLE -> Ui.LABEL;
            case NO_COMPRESSOR, CHAMBER_OPEN -> Ui.AVERAGE;
        };
    }
}
