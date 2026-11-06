package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import io.github.west3436.zas.atmos.pump.PumpMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A pump's window (CONT-011, CONT-012): its strip, then its state and its one setting. A pressure pump
 * has a target with a default. A volume pump has a rate, with a bar against the most it can be.
 */
final class PumpScreen extends InlineScreen<PumpMenu> {
    private static final int RATE_BAR = 4;
    private static final int PRESSURE_SECTION = 2 + STATE_ROW + Ui.BUTTON_ROW + 1;
    private static final int RATE_SECTION = PRESSURE_SECTION + RATE_BAR + 1;

    private final boolean rate;

    private PumpScreen(PumpMenu menu, Inventory inventory, Component title, boolean rate) {
        super(menu, inventory, title, STRIP + Ui.PAD + (rate ? RATE_SECTION : PRESSURE_SECTION));
        this.rate = rate;
    }

    /** A pressure pump's. */
    static PumpScreen pressure(PumpMenu menu, Inventory inventory, Component title) {
        return new PumpScreen(menu, inventory, title, false);
    }

    /** A volume pump's. */
    static PumpScreen volume(PumpMenu menu, Inventory inventory, Component title) {
        return new PumpScreen(menu, inventory, title, true);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = noPower(ui, x, y, w);
        strip(ui, x, y, w);
        y += STRIP + Ui.PAD;
        ui.section(x, y, w, rate ? RATE_SECTION : PRESSURE_SECTION, null);
        int row = y + 2;
        stateRow(ui, x, row, w);
        row += STATE_ROW;
        int setting = menu.value(PumpMenu.DATA_SETTING);
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;
        DeviceParts.label(ui, rate ? "rate" : "target", labelX, row, Ui.BUTTON_HEIGHT);
        if (rate) {
            ui.stepper(
                    valueX,
                    row,
                    valueW,
                    DeviceParts.text("litres_per_second", setting),
                    Ui.WHITE,
                    DeviceParts.SMALL_STEPS,
                    "L/s",
                    i -> press(PumpMenu.STEP_DOWN_BIG + i),
                    null,
                    null);
            ui.track(valueX, row + Ui.BUTTON_ROW, valueW, RATE_BAR);
            ui.box(valueX, row + Ui.BUTTON_ROW, (float) (valueW * setting / PumpFlow.MAX_RATE), RATE_BAR, Ui.BLUE);
        } else {
            ui.stepper(
                    valueX,
                    row,
                    valueW,
                    Component.literal(Readout.pressure(setting)),
                    Ui.WHITE,
                    DeviceParts.BIG_STEPS,
                    DeviceParts.KPA,
                    i -> press(PumpMenu.STEP_DOWN_BIG + i),
                    DeviceParts.text("default"),
                    () -> press(PumpMenu.DEFAULT));
        }
    }
}
