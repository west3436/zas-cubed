package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.heater.ThermoMachineMenu;
import io.github.west3436.zas.atmos.heater.ThermoStatus;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A heater's or air conditioner's window (CONT-019): its state and switch, its room's temperature, its
 * setpoint and its battery. Temperatures follow the player's units (CLIENT-006), while the steps are
 * in K, which is what the server applies.
 */
final class ThermoMachineScreen<M extends ThermoMachineMenu> extends TguiScreen<M> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = heightWithInventory(ThermoMachineMenu.INVENTORY_Y);
    private static final int SECTION_HEIGHT = 2 + Ui.BUTTON_ROW + Ui.ROW + Ui.BUTTON_ROW + 1 + 18 + 2;

    ThermoMachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ui.section(x, y, w, SECTION_HEIGHT, null);
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int row = y + 2;

        DeviceParts.label(ui, "state", labelX, row, Ui.BUTTON_HEIGHT);
        ThermoStatus status = menu.status();
        Component word = status.working()
                ? Component.translatable(
                        status.key(), String.format(Locale.ROOT, "%,d", menu.value(ThermoMachineMenu.DATA_WATTS)))
                : Component.translatable(status.key());
        DeviceParts.status(ui, word, colour(status), valueX, row, Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(
                ui,
                x + w - Ui.PAD,
                row,
                menu.value(ThermoMachineMenu.DATA_ON) != 0,
                () -> press(ThermoMachineMenu.TOGGLE));
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, "room", labelX, row, Ui.ROW);
        int room = menu.value(ThermoMachineMenu.DATA_ROOM);
        ui.line(
                Component.literal(room < 0 ? "—" : Readout.temperature(room / 10.0)),
                valueX,
                row,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        row += Ui.ROW;

        DeviceParts.label(ui, "setpoint", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH,
                Component.literal(Readout.temperature(menu.value(ThermoMachineMenu.DATA_SETPOINT) / 10.0)),
                Ui.WHITE,
                DeviceParts.SMALL_STEPS,
                DeviceParts.KELVIN,
                i -> press(ThermoMachineMenu.SETPOINT_DOWN_10 + i),
                null,
                null);

        slots(ui, ThermoMachineMenu.INVENTORY_Y);
        int slotX = leftPos + ThermoMachineMenu.BATTERY_X;
        int slotY = topPos + ThermoMachineMenu.BATTERY_Y;
        if (!menu.getSlot(0).hasItem()) {
            ui.ghost(slotX, slotY, new ItemStack(AtmosItems.BATTERY.get()));
        }
        int barX = slotX + 19;
        DeviceParts.charge(ui, barX, slotY + 3, x + w - Ui.PAD - barX, Ui.ROW, menu.energy(), menu.capacity());
    }

    private static int colour(ThermoStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case HEATING, COOLING -> Ui.GOOD;
            case AT_SETPOINT -> Ui.LABEL;
            case NO_BATTERY, BATTERY_EMPTY, NO_ROOM -> Ui.AVERAGE;
        };
    }
}
