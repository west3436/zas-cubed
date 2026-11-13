package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity.Slot;
import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.airlock.AirlockCycle;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.label.Labels;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * An airlock controller's window (CONT-138, CONT-153). Its first section has its state with a Stop
 * button, and the airlock drawn from side to side: the pressure at the inner sensor, the chamber
 * sensor and the outer sensor, with each door between them, green open, grey closed and red bolted.
 * Under each side, a button cycles toward it; the one it's cycling toward is lit, and one the server
 * would refuse is grey. A side or chamber with no sensor says so, and a part that isn't set shows a
 * dash. Its second section has the five slots, each a button that arms it, lit while armed, then a
 * dot, green filled, orange armed and grey empty, what fills it, and a button that clears it.
 */
final class AirlockControllerScreen extends TguiScreen<AirlockControllerMenu> {
    private static final int WIDTH = 216;
    private static final int DOOR_GAP = 9;
    private static final int SECTION_HEIGHT = 2 + Ui.BUTTON_ROW + 3 + 2 * Ui.ROW + 2 + Ui.BUTTON_ROW + 2;
    private static final int SLOTS_HEIGHT = 2 + Slot.values().length * Ui.BUTTON_ROW + 1;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + SECTION_HEIGHT + Ui.PAD + SLOTS_HEIGHT;
    private static final int CLEAR_WIDTH = 12;

    AirlockControllerScreen(AirlockControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private static Component text(String key) {
        return Component.translatable("gui.zas_atmos.airlock_controller." + key);
    }

    private boolean powered() {
        return menu.value(AirlockControllerMenu.DATA_POWERED) != 0;
    }

    /** The door it's cycling toward, 0 inner or 1 outer, or −1. */
    private int cycling() {
        return menu.value(AirlockControllerMenu.DATA_CYCLING) - 1;
    }

    /** The sensor slot for the inner side (column 0), the chamber (1) or the outer side (2). */
    private static Slot sensor(int column) {
        return column == 0 ? Slot.INNER_SENSOR : column == 1 ? Slot.CHAMBER_SENSOR : Slot.OUTER_SENSOR;
    }

    @Override
    protected int windowHeight() {
        return HEIGHT + DeviceParts.noticesHeight(powered(), false);
    }

    @Override
    protected int eyeColour() {
        return !powered() ? Ui.BAD : cycling() >= 0 ? Ui.AVERAGE : Ui.GOOD;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = DeviceParts.notices(ui, x, y, w, powered(), false, Optional.empty());
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

        // Inner side | inner door | chamber | outer door | outer side
        int inner = w - 2 * Ui.PAD;
        int col = (inner - 2 * DOOR_GAP) / 3;
        int[] cols = {labelX, labelX + col + DOOR_GAP, labelX + 2 * (col + DOOR_GAP)};
        double[] kpa = {
            menu.pressure(AirlockControllerMenu.DATA_BEYOND),
            menu.pressure(AirlockControllerMenu.DATA_CHAMBER),
            menu.pressure(AirlockControllerMenu.DATA_BEYOND + 2)
        };
        Component[] names = {text("inner"), text("chamber"), text("outer")};
        for (int i = 0; i < 3; i++) {
            ui.lineCentred(names[i], cols[i], col, row, Ui.ROW, Ui.LABEL, Face.BODY);
            boolean missing = !menu.filled(sensor(i));
            boolean show = !missing && !Double.isNaN(kpa[i]);
            ui.lineCentred(
                    missing ? text("no_sensor") : Component.literal(show ? Readout.pressure(kpa[i]) : "—"),
                    cols[i],
                    col,
                    row + Ui.ROW,
                    Ui.ROW,
                    missing ? Ui.BAD : show ? Ui.WHITE : Ui.DIM,
                    Face.BODY);
        }
        for (int i = 0; i < 2; i++) {
            int doorX = cols[i] + col + (DOOR_GAP - 3) / 2;
            ui.fill(doorX, row, 3, 2 * Ui.ROW, doorColour(i));
        }
        row += 2 * Ui.ROW + 2;

        // A cycle toward each side.
        boolean doors = menu.filled(Slot.INNER_DOOR) && menu.filled(Slot.OUTER_DOOR);
        for (int i = 0; i < 2; i++) {
            int side = i;
            boolean ready = powered()
                    && cycling() < 0
                    && doors
                    && menu.filled(Slot.CHAMBER_SENSOR)
                    && menu.filled(Slot.sensor(side));
            ui.button(
                    cols[i == AirlockCycle.INNER ? 0 : 2],
                    row,
                    col,
                    Ui.BUTTON_HEIGHT,
                    text("cycle"),
                    cycling() == i ? Tone.SELECTED : Tone.DEFAULT,
                    ready ? () -> press(AirlockControllerMenu.CYCLE + side) : null);
        }

        y += SECTION_HEIGHT + Ui.PAD;
        slots(ui, x, y, w);
    }

    /** The slots: arm, what fills it, clear. */
    private void slots(Ui ui, int x, int y, int w) {
        ui.section(x, y, w, SLOTS_HEIGHT, null);
        int buttonW = 0;
        for (Slot s : Slot.values()) {
            buttonW = Math.max(buttonW, ui.buttonWidth(text(s.key())));
        }
        Slot armed = menu.armed();
        int row = y + 2;
        for (Slot s : Slot.values()) {
            int left = x + Ui.PAD;
            boolean isArmed = s == armed;
            ui.button(
                    left,
                    row,
                    buttonW,
                    Ui.BUTTON_HEIGHT,
                    text(s.key()),
                    isArmed ? Tone.SELECTED : Tone.DEFAULT,
                    () -> press(AirlockControllerMenu.ARM + s.ordinal()));
            BlockPos at = menu.slotPos(s);
            int dotX = left + buttonW + 5;
            ui.dot(dotX, row + (Ui.BUTTON_HEIGHT - 4) / 2, isArmed ? Ui.AVERAGE : at != null ? Ui.GOOD : Ui.DIM);
            int room = x + w - Ui.PAD - CLEAR_WIDTH - Ui.PAD - (dotX + 8);
            ui.line(
                    ui.fit(Component.literal(at == null ? "—" : named(at)), Face.BODY, room),
                    dotX + 8,
                    row,
                    Ui.BUTTON_HEIGHT,
                    at == null ? Ui.DIM : Ui.WHITE,
                    Face.BODY);
            ui.button(
                    x + w - Ui.PAD - CLEAR_WIDTH,
                    row,
                    CLEAR_WIDTH,
                    Ui.BUTTON_HEIGHT,
                    Component.literal("✕"),
                    Tone.DANGER,
                    at != null ? () -> press(AirlockControllerMenu.CLEAR + s.ordinal()) : null);
            row += Ui.BUTTON_ROW;
        }
    }

    /** What fills a slot: its label, if a player gave it one and it is loaded here, or where it is. */
    private String named(BlockPos at) {
        return minecraft == null || minecraft.level == null
                ? at.getX() + " " + at.getY() + " " + at.getZ()
                : Labels.named(minecraft.level, at);
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
        if (!menu.filled(Slot.door(i))) {
            return Ui.DIM;
        }
        int bits = menu.value(AirlockControllerMenu.DATA_DOORS) >> 2 * i;
        if ((bits & 2) != 0) {
            return Ui.BAD;
        }
        return (bits & 1) != 0 ? Ui.GOOD : Ui.LABEL;
    }
}
