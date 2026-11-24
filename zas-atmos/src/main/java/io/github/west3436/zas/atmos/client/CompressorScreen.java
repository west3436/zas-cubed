package io.github.west3436.zas.atmos.client;

import io.github.west3436.ss13.client.tgui.Readout;
import io.github.west3436.ss13.client.tgui.TguiScreen;
import io.github.west3436.ss13.client.tgui.Ui;
import io.github.west3436.ss13.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.turbine.CompressorMenu;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A compressor's own window (CONT-160, CONT-163): its engine's state and its controller, a tick and
 * where it is or a cross; then its buffer: its pressure, a bar of the fuel's, the oxidizer's and the
 * other gases' shares of it, and a row for the fuel and one for the oxidizer, each with what the
 * buffer holds and what it released at its last atmos tick. Nothing to set: its controller does that.
 */
final class CompressorScreen extends TguiScreen<CompressorMenu> {
    private static final int WIDTH = 216;
    private static final int STATE = 2 + 2 * Ui.ROW + 1;
    private static final int BAR = 5;
    private static final int GASES = 2 + Ui.ROW + BAR + 3 + 3 * Ui.ROW + 1;
    private static final int HEIGHT = Ui.TITLE_HEIGHT + 2 * Ui.PAD + STATE + Ui.PAD + GASES;

    /** The other gases' share of the bar. */
    private static final int OTHER = 0xFF56606B;

    CompressorScreen(CompressorMenu menu, Inventory inventory, Component title) {
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
        int right = x + w - Ui.PAD;

        ui.section(x, y, w, STATE, null);
        int row = y + 1;
        TurbineStatus status = menu.status();
        DeviceParts.label(ui, "state", labelX, row, Ui.ROW);
        DeviceParts.status(
                ui, TurbineScreen.state(status), TurbineControllerScreen.colour(status), valueX, row, Ui.ROW);
        row += Ui.ROW;
        DeviceParts.label(ui, "controller", labelX, row, Ui.ROW);
        TurbineScreen.link(ui, menu.linked(), menu.controller(), valueX, row);
        y += STATE + Ui.PAD;

        ui.section(x, y, w, GASES, null);
        row = y + 1;
        int total = menu.value(CompressorMenu.DATA_BUFFER_PRESSURE);
        int fuelKpa = menu.value(CompressorMenu.DATA_BUFFER_FUEL);
        int oxidizerKpa = menu.value(CompressorMenu.DATA_BUFFER_OXIDIZER);
        Optional<ResourceLocation> fuel = menu.fuel();
        Optional<ResourceLocation> oxidizer = menu.oxidizer();
        DeviceParts.label(ui, "buffer", labelX, row, Ui.ROW);
        ui.line(Component.literal(Readout.pressure(total)), valueX, row, Ui.ROW, Ui.WHITE, Face.BODY);
        row += Ui.ROW;
        int barW = right - labelX;
        ui.track(labelX, row, barW, BAR);
        if (total > 0) {
            float at = labelX;
            at = share(
                    ui,
                    at,
                    row,
                    barW,
                    fuelKpa,
                    total,
                    fuel.map(g -> GasParts.colour(g.toString())).orElse(OTHER));
            at = share(
                    ui,
                    at,
                    row,
                    barW,
                    oxidizerKpa,
                    total,
                    oxidizer.map(g -> GasParts.colour(g.toString())).orElse(OTHER));
            share(ui, at, row, barW, Math.max(0, total - fuelKpa - oxidizerKpa), total, OTHER);
        }
        row += BAR + 3;

        int heldX = right - 96;
        int releaseX = right - 46;
        ui.lineCentred(DeviceParts.text("buffer"), heldX, 46, row, Ui.ROW, Ui.LABEL, Face.BODY);
        ui.lineCentred(DeviceParts.text("release"), releaseX, 46, row, Ui.ROW, Ui.LABEL, Face.BODY);
        row += Ui.ROW;
        gas(ui, labelX, heldX, releaseX, row, "fuel", fuel, fuelKpa, menu.released(true));
        row += Ui.ROW;
        gas(ui, labelX, heldX, releaseX, row, "oxidizer", oxidizer, oxidizerKpa, menu.released(false));
    }

    /** A gas's part of the bar, from {@code at}; returns where it ends. */
    private static float share(Ui ui, float at, int y, int w, int kpa, int total, int colour) {
        float part = (float) w * Math.max(0, Math.min(kpa, total)) / total;
        ui.box(at, y, part, BAR, colour);
        return at + part;
    }

    /** A row for the fuel or the oxidizer: its name, what the buffer holds, what it released. */
    private static void gas(
            Ui ui,
            int x,
            int heldX,
            int releaseX,
            int row,
            String role,
            Optional<ResourceLocation> gas,
            int kpa,
            double released) {
        if (gas.isEmpty()) {
            ui.line(DeviceParts.text(role), x, row, Ui.ROW, Ui.DIM, Face.BODY);
            ui.lineCentred(Component.literal("—"), heldX, 46, row, Ui.ROW, Ui.DIM, Face.BODY);
            ui.lineCentred(Component.literal("—"), releaseX, 46, row, Ui.ROW, Ui.DIM, Face.BODY);
            return;
        }
        ui.line(GasParts.label(gas.get()), x, row, Ui.ROW, Ui.WHITE, Face.BODY);
        ui.lineCentred(Component.literal(Readout.pressure(kpa)), heldX, 46, row, Ui.ROW, Ui.WHITE, Face.BODY);
        ui.lineCentred(
                DeviceParts.text("mol_per_second", String.format(Locale.ROOT, "%.2f", released)),
                releaseX,
                46,
                row,
                Ui.ROW,
                released > 0 ? Ui.GOOD : Ui.DIM,
                Face.BODY);
    }
}
