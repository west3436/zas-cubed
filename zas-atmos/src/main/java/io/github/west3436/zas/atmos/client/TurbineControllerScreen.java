package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity.Part;
import io.github.west3436.zas.atmos.turbine.TurbineControllerMenu;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A gas turbine controller's window (CONT-163): its state and switch, and a tick or a cross for each
 * linked part; the shaft's speed as a bar, the chamber, the compressor's buffer, what it makes and
 * what it has stored; the burn rate, the igniter's button and auto switch; then a box per fuel and per
 * oxidizer, of which the ticked one is what the compressor feeds.
 */
final class TurbineControllerScreen extends TguiScreen<TurbineControllerMenu> {
    private static final int WIDTH = 236;
    private static final int COLUMNS = 3;
    private static final int STATE = 2 + Ui.BUTTON_ROW + Ui.ROW + 1;
    private static final int READINGS = 2 + 5 * Ui.ROW + 1;
    private static final int SETTINGS = 2 + 2 * Ui.BUTTON_ROW + 1;
    private static final String[] RATE_STEPS = {"−0.1", "−0.01", "+0.01", "+0.1"};

    TurbineControllerScreen(TurbineControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, height(menu));
    }

    private static int rows(List<ResourceLocation> gases) {
        return Math.max(1, (Math.min(gases.size(), TurbineControllerMenu.MOST_GASES) + COLUMNS - 1) / COLUMNS);
    }

    private static int listHeight(List<ResourceLocation> gases) {
        return 14 + rows(gases) * Ui.ROW + 2;
    }

    private static int height(TurbineControllerMenu menu) {
        return Ui.TITLE_HEIGHT
                + 2 * Ui.PAD
                + STATE
                + Ui.PAD
                + READINGS
                + Ui.PAD
                + SETTINGS
                + Ui.PAD
                + listHeight(menu.fuels())
                + Ui.PAD
                + listHeight(menu.oxidizers());
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
        DeviceParts.onOff(ui, x + w - Ui.PAD, row, menu.on(), () -> press(TurbineControllerMenu.TOGGLE));
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "links", labelX, row, Ui.ROW);
        int partX = valueX;
        for (Part part : Part.values()) {
            boolean linked = menu.linked(part);
            Component word = Component.literal(linked ? "✔ " : "✖ ").append(Component.translatable(part.key()));
            ui.line(word, partX, row, Ui.ROW, linked ? Ui.GOOD : Ui.BAD, Face.BODY);
            partX += valueW / 3;
        }
        y += STATE + Ui.PAD;

        ui.section(x, y, w, READINGS, null);
        row = y + 1;
        int rpm = menu.value(TurbineControllerMenu.DATA_RPM);
        int top = Math.max(1, menu.value(TurbineControllerMenu.DATA_TOP_RPM));
        DeviceParts.label(ui, "speed", labelX, row, Ui.ROW - 1);
        ui.bar(valueX, row, valueW, Ui.ROW - 1, (double) rpm / top, Ui.BLUE, Ui.BLUE_EDGE, DeviceParts.text("rpm", rpm));
        row += Ui.ROW;
        DeviceParts.label(ui, "chamber", labelX, row, Ui.ROW);
        int kpa = menu.value(TurbineControllerMenu.DATA_CHAMBER_PRESSURE);
        int kelvin = menu.value(TurbineControllerMenu.DATA_CHAMBER_TEMPERATURE);
        ui.line(
                Component.literal(kelvin <= 0 ? "—" : Readout.pressure(kpa) + "  " + Readout.temperature(kelvin)),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        row += Ui.ROW;
        DeviceParts.label(ui, "buffer", labelX, row, Ui.ROW);
        int buffer = menu.value(TurbineControllerMenu.DATA_BUFFER_PRESSURE);
        ui.line(
                Component.literal(buffer == TurbineControllerMenu.NONE
                        ? "—"
                        : Readout.pressure(buffer)
                                + "  "
                                + share(menu.fuels(), menu.value(TurbineControllerMenu.DATA_FUEL))
                                + " "
                                + Readout.pressure(menu.value(TurbineControllerMenu.DATA_BUFFER_FUEL))
                                + "  "
                                + share(menu.oxidizers(), menu.value(TurbineControllerMenu.DATA_OXIDIZER))
                                + " "
                                + Readout.pressure(menu.value(TurbineControllerMenu.DATA_BUFFER_OXIDIZER))),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        row += Ui.ROW;
        int power = menu.value(TurbineControllerMenu.DATA_POWER);
        DeviceParts.label(ui, "output", labelX, row, Ui.ROW);
        ui.line(DeviceParts.text("fe_per_tick", power), valueX, row, Ui.ROW, power > 0 ? Ui.GOOD : Ui.DIM, Face.BOLD);
        row += Ui.ROW;
        DeviceParts.label(ui, "stored", labelX, row, Ui.ROW - 1);
        DeviceParts.charge(ui, valueX, row, valueW, Ui.ROW - 1, menu.energy(), menu.capacity());
        y += READINGS + Ui.PAD;

        ui.section(x, y, w, SETTINGS, null);
        row = y + 1;
        DeviceParts.label(ui, "burn_rate", labelX, row, Ui.BUTTON_HEIGHT);
        double rate = menu.value(TurbineControllerMenu.DATA_RATE) / 100.0;
        ui.stepper(
                valueX,
                row,
                valueW,
                DeviceParts.text("mol_per_second", String.format(Locale.ROOT, "%.2f", rate)),
                Ui.WHITE,
                RATE_STEPS,
                "mol/s",
                i -> press(TurbineControllerMenu.RATE_DOWN_TENTH + i),
                null,
                null);
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "igniter", labelX, row, Ui.BUTTON_HEIGHT);
        Component spark = DeviceParts.text("ignite");
        int sparkW = ui.buttonWidth(spark);
        ui.button(
                valueX,
                row,
                sparkW,
                Ui.BUTTON_HEIGHT,
                spark,
                Ui.Tone.CAUTION,
                menu.linked(Part.IGNITER) ? () -> press(TurbineControllerMenu.IGNITE) : null);
        ui.checkbox(
                valueX + sparkW + 6,
                row,
                valueW - sparkW - 6,
                Ui.BUTTON_HEIGHT,
                DeviceParts.text("auto"),
                menu.value(TurbineControllerMenu.DATA_AUTO) != 0,
                () -> press(TurbineControllerMenu.AUTO));
        y += SETTINGS + Ui.PAD;

        y = gases(ui, x, y, w, "fuel", menu.fuels(), TurbineControllerMenu.DATA_FUEL, TurbineControllerMenu.FUEL);
        gases(ui, x, y + Ui.PAD, w, "oxidizer", menu.oxidizers(), TurbineControllerMenu.DATA_OXIDIZER, TurbineControllerMenu.OXIDIZER);
    }

    /** A list of gases, the chosen one ticked; returns where it ends. */
    private int gases(Ui ui, int x, int y, int w, String key, List<ResourceLocation> gases, int dataIndex, int firstButton) {
        int height = listHeight(gases);
        int top = ui.section(x, y, w, height, DeviceParts.text(key));
        int chosen = menu.value(dataIndex);
        int columnWidth = (w - 2 * Ui.PAD) / COLUMNS;
        if (gases.isEmpty()) {
            ui.line(Component.literal("—"), x + Ui.PAD, top, Ui.ROW, Ui.DIM, Face.BODY);
        }
        for (int i = 0; i < gases.size() && i < TurbineControllerMenu.MOST_GASES; i++) {
            int index = i;
            ui.checkbox(
                    x + Ui.PAD + i % COLUMNS * columnWidth,
                    top + i / COLUMNS * Ui.ROW,
                    columnWidth - 2,
                    Ui.ROW,
                    GasParts.label(gases.get(i)),
                    i == chosen,
                    () -> press(firstButton + index));
        }
        return y + height;
    }

    /** A gas's short name in a list by its index, or a dash. */
    private static String share(List<ResourceLocation> gases, int index) {
        return index >= 0 && index < gases.size() ? GasParts.label(gases.get(index)).getString() : "—";
    }

    private static int colour(TurbineStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case RUNNING -> Ui.GOOD;
            case IDLE -> Ui.LABEL;
            case UNLINKED, NO_COMPRESSOR, CHAMBER_OPEN, NO_FUEL -> Ui.AVERAGE;
        };
    }
}
