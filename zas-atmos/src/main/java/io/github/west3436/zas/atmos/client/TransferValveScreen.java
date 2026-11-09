package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A held TTV's window (CONT-103, CONT-104): its two tanks with an arrow from the first to the second,
 * green while the valve is open, its valve and timer, and the player's inventory. The TTV's own slot
 * is locked. Starting the timer can't be undone, so it asks for a second click. The server applies and
 * checks everything, and closes the window when the hand no longer holds the TTV, as it doesn't once
 * its timer starts.
 */
final class TransferValveScreen extends TguiScreen<TransferValveMenu> {
    private static final int HEIGHT = heightWithInventory(TransferValveMenu.INVENTORY_Y);
    private static final int TANKS_HEIGHT = TransferValveMenu.SECOND_Y + 17 + 2 - (Ui.TITLE_HEIGHT + Ui.PAD);
    private static final int CONTROLS_HEIGHT = 2 + 3 * Ui.BUTTON_ROW + 1;
    private static final List<Component> VALVE = List.of(DeviceParts.text("open"), DeviceParts.text("closed"));

    TransferValveScreen(TransferValveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TransferValveMenu.WIDTH, HEIGHT);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        TransferValve valve = menu.valve();
        ui.section(x, y, w, TANKS_HEIGHT, null);
        slots(ui, TransferValveMenu.INVENTORY_Y);
        tank(ui, valve.first(), TransferValveMenu.FIRST_Y, x + w - Ui.PAD);
        tank(ui, valve.second(), TransferValveMenu.SECOND_Y, x + w - Ui.PAD);
        float arrowX = leftPos + TransferValveMenu.TANK_X + 8;
        ui.arrow(
                arrowX,
                topPos + TransferValveMenu.FIRST_Y + 17,
                arrowX,
                topPos + TransferValveMenu.SECOND_Y - 1,
                valve.isOpen() ? Ui.GOOD : Ui.DIM);

        if (!menu.isPlaced()) {
            Slot own = menu.getSlot(menu.valveSlot());
            ui.lockedSlot(leftPos + own.x, topPos + own.y);
        }

        y += TANKS_HEIGHT + Ui.PAD;
        ui.section(x, y, w, CONTROLS_HEIGHT, null);
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int row = y + 2;
        DeviceParts.label(ui, "valve", labelX, row, Ui.BUTTON_HEIGHT);
        ui.choice(
                valueX,
                row,
                VALVE,
                valve.isOpen() ? 0 : 1,
                -1,
                valve.joined() ? i -> press(TransferValveMenu.VALVE) : null);
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "timer", labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH,
                DeviceParts.text("seconds", menu.timer()),
                Ui.WHITE,
                DeviceParts.SMALL_STEPS,
                "s",
                i -> press(TransferValveMenu.DOWN_10 + i),
                DeviceParts.text("default"),
                () -> press(TransferValveMenu.DEFAULT));
        row += Ui.BUTTON_ROW;
        Component start = DeviceParts.text("start");
        ui.confirm(
                valueX,
                row,
                ui.buttonWidth(start),
                Ui.BUTTON_HEIGHT,
                start,
                DeviceParts.text("confirm"),
                Tone.CAUTION,
                !valve.isOpen() && menu.getCarried().isEmpty() ? () -> press(TransferValveMenu.START) : null);
        if (valve.isOpen() && !menu.getCarried().isEmpty()) {
            for (int y0 : new int[] {TransferValveMenu.FIRST_Y, TransferValveMenu.SECOND_Y}) {
                ui.tooltip(leftPos + TransferValveMenu.TANK_X, topPos + y0, 16, 16, DeviceParts.text("valve_is_open"));
            }
        }
    }

    /** A tank's slot, with what it holds beside it, its name cut short if it was renamed. */
    private void tank(Ui ui, ItemStack tank, int slotY, int right) {
        int slotX = leftPos + TransferValveMenu.TANK_X;
        int y = topPos + slotY;
        if (tank.isEmpty()) {
            ui.ghost(slotX, y, new ItemStack(AtmosItems.EMPTY_TANK.get()));
            return;
        }
        int textX = slotX + 20;
        ui.clip(textX, y - 1, right - textX, 18);
        ui.line(tank.getHoverName(), textX, y - 1, 9, Ui.WHITE, Face.BODY);
        double pressure = GasTankItem.contents(tank).pressure();
        String reading = pressure > 0
                ? Readout.pressure(pressure) + ", "
                        + Readout.temperature(GasTankItem.gas(tank).temperature())
                : Readout.pressure(0);
        ui.line(Component.literal(reading), textX, y + 8, 9, Ui.LABEL, Face.BODY);
        ui.endClip();
    }

    @Override
    protected void drawOver(Ui ui) {
        if (!menu.isPlaced()) {
            Slot own = menu.getSlot(menu.valveSlot());
            ui.lock(leftPos + own.x + 11, topPos + own.y, Ui.AVERAGE);
        }
    }
}
