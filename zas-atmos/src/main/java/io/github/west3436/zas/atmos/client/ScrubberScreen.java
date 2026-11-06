package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A scrubber's window (CONT-015): its mode, what it did last step, its network's pressure, and what
 * it takes. Panic stands out in amber until it's chosen. While a controller runs it, the eye turns
 * amber, a notice says which controller, and every control greys out, as the server refuses them.
 */
final class ScrubberScreen extends TguiScreen<ScrubberMenu> {
    private static final int WIDTH = 216;
    private static final int TOP_HEIGHT = 2 + Ui.BUTTON_ROW + 2 * Ui.ROW + 2;
    private static final int GAS_COLUMNS = 2;
    private static final int GAS_ROWS = (ScrubberMenu.GASES.size() + GAS_COLUMNS - 1) / GAS_COLUMNS;
    private static final int GASES_HEIGHT = 14 + GAS_ROWS * Ui.ROW + 2;
    private static final int TAGS_HEIGHT = 14 + Ui.ROW + 2;
    private static final int HEIGHT =
            Ui.TITLE_HEIGHT + 2 * Ui.PAD + TOP_HEIGHT + Ui.PAD + GASES_HEIGHT + Ui.PAD + TAGS_HEIGHT;
    private static final List<Component> MODES = modes();

    ScrubberScreen(ScrubberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private static List<Component> modes() {
        List<Component> modes = new ArrayList<>();
        for (ScrubberMode mode : ScrubberMode.values()) {
            modes.add(Component.translatable("gui.zas_atmos.scrubber.mode." + mode.getSerializedName()));
        }
        return List.copyOf(modes);
    }

    private boolean powered() {
        return menu.value(ScrubberMenu.DATA_POWERED) != 0;
    }

    @Override
    protected int windowHeight() {
        return HEIGHT + DeviceParts.noticesHeight(powered(), menu.controlled());
    }

    @Override
    protected int eyeColour() {
        return powered() && !menu.controlled() ? Ui.GOOD : Ui.AVERAGE;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = DeviceParts.notices(ui, x, y, w, powered(), menu.controlled(), menu.controller());
        boolean free = !menu.controlled();
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;

        ui.section(x, y, w, TOP_HEIGHT, null);
        int row = y + 2;
        DeviceParts.label(ui, "mode", labelX, row, Ui.BUTTON_HEIGHT);
        ui.choice(
                valueX,
                row,
                MODES,
                menu.mode().ordinal(),
                ScrubberMode.PANIC.ordinal(),
                free ? i -> press(ScrubberMenu.SET_MODE + i) : null);
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "state", labelX, row, Ui.ROW);
        DeviceParts.status(ui, Component.translatable(menu.status().key()), statusColour(), valueX, row, Ui.ROW);
        row += Ui.ROW;
        DeviceParts.label(ui, "network", labelX, row, Ui.ROW);
        ui.line(
                Component.literal(Readout.pressure(menu.value(ScrubberMenu.DATA_NETWORK))),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);

        y += TOP_HEIGHT + Ui.PAD;
        row = ui.section(x, y, w, GASES_HEIGHT, DeviceParts.text("gases"));
        int columnWidth = (w - 2 * Ui.PAD) / GAS_COLUMNS;
        for (int i = 0; i < ScrubberMenu.GASES.size(); i++) {
            int option = i;
            ui.checkbox(
                    labelX + i % GAS_COLUMNS * columnWidth,
                    row + i / GAS_COLUMNS * Ui.ROW,
                    columnWidth - 2,
                    Ui.ROW,
                    GasParts.label(ScrubberMenu.GASES.get(i)),
                    menu.selected(option),
                    free ? () -> press(ScrubberMenu.FIRST_OPTION + option) : null);
        }

        y += GASES_HEIGHT + Ui.PAD;
        row = ui.section(x, y, w, TAGS_HEIGHT, DeviceParts.text("tags"));
        int tagWidth = (w - 2 * Ui.PAD) / ScrubberMenu.TAGS.size();
        for (int i = 0; i < ScrubberMenu.TAGS.size(); i++) {
            int option = ScrubberMenu.GASES.size() + i;
            ui.checkbox(
                    labelX + i * tagWidth,
                    row,
                    tagWidth - 2,
                    Ui.ROW,
                    Component.translatable("gui.zas_atmos.scrubber.tag."
                            + ScrubberMenu.TAGS.get(i).id().getPath()),
                    menu.selected(option),
                    free ? () -> press(ScrubberMenu.FIRST_OPTION + option) : null);
        }
    }

    private int statusColour() {
        return switch (menu.status()) {
            case OFF -> Ui.DIM;
            case MOVING -> Ui.GOOD;
            case WAITING, NOTHING -> Ui.LABEL;
            case STALLED -> Ui.AVERAGE;
        };
    }
}
