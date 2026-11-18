package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.miner.GasMinerMenu;
import io.github.west3436.zas.atmos.miner.GasMinerStatus;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A gas miner's window (CONT-181): its state and switch, its room's pressure, its target and its gas's
 * temperature, and a box per gas, of which the ticked one is what it makes. Pressures and temperatures
 * follow the player's units (CLIENT-006), while the steps are in kPa and K, which the server applies.
 * The list scrolls a row at a time when it doesn't all fit.
 */
final class GasMinerScreen extends TguiScreen<GasMinerMenu> {
    private static final int WIDTH = 216;
    private static final int SETTINGS = 2 + Ui.BUTTON_ROW + Ui.ROW + 2 * Ui.BUTTON_ROW + 1;
    private static final int MOST_ROWS = 8;
    private static final int COLUMNS = 3;

    private final int allRows;
    private final int shownRows;
    private int scrollRows;

    GasMinerScreen(GasMinerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, Ui.TITLE_HEIGHT + 2 * Ui.PAD + SETTINGS + Ui.PAD + listHeight(rows(menu)));
        allRows = rows(menu);
        shownRows = Math.min(allRows, MOST_ROWS);
    }

    private static int rows(GasMinerMenu menu) {
        return (menu.gases().size() + COLUMNS - 1) / COLUMNS;
    }

    private static int listHeight(int rows) {
        return 14 + Math.min(rows, MOST_ROWS) * Ui.ROW + 2;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ui.section(x, y, w, SETTINGS, null);
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;
        int row = y + 2;

        DeviceParts.label(ui, "state", labelX, row, Ui.BUTTON_HEIGHT);
        GasMinerStatus status = menu.status();
        DeviceParts.status(ui, Component.translatable(status.key()), colour(status), valueX, row, Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(
                ui, x + w - Ui.PAD, row, menu.value(GasMinerMenu.DATA_ON) != 0, () -> press(GasMinerMenu.TOGGLE));
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, "room", labelX, row, Ui.ROW);
        int room = menu.value(GasMinerMenu.DATA_ROOM);
        ui.line(
                Component.literal(room == GasMinerMenu.NO_ROOM ? "—" : Readout.pressure(room)),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        row += Ui.ROW;

        DeviceParts.label(ui, "target", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                valueW,
                Component.literal(Readout.pressure(menu.value(GasMinerMenu.DATA_TARGET))),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                DeviceParts.KPA,
                i -> press(GasMinerMenu.TARGET_DOWN_100 + i),
                DeviceParts.text("default"),
                () -> press(GasMinerMenu.TARGET_DEFAULT));
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, "temperature", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                valueW,
                Component.literal(Readout.temperature(menu.value(GasMinerMenu.DATA_TEMPERATURE) / 10.0)),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                DeviceParts.KELVIN,
                i -> press(GasMinerMenu.TEMPERATURE_DOWN_100 + i),
                DeviceParts.text("default"),
                () -> press(GasMinerMenu.TEMPERATURE_DEFAULT));
        y += SETTINGS + Ui.PAD;

        int top = ui.section(x, y, w, listHeight(allRows), DeviceParts.text("gas"));
        List<ResourceLocation> gases = menu.gases();
        int chosen = menu.value(GasMinerMenu.DATA_GAS);
        int columnWidth = (w - 2 * Ui.PAD - 3) / COLUMNS;
        for (int i = scrollRows * COLUMNS; i < gases.size() && i < (scrollRows + shownRows) * COLUMNS; i++) {
            int index = i;
            ui.checkbox(
                    x + Ui.PAD + i % COLUMNS * columnWidth,
                    top + (i / COLUMNS - scrollRows) * Ui.ROW,
                    columnWidth - 2,
                    Ui.ROW,
                    GasParts.label(gases.get(i)),
                    i == chosen,
                    () -> press(GasMinerMenu.GAS + index));
        }
        ui.scrollbar(
                x + w - 3,
                top,
                shownRows * Ui.ROW,
                scrollRows * Ui.ROW,
                (allRows - shownRows) * Ui.ROW,
                allRows * Ui.ROW);
    }

    private static int colour(GasMinerStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case FILLING -> Ui.GOOD;
            case AT_TARGET, WAITING -> Ui.LABEL;
            case OPEN_AIR -> Ui.AVERAGE;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (allRows > shownRows && scrollY != 0) {
            scrollRows = Math.max(0, Math.min(allRows - shownRows, scrollRows - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
