package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiWindow;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.TankSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A held gas tank's window (LIFE-044): a gauge of its pressure against where it leaks, ruptures and
 * explodes, what it holds, and its distribution pressure. A button sends only the hand and its id,
 * which the server applies and checks. It doesn't pause the game, and it closes when the hand no longer
 * holds a tank.
 */
final class TankScreen extends TguiWindow {
    private static final int WIDTH = 216;
    private static final double[] GAUGE_STOPS = {
        ContainerFailure.TANK_LEAK_KPA, ContainerFailure.TANK_RUPTURE_KPA, ContainerFailure.TANK_EXPLODE_KPA
    };
    private static final int[] GAUGE_COLOURS = {Ui.GOOD, Ui.AVERAGE, Ui.BAD_FILL};
    private static final int GAUGE_RADIUS = 21;
    private static final int READINGS_HEIGHT = 26;
    private static final int OUTPUT_HEIGHT = 2 + 2 * Ui.BUTTON_ROW + 1;

    private final InteractionHand hand;

    TankScreen(InteractionHand hand, Component title) {
        super(title, WIDTH);
        this.hand = hand;
    }

    private ItemStack tank() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private void send(int id) {
        PacketDistributor.sendToServer(new TankSettings(hand.ordinal(), id));
    }

    @Override
    public void tick() {
        if (!(tank().getItem() instanceof GasTankItem)) {
            onClose();
        }
    }

    @Override
    protected Component windowTitle() {
        ItemStack tank = tank();
        return tank.isEmpty() ? title : tank.getHoverName();
    }

    private static int contentsHeight(GasParcel gas) {
        int gases = gas.moles().size();
        return 2 + READINGS_HEIGHT + (gases > 0 ? 3 + GasParts.contentsHeight(gases) : 0) + 2;
    }

    @Override
    protected int windowHeight() {
        ItemStack tank = tank();
        GasParcel gas = tank.getItem() instanceof GasTankItem ? GasTankItem.gas(tank) : GasParcel.EMPTY;
        return Ui.TITLE_HEIGHT + 2 * Ui.PAD + contentsHeight(gas) + Ui.PAD + OUTPUT_HEIGHT;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ItemStack tank = tank();
        if (!(tank.getItem() instanceof GasTankItem)) {
            return;
        }
        GasParcel gas = GasTankItem.gas(tank);
        double pressure = GasTankItem.contents(tank).pressure();
        int height = contentsHeight(gas);
        ui.section(x, y, w, height, null);
        int inner = x + Ui.PAD;
        ui.gauge(inner + GAUGE_RADIUS, y + 2 + GAUGE_RADIUS + 1, GAUGE_RADIUS, pressure, GAUGE_STOPS, GAUGE_COLOURS);

        int labelX = inner + 2 * GAUGE_RADIUS + 6;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int row = y + 3;
        DeviceParts.label(ui, "pressure", labelX, row, Ui.ROW);
        ui.line(
                Component.literal(Readout.pressure(pressure)),
                valueX,
                row,
                Ui.ROW,
                pressure > ContainerFailure.TANK_LEAK_KPA ? Ui.BAD : Ui.WHITE,
                Face.BOLD);
        row += Ui.ROW;
        DeviceParts.label(ui, "temperature", labelX, row, Ui.ROW);
        ui.line(
                Component.literal(gas.isEmpty() ? "—" : Readout.temperature(gas.temperature())),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        GasParts.contents(ui, inner, y + 2 + READINGS_HEIGHT + 3, w - 2 * Ui.PAD, gas.moles());

        y += height + Ui.PAD;
        ui.section(x, y, w, OUTPUT_HEIGHT, null);
        row = y + 2;
        double output = GasTankItem.distributionPressure(tank);
        int rowValueX = inner + DeviceParts.LABEL_WIDTH;
        DeviceParts.label(ui, "output", inner, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                rowValueX,
                row,
                w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH,
                output > 0 ? Component.literal(Readout.pressure(output)) : DeviceParts.text("closed"),
                output > 0 ? Ui.WHITE : Ui.DIM,
                DeviceParts.SMALL_STEPS,
                DeviceParts.KPA,
                i -> send(
                        switch (i) {
                            case 0 -> TankSettings.DOWN_10;
                            case 1 -> TankSettings.DOWN_1;
                            case 2 -> TankSettings.UP_1;
                            default -> TankSettings.UP_10;
                        }),
                null,
                null);
        row += Ui.BUTTON_ROW;
        Component close = DeviceParts.text("close");
        int closeWidth = ui.buttonWidth(close);
        ui.button(
                rowValueX,
                row,
                closeWidth,
                Ui.BUTTON_HEIGHT,
                close,
                Tone.DEFAULT,
                output > 0 ? () -> send(TankSettings.CLOSE) : null);
        Component reset = DeviceParts.text("default");
        ui.button(
                rowValueX + closeWidth + 1,
                row,
                ui.buttonWidth(reset),
                Ui.BUTTON_HEIGHT,
                reset,
                Tone.DEFAULT,
                () -> send(TankSettings.DEFAULT));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
