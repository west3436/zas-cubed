package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.atmos.vent.VentMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A vent's window (CONT-014): its mode, what it did last step, its network's pressure and its two
 * bounds. While a controller runs it, the eye turns amber, a notice says which controller, and every
 * control greys out, as the server refuses them. The steps are in kPa, which is what the server
 * applies, while the values follow the player's units (CLIENT-006).
 */
final class VentScreen extends TguiScreen<VentMenu> {
    private static final int WIDTH = 216;
    private static final int TOP_HEIGHT = 2 + Ui.BUTTON_ROW + 2 * Ui.ROW + 2;
    private static final int BOUNDS_HEIGHT = 14 + 1 + 2 * Ui.BUTTON_ROW + 1;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + TOP_HEIGHT + Ui.PAD + BOUNDS_HEIGHT;
    private static final List<Component> MODES = modes();

    VentScreen(VentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private static List<Component> modes() {
        List<Component> modes = new ArrayList<>();
        for (VentMode mode : VentMode.values()) {
            modes.add(Component.translatable("gui.zas_atmos.vent.mode." + mode.getSerializedName()));
        }
        return List.copyOf(modes);
    }

    private boolean powered() {
        return menu.value(VentMenu.DATA_POWERED) != 0;
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
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;

        ui.section(x, y, w, TOP_HEIGHT, null);
        int row = y + 2;
        DeviceParts.label(ui, "mode", labelX, row, Ui.BUTTON_HEIGHT);
        ui.choice(valueX, row, MODES, menu.mode().ordinal(), -1, free ? i -> press(VentMenu.SET_MODE + i) : null);
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "state", labelX, row, Ui.ROW);
        DeviceParts.status(ui, Component.translatable(menu.status().key()), statusColour(), valueX, row, Ui.ROW);
        row += Ui.ROW;
        DeviceParts.label(ui, "network", labelX, row, Ui.ROW);
        ui.line(
                Component.literal(Readout.pressure(menu.value(VentMenu.DATA_NETWORK))),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);

        y += TOP_HEIGHT + Ui.PAD;
        row = ui.section(x, y, w, BOUNDS_HEIGHT, DeviceParts.text("bounds")) + 1;
        bound(
                ui,
                "external",
                VentMenu.DATA_EXTERNAL,
                VentMenu.EXTERNAL_DOWN_100,
                VentMenu.EXTERNAL_DEFAULT,
                labelX,
                valueX,
                valueW,
                row,
                free);
        row += Ui.BUTTON_ROW;
        bound(
                ui,
                "internal",
                VentMenu.DATA_INTERNAL,
                VentMenu.INTERNAL_DOWN_100,
                VentMenu.INTERNAL_DEFAULT,
                labelX,
                valueX,
                valueW,
                row,
                free);
    }

    private void bound(
            Ui ui, String key, int data, int down, int reset, int labelX, int valueX, int valueW, int y, boolean free) {
        DeviceParts.label(ui, key, labelX, y, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                y,
                valueW,
                Component.literal(Readout.pressure(menu.value(data))),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                DeviceParts.KPA,
                free ? i -> press(down + i) : null,
                DeviceParts.text("default"),
                free ? () -> press(reset) : null);
    }

    private int statusColour() {
        return switch (menu.status()) {
            case OFF -> Ui.DIM;
            case MOVING -> Ui.GOOD;
            case WAITING, NETWORK_BOUND, ROOM_BOUND -> Ui.LABEL;
            case NETWORK_EMPTY, ROOM_EMPTY -> Ui.AVERAGE;
        };
    }
}
