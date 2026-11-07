package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.inline.SidePortMenu;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The window of a device in a pipe line (CONT-011, CONT-012, CONT-013, CONT-077). A strip shows its
 * ports and their pressures with the gas going left to right, and the arrows turn green while it
 * moves. Below that come its state, its switch and its own settings.
 */
abstract class InlineScreen<M extends InlineDeviceMenu> extends TguiScreen<M> {
    static final int WIDTH = 216;

    /** The state row, with its switch. */
    static final int STATE_ROW = Ui.BUTTON_ROW;

    /** A port's cell in the strip. */
    static final int CELL_WIDTH = 72;

    static final int CELL_HEIGHT = 21;
    private static final int SIDE_GAP = 7;

    /** The gap between an arrow and what it starts or ends at. */
    static final int ARROW_GAP = 3;

    /** How tall a strip with no side port is. */
    static final int STRIP = CELL_HEIGHT;

    /** How tall a strip with one is. */
    static final int SIDE_STRIP = 2 * CELL_HEIGHT + SIDE_GAP;

    private final int baseHeight;

    /** @param contentHeight how tall the strip and sections are together */
    InlineScreen(M menu, Inventory inventory, Component title, int contentHeight) {
        super(menu, inventory, title, WIDTH, Ui.TITLE_HEIGHT + 2 * Ui.PAD + contentHeight);
        this.baseHeight = imageHeight;
    }

    @Override
    protected int windowHeight() {
        return baseHeight + DeviceParts.noticesHeight(menu.powered(), false);
    }

    @Override
    protected int eyeColour() {
        return menu.powered() ? Ui.GOOD : Ui.AVERAGE;
    }

    /** @return where what's below the notice starts */
    protected final int noPower(Ui ui, int x, int y, int w) {
        return DeviceParts.notices(ui, x, y, w, menu.powered(), false, Optional.empty());
    }

    private boolean moving() {
        return menu.status() == InlineStatus.MOVING;
    }

    /** Green while it moves gas, dim otherwise. */
    final int arrowColour() {
        return moving() ? Ui.GOOD : Ui.DIM;
    }

    /** A port's cell: its label over its network's pressure. */
    final void cell(Ui ui, int x, int y, Component label, int port) {
        ui.section(x, y, CELL_WIDTH, CELL_HEIGHT, null);
        ui.lineCentred(label, x, CELL_WIDTH, y + 1, 9, Ui.LABEL, Face.BODY);
        ui.lineCentred(
                Component.literal(Readout.pressure(menu.pressure(port))),
                x,
                CELL_WIDTH,
                y + 10,
                10,
                Ui.WHITE,
                Face.BOLD);
    }

    /** The strip of a device with two ports: port 0 into port 1. */
    protected final void strip(Ui ui, int x, int y, int w) {
        cell(ui, x, y, DeviceParts.text("input"), 0);
        cell(ui, x + w - CELL_WIDTH, y, DeviceParts.text("output"), 1);
        float middle = y + CELL_HEIGHT / 2f;
        ui.arrow(x + CELL_WIDTH + ARROW_GAP, middle, x + w - CELL_WIDTH - ARROW_GAP, middle, arrowColour());
    }

    /**
     * The strip of a device with a side port, on the side of the line that it's on: the right of the
     * way the gas goes is the bottom of the strip.
     *
     * @param first the label of port 0, at the back
     * @param side the label of port 2, on its side
     * @param sideIsInput whether gas comes in at the side
     */
    protected final void sideStrip(
            Ui ui, SidePortMenu ports, int x, int y, int w, Component first, Component side, boolean sideIsInput) {
        boolean above = ports.mirrored();
        int lineY = above ? y + CELL_HEIGHT + SIDE_GAP : y;
        int sideY = above ? y : y + CELL_HEIGHT + SIDE_GAP;
        cell(ui, x, lineY, first, 0);
        cell(ui, x + w - CELL_WIDTH, lineY, DeviceParts.text("output"), 1);
        float middle = lineY + CELL_HEIGHT / 2f;
        ui.arrow(x + CELL_WIDTH + ARROW_GAP, middle, x + w - CELL_WIDTH - ARROW_GAP, middle, arrowColour());

        int centre = x + w / 2;
        int cellX = centre - CELL_WIDTH / 2;
        cell(ui, cellX, sideY, side, 2);
        float edge = above ? sideY + CELL_HEIGHT + 1 : sideY - 1;
        if (sideIsInput) {
            ui.arrow(centre, edge, centre, middle, arrowColour());
        } else {
            ui.arrow(centre, middle, centre, edge, arrowColour());
        }
    }

    /** The state and the switch. */
    protected final void stateRow(Ui ui, int x, int y, int w) {
        int labelX = x + Ui.PAD;
        DeviceParts.label(ui, "state", labelX, y, Ui.BUTTON_HEIGHT);
        InlineStatus status = menu.status();
        DeviceParts.status(
                ui, statusWord(status), colour(status), labelX + DeviceParts.LABEL_WIDTH, y, Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(ui, x + w - Ui.PAD, y, menu.on(), () -> press(InlineDeviceMenu.TOGGLE));
    }

    /** The words of its state row. The default is the status's own. */
    protected Component statusWord(InlineStatus status) {
        return Component.translatable(status.key());
    }

    private static int colour(InlineStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case MOVING -> Ui.GOOD;
            case TARGET_REACHED, SIDE_TARGET_REACHED, OUTPUT_FULL -> Ui.LABEL;
            case INPUT_EMPTY, SAME_NETWORK, RATE_ZERO, NO_WATER -> Ui.AVERAGE;
        };
    }

    /** A target pressure's row, in the steps a side port's device has. */
    protected final void targetRow(Ui ui, int x, int y, int w, int kpa) {
        int labelX = x + Ui.PAD;
        DeviceParts.label(ui, "target", labelX, y, Ui.BUTTON_HEIGHT);
        ui.stepper(
                labelX + DeviceParts.LABEL_WIDTH,
                y,
                w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH,
                Component.literal(Readout.pressure(kpa)),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                DeviceParts.KPA,
                i -> press(SidePortMenu.STEP_DOWN_BIG + i),
                DeviceParts.text("default"),
                () -> press(SidePortMenu.TARGET_DEFAULT));
    }
}
