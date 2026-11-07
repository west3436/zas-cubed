package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A canister's window (CONT-084, CONT-107): a gauge of its pressure against its limits, what it holds,
 * whether it stands on a connector port, its valve and its release pressure. The gauge is green to
 * half its rating, amber to its rating, and red to where it explodes.
 */
public class CanisterScreen extends TguiScreen<CanisterMenu> {
    private static final int WIDTH = 216;
    static final double[] GAUGE_STOPS = {
        ContainerFailure.CANISTER_RATED_KPA / 2,
        ContainerFailure.CANISTER_RATED_KPA,
        ContainerFailure.CANISTER_EXPLODE_KPA
    };
    private static final int[] GAUGE_COLOURS = {Ui.GOOD, Ui.AVERAGE, Ui.BAD_FILL};
    static final int GAUGE_RADIUS = 21;
    private static final int READINGS_HEIGHT = 3 * Ui.ROW;
    private static final int RELEASE_HEIGHT = 2 + 2 * Ui.BUTTON_ROW + 1;
    private static final List<Component> VALVE = List.of(DeviceParts.text("open"), DeviceParts.text("closed"));

    public CanisterScreen(CanisterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, Ui.TITLE_HEIGHT + 2 * Ui.PAD + 100);
    }

    private int contentsHeight() {
        int gases = menu.contents().size();
        return 2 + READINGS_HEIGHT + (gases > 0 ? 3 + GasParts.contentsHeight(gases) : 0) + 2;
    }

    @Override
    protected int windowHeight() {
        return Ui.TITLE_HEIGHT + 2 * Ui.PAD + contentsHeight() + Ui.PAD + RELEASE_HEIGHT;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        int pressure = menu.value(CanisterMenu.DATA_PRESSURE);
        int height = contentsHeight();
        ui.section(x, y, w, height, null);
        int inner = x + Ui.PAD;
        ui.gauge(inner + GAUGE_RADIUS, y + 2 + GAUGE_RADIUS + 3, GAUGE_RADIUS, pressure, GAUGE_STOPS, GAUGE_COLOURS);

        int labelX = inner + 2 * GAUGE_RADIUS + 6;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int row = y + 2;
        DeviceParts.label(ui, "pressure", labelX, row, Ui.ROW);
        ui.line(
                Component.literal(Readout.pressure(pressure)),
                valueX,
                row,
                Ui.ROW,
                pressure > ContainerFailure.CANISTER_RATED_KPA / 2 ? Ui.AVERAGE : Ui.WHITE,
                Face.BOLD);
        row += Ui.ROW;
        DeviceParts.label(ui, "amount", labelX, row, Ui.ROW);
        ui.line(
                DeviceParts.text("moles", String.format(Locale.ROOT, "%,d", menu.value(CanisterMenu.DATA_MOLES))),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        row += Ui.ROW;
        DeviceParts.label(ui, "port", labelX, row, Ui.ROW);
        boolean onPort = menu.value(CanisterMenu.DATA_PORT) != 0;
        ui.line(Component.literal(onPort ? "✔" : "✕"), valueX, row, Ui.ROW, onPort ? Ui.GOOD : Ui.DIM, Face.BODY);
        row += Ui.ROW + 3;
        GasParts.contents(ui, inner, row, w - 2 * Ui.PAD, menu.contents());

        y += height + Ui.PAD;
        ui.section(x, y, w, RELEASE_HEIGHT, null);
        row = y + 2;
        int rowValueX = inner + DeviceParts.LABEL_WIDTH;
        DeviceParts.label(ui, "valve", inner, row, Ui.BUTTON_HEIGHT);
        ui.choice(
                rowValueX,
                row,
                VALVE,
                menu.value(CanisterMenu.DATA_OPEN) != 0 ? 0 : 1,
                -1,
                i -> press(CanisterMenu.TOGGLE_VALVE));
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "release", inner, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                rowValueX,
                row,
                w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH,
                Component.literal(Readout.pressure(menu.value(CanisterMenu.DATA_RELEASE))),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                DeviceParts.KPA,
                i -> press(CanisterMenu.RELEASE_DOWN_100 + i),
                DeviceParts.text("default"),
                () -> press(CanisterMenu.RELEASE_DEFAULT));
    }
}
