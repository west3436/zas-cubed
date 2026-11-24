package io.github.west3436.zas.atmos.client;

import io.github.west3436.ss13.client.tgui.Readout;
import io.github.west3436.ss13.client.tgui.TguiScreen;
import io.github.west3436.ss13.client.tgui.Ui;
import io.github.west3436.ss13.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.turbine.TurbineMenu;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A gas turbine's own window (CONT-161, CONT-163): its engine's state and its controller, a tick and
 * where it is or a cross; then its blades' speed as a bar, the gas it lets through with the chamber's
 * and the exhaust's pressures, and what its generator makes. Nothing to set: its controller does that.
 */
final class TurbineScreen extends TguiScreen<TurbineMenu> {
    private static final int WIDTH = 216;
    private static final int STATE = 2 + 2 * Ui.ROW + 1;
    private static final int READINGS = 2 + 3 * Ui.ROW + 1;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + STATE + Ui.PAD + READINGS;

    TurbineScreen(TurbineMenu menu, Inventory inventory, Component title) {
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
        TurbineStatus status = menu.status();
        DeviceParts.label(ui, "state", labelX, row, Ui.ROW);
        DeviceParts.status(
                ui, TurbineScreen.state(status), TurbineControllerScreen.colour(status), valueX, row, Ui.ROW);
        row += Ui.ROW;
        DeviceParts.label(ui, "controller", labelX, row, Ui.ROW);
        link(ui, menu.linked(), menu.controller(), valueX, row);
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
        DeviceParts.label(ui, "flow", labelX, row, Ui.ROW);
        double flow = menu.flow();
        ui.line(
                DeviceParts.text("mol_per_second", String.format(Locale.ROOT, "%.2f", flow)),
                valueX,
                row,
                Ui.ROW,
                flow > 0 ? Ui.WHITE : Ui.DIM,
                Face.BODY);
        int in = menu.value(TurbineMenu.DATA_CHAMBER_PRESSURE);
        int out = menu.value(TurbineMenu.DATA_EXHAUST_PRESSURE);
        ui.lineRight(
                Component.literal(in <= 0 ? "—" : Readout.pressure(in) + " → " + Readout.pressure(out)),
                x + w - Ui.PAD,
                row,
                Ui.ROW,
                Ui.LABEL,
                Face.BODY);
        row += Ui.ROW;
        int power = menu.power();
        DeviceParts.label(ui, "output", labelX, row, Ui.ROW);
        ui.line(DeviceParts.text("fe_per_tick", power), valueX, row, Ui.ROW, power > 0 ? Ui.GOOD : Ui.DIM, Face.BOLD);
    }

    /** A part's state: the engine's, or Unlinked with no controller (the controller's own word is "No turbine"). */
    static Component state(TurbineStatus status) {
        return status == TurbineStatus.UNLINKED ? DeviceParts.text("unlinked") : Component.translatable(status.key());
    }

    /** A tick and where its controller is, or a cross. */
    static void link(Ui ui, boolean linked, Optional<BlockPos> controller, int x, int row) {
        Component word = linked && controller.isPresent()
                ? Component.literal("✔ ").append(DeviceParts.position(controller.get()))
                : Component.literal(linked ? "✔" : "✖");
        ui.line(word, x, row, Ui.ROW, linked ? Ui.GOOD : Ui.BAD, Face.BODY);
    }
}
